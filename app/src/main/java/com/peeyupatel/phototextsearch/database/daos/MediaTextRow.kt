package com.peeyupatel.phototextsearch.database.daos

/** Lightweight projection (id + extracted text only) used by DocumentSimilarityMatcher to
 * scan every indexed photo's text without pulling unused columns (confidence, timestamps, etc). */
data class MediaTextRow(
    val mediaId: Long,
    val extractedText: String
)
