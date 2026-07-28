package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

/**
 * Shared small-bitmap loader used by both the Latin pre-scan (FastTextPreScanner) and the
 * Devanagari language gate (DevanagariLanguageGate) -- both need a cheap, heavily downscaled
 * decode of the same source image for a fast presence/language check ahead of full extraction.
 */
object BitmapDownsampler {
    private const val TAG = "BitmapDownsampler"

    fun loadSmallBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)

                val sampleSize = calculateSampleSize(boundsOptions, maxDimension, maxDimension)

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(stream, null, loadOptions)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load small bitmap: $uri", e)
            null
        }
    }

    private fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
