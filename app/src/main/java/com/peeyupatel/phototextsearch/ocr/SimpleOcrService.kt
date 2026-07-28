package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.database.entities.OcrTextEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple OCR service for processing images in background
 * Based on ScreenshotGo's approach - simple and effective
 */
class SimpleOcrService(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleOcrService"
        private const val BATCH_SIZE = 10
        private const val FUZZY_MAX_EDIT_DISTANCE = 2
        private const val FUZZY_CANDIDATE_LIMIT = 200

        @Volatile
        private var INSTANCE: SimpleOcrService? = null

        fun getInstance(context: Context): SimpleOcrService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimpleOcrService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val database by lazy {
        MediaDatabase.getInstance(context)
    }

    private val classificationDb by lazy {
        ClassificationDatabase.getInstance(context)
    }

    private var isProcessing = false
    
    /**
     * Start OCR processing for all unprocessed images
     */
    fun startOcrProcessing() {
        if (isProcessing) {
            Log.d(TAG, "OCR processing already in progress")
            return
        }
        
        Log.d(TAG, "Starting OCR processing...")
        isProcessing = true
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processAllImages()
            } catch (e: Exception) {
                Log.e(TAG, "OCR processing failed", e)
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * Process a single image immediately
     */
    fun processImage(mediaId: Long, imageUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MLKitTextHelper.processImageAndSaveText(
                    context = context,
                    mediaId = mediaId,
                    imageUri = imageUri,
                    database = database
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process single image $mediaId", e)
            }
        }
    }
    
    /**
     * Get all unprocessed images and process them
     */
    private suspend fun processAllImages() {
        Log.d(TAG, "Getting unprocessed images...")
        
        // Get already processed media IDs
        val processedIds = try {
            database.ocrTextDao().getAllProcessedMediaIds().toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get processed IDs, assuming none processed", e)
            emptySet<Long>()
        }
        
        Log.d(TAG, "Found ${processedIds.size} already processed images")
        
        // Get all images from MediaStore
        val allImages = getAllImagesFromMediaStore()
        Log.d(TAG, "Found ${allImages.size} total images in MediaStore")
        
        // Filter unprocessed images
        val unprocessedImages = allImages.filter { !processedIds.contains(it.mediaId) }
        Log.d(TAG, "Found ${unprocessedImages.size} unprocessed images")
        
        if (unprocessedImages.isEmpty()) {
            Log.d(TAG, "No images to process")
            return
        }
        
        // Process in batches
        val batches = unprocessedImages.chunked(BATCH_SIZE)
        Log.d(TAG, "Processing ${batches.size} batches of up to $BATCH_SIZE images each")
        
        for ((batchIndex, batch) in batches.withIndex()) {
            Log.d(TAG, "Processing batch ${batchIndex + 1}/${batches.size}")
            
            MLKitTextHelper.batchProcessImages(
                context = context,
                imageInfoList = batch,
                database = database,
                onProgress = { processed, total ->
                    Log.d(TAG, "Batch ${batchIndex + 1} progress: $processed/$total")
                }
            )
        }
        
        Log.d(TAG, "OCR processing completed for all images")
    }
    
    /**
     * Get all images from MediaStore
     */
    private fun getAllImagesFromMediaStore(): List<MLKitTextHelper.ImageInfo> {
        val images = mutableListOf<MLKitTextHelper.ImageInfo>()
        
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME
                ),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    
                    images.add(MLKitTextHelper.ImageInfo(id, uri, name))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get images from MediaStore", e)
        }
        
        return images
    }
    
    /**
     * Search images by OCR text (both Latin and Devanagari)
     * Based on ScreenshotGo's search approach with FTS and fallback
     */
    suspend fun searchImagesByText(query: String): List<Long> {
        return try {
            if (query.isBlank()) {
                emptyList()
            } else {
                Log.d(TAG, "Searching for: '$query' in both Latin and Devanagari OCR")

                val results = mutableSetOf<Long>()

                // Search Latin OCR table
                try {
                    // Strategy 1: Exact phrase search in Latin OCR -- FTS4 MATCH against the
                    // tokenized index first (can use the index, unlike a leading-wildcard LIKE
                    // scan), falling back to the old LIKE-based search if MATCH's query syntax
                    // rejects this particular input (e.g. stray special characters).
                    val latinExactResults = searchLatinFtsOrFallback(ftsPhraseQuery(query), query)
                    results.addAll(latinExactResults.map { it.mediaId })
                    Log.d(TAG, "Latin exact search found ${latinExactResults.size} results")
                } catch (e: Exception) {
                    Log.w(TAG, "Latin exact search failed: ${e.message}")
                }

                // Search Devanagari OCR table
                try {
                    // Strategy 1: Exact phrase search in Devanagari OCR
                    val devanagariExactResults = database.devanagariOcrTextDao().searchOcrTextFallback(query)
                    results.addAll(devanagariExactResults.map { it.mediaId })
                    Log.d(TAG, "Devanagari exact search found ${devanagariExactResults.size} results")
                } catch (e: Exception) {
                    Log.w(TAG, "Devanagari exact search failed: ${e.message}")
                }

                // Additional word-based search for better recall
                val words = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (words.size > 1) {
                    for (word in words) {
                        if (word.length >= 2) {
                            // Search Latin OCR for individual words -- FTS prefix match first
                            try {
                                val latinWordResults = searchLatinFtsOrFallback(ftsPrefixQuery(word), word)
                                results.addAll(latinWordResults.map { it.mediaId })
                            } catch (e: Exception) {
                                Log.w(TAG, "Latin word search failed for '$word': ${e.message}")
                            }

                            // Search Devanagari OCR for individual words
                            try {
                                val devanagariWordResults = database.devanagariOcrTextDao().searchOcrTextFallback(word)
                                results.addAll(devanagariWordResults.map { it.mediaId })
                            } catch (e: Exception) {
                                Log.w(TAG, "Devanagari word search failed for '$word': ${e.message}")
                            }
                        }
                    }
                }

                // Search detected barcode/QR payloads (receipts, tickets, boarding passes)
                try {
                    val barcodeResults = classificationDb.barcodeDao().searchMediaIds(query)
                    results.addAll(barcodeResults)
                    Log.d(TAG, "Barcode search found ${barcodeResults.size} results")
                } catch (e: Exception) {
                    Log.w(TAG, "Barcode search failed: ${e.message}")
                }

                // Cross-language search: also search using the query translated to the other
                // language (English<->Hindi), so e.g. searching "car" also finds "गाड़ी". Only
                // translates the short query itself (not the OCR corpus), and silently skips
                // this step if the on-device translation model isn't downloaded yet -- never
                // blocks or slows down the primary same-language search above.
                try {
                    val translatedQuery = QueryTranslator.translateForCrossLanguageSearch(query)
                    if (translatedQuery != null) {
                        Log.d(TAG, "Cross-language search: '$query' -> '$translatedQuery'")

                        try {
                            val latinTranslatedResults = searchLatinFtsOrFallback(ftsPhraseQuery(translatedQuery), translatedQuery)
                            results.addAll(latinTranslatedResults.map { it.mediaId })
                        } catch (e: Exception) {
                            Log.w(TAG, "Latin translated search failed: ${e.message}")
                        }

                        try {
                            val devanagariTranslatedResults = database.devanagariOcrTextDao().searchOcrTextFallback(translatedQuery)
                            results.addAll(devanagariTranslatedResults.map { it.mediaId })
                        } catch (e: Exception) {
                            Log.w(TAG, "Devanagari translated search failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cross-language search step failed, continuing with same-language results: ${e.message}")
                }

                // Fuzzy fallback: only when every exact/prefix/cross-language strategy above
                // found nothing, catch OCR misreads (e.g. "rn"/"m", "0"/"O" confusions) that no
                // exact-match strategy could ever find. Bounded to a small, recent candidate set
                // (not the whole corpus) to keep this cheap since it's a last-resort scan, not
                // an index lookup.
                if (results.isEmpty()) {
                    try {
                        results.addAll(fuzzySearchFallback(query))
                    } catch (e: Exception) {
                        Log.w(TAG, "Fuzzy fallback search failed: ${e.message}")
                    }
                }

                val mediaIds = results.toList()
                Log.d(TAG, "Found ${mediaIds.size} total images matching '$query' across both Latin and Devanagari OCR")
                mediaIds
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $query", e)
            emptyList()
        }
    }

    /**
     * Try an FTS4 MATCH query against the Latin OCR table first (can use the index, unlike a
     * leading-wildcard LIKE scan), falling back to the old LIKE-based search if MATCH's query
     * syntax rejects [ftsQuery] (e.g. stray special characters) -- shared by every Latin search
     * strategy (exact phrase, word prefix, translated phrase) below.
     */
    private suspend fun searchLatinFtsOrFallback(
        ftsQuery: String,
        rawQuery: String
    ): List<OcrTextEntity> {
        return try {
            database.ocrTextDao().searchOcrTextFts(ftsQuery)
        } catch (e: Exception) {
            Log.w(TAG, "Latin FTS search failed for '$ftsQuery', falling back to LIKE: ${e.message}")
            database.ocrTextDao().searchOcrTextFallback(rawQuery)
        }
    }

    /**
     * Build an FTS4 MATCH phrase query for an exact/translated-query search. Double quotes are
     * stripped rather than escaped -- FTS4's escaping story for embedded quotes is unreliable
     * across sqlite versions, and every call site here already falls back to the LIKE-based
     * search if MATCH throws, so a stripped-down phrase is a safe, simple default.
     */
    private fun ftsPhraseQuery(query: String): String {
        val sanitized = query.trim().replace("\"", "")
        return "\"$sanitized\""
    }

    /**
     * Build an FTS4 MATCH prefix query for a single word.
     */
    private fun ftsPrefixQuery(word: String): String {
        val sanitized = word.trim().replace("\"", "").replace("*", "")
        return "$sanitized*"
    }

    /**
     * Last-resort fuzzy match: scan a small, bounded set of the most recently OCR'd rows (not
     * the whole corpus) and check whether any word in each row's text is within a small edit
     * distance of any query word -- catches OCR misreads that no exact/prefix FTS match ever
     * could, at the cost of being a linear scan rather than an index lookup, which is why it's
     * only run when every faster strategy already came back empty.
     */
    private suspend fun fuzzySearchFallback(query: String): List<Long> {
        val queryWords = query.trim().lowercase().split("\\s+".toRegex()).filter { it.length >= 3 }
        if (queryWords.isEmpty()) return emptyList()

        val candidates = database.ocrTextDao().getRecentOcrTexts(FUZZY_CANDIDATE_LIMIT)
        val matches = mutableListOf<Long>()

        for (candidate in candidates) {
            if (candidate.extractedText.isBlank()) continue
            val candidateWords = candidate.extractedText.lowercase().split("\\W+".toRegex()).filter { it.length >= 3 }
            val isMatch = queryWords.any { queryWord ->
                candidateWords.any { candidateWord ->
                    // Cheap length-difference prune before the actual edit-distance computation
                    kotlin.math.abs(queryWord.length - candidateWord.length) <= FUZZY_MAX_EDIT_DISTANCE &&
                        boundedLevenshtein(queryWord, candidateWord, FUZZY_MAX_EDIT_DISTANCE) <= FUZZY_MAX_EDIT_DISTANCE
                }
            }
            if (isMatch) matches.add(candidate.mediaId)
        }

        Log.d(TAG, "Fuzzy fallback found ${matches.size} results among ${candidates.size} recent candidates")
        return matches
    }

    /**
     * Levenshtein distance with an early exit once it's clear the result will exceed [maxDistance]
     * -- exact value beyond that threshold is never needed, only "close enough or not".
     */
    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1

        var previousRow = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val currentRow = IntArray(b.length + 1)
            currentRow[0] = i
            var rowMin = currentRow[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    previousRow[j] + 1,
                    currentRow[j - 1] + 1,
                    previousRow[j - 1] + cost
                )
                rowMin = minOf(rowMin, currentRow[j])
            }
            if (rowMin > maxDistance) return maxDistance + 1
            previousRow = currentRow
        }
        return previousRow[b.length]
    }
    
    /**
     * Get OCR text for a specific image (from both Latin and Devanagari)
     */
    suspend fun getOcrTextForImage(mediaId: Long): String? {
        return try {
            val latinText = database.ocrTextDao().getOcrTextByMediaId(mediaId)?.extractedText
            val devanagariText = database.devanagariOcrTextDao().getOcrTextByMediaId(mediaId)?.extractedText

            // Combine both texts if available
            when {
                latinText != null && devanagariText != null -> "$latinText\n$devanagariText"
                latinText != null -> latinText
                devanagariText != null -> devanagariText
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get OCR text for media $mediaId", e)
            null
        }
    }
    
    /**
     * Check if an image has been processed
     */
    suspend fun isImageProcessed(mediaId: Long): Boolean {
        return try {
            database.ocrTextDao().getOcrTextByMediaId(mediaId) != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if image $mediaId is processed", e)
            false
        }
    }
    
    /**
     * Get processing statistics
     */
    suspend fun getProcessingStats(): ProcessingStats {
        return try {
            val totalProcessed = database.ocrTextDao().getOcrTextCount()
            val totalImages = getAllImagesFromMediaStore().size
            
            ProcessingStats(
                totalImages = totalImages,
                processedImages = totalProcessed,
                isComplete = totalProcessed >= totalImages
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get processing stats", e)
            ProcessingStats(0, 0, false)
        }
    }
    
    data class ProcessingStats(
        val totalImages: Int,
        val processedImages: Int,
        val isComplete: Boolean
    )
}
