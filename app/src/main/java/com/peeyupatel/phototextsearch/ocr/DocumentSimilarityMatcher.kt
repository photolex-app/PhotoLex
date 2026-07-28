package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import com.peeyupatel.phototextsearch.database.MediaDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ln

/**
 * "Find similar documents" -- given one photo the user picked as an example, ranks every other
 * indexed photo by how much of its distinctive OCR vocabulary it shares with the example.
 * Deliberately keyword-overlap based rather than a new visual-similarity ML model: OCR text is
 * already extracted for every photo at zero extra cost, so this needs no additional indexing
 * pass and works the instant a photo has been OCR'd. Two documents of "the same type" (e.g. two
 * electricity bills, two Aadhaar cards) reliably share template boilerplate text even though the
 * specific numbers/names differ, which is exactly what this overlap score picks up on.
 *
 * Uses inverse-document-frequency weighting (not just raw shared-word count): a word that shows
 * up on nearly every Indian government-issued document in the gallery (e.g. "government",
 * "india", "signature", "authority") contributes almost nothing to the score, while a word rare
 * across the whole corpus (e.g. "aadhaar", "voter", "licence") contributes much more. Without
 * this, an Aadhaar card and an unrelated passport-website screenshot that both happen to say
 * "Government of India" would score as "similar" -- confirmed as a real false-positive on this
 * device's own gallery before this fix.
 */
object DocumentSimilarityMatcher {

    /** Common words that appear in almost any document regardless of type -- excluding these
     * keeps the match signal on distinctive template vocabulary instead of filler words. IDF
     * weighting handles most of this automatically, but these are common enough (and short/
     * grammatical enough) that they're worth excluding outright rather than relying on a small
     * weight to suppress them. */
    private val stopwords = setOf(
        "the", "and", "for", "are", "was", "were", "this", "that", "with", "from", "have",
        "has", "had", "you", "your", "not", "all", "any", "can", "will", "date", "name",
        "address", "please", "shall", "each", "also", "into", "such", "than", "then", "them",
        "their", "these", "those", "here", "when", "where", "which", "while", "about"
    )

    /** Matches whole words -- Latin (English) or Devanagari (Hindi), U+0900-U+097F -- but
     * deliberately NOT pure digit sequences. Two unrelated Aadhaar cards otherwise spuriously
     * "match" on a coincidentally-shared 4-digit substring of their (different) ID numbers --
     * numbers are per-instance identifiers, not vocabulary indicative of a document TYPE.
     * Devanagari words use a shorter minimum length than Latin (2 vs 4) since conjunct/matra
     * combinations pack more meaning per codepoint than Latin letters do. */
    private val termPattern = Regex("[A-Za-z]{4,}|[ऀ-ॿ]{2,}")

    /** Detects likely person-name sequences -- 2 to 4 consecutive Title-Case or ALL-CAPS words,
     * e.g. "Peeyu Patel", "PEEYU SUBHASH PATEL", "Ranju Praveen Kalariya" -- run against the
     * ORIGINAL (not lowercased) text, since case is the only signal available. A person's own
     * name is instance-specific, not indicative of document TYPE, yet it can be corpus-rare
     * (high IDF) purely because it's the account owner's identity recurring across many
     * otherwise-unrelated documents (a payment receipt, an ID card, an application form) --
     * confirmed as a real false-positive on this device's own gallery (a UPI payment
     * screenshot's only "distinctive" vocabulary turned out to be the user's own name, which
     * also appears on ~150-270 completely unrelated photos). Tradeoff: this also strips
     * legitimate multi-word institutional phrases in Title/ALL-CAPS (e.g. "Unique
     * Identification Authority of India") since there's no cheap way to tell a person's name
     * from an institution's name without a real names database -- accepted since a single word
     * from that phrase (e.g. "authority") still counts normally, only the multi-word run is lost. */
    private val nameSequencePattern =
        Regex("\\b(?:[A-Z][a-z]+|[A-Z]{2,})(?:\\s+(?:[A-Z][a-z]+|[A-Z]{2,})){1,3}\\b")

    private fun significantTerms(text: String): Set<String> {
        val nameWords = nameSequencePattern.findAll(text)
            .flatMap { it.value.split(Regex("\\s+")) }
            .map { it.lowercase() }
            .toSet()

        return termPattern.findAll(text.lowercase())
            .map { it.value }
            .filter { it !in stopwords && it !in nameWords }
            .toSet()
    }

    data class Match(val mediaId: Long, val score: Double, val sharedTermCount: Int)

    // Cached corpus (per-document term sets + corpus-wide document frequency), so repeated
    // findSimilar() calls don't re-tokenize every OCR'd document from scratch every time --
    // only rebuilt once per process, then kept in sync incrementally via onOcrResultUpdated().
    private val cacheMutex = Mutex()
    private var cachedTermsByMediaId: MutableMap<Long, Set<String>>? = null
    private val cachedDocumentFrequency: MutableMap<String, Int> = mutableMapOf()

    /**
     * Incrementally update the cached corpus for one document as soon as its OCR result lands,
     * instead of waiting for the next findSimilar() call to rebuild everything from scratch.
     * No-op if the cache hasn't been built yet (no findSimilar() call has happened this process)
     * -- the first findSimilar() call builds it fresh from the database anyway, so nothing is
     * lost, just deferred until it's actually needed.
     */
    suspend fun onOcrResultUpdated(mediaId: Long, extractedText: String) {
        cacheMutex.withLock {
            val terms = cachedTermsByMediaId ?: return
            val newTerms = significantTerms(extractedText)
            terms[mediaId]?.let { oldTerms ->
                for (term in oldTerms) {
                    val count = (cachedDocumentFrequency[term] ?: 1) - 1
                    if (count <= 0) cachedDocumentFrequency.remove(term) else cachedDocumentFrequency[term] = count
                }
            }
            for (term in newTerms) {
                cachedDocumentFrequency[term] = (cachedDocumentFrequency[term] ?: 0) + 1
            }
            terms[mediaId] = newTerms
        }
    }

    private suspend fun getOrBuildCorpus(database: MediaDatabase): Pair<Map<Long, Set<String>>, Map<String, Int>> {
        cacheMutex.withLock {
            cachedTermsByMediaId?.let { return it to cachedDocumentFrequency }

            val allRows = database.ocrTextDao().getAllMediaIdAndText() +
                database.devanagariOcrTextDao().getAllMediaIdAndText()

            val terms = allRows.associateTo(mutableMapOf()) { it.mediaId to significantTerms(it.extractedText) }
            val df = mutableMapOf<String, Int>()
            for (docTerms in terms.values) {
                for (term in docTerms) {
                    df[term] = (df[term] ?: 0) + 1
                }
            }

            cachedTermsByMediaId = terms
            cachedDocumentFrequency.clear()
            cachedDocumentFrequency.putAll(df)
            return terms to cachedDocumentFrequency
        }
    }

    /**
     * @param sourceMediaId the example photo the user picked
     * @param minSharedTerms results need at least this many distinctive words in common --
     * a cheap guard against a single coincidental word match, alongside the IDF score threshold.
     * Lowered from an initial 2 to 1: excluding name-sequences (see nameSequencePattern) already
     * shrinks the per-document vocabulary pool a lot, especially for terse ID-card-style text
     * that's mostly proper nouns -- requiring 2 overlaps on top of that made genuinely similar
     * documents (e.g. two Aadhaar cards) fail to match at all, confirmed as real over-restriction
     * on this device's own gallery. The IDF score threshold below is the real filter now.
     * @param relativeScoreThreshold a candidate must reach at least this fraction of the
     * source document's own total distinctiveness score to count as a match -- scales with the
     * source document automatically instead of a fixed magic number, since a longer/more
     * distinctive source document has a higher possible score than a short one. Lowered from an
     * initial 0.15 to 0.08 for the same reason as minSharedTerms above: a shrunk vocabulary pool
     * means fewer/smaller shared-term scores in absolute terms, so the same relative bar became
     * stricter than intended once name-exclusion was added.
     * @param maxResults cap on how many ranked matches to return
     */
    suspend fun findSimilar(
        context: Context,
        sourceMediaId: Long,
        minSharedTerms: Int = 1,
        relativeScoreThreshold: Double = 0.08,
        maxResults: Int = 200
    ): List<Match> {
        val database = MediaDatabase.getInstance(context.applicationContext)

        val sourceText =
            database.ocrTextDao().getOcrTextByMediaId(sourceMediaId)?.extractedText
                ?: database.devanagariOcrTextDao().getOcrTextByMediaId(sourceMediaId)?.extractedText
                ?: return emptyList()

        val sourceTerms = significantTerms(sourceText)
        if (sourceTerms.isEmpty()) return emptyList()

        // Tokenize every row once, reused both to build corpus-wide document frequency (so
        // boilerplate shared across many unrelated document types can be weighted down) and to
        // score candidates against the source. Cached across calls (see getOrBuildCorpus/
        // onOcrResultUpdated) instead of re-tokenizing the whole corpus on every single tap.
        val (termsByMediaId, documentFrequency) = getOrBuildCorpus(database)

        val totalDocs = termsByMediaId.size.coerceAtLeast(1)

        fun idf(term: String): Double {
            val df = documentFrequency[term] ?: return 0.0
            return ln(totalDocs.toDouble() / df)
        }

        val sourceMaxScore = sourceTerms.sumOf { idf(it) }
        if (sourceMaxScore <= 0.0) return emptyList()
        val minScore = sourceMaxScore * relativeScoreThreshold

        return termsByMediaId.entries.asSequence()
            .filter { it.key != sourceMediaId }
            .map { (mediaId, terms) ->
                val shared = terms.intersect(sourceTerms)
                Match(mediaId, shared.sumOf { idf(it) }, shared.size)
            }
            .filter { it.sharedTermCount >= minSharedTerms && it.score >= minScore }
            .sortedByDescending { it.score }
            .take(maxResults)
            .toList()
    }
}
