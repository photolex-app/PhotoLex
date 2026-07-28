package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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
            // General media change - debounce so a burst of rapid onChange events
            // (e.g. repeated EXIF/metadata touches) collapses into a single check
            pendingCheckJob?.cancel()
            pendingCheckJob = observerScope.launch {
                delay(CHECK_DEBOUNCE_MS)
                checkForNewImages()
            }
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
                        
                        // Process the new image
                        ocrManager.processImage(imageDetails)
                        
                        Log.d(TAG, "Started OCR processing for new image: ${imageDetails.id}")
                    }
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

                // Start batch processing for unprocessed images
                ocrManager.processBatch()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for new images", e)
        }
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
