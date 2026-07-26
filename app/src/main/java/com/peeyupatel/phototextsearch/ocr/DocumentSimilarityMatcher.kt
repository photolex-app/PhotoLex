package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import androidx.room.Room
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.database.Migration3to4
import com.peeyupatel.phototextsearch.database.Migration4to5
import com.peeyupatel.phototextsearch.database.Migration5to6
import com.peeyupatel.phototextsearch.database.Migration6to7

/**
 * "Find similar documents" -- given one photo the user picked as an example, ranks every other
 * indexed photo by how much of its distinctive OCR vocabulary it shares with the example.
 * Deliberately keyword-overlap based rather than a new visual-similarity ML model: OCR text is
 * already extracted for every photo at zero extra cost, so this needs no additional indexing
 * pass and works the instant a photo has been OCR'd. Two documents of "the same type" (e.g. two
 * electricity bills, two Aadhaar cards) reliably share template boilerplate text even though the
 * specific numbers/names differ, which is exactly what this overlap score picks up on.
 */
object DocumentSimilarityMatcher {

    /** Common words that appear in almost any document regardless of type -- excluding these
     * keeps the match signal on distinctive template vocabulary instead of filler words. */
    private val stopwords = setOf(
        "the", "and", "for", "are", "was", "were", "this", "that", "with", "from", "have",
        "has", "had", "you", "your", "not", "all", "any", "can", "will", "date", "name",
        "address", "please", "shall", "each", "also", "into", "such", "than", "then", "them",
        "their", "these", "those", "here", "when", "where", "which", "while", "about"
    )

    private fun significantTerms(text: String): Set<String> {
        return Regex("[A-Za-z0-9]{4,}")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it !in stopwords }
            .toSet()
    }

    data class Match(val mediaId: Long, val sharedTermCount: Int)

    /**
     * @param sourceMediaId the example photo the user picked
     * @param minSharedTerms results need at least this many distinctive words in common --
     * guards against near-random matches when the source document has very generic text
     * @param maxResults cap on how many ranked matches to return
     */
    suspend fun findSimilar(
        context: Context,
        sourceMediaId: Long,
        minSharedTerms: Int = 2,
        maxResults: Int = 200
    ): List<Match> {
        val database = Room.databaseBuilder(
            context.applicationContext,
            MediaDatabase::class.java,
            "media-database"
        ).addMigrations(
            Migration3to4(context.applicationContext),
            Migration4to5(context.applicationContext),
            Migration5to6(context.applicationContext),
            Migration6to7(context.applicationContext),
            com.peeyupatel.phototextsearch.database.migrations.Migration7to8
        ).build()

        val sourceText =
            database.ocrTextDao().getOcrTextByMediaId(sourceMediaId)?.extractedText
                ?: database.devanagariOcrTextDao().getOcrTextByMediaId(sourceMediaId)?.extractedText
                ?: return emptyList()

        val sourceTerms = significantTerms(sourceText)
        if (sourceTerms.isEmpty()) return emptyList()

        val allRows = database.ocrTextDao().getAllMediaIdAndText() +
            database.devanagariOcrTextDao().getAllMediaIdAndText()

        return allRows.asSequence()
            .filter { it.mediaId != sourceMediaId }
            .map { row -> Match(row.mediaId, significantTerms(row.extractedText).count { it in sourceTerms }) }
            .filter { it.sharedTermCount >= minSharedTerms }
            .sortedByDescending { it.sharedTermCount }
            .take(maxResults)
            .toList()
    }
}
