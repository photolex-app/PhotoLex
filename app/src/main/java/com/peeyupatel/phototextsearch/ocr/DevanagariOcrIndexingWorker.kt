package com.peeyupatel.phototextsearch.ocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.workDataOf
import com.peeyupatel.phototextsearch.MainActivity
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.database.entities.DevanagariOcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.PhotoClassificationEntity
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import android.provider.MediaStore
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

/**
 * Worker for processing Devanagari OCR in background
 */
class DevanagariOcrIndexingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DevanagariOcrIndexingWorker"
        
        // Input data keys
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_MEDIA_URI = "media_uri"
        const val KEY_BATCH_SIZE = "batch_size"
        const val KEY_CONTINUOUS_PROCESSING = "continuous_processing"
        const val KEY_PROCESS_ALL = "process_all"
        
        // Output data keys
        const val KEY_TOTAL_PROCESSED = "total_processed"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        
        // Default values
        const val DEFAULT_BATCH_SIZE = 50

        private const val FOREGROUND_NOTIFICATION_ID = 3003
        private const val FOREGROUND_CHANNEL_ID = "ocr_worker_foreground"

        // Hard app-controlled safety floor for the backlog-clearing path -- same rationale as
        // OcrIndexingWorker.CRITICAL_BATTERY_PERCENT (startContinuousProcessing deliberately
        // ignores Android's system "battery low" signal so a real backlog scan doesn't silently
        // stall for hours, so this is what actually stops a long scan before 0%).
        private const val CRITICAL_BATTERY_PERCENT = 10
    }

    private val textExtractor = DevanagariOcrTextExtractor(applicationContext)
    private val notificationManager = DevanagariOcrNotificationManager(applicationContext)
    private val classificationDb by lazy { ClassificationDatabase.getInstance(applicationContext) }
    private val preScanner by lazy { FastTextPreScanner(applicationContext) }
    private val languageGate by lazy { DevanagariLanguageGate(applicationContext) }

    /**
     * Same missing-setForeground gap and fix as OcrIndexingWorker.ensureForeground() -- see
     * that comment for the full rationale. Uses the same shared channel ID (harmless to
     * re-declare) but a distinct notification ID so this worker's own foreground association
     * doesn't collide with the Latin worker's.
     */
    private suspend fun ensureForeground() {
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to promote worker to foreground, continuing anyway: ${e.message}")
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val systemNotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "OCR Background Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            systemNotificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_ocr_settings", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_CHANNEL_ID)
            .setContentTitle("OCR Processing (Devanagari)")
            .setContentText("Processing images in background")
            .setSmallIcon(R.drawable.ocr)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Reorders unprocessed images so has-text photos (per the shared fast pre-scan, also used
     * by the Latin OCR worker) come first -- same priority-indexing fix as OcrIndexingWorker,
     * reusing the same ClassificationDatabase so the pre-scan only ever runs once per photo
     * regardless of which language pipeline gets to it first.
     */
    private suspend fun prioritizeByTextPresence(candidates: List<ImageInfo>): List<ImageInfo> {
        if (candidates.isEmpty()) return candidates
        val dao = classificationDb.photoClassificationDao()
        val existing = dao.getByMediaIds(candidates.map { it.id }).associateBy { it.mediaId }

        for (candidate in candidates) {
            if (!existing.containsKey(candidate.id)) {
                val hasText = try {
                    preScanner.hasTextOrNull(candidate.uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-scan threw for ${candidate.id}: ${e.message}")
                    null
                }
                if (hasText != null) {
                    dao.upsert(
                        PhotoClassificationEntity(
                            mediaId = candidate.id,
                            hasText = hasText,
                            preScannedAt = System.currentTimeMillis() / 1000
                        )
                    )
                }
            }
        }

        val refreshed = dao.getByMediaIds(candidates.map { it.id }).associateBy { it.mediaId }
        return candidates.sortedByDescending { refreshed[it.id]?.hasText == true }
    }

    /**
     * Category classification after successful extraction, same keyword-based approach and
     * shared table as the Latin worker -- category isn't language-specific.
     */
    private suspend fun categorizeAfterExtraction(mediaId: Long, extractedText: String, imageWidth: Int = 0, imageHeight: Int = 0) {
        try {
            val category = PhotoCategoryClassifier.classify(extractedText, imageWidth, imageHeight)
            val dao = classificationDb.photoClassificationDao()
            val existing = dao.getByMediaId(mediaId)
            dao.upsert(
                (existing ?: PhotoClassificationEntity(
                    mediaId = mediaId,
                    hasText = extractedText.isNotBlank(),
                    preScannedAt = System.currentTimeMillis() / 1000
                )).copy(category = category, categorizedAt = System.currentTimeMillis() / 1000)
            )

            DocumentSimilarityMatcher.onOcrResultUpdated(mediaId, extractedText)
        } catch (e: Exception) {
            Log.w(TAG, "Category classification failed for $mediaId: ${e.message}")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 === DEVANAGARI OCR WORKER STARTED ===")
        Log.d(TAG, "Worker ID: $id")
        Log.d(TAG, "Run attempt: $runAttemptCount")

        OcrConcurrencyCoordinator.markActive(OcrPipeline.DEVANAGARI)
        try {
            ensureForeground()

            Log.d(TAG, "Creating database instance...")
            // Get database instance
            val database = MediaDatabase.getInstance(applicationContext)
            Log.d(TAG, "✅ Database instance created successfully")

            Log.d(TAG, "Parsing input data...")
            val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
            val mediaUri = inputData.getString(KEY_MEDIA_URI)
            val batchSize = inputData.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE)
            val continuousProcessing = inputData.getBoolean(KEY_CONTINUOUS_PROCESSING, false)
            val processAll = inputData.getBoolean(KEY_PROCESS_ALL, false)

            Log.d(TAG, "Input parameters:")
            Log.d(TAG, "  mediaId: $mediaId")
            Log.d(TAG, "  mediaUri: $mediaUri")
            Log.d(TAG, "  batchSize: $batchSize")
            Log.d(TAG, "  continuousProcessing: $continuousProcessing")
            Log.d(TAG, "  processAll: $processAll")

            return@withContext when {
                mediaId != -1L && mediaUri != null -> {
                    Log.d(TAG, "📷 Processing single image: $mediaId")
                    // Process single image
                    processSingleImage(database, mediaId, Uri.parse(mediaUri))
                }
                continuousProcessing || processAll -> {
                    Log.d(TAG, "📚 Processing batch of images (batchSize: $batchSize, processAll: $processAll)")
                    // Process batch of images
                    processBatchImages(database, batchSize, processAll)
                }
                else -> {
                    Log.e(TAG, "❌ Invalid input parameters for Devanagari OCR worker")
                    Log.e(TAG, "mediaId: $mediaId, mediaUri: $mediaUri, continuous: $continuousProcessing, processAll: $processAll")
                    Result.failure(workDataOf(KEY_ERROR to "Invalid input parameters"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Devanagari OCR worker failed with exception", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            return@withContext Result.failure(workDataOf(KEY_ERROR to e.message))
        } finally {
            Log.d(TAG, "🧹 Cleaning up text extractor...")
            textExtractor.cleanup()
            preScanner.cleanup()
            languageGate.cleanup()
            OcrConcurrencyCoordinator.markInactive(OcrPipeline.DEVANAGARI)
            Log.d(TAG, "🏁 === DEVANAGARI OCR WORKER FINISHED ===")
        }
    }

    /**
     * Process a single image for OCR
     */
    private suspend fun processSingleImage(
        database: MediaDatabase,
        mediaId: Long,
        imageUri: Uri
    ): Result {
        Log.d(TAG, "Processing single Devanagari OCR for image: $mediaId")

        try {
            // Check if already processed
            val existingText = database.devanagariOcrTextDao().getOcrTextByMediaId(mediaId)
            if (existingText != null) {
                Log.d(TAG, "Image $mediaId already processed for Devanagari OCR, skipping")
                return Result.success(workDataOf(
                    KEY_TOTAL_PROCESSED to 0,
                    KEY_PROGRESS to "Image already processed"
                ))
            }

            // Extract text using Devanagari OCR
            val ocrResult = textExtractor.extractTextFromImage(imageUri)
            
            return when (ocrResult) {
                is DevanagariOcrResult.Success -> {
                    // Save OCR result to database
                    val ocrEntity = DevanagariOcrTextEntity(
                        mediaId = mediaId,
                        extractedText = ocrResult.extractedText,
                        extractionTimestamp = System.currentTimeMillis() / 1000,
                        confidenceScore = ocrResult.confidence,
                        textBlocksCount = ocrResult.textBlocksCount,
                        processingTimeMs = ocrResult.processingTimeMs
                    )

                    database.devanagariOcrTextDao().insertOcrText(ocrEntity)
                    categorizeAfterExtraction(mediaId, ocrResult.extractedText)

                    Log.d(TAG, "Successfully processed Devanagari OCR for image $mediaId: ${ocrResult.extractedText.length} characters extracted")

                    Result.success(workDataOf(
                        KEY_TOTAL_PROCESSED to 1,
                        KEY_PROGRESS to "Processed image $mediaId"
                    ))
                }
                is DevanagariOcrResult.Error -> {
                    Log.e(TAG, "Devanagari OCR failed for image $mediaId: ${ocrResult.message}")

                    // Update failed count in progress
                    database.devanagariOcrProgressDao().incrementFailedCount()

                    Result.failure(workDataOf(
                        KEY_ERROR to "Devanagari OCR failed: ${ocrResult.message}"
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception processing Devanagari OCR for image $mediaId", e)
            
            // Update failed count in progress
            database.devanagariOcrProgressDao().incrementFailedCount()
            
            return Result.failure(workDataOf(
                KEY_ERROR to "Exception: ${e.message}"
            ))
        }
    }

    /**
     * Process batch of images for OCR
     */
    private suspend fun processBatchImages(
        database: MediaDatabase,
        batchSize: Int,
        processAll: Boolean
    ): Result {
        Log.d(TAG, "🔄 === STARTING DEVANAGARI BATCH PROCESSING ===")
        Log.d(TAG, "Batch size: $batchSize, Process all: $processAll")

        try {
            Log.d(TAG, "📱 Getting all images from MediaStore...")
            // Get all images from MediaStore
            val allImages = getAllImages()
            Log.d(TAG, "📊 Found ${allImages.size} total images for Devanagari OCR processing")

            if (allImages.isEmpty()) {
                Log.w(TAG, "⚠️ No images found for Devanagari OCR processing")
                return Result.success(workDataOf(
                    KEY_TOTAL_PROCESSED to 0,
                    KEY_PROGRESS to "No images found"
                ))
            }

            // Initialize or update progress tracking
            val totalImages = allImages.size
            Log.d(TAG, "🗃️ Checking existing progress in database...")
            val existingProgress = database.devanagariOcrProgressDao().getProgress()
            Log.d(TAG, "Existing progress: $existingProgress")

            if (existingProgress == null) {
                Log.d(TAG, "📝 Creating initial progress tracking...")
                val initialProgress = com.peeyupatel.phototextsearch.database.entities.DevanagariOcrProgressEntity(
                    totalImages = totalImages,
                    processedImages = 0,
                    isProcessing = true,
                    isPaused = false,
                    lastUpdated = System.currentTimeMillis() / 1000
                )
                database.devanagariOcrProgressDao().insertProgress(initialProgress)
                Log.d(TAG, "✅ Created initial Devanagari progress tracking for $totalImages images")
            } else {
                Log.d(TAG, "📝 Updating existing progress tracking...")
                database.devanagariOcrProgressDao().updateTotalCount(totalImages)
                database.devanagariOcrProgressDao().updateProcessingStatus(true)
                Log.d(TAG, "✅ Updated existing Devanagari progress tracking: ${existingProgress.processedImages}/$totalImages")
            }

            // Get already processed images to avoid reprocessing
            val processedMediaIds = database.devanagariOcrTextDao().getAllProcessedMediaIds().toSet()
            val unprocessedImages = allImages.filter { it.id !in processedMediaIds }

            Log.d(TAG, "Found ${unprocessedImages.size} unprocessed images for Devanagari OCR (${processedMediaIds.size} already processed)")

            if (unprocessedImages.isEmpty()) {
                Log.d(TAG, "All images already processed for Devanagari OCR")
                database.devanagariOcrProgressDao().updateProcessingStatus(false)
                return Result.success(workDataOf(
                    KEY_TOTAL_PROCESSED to 0,
                    KEY_PROGRESS to "All images already processed"
                ))
            }

            // Process images in batches -- prioritize has-text photos first (same fix as the
            // Latin worker) so real, searchable documents get processed before photos with no
            // text at all, instead of equal-priority date-order processing. Only prioritize a
            // bounded prefix (not the entire remaining backlog, which could be thousands of
            // images) so this doesn't itself become a slow upfront pre-scan pass -- the rest
            // of the backlog (beyond this prioritized window) is appended unprioritized after.
            val priorityWindowSize = minOf(maxOf(batchSize * 4, 400), unprocessedImages.size)
            val priorityWindow = unprocessedImages.take(priorityWindowSize)
            val remainder = unprocessedImages.drop(priorityWindowSize)
            val prioritized = prioritizeByTextPresence(priorityWindow) + remainder
            val imagesToProcess = if (processAll) prioritized else prioritized.take(batchSize)
            val processedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            var wasPausedMidRun = false

            // Real data from this device: ~61% of photos have zero text at all per the fast
            // pre-scan (shared with the Latin worker), yet full Devanagari OCR used to run on
            // every one of them anyway. prioritizeByTextPresence already populated hasText for
            // everything in priorityWindow, so one batch lookup is enough to skip the expensive
            // full-resolution OCR call on confidently-no-text photos. Unknown/not-yet-scanned
            // (null, e.g. the unprioritized remainder) still gets full OCR -- only skips on a
            // confident answer, never guesses.
            //
            // Chunked (not one IN-clause query for all of imagesToProcess) -- when processAll is
            // true, imagesToProcess is the *entire* remaining backlog, which can be many
            // thousands of media IDs on a large gallery. A single query with that many bound
            // parameters sat well past SQLite's default ~999-variable limit and stalled the
            // worker indefinitely on every restart with a large backlog remaining (observed:
            // 13,896 remaining images, worker never got past this line). 900 stays safely under
            // that limit regardless of device/SQLite build.
            val hasTextByMediaId = imagesToProcess.map { it.id }.chunked(900)
                .flatMap { classificationDb.photoClassificationDao().getByMediaIds(it) }
                .associateBy({ it.mediaId }, { it.hasText })

            Log.d(TAG, "Processing ${imagesToProcess.size} images for Devanagari OCR")

            // Bounded concurrency instead of one-at-a-time processing (see matching comment in
            // OcrIndexingWorker.kt) -- also drops the old flat 100ms-per-image delay, which was
            // costing ~28 minutes of pure sleep across a 17k-photo gallery on its own; the
            // concurrency cap below is the real throttle now. Same core-scaled/thermal-throttled
            // treatment as the Latin worker (currentOcrConcurrency()) -- this pipeline used to
            // hardcode 3 regardless of device capability or heat, unlike Latin.
            val concurrency = currentOcrConcurrency()
            val semaphore = Semaphore(concurrency)

            // Records a skipped (not actually OCR'd) image as an empty Devanagari OCR entity and
            // updates progress -- shared by the pre-scan no-text skip and the language-gate
            // non-Hindi skip below, which differ only in why the image was skipped.
            suspend fun recordSkippedDevanagari(imageInfo: ImageInfo, reason: String) {
                val emptyOcrEntity = DevanagariOcrTextEntity(
                    mediaId = imageInfo.id,
                    extractedText = "",
                    extractionTimestamp = System.currentTimeMillis() / 1000,
                    confidenceScore = 0.0f,
                    textBlocksCount = 0,
                    processingTimeMs = 0L
                )
                database.devanagariOcrTextDao().insertOcrText(emptyOcrEntity)
                processedCount.incrementAndGet()
                Log.d(TAG, "Skipped full Devanagari OCR for image ${imageInfo.id} ($reason)")
                val skippedTotalProcessed = database.devanagariOcrTextDao().getOcrTextCount()
                database.devanagariOcrProgressDao().updateProcessedCount(skippedTotalProcessed)
            }

            coroutineScope {
                for ((index, imageInfo) in imagesToProcess.withIndex()) {
                    // Check if processing should be paused, once per image before dispatching it
                    val currentProgress = database.devanagariOcrProgressDao().getProgress()
                    if (currentProgress?.isPaused == true) {
                        Log.d(TAG, "Devanagari OCR processing paused, stopping worker (not dispatching further images)")
                        wasPausedMidRun = true
                        break
                    }

                    // Hard safety floor: this backlog-clearing path ignores Android's system
                    // "battery low" signal (see DevanagariOcrManager.startContinuousProcessing),
                    // so nothing else stops a long scan short of 0% without this. Checked once
                    // per image dispatch, same cadence as the isPaused check above.
                    val batteryPercent = currentBatteryPercent()
                    if (batteryPercent != null && batteryPercent <= CRITICAL_BATTERY_PERCENT) {
                        Log.d(TAG, "Battery critically low (${batteryPercent}%), pausing OCR scan to preserve battery")
                        wasPausedMidRun = true
                        break
                    }

                    semaphore.acquire()
                    launch {
                        try {
                            Log.d(TAG, "Processing Devanagari OCR for image ${index + 1}/${imagesToProcess.size}: ${imageInfo.id}")

                            // Skip the expensive full-resolution OCR call entirely when the fast
                            // pre-scan already confidently found no text.
                            if (hasTextByMediaId[imageInfo.id] == false) {
                                recordSkippedDevanagari(imageInfo, "pre-scan confirmed no text")
                                return@launch
                            }

                            // Skip the expensive full-resolution Devanagari OCR call when a
                            // cheap pre-check (small Devanagari-recognizer pass + language-id)
                            // confidently shows this photo's text isn't Hindi -- same skip
                            // pattern as the no-text case above, just narrower.
                            if (languageGate.isConfidentlyNotDevanagari(imageInfo.uri)) {
                                recordSkippedDevanagari(imageInfo, "language-id confirmed non-Hindi content")
                                return@launch
                            }

                            // Extract text using Devanagari OCR
                            val ocrResult = textExtractor.extractTextFromImage(imageInfo.uri)

                            when (ocrResult) {
                                is DevanagariOcrResult.Success -> {
                                    // Save OCR result to database
                                    val ocrEntity = DevanagariOcrTextEntity(
                                        mediaId = imageInfo.id,
                                        extractedText = ocrResult.extractedText,
                                        extractionTimestamp = System.currentTimeMillis() / 1000,
                                        confidenceScore = ocrResult.confidence,
                                        textBlocksCount = ocrResult.textBlocksCount,
                                        processingTimeMs = ocrResult.processingTimeMs
                                    )

                                    database.devanagariOcrTextDao().insertOcrText(ocrEntity)
                                    categorizeAfterExtraction(imageInfo.id, ocrResult.extractedText, imageInfo.width, imageInfo.height)
                                    processedCount.incrementAndGet()

                                    Log.d(TAG, "Successfully processed Devanagari OCR for image ${imageInfo.id}: ${ocrResult.extractedText.length} characters extracted")
                                }
                                is DevanagariOcrResult.Error -> {
                                    Log.e(TAG, "Devanagari OCR failed for image ${imageInfo.id}: ${ocrResult.message}")
                                    failedCount.incrementAndGet()
                                    database.devanagariOcrProgressDao().incrementFailedCount()
                                }
                            }

                            // Update progress
                            val totalProcessedImages = database.devanagariOcrTextDao().getOcrTextCount()
                            database.devanagariOcrProgressDao().updateProcessedCount(totalProcessedImages)

                        } catch (e: Exception) {
                            Log.e(TAG, "Exception processing Devanagari OCR for image ${imageInfo.id}", e)
                            failedCount.incrementAndGet()
                            database.devanagariOcrProgressDao().incrementFailedCount()
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            } // coroutineScope waits here for all launched (in-flight) images to finish

            // Update final processing status. This must also cover the "paused mid-continuous-run"
            // case (wasPausedMidRun) -- without it, isProcessing stays true forever after a pause
            // during continuous processing (neither isComplete nor !processAll is true in that
            // case), so the notification/UI kept showing "running" even though the worker had
            // correctly stopped doing any actual work.
            val finalProgress = database.devanagariOcrProgressDao().getProgress()
            val isComplete = finalProgress?.isComplete == true

            if (isComplete || !processAll || wasPausedMidRun) {
                database.devanagariOcrProgressDao().updateProcessingStatus(false)
                Log.d(TAG, "Devanagari OCR batch processing completed or paused")
            }

            Log.d(TAG, "Devanagari OCR batch processing finished: ${processedCount.get()} processed, ${failedCount.get()} failed")

            return Result.success(workDataOf(
                KEY_TOTAL_PROCESSED to processedCount.get(),
                KEY_PROGRESS to "Processed ${processedCount.get()} images, ${failedCount.get()} failed"
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Devanagari OCR batch processing failed", e)
            database.devanagariOcrProgressDao().updateProcessingStatus(false)
            return Result.failure(workDataOf(
                KEY_ERROR to "Batch processing failed: ${e.message}"
            ))
        }
    }

    /**
     * Reads the current battery percentage (0-100), or null if unavailable. Used as the hard
     * safety floor for the backlog-clearing path -- see CRITICAL_BATTERY_PERCENT.
     */
    private fun currentBatteryPercent(): Int? {
        return try {
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level in 0..100) level else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery level", e)
            null
        }
    }

    /**
     * Concurrency for the batch OCR loop: delegates to OcrConcurrencyCoordinator, which shares
     * the core-scaled/thermal-throttled budget with the Latin worker when both are running at
     * once instead of each independently claiming the full cap (see coordinator kdoc).
     */
    private fun currentOcrConcurrency(): Int =
        OcrConcurrencyCoordinator.concurrencyFor(OcrPipeline.DEVANAGARI, applicationContext)

    /**
     * Get all images from MediaStore
     */
    private fun getAllImages(): List<ImageInfo> {
        val images = mutableListOf<ImageInfo>()

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val cursor = applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val data = it.getString(dataColumn)
                    val name = it.getString(nameColumn)
                    val width = it.getInt(widthColumn)
                    val height = it.getInt(heightColumn)

                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    images.add(ImageInfo(id, uri, name, data, width, height))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get images from MediaStore for Devanagari OCR", e)
        }

        return images
    }

    /**
     * Data class for image information
     */
    private data class ImageInfo(
        val id: Long,
        val uri: Uri,
        val name: String,
        val path: String,
        val width: Int = 0,
        val height: Int = 0
    )
}
