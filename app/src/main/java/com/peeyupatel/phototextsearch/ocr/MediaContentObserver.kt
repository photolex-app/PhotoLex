package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Content observer to detect new images added to MediaStore
 */
class MediaContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {
    
    companion object {
        private const val TAG = "MediaContentObserver"
        private const val CHECK_DEBOUNCE_MS = 1500L
    }

    private val database by lazy {
        MediaDatabase.getInstance(context)
    }

    private val classificationDb by lazy {
        ClassificationDatabase.getInstance(context)
    }

    private val ocrManager by lazy {
        OcrManager(context, database)
    }

    private val observerScope = CoroutineScope(Dispatchers.IO)
    private var pendingCheckJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        Log.d(TAG, "Media content changed: $uri")

        // Check if this is an image URI
        if (uri != null && isImageUri(uri)) {
            handleNewImage(uri)
        } else {
            scheduleGeneralCheck()
        }
    }

    /**
     * General media change - debounce so a burst of rapid onChange events (e.g. repeated
     * EXIF/metadata touches, or a multi-photo delete firing one notification per item) collapses
     * into a single check instead of running a full reconciliation pass per event.
     */
    private fun scheduleGeneralCheck() {
        pendingCheckJob?.cancel()
        pendingCheckJob = observerScope.launch {
            delay(CHECK_DEBOUNCE_MS)
            checkForNewImages()
        }
    }

    /**
     * Check if the URI is for an image
     */
    private fun isImageUri(uri: Uri): Boolean {
        return uri.toString().contains("images") || 
               uri.authority == MediaStore.AUTHORITY && 
               uri.path?.contains("images") == true
    }
    
    /**
     * Handle a new image being added
     */
    private fun handleNewImage(uri: Uri) {
        observerScope.launch {
            try {
                Log.d(TAG, "Processing new image: $uri")
                
                // Get image details from MediaStore
                val imageDetails = getImageDetails(uri)
                if (imageDetails != null) {
                    // Check if this image is already processed
                    val existingOcr = database.ocrTextDao().getOcrTextByMediaId(imageDetails.id)
                    if (existingOcr == null) {
                        // Update total count in progress
                        updateTotalImageCount()

                        // Process the new image -- automatic, not user-initiated, so require
                        // charging rather than running unconstrained like manual "index now"
                        ocrManager.processImage(imageDetails, requireCharging = true)

                        Log.d(TAG, "Started OCR processing for new image: ${imageDetails.id}")
                    }
                } else {
                    // This item-specific URI no longer resolves to a row -- the most common
                    // reason is that it was just deleted (MediaStore also notifies per-item on
                    // delete, not just insert). Piggyback on the same debounced general check
                    // (which now also reconciles orphaned OCR/classification rows) instead of
                    // silently doing nothing, which was the previous behavior.
                    scheduleGeneralCheck()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle new image", e)
            }
        }
    }
    
    /**
     * Check for new images in general
     */
    private suspend fun checkForNewImages() {
        try {
            // Update total image count
            updateTotalImageCount()

            // Check if there are unprocessed images (cheap COUNT instead of loading every ID)
            val processedCount = database.ocrTextDao().getOcrTextCount()
            val totalImages = getTotalImageCount()

            if (processedCount < totalImages) {
                Log.d(TAG, "Found ${totalImages - processedCount} unprocessed images")

                // Start batch processing for unprocessed images -- automatic re-index
                // triggered by a MediaStore content change, so require charging rather than
                // running unconstrained like a manual "index now" batch
                ocrManager.processBatch(requireCharging = true)
            }

            // Clean up any OCR/classification/similarity data left behind by photos that no
            // longer exist -- deletion (in-app, via another app, or trash-then-permanent-delete)
            // was never reconciled anywhere before this, so those rows lived on forever and
            // could keep surfacing in search/Find Similar results for photos that were gone.
            reconcileDeletedImages()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for new images", e)
        }
    }

    /**
     * Diffs the media IDs both OCR tables have processed against the media IDs MediaStore
     * currently reports, and deletes every row (OCR text, classification, barcode, Find Similar
     * cache) for anything no longer present. Runs opportunistically on the same debounced
     * "general media change" signal used for detecting new images, rather than needing a
     * dedicated deletion hook at every delete call site (in-app permanent delete, trash auto-
     * expiry, or a photo removed by an entirely different app all funnel through the same
     * MediaStore ContentObserver notification).
     */
    private suspend fun reconcileDeletedImages() {
        try {
            val liveIds = getAllMediaStoreImageIds()
            val processedIds = (database.ocrTextDao().getAllProcessedMediaIds() +
                database.devanagariOcrTextDao().getAllProcessedMediaIds()).toSet()

            val orphanedIds = (processedIds - liveIds).toList()
            if (orphanedIds.isEmpty()) return

            Log.d(TAG, "Found ${orphanedIds.size} orphaned OCR rows for deleted photos, cleaning up")

            database.ocrTextDao().deleteOcrTextsByMediaIds(orphanedIds)
            database.devanagariOcrTextDao().deleteOcrTextsByMediaIds(orphanedIds)
            classificationDb.photoClassificationDao().deleteByMediaIds(orphanedIds)
            classificationDb.barcodeDao().deleteByMediaIds(orphanedIds)
            DocumentSimilarityMatcher.evict(orphanedIds)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconcile deleted images", e)
        }
    }

    /**
     * Every image ID MediaStore currently reports -- used to diff against what's been OCR'd so
     * far and find rows for photos that no longer exist. A single indexed-column scan (just
     * _ID, no other projection), same cost class as getTotalImageCount() above.
     */
    private fun getAllMediaStoreImageIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    ids.add(it.getLong(idColumn))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all MediaStore image IDs", e)
        }
        return ids
    }
    
    /**
     * Get image details from MediaStore
     */
    private fun getImageDetails(uri: Uri): ImageDetails? {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                ),
                null,
                null,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    
                    ImageDetails(id, displayName, path, uri)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image details", e)
            null
        }
    }
    
    /**
     * Update total image count in progress tracking
     */
    private suspend fun updateTotalImageCount() {
        try {
            val totalImages = getTotalImageCount()
            val currentProgress = database.ocrProgressDao().getProgress()
            
            if (currentProgress != null) {
                database.ocrProgressDao().updateTotalCount(totalImages)
            } else {
                // Initialize progress tracking
                val initialProgress = com.peeyupatel.phototextsearch.database.entities.OcrProgressEntity(
                    totalImages = totalImages,
                    processedImages = 0,
                    isProcessing = false,
                    lastUpdated = System.currentTimeMillis() / 1000
                )
                database.ocrProgressDao().insertProgress(initialProgress)
            }
            
            Log.d(TAG, "Updated total image count to: $totalImages")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update total image count", e)
        }
    }
    
    /**
     * Get total number of images in MediaStore
     */
    private fun getTotalImageCount(): Int {
        return try {
            val cursor = context.contentResolver.query(
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
     * Data class for image details
     */
    data class ImageDetails(
        val id: Long,
        val displayName: String,
        val path: String,
        val uri: Uri
    )
}
