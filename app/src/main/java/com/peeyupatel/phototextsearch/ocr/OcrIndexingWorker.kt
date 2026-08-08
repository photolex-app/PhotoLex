package com.peeyupatel.phototextsearch.ocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.peeyupatel.phototextsearch.database.entities.OcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.PhotoClassificationEntity
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkManager worker for background OCR text extraction and indexing
 */
class OcrIndexingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "OcrIndexingWorker"
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_MEDIA_URI = "media_uri"
        const val KEY_BATCH_SIZE = "batch_size"
        const val KEY_CONTINUOUS_PROCESSING = "continuous_processing"
        const val KEY_PROCESS_ALL = "process_all"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL_PROCESSED = "total_processed"
        const val KEY_ERRORS = "errors"

        const val DEFAULT_BATCH_SIZE = 50

        // Weight given to each new instantaneous processing-rate sample when smoothing the
        // ETA estimate. Has-text vs no-text photos cost ~7-8x differently (FastTextPreScanner's
        // skip path), so a single raw sample swings wildly depending on which kind of photo
        // just finished -- a low weight keeps the shown ETA from jumping between "40 minutes"
        // and "5 hours" every update while still adapting as the true rate shifts over time.
        private const val PROCESSING_TIME_EMA_ALPHA = 0.15

        // Matches currentOcrConcurrency()'s upper cap. Experiment: give each concurrent OCR
        // slot its own TextRecognizer client instead of sharing one -- ML Kit's on-device
        // recognizer is suspected to serialize calls internally against a single shared client
        // (concurrency alone didn't help when first tried, see memory), so this tests whether
        // the bottleneck is at the shared-client level specifically. Costs more RAM (each
        // instance holds its own loaded model); worth it only if it actually unlocks real
        // parallelism -- unused pool slots during throttled/low-concurrency periods just sit
        // idle, no correctness cost.
        private const val RECOGNIZER_POOL_SIZE = 6

        private const val FOREGROUND_NOTIFICATION_ID = 3002
        private const val FOREGROUND_CHANNEL_ID = "ocr_worker_foreground"

        // Hard app-controlled safety floor for the backlog-clearing path (startContinuousProcessing
        // now runs with setRequiresBatteryNotLow(false), deliberately ignoring Android's system
        // "battery low" signal so a real backlog scan doesn't silently stall for hours -- see
        // OcrManager.startContinuousProcessing). Without this floor, nothing would stop a long
        // scan short of literally running the battery to 0%.
        private const val CRITICAL_BATTERY_PERCENT = 10
    }

    /**
     * WorkManager enforces its own ~10-minute execution window on a CoroutineWorker
     * independently of any other foreground service the app runs (OcrForegroundService's own
     * startForeground() does not exempt this worker) -- without this, "continuous processing"
     * mode (meant to run for hours across a large gallery) was getting force-cancelled roughly
     * every 10 minutes, which also broke the Devanagari progress UI (see DevanagariOcrManager).
     * Calling setForeground() here grants the extended execution time WorkManager reserves for
     * genuinely foreground work.
     */
    private suspend fun ensureForeground() {
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to promote worker to foreground, continuing anyway: ${e.message}")
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            notificationManager.createNotificationChannel(channel)
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
            .setContentTitle("OCR Processing (Latin)")
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

    private val database by lazy {
        MediaDatabase.getInstance(applicationContext)
    }

    private val ocrExtractorPool by lazy {
        List(RECOGNIZER_POOL_SIZE) { OcrTextExtractor(applicationContext) }
    }

    // Used by the low-concurrency call sites (single-image processing, fallback loop) that
    // don't need a dedicated pool slot each.
    private val ocrExtractor: OcrTextExtractor
        get() = ocrExtractorPool[0]

    private val classificationDb by lazy {
        ClassificationDatabase.getInstance(applicationContext)
    }

    private val preScanner by lazy {
        FastTextPreScanner(applicationContext)
    }

    /**
     * Ensure a fast text-presence pre-scan exists for this candidate pool, then return the
     * same list of ImageInfo reordered so has-text photos come first -- so a user's actually-
     * searchable results (documents/receipts/screenshots) appear within minutes instead of
     * after processing the whole gallery at equal priority, since a large fraction of any
     * gallery (selfies, landscapes) has no text and doesn't need to block that.
     */
    private suspend fun prioritizeByTextPresence(candidates: List<ImageInfo>): List<ImageInfo> {
        if (candidates.isEmpty()) return candidates

        val classificationDao = classificationDb.photoClassificationDao()
        val existing = classificationDao.getByMediaIds(candidates.map { it.id }).associateBy { it.mediaId }

        // Pre-scan any candidates that don't have a record yet (best-effort -- a failed/unknown
        // scan just leaves that photo unprioritized this round, it'll still get processed).
        for (candidate in candidates) {
            if (!existing.containsKey(candidate.id)) {
                val scan = try {
                    preScanner.scanWithHash(candidate.uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-scan threw for ${candidate.id}: ${e.message}")
                    FastTextPreScanner.PreScanResult(null, null)
                }
                if (scan.hasText != null) {
                    classificationDao.upsert(
                        PhotoClassificationEntity(
                            mediaId = candidate.id,
                            hasText = scan.hasText,
                            preScannedAt = System.currentTimeMillis() / 1000,
                            dHash = scan.dHash,
                            isLikelyDocumentVisually = scan.isLikelyDocument
                        )
                    )
                }
                scan.barcode?.let { barcode ->
                    classificationDb.barcodeDao().upsert(
                        com.peeyupatel.phototextsearch.database.entities.BarcodeEntity(
                            mediaId = candidate.id,
                            barcodeText = barcode.text,
                            format = barcode.format,
                            scannedAt = System.currentTimeMillis() / 1000
                        )
                    )
                }
            }
        }

        val refreshed = classificationDao.getByMediaIds(candidates.map { it.id }).associateBy { it.mediaId }
        return candidates.sortedByDescending { refreshed[it.id]?.hasText == true }
    }

    /**
     * Looks for a near-identical, already-OCR'd neighbor for each candidate using recently
     * pre-scanned rows (a proxy for date_added adjacency, since pre-scans happen in
     * date-ordered batches) plus the current candidate pool. Conservative: only flags a
     * duplicate when within DUPLICATE_HAMMING_THRESHOLD and the neighbor already has a real
     * OCR result to copy. A same-batch pair where neither photo has been OCR'd before is not
     * caught by this (documented limitation, not a correctness risk -- those photos simply get
     * OCR'd normally, same as before this feature existed).
     */
    private suspend fun findDuplicateSources(
        candidates: List<ImageInfo>,
        classifications: Map<Long, PhotoClassificationEntity>
    ): Map<Long, Long> {
        val classificationDao = classificationDb.photoClassificationDao()
        val neighborPool = (classificationDao.getRecentWithHash(100) + classifications.values)
            .filter { it.dHash != null }
            .distinctBy { it.mediaId }

        val duplicates = mutableMapOf<Long, Long>()
        for (candidate in candidates) {
            val candidateHash = classifications[candidate.id]?.dHash ?: continue
            val match = neighborPool.firstOrNull { neighbor ->
                neighbor.mediaId != candidate.id &&
                    FastTextPreScanner.hammingDistance(candidateHash, neighbor.dHash!!) <= FastTextPreScanner.DUPLICATE_HAMMING_THRESHOLD
            } ?: continue

            // Only worth skipping full OCR if the neighbor already has a real result to copy
            val sourceOcr = database.ocrTextDao().getOcrTextByMediaId(match.mediaId)
            if (sourceOcr != null) {
                duplicates[candidate.id] = match.mediaId
            }
        }
        return duplicates
    }

    /**
     * After a successful full OCR extraction, run the lightweight keyword-based category
     * classifier on the extracted text and persist the result for Smart Albums browsing.
     * Zero extra OCR cost -- the text is already extracted, this just pattern-matches it.
     */
    private suspend fun categorizeAfterExtraction(mediaId: Long, extractedText: String, imageWidth: Int = 0, imageHeight: Int = 0) {
        try {
            val entities = EntityExtractionHelper.extract(extractedText)
            val classificationDao = classificationDb.photoClassificationDao()
            val existing = classificationDao.getByMediaId(mediaId)
            val category = PhotoCategoryClassifier.classify(
                extractedText, imageWidth, imageHeight, entities, existing?.isLikelyDocumentVisually
            )
            classificationDao.upsert(
                (existing ?: PhotoClassificationEntity(
                    mediaId = mediaId,
                    hasText = extractedText.isNotBlank(),
                    preScannedAt = System.currentTimeMillis() / 1000
                )).copy(
                    category = category,
                    categorizedAt = System.currentTimeMillis() / 1000
                )
            )

            // Keep Find Similar's cached corpus in sync as new OCR results land, instead of it
            // only refreshing on its own next unrelated rebuild.
            DocumentSimilarityMatcher.onOcrResultUpdated(mediaId, extractedText)
        } catch (e: Exception) {
            Log.w(TAG, "Category classification failed for $mediaId: ${e.message}")
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        OcrConcurrencyCoordinator.markActive(OcrPipeline.LATIN)
        try {
            ensureForeground()

            val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
            val mediaUri = inputData.getString(KEY_MEDIA_URI)
            val batchSize = inputData.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE)
            val continuousProcessing = inputData.getBoolean(KEY_CONTINUOUS_PROCESSING, false)
            val processAll = inputData.getBoolean(KEY_PROCESS_ALL, false)

            return@withContext when {
                mediaId != -1L && mediaUri != null -> {
                    // Process single image
                    processSingleImage(mediaId, mediaUri)
                }
                processAll -> {
                    // Process all unprocessed images continuously
                    processContinuouslyUntilComplete(batchSize)
                }
                else -> {
                    // Process batch of unprocessed images
                    processBatchImages(batchSize, continuousProcessing)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR indexing worker failed", e)
            Result.failure(workDataOf(KEY_ERRORS to e.message))
        } finally {
            ocrExtractorPool.forEach { it.cleanup() }
            preScanner.cleanup()
            OcrConcurrencyCoordinator.markInactive(OcrPipeline.LATIN)
        }
    }
    
    /**
     * Process a single image for OCR
     */
    private suspend fun processSingleImage(mediaId: Long, mediaUri: String): Result {
        return try {
            Log.d(TAG, "Processing single image: $mediaId")
            
            // Check if already processed
            val existingOcr = database.ocrTextDao().getOcrTextByMediaId(mediaId)
            if (existingOcr != null) {
                Log.d(TAG, "Image $mediaId already processed, skipping")
                return Result.success()
            }
            
            // Extract text from image
            val uri = android.net.Uri.parse(mediaUri)
            val ocrResult = ocrExtractor.extractTextFromImage(uri)
            
            when (ocrResult) {
                is OcrResult.Success -> {
                    // Save OCR result to database
                    val ocrEntity = OcrTextEntity(
                        mediaId = mediaId,
                        extractedText = ocrResult.extractedText,
                        extractionTimestamp = System.currentTimeMillis() / 1000,
                        confidenceScore = ocrResult.confidence,
                        textBlocksCount = ocrResult.textBlocksCount,
                        processingTimeMs = ocrResult.processingTimeMs
                    )
                    
                    database.ocrTextDao().insertOcrText(ocrEntity)
                    categorizeAfterExtraction(mediaId, ocrResult.extractedText)

                    Log.d(TAG, "Successfully processed image $mediaId: ${ocrResult.extractedText.length} characters extracted")
                    
                    Result.success(workDataOf(
                        KEY_TOTAL_PROCESSED to 1,
                        KEY_PROGRESS to "Processed image $mediaId"
                    ))
                }
                is OcrResult.Error -> {
                    Log.e(TAG, "OCR failed for image $mediaId: ${ocrResult.message}")

                    // Mark failed image as processed with empty text to avoid infinite retry
                    val failedOcrEntity = OcrTextEntity(
                        mediaId = mediaId,
                        extractedText = "", // Empty text for failed OCR
                        extractionTimestamp = System.currentTimeMillis() / 1000,
                        confidenceScore = 0.0f,
                        textBlocksCount = 0,
                        processingTimeMs = 0L
                    )
                    database.ocrTextDao().insertOcrText(failedOcrEntity)

                    Log.d(TAG, "Marked failed image $mediaId as processed with empty text")

                    Result.success(workDataOf(
                        KEY_TOTAL_PROCESSED to 1,
                        KEY_PROGRESS to "Processed (failed) image $mediaId"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image $mediaId", e)
            Result.failure(workDataOf(KEY_ERRORS to e.message))
        }
    }
    
    /**
     * Process all unprocessed images continuously until complete
     */
    private suspend fun processContinuouslyUntilComplete(batchSize: Int): Result {
        Log.d(TAG, "Starting continuous processing with batch size: $batchSize")

        var totalProcessed = 0
        var totalErrors = 0
        var iterationCount = 0

        while (true) {
            iterationCount++
            Log.d(TAG, "Continuous processing iteration $iterationCount")

            // Get current progress (cheap COUNT instead of loading every processed ID)
            val totalImages = getTotalImageCount()
            val processedCount = database.ocrTextDao().getOcrTextCount()
            val remainingImages = totalImages - processedCount

            Log.d(TAG, "Progress: $processedCount/$totalImages processed, $remainingImages remaining")

            if (remainingImages <= 0) {
                Log.d(TAG, "All images processed! Stopping continuous processing.")
                updateProgressInDatabase(processedCount, totalImages, false)
                break
            }

            // Process next batch
            val result = processBatchImages(minOf(batchSize, remainingImages), false)

            when (result) {
                is Result.Success -> {
                    val batchProcessed = result.outputData.getInt(KEY_TOTAL_PROCESSED, 0)
                    totalProcessed += batchProcessed
                    Log.d(TAG, "Batch completed: $batchProcessed processed in iteration $iterationCount")

                    // If no images were processed in this batch, we're done
                    if (batchProcessed == 0) {
                        Log.d(TAG, "No more images to process. Stopping.")
                        break
                    }
                }
                is Result.Failure -> {
                    totalErrors++
                    Log.e(TAG, "Batch failed in iteration $iterationCount")

                    // Continue processing even if one batch fails
                    if (totalErrors > 5) {
                        Log.e(TAG, "Too many batch failures ($totalErrors). Stopping continuous processing.")
                        break
                    }
                }
                else -> {
                    Log.w(TAG, "Unexpected result type: $result")
                    break
                }
            }

            // Small delay between batches to prevent overwhelming the system
            kotlinx.coroutines.delay(1000)
        }

        Log.d(TAG, "Continuous processing completed: $totalProcessed total processed, $totalErrors errors, $iterationCount iterations")

        return Result.success(workDataOf(
            KEY_TOTAL_PROCESSED to totalProcessed,
            KEY_PROGRESS to "Continuous processing completed: $totalProcessed images processed"
        ))
    }

    /**
     * Process a batch of unprocessed images
     */
    private suspend fun processBatchImages(batchSize: Int, scheduleNext: Boolean = false): Result {
        return try {
            Log.d(TAG, "Processing batch of $batchSize images")

            // Get list of already processed media IDs
            val processedMediaIds = try {
                database.ocrTextDao().getAllProcessedMediaIds().toSet()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get processed media IDs, assuming none processed", e)
                emptySet<Long>()
            }
            Log.d(TAG, "Found ${processedMediaIds.size} already processed images")

            // Get total images available
            val totalAvailable = getTotalImageCount()
            Log.d(TAG, "Total images available in MediaStore: $totalAvailable")

            // Get unprocessed images from MediaStore -- fetch a larger candidate pool than the
            // actual batch size so there's something meaningful to reorder by text-presence
            // priority within (capped to keep the MediaStore query and pre-scan pass bounded).
            val candidatePoolSize = minOf(batchSize * 4, 400)
            val candidatePool = getUnprocessedImages(processedMediaIds, candidatePoolSize)
            val unprocessedImages = prioritizeByTextPresence(candidatePool).take(batchSize)
            Log.d(TAG, "Found ${unprocessedImages.size} unprocessed images to process (from a pool of ${candidatePool.size}, prioritized by text presence)")

            // Real data from this device: ~61% of photos have zero text at all per the fast
            // pre-scan, yet full OCR used to run on every single one of them anyway (the
            // pre-scan was only used to reorder priority, never to skip). Since imagesToProcess
            // is always a subset of the just-prioritized window, prioritizeByTextPresence has
            // already populated hasText for every one of them -- one batch lookup here is enough
            // to skip the expensive full-resolution OCR call on confidently-no-text photos.
            // Unknown/not-yet-scanned (null) still gets full OCR -- this only skips when we
            // already have a confident answer, never guesses.
            val classificationsForBatch = classificationDb.photoClassificationDao()
                .getByMediaIds(unprocessedImages.map { it.id })
                .associateBy { it.mediaId }
            val hasTextByMediaId = classificationsForBatch.mapValues { it.value.hasText }

            // Near-duplicate/burst-shot skip: copy an already-OCR'd neighbor's result instead
            // of re-running full extraction. Best-effort -- if this fails for any reason, every
            // image just falls through to normal processing, same as if this feature didn't exist.
            val duplicateSourceByMediaId = try {
                findDuplicateSources(unprocessedImages, classificationsForBatch)
            } catch (e: Exception) {
                Log.w(TAG, "Duplicate detection failed, continuing without it: ${e.message}")
                emptyMap()
            }

            if (unprocessedImages.isEmpty()) {
                Log.d(TAG, "No unprocessed images found using normal method")

                // Fallback: try to get some images anyway for debugging
                if (processedMediaIds.isEmpty() && totalAvailable > 0) {
                    Log.d(TAG, "No processed images in database but images exist - trying fallback method")
                    val fallbackImages = getUnprocessedImages(emptySet(), minOf(batchSize, 3))
                    if (fallbackImages.isNotEmpty()) {
                        Log.d(TAG, "Fallback found ${fallbackImages.size} images to process")
                        // Continue with fallback images
                        return processFallbackImages(fallbackImages)
                    }
                }

                Log.d(TAG, "Truly no images to process")
                return Result.success(workDataOf(
                    KEY_TOTAL_PROCESSED to 0,
                    KEY_PROGRESS to "No images to process"
                ))
            }

            val processedCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            var wasPausedMidRun = false

            // Update progress tracking
            updateProgressInDatabase(0, unprocessedImages.size, true)

            // Process images with bounded concurrency instead of one at a time -- ML Kit's
            // recognizer call is async under the hood (suspends on a callback rather than
            // blocking a thread), so running several in flight at once lets bitmap decode and
            // OCR inference for different images overlap instead of serializing everything,
            // which was the main real lever on indexing speed (bitmaps are already downsampled
            // to MAX_IMAGE_SIZE before OCR, so that wasn't the bottleneck). Scaled to the
            // device's core count (capped so background OCR doesn't visibly compete with
            // foreground UI on many-core devices) and throttled down when the device is
            // already thermal-throttled, since running full concurrency while hot burns more
            // power for less actual throughput.
            val concurrency = currentOcrConcurrency()
            val semaphore = Semaphore(concurrency)

            coroutineScope {
                for ((index, imageInfo) in unprocessedImages.withIndex()) {
                    // Check if processing should be paused -- without this, a batch already
                    // mid-flight when the user hits pause would keep running to completion and
                    // its own per-image progress update below (hardcoded isProcessing=true) would
                    // overwrite pauseProcessing()'s isProcessing=false back to true on the very
                    // next image, making the notification/UI keep showing "running" after pause.
                    // Checked once per image before dispatching it, same granularity as before.
                    val currentProgress = database.ocrProgressDao().getProgress()
                    if (currentProgress?.isPaused == true) {
                        Log.d(TAG, "OCR processing paused, stopping worker (not dispatching further images)")
                        wasPausedMidRun = true
                        break
                    }

                    // Hard safety floor: this backlog-clearing path now ignores Android's system
                    // "battery low" signal (see OcrManager.startContinuousProcessing), so nothing
                    // else stops a long scan short of 0% without this. Checked once per image
                    // dispatch, same cadence as the isPaused check above.
                    val batteryPercent = currentBatteryPercent()
                    if (batteryPercent != null && batteryPercent <= CRITICAL_BATTERY_PERCENT) {
                        Log.d(TAG, "Battery critically low (${batteryPercent}%), pausing OCR scan to preserve battery")
                        wasPausedMidRun = true
                        break
                    }

                    semaphore.acquire()
                    launch {
                        // Tracks whether the permit was already released early (right after the
                        // OCR result is persisted) so the finally block below doesn't release it
                        // a second time -- see the two categorizeAfterExtraction call sites.
                        var semaphoreReleased = false
                        try {
                            Log.d(TAG, "Processing image ${index + 1}/${unprocessedImages.size}: ${imageInfo.id}")

                            // Check if already processed (double-check)
                            val existingOcr = database.ocrTextDao().getOcrTextByMediaId(imageInfo.id)
                            if (existingOcr != null) {
                                Log.d(TAG, "Image ${imageInfo.id} already processed, skipping")
                                processedCount.incrementAndGet()
                                return@launch
                            }

                            // Near-duplicate of an already-OCR'd neighbor -- copy its result
                            // instead of running full extraction again.
                            val duplicateSourceId = duplicateSourceByMediaId[imageInfo.id]
                            if (duplicateSourceId != null) {
                                val sourceEntity = database.ocrTextDao().getOcrTextByMediaId(duplicateSourceId)
                                if (sourceEntity != null) {
                                    val copiedEntity = OcrTextEntity(
                                        mediaId = imageInfo.id,
                                        extractedText = sourceEntity.extractedText,
                                        extractionTimestamp = System.currentTimeMillis() / 1000,
                                        confidenceScore = sourceEntity.confidenceScore,
                                        textBlocksCount = sourceEntity.textBlocksCount,
                                        processingTimeMs = 0L
                                    )
                                    database.ocrTextDao().insertOcrText(copiedEntity)
                                    // Release the concurrency slot now -- the only work left
                                    // (entity extraction, similarity-cache update) doesn't need
                                    // to hold up the next image's OCR from starting.
                                    semaphore.release()
                                    semaphoreReleased = true
                                    categorizeAfterExtraction(imageInfo.id, sourceEntity.extractedText, imageInfo.width, imageInfo.height)
                                    processedCount.incrementAndGet()
                                    Log.d(TAG, "Skipped full OCR for image ${imageInfo.id} (near-duplicate of $duplicateSourceId, copied result)")
                                    updateProgressInDatabase(processedCount.get(), unprocessedImages.size, true)
                                    updateProgress(processedCount.get(), unprocessedImages.size, "Image ${imageInfo.id}")
                                    return@launch
                                }
                            }

                            // Skip the expensive full-resolution OCR call entirely when the fast
                            // pre-scan already confidently found no text -- this is the real
                            // speed lever (see comment above hasTextByMediaId), not a fallback.
                            if (hasTextByMediaId[imageInfo.id] == false) {
                                val emptyOcrEntity = OcrTextEntity(
                                    mediaId = imageInfo.id,
                                    extractedText = "",
                                    extractionTimestamp = System.currentTimeMillis() / 1000,
                                    confidenceScore = 0.0f,
                                    textBlocksCount = 0,
                                    processingTimeMs = 0L
                                )
                                database.ocrTextDao().insertOcrText(emptyOcrEntity)
                                processedCount.incrementAndGet()
                                Log.d(TAG, "Skipped full OCR for image ${imageInfo.id} (pre-scan confirmed no text)")
                                updateProgressInDatabase(processedCount.get(), unprocessedImages.size, true)
                                updateProgress(processedCount.get(), unprocessedImages.size, "Image ${imageInfo.id}")
                                return@launch
                            }

                            // Extract text from image -- round-robin across the recognizer pool
                            // so concurrent slots use separate client instances instead of one
                            // shared one (see RECOGNIZER_POOL_SIZE).
                            val ocrResult = ocrExtractorPool[index % ocrExtractorPool.size].extractTextFromImage(imageInfo.uri)

                            when (ocrResult) {
                                is OcrResult.Success -> {
                                    // Save OCR result to database
                                    val ocrEntity = OcrTextEntity(
                                        mediaId = imageInfo.id,
                                        extractedText = ocrResult.extractedText,
                                        extractionTimestamp = System.currentTimeMillis() / 1000,
                                        confidenceScore = ocrResult.confidence,
                                        textBlocksCount = ocrResult.textBlocksCount,
                                        processingTimeMs = ocrResult.processingTimeMs
                                    )

                                    database.ocrTextDao().insertOcrText(ocrEntity)
                                    // Same early-release as the duplicate-copy path above.
                                    semaphore.release()
                                    semaphoreReleased = true
                                    categorizeAfterExtraction(imageInfo.id, ocrResult.extractedText, imageInfo.width, imageInfo.height)
                                    processedCount.incrementAndGet()

                                    Log.d(TAG, "Successfully processed image ${imageInfo.id}: ${ocrResult.extractedText.length} characters extracted")
                                }
                                is OcrResult.Error -> {
                                    Log.e(TAG, "OCR failed for image ${imageInfo.id}: ${ocrResult.message}")
                                    errorCount.incrementAndGet()

                                    // Mark failed image as processed with empty text to avoid infinite retry
                                    val failedOcrEntity = OcrTextEntity(
                                        mediaId = imageInfo.id,
                                        extractedText = "", // Empty text for failed OCR
                                        extractionTimestamp = System.currentTimeMillis() / 1000,
                                        confidenceScore = 0.0f,
                                        textBlocksCount = 0,
                                        processingTimeMs = 0L
                                    )
                                    database.ocrTextDao().insertOcrText(failedOcrEntity)
                                    processedCount.incrementAndGet() // Count failed images as processed
                                }
                            }

                            // Update progress
                            updateProgressInDatabase(processedCount.get(), unprocessedImages.size, true)
                            updateProgress(processedCount.get(), unprocessedImages.size, "Image ${imageInfo.id}")

                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process image ${imageInfo.id}", e)
                            errorCount.incrementAndGet()
                        } finally {
                            if (!semaphoreReleased) semaphore.release()
                        }
                    }
                }
            } // coroutineScope waits here for all launched (in-flight) images to finish

            // Mark processing as complete if we processed all available images, or if we stopped
            // early because the user paused -- otherwise isProcessing would stay true (this
            // branch was previously only reached on true 100% completion, so a mid-batch pause
            // left isProcessing stuck at true, showing "running" in the notification/UI forever).
            val totalImages = getTotalImageCount()
            val totalProcessed = database.ocrTextDao().getOcrTextCount()
            if (totalProcessed >= totalImages || wasPausedMidRun) {
                updateProgressInDatabase(totalProcessed, totalImages, false)
                Log.d(TAG, "All images processed or paused! Total: $totalProcessed")
            }

            Log.d(TAG, "Batch processing completed: ${processedCount.get()} processed, ${errorCount.get()} errors")

            Result.success(workDataOf(
                KEY_TOTAL_PROCESSED to processedCount.get(),
                KEY_PROGRESS to "Processed ${processedCount.get()} images with ${errorCount.get()} errors"
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process batch", e)
            Result.failure(workDataOf(KEY_ERRORS to e.message))
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
     * the core-scaled/thermal-throttled budget with the Devanagari worker when both are running
     * at once instead of each independently claiming the full cap (see coordinator kdoc).
     */
    private fun currentOcrConcurrency(): Int =
        OcrConcurrencyCoordinator.concurrencyFor(OcrPipeline.LATIN, applicationContext)

    /**
     * Get unprocessed images from MediaStore
     */
    private fun getUnprocessedImages(processedIds: Set<Long>, batchSize: Int): List<ImageInfo> {
        val unprocessedImages = mutableListOf<ImageInfo>()

        try {
            Log.d(TAG, "Querying MediaStore for images...")
            val cursor = applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                ),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                Log.d(TAG, "MediaStore cursor has ${it.count} total images")
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                var checkedCount = 0
                while (it.moveToNext() && unprocessedImages.size < batchSize) {
                    val id = it.getLong(idColumn)
                    checkedCount++

                    if (!processedIds.contains(id)) {
                        val name = it.getString(nameColumn) ?: "unknown"
                        val path = it.getString(pathColumn) ?: ""
                        val width = it.getInt(widthColumn)
                        val height = it.getInt(heightColumn)
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                        unprocessedImages.add(ImageInfo(id, name, path, uri, width, height))
                        Log.d(TAG, "Added unprocessed image: $id ($name)")
                    } else {
                        Log.v(TAG, "Skipping already processed image: $id")
                    }
                }
                Log.d(TAG, "Checked $checkedCount images, found ${unprocessedImages.size} unprocessed")
            } ?: run {
                Log.w(TAG, "MediaStore cursor is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get unprocessed images", e)
        }

        return unprocessedImages
    }

    /**
     * Process fallback images for debugging
     */
    private suspend fun processFallbackImages(images: List<ImageInfo>): Result {
        Log.d(TAG, "Processing ${images.size} fallback images")

        var processedCount = 0
        for ((index, imageInfo) in images.withIndex()) {
            try {
                Log.d(TAG, "Processing fallback image ${index + 1}/${images.size}: ${imageInfo.id}")

                // Extract text from image
                val ocrResult = ocrExtractor.extractTextFromImage(imageInfo.uri)

                when (ocrResult) {
                    is OcrResult.Success -> {
                        // Save OCR result to database
                        val ocrEntity = OcrTextEntity(
                            mediaId = imageInfo.id,
                            extractedText = ocrResult.extractedText,
                            extractionTimestamp = System.currentTimeMillis() / 1000,
                            confidenceScore = ocrResult.confidence,
                            textBlocksCount = ocrResult.textBlocksCount,
                            processingTimeMs = ocrResult.processingTimeMs
                        )

                        database.ocrTextDao().insertOcrText(ocrEntity)
                        categorizeAfterExtraction(imageInfo.id, ocrResult.extractedText, imageInfo.width, imageInfo.height)
                        processedCount++

                        Log.d(TAG, "Successfully processed fallback image ${imageInfo.id}: ${ocrResult.extractedText.length} characters extracted")
                    }
                    is OcrResult.Error -> {
                        Log.e(TAG, "OCR failed for fallback image ${imageInfo.id}: ${ocrResult.message}")

                        // Mark failed image as processed with empty text to avoid infinite retry
                        val failedOcrEntity = OcrTextEntity(
                            mediaId = imageInfo.id,
                            extractedText = "", // Empty text for failed OCR
                            extractionTimestamp = System.currentTimeMillis() / 1000,
                            confidenceScore = 0.0f,
                            textBlocksCount = 0,
                            processingTimeMs = 0L
                        )
                        database.ocrTextDao().insertOcrText(failedOcrEntity)
                        processedCount++ // Count failed images as processed
                    }
                }

                // Update progress
                updateProgressInDatabase(processedCount, images.size, true)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process fallback image ${imageInfo.id}", e)
            }
        }

        return Result.success(workDataOf(
            KEY_TOTAL_PROCESSED to processedCount,
            KEY_PROGRESS to "Processed $processedCount fallback images"
        ))
    }

    /**
     * Get total number of images in MediaStore
     */
    private fun getTotalImageCount(): Int {
        return try {
            val cursor = applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )
            cursor?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get total image count", e)
            0
        }
    }

    /**
     * Update progress in database with overall progress and timing information
     */
    private suspend fun updateProgressInDatabase(processed: Int, total: Int, isProcessing: Boolean) {
        try {
            // Get overall progress instead of just batch progress
            val totalImages = getTotalImageCount()
            val totalProcessedImages = database.ocrTextDao().getOcrTextCount()

            val currentProgress = database.ocrProgressDao().getProgress()
            val currentTime = System.currentTimeMillis()

            // Calculate average processing time and estimated completion
            val averageProcessingTime = if (currentProgress != null && totalProcessedImages > 0) {
                val timeElapsed = currentTime - (currentProgress.lastUpdated * 1000)
                val imagesProcessedSinceLastUpdate = totalProcessedImages - currentProgress.processedImages
                if (imagesProcessedSinceLastUpdate > 0) {
                    val instantRateMs = timeElapsed / imagesProcessedSinceLastUpdate
                    val previousAverage = currentProgress.averageProcessingTimeMs
                    if (previousAverage > 0) {
                        (PROCESSING_TIME_EMA_ALPHA * instantRateMs + (1 - PROCESSING_TIME_EMA_ALPHA) * previousAverage).toLong()
                    } else {
                        instantRateMs
                    }
                } else {
                    currentProgress.averageProcessingTimeMs
                }
            } else {
                0L
            }

            val estimatedCompletionTime = if (averageProcessingTime > 0 && totalProcessedImages < totalImages) {
                val remainingImages = totalImages - totalProcessedImages
                currentTime + (remainingImages * averageProcessingTime)
            } else {
                0L
            }

            if (currentProgress != null) {
                val updatedProgress = currentProgress.copy(
                    processedImages = totalProcessedImages,
                    totalImages = totalImages,
                    isProcessing = isProcessing,
                    lastUpdated = currentTime / 1000,
                    averageProcessingTimeMs = averageProcessingTime,
                    estimatedCompletionTime = estimatedCompletionTime
                )
                database.ocrProgressDao().updateProgress(updatedProgress)
            } else {
                // Initialize progress if it doesn't exist
                val initialProgress = com.peeyupatel.phototextsearch.database.entities.OcrProgressEntity(
                    totalImages = totalImages,
                    processedImages = totalProcessedImages,
                    isProcessing = isProcessing,
                    lastUpdated = currentTime / 1000,
                    averageProcessingTimeMs = averageProcessingTime,
                    estimatedCompletionTime = estimatedCompletionTime
                )
                database.ocrProgressDao().insertProgress(initialProgress)
            }
            Log.d(TAG, "Updated overall progress: $totalProcessedImages/$totalImages (avg: ${averageProcessingTime}ms/image)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update progress in database", e)
        }
    }

    /**
     * Set progress for the worker
     */
    private suspend fun updateProgress(processed: Int, total: Int, currentItem: String) {
        val progress = if (total > 0) (processed * 100) / total else 0
        setProgress(workDataOf(
            KEY_PROGRESS to "Processing: $currentItem ($processed/$total)",
            KEY_TOTAL_PROCESSED to processed
        ))
    }

    /**
     * Data class for image information
     */
    data class ImageInfo(
        val id: Long,
        val name: String,
        val path: String,
        val uri: Uri,
        val width: Int = 0,
        val height: Int = 0
    )
}
