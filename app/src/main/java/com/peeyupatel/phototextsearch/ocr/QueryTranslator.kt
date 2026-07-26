package com.peeyupatel.phototextsearch.ocr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "QueryTranslator"

/**
 * Translates a short search query between English and Hindi so search can match photos whose
 * OCR'd text is in the other language (e.g. searching "car" also finds "गाड़ी"). Only ever
 * translates the query itself (a few words), never the OCR corpus -- indexing speed is untouched.
 *
 * If the on-device English<->Hindi model isn't downloaded yet, this silently kicks off a
 * background download for next time and returns null immediately rather than blocking search.
 */
object QueryTranslator {

    private val devanagariRange = 'ऀ'..'ॿ'

    private fun looksLikeDevanagari(text: String): Boolean =
        text.any { it in devanagariRange }

    /**
     * Returns the translated query (English<->Hindi, whichever direction the input script
     * suggests), or null if translation isn't available/applicable right now.
     */
    suspend fun translateForCrossLanguageSearch(query: String): String? {
        val trimmed = query.trim()

        // Not worth translating: too short, or looks like a date/number rather than a word.
        if (trimmed.length < 2) return null
        if (trimmed.all { it.isDigit() || it == '/' || it == '-' || it == ' ' }) return null

        val sourceLanguage: String
        val targetLanguage: String
        if (looksLikeDevanagari(trimmed)) {
            sourceLanguage = TranslateLanguage.HINDI
            targetLanguage = TranslateLanguage.ENGLISH
        } else {
            sourceLanguage = TranslateLanguage.ENGLISH
            targetLanguage = TranslateLanguage.HINDI
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)

        return try {
            val modelReady = isModelDownloaded(targetLanguage)
            if (!modelReady) {
                // Don't block this search on a multi-second/multi-MB download -- kick it off
                // in the background for next time, and just skip cross-language search now.
                downloadModelInBackground(translator)
                translator.close()
                return null
            }

            val result = suspendCancellableCoroutine<String?> { continuation ->
                translator.translate(trimmed)
                    .addOnSuccessListener { translatedText ->
                        continuation.resume(translatedText)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Translation failed for query: ${e.message}")
                        continuation.resume(null)
                    }
            }
            translator.close()

            // Don't bother returning a "translation" that's identical to the input (nothing
            // gained) or blank.
            if (result.isNullOrBlank() || result.equals(trimmed, ignoreCase = true)) null else result
        } catch (e: Exception) {
            Log.w(TAG, "Translation attempt failed: ${e.message}")
            translator.close()
            null
        }
    }

    private suspend fun isModelDownloaded(languageTag: String): Boolean {
        return try {
            val model = TranslateRemoteModel.Builder(languageTag).build()
            suspendCancellableCoroutine { continuation ->
                RemoteModelManager.getInstance().isModelDownloaded(model)
                    .addOnSuccessListener { downloaded -> continuation.resume(downloaded) }
                    .addOnFailureListener { continuation.resume(false) }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun downloadModelInBackground(translator: com.google.mlkit.nl.translate.Translator) {
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    Log.d(TAG, "Translation model downloaded, ready for next search")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Translation model download failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start translation model download: ${e.message}")
        }
    }
}
