package com.peeyupatel.phototextsearch.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.peeyupatel.phototextsearch.database.entities.DevanagariOcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.OcrTextEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the FTS4 virtual tables (ocr_text_fts / devanagari_ocr_text_fts)
 * actually staying in sync with their base tables. Both use Room's @Fts4(contentEntity = ...)
 * external-content pattern, which auto-generates the sync triggers as part of schema creation --
 * these tests exist to catch a future schema/migration change that breaks that sync silently
 * (the exact class of bug that made Devanagari search permanently slow before the FTS4 table
 * existed at all, and the kind of thing that otherwise only shows up via manual on-device SQL
 * forensics, as it did once already in this project's history).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrFtsSyncTest {

    private lateinit var db: MediaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `inserting a Latin OCR row makes it findable via FTS MATCH`() = runTest {
        db.ocrTextDao().insertOcrText(
            OcrTextEntity(mediaId = 1L, extractedText = "hello world", extractionTimestamp = 0L)
        )

        val results = db.ocrTextDao().searchOcrTextFts("\"hello\"")

        assertEquals(1, results.size)
        assertEquals(1L, results[0].mediaId)
    }

    @Test
    fun `inserting a Devanagari OCR row makes it findable via FTS MATCH`() = runTest {
        db.devanagariOcrTextDao().insertOcrText(
            DevanagariOcrTextEntity(mediaId = 1L, extractedText = "नमस्ते दुनिया", extractionTimestamp = 0L)
        )

        val results = db.devanagariOcrTextDao().searchOcrTextFts("\"नमस्ते\"")

        assertEquals(1, results.size)
        assertEquals(1L, results[0].mediaId)
    }

    @Test
    fun `updating a Latin OCR row's text updates the FTS index, not just the base table`() = runTest {
        db.ocrTextDao().insertOcrText(
            OcrTextEntity(mediaId = 1L, extractedText = "original text", extractionTimestamp = 0L)
        )
        // Same media_id, REPLACE conflict strategy (matches production's insertOcrText usage
        // for re-processing an image) -- this is the path that must keep the FTS index in sync.
        db.ocrTextDao().insertOcrText(
            OcrTextEntity(mediaId = 1L, extractedText = "updated text", extractionTimestamp = 1L)
        )

        val staleResults = db.ocrTextDao().searchOcrTextFts("\"original\"")
        val freshResults = db.ocrTextDao().searchOcrTextFts("\"updated\"")

        assertTrue("stale text should no longer match after being replaced", staleResults.isEmpty())
        assertEquals(1, freshResults.size)
    }

    @Test
    fun `deleting a Latin OCR row removes it from the FTS index`() = runTest {
        db.ocrTextDao().insertOcrText(
            OcrTextEntity(mediaId = 1L, extractedText = "deleteme", extractionTimestamp = 0L)
        )
        assertEquals(1, db.ocrTextDao().searchOcrTextFts("\"deleteme\"").size)

        db.ocrTextDao().deleteOcrTextsByMediaIds(listOf(1L))

        assertTrue(db.ocrTextDao().searchOcrTextFts("\"deleteme\"").isEmpty())
    }

    @Test
    fun `deleting a Devanagari OCR row removes it from the FTS index`() = runTest {
        db.devanagariOcrTextDao().insertOcrText(
            DevanagariOcrTextEntity(mediaId = 1L, extractedText = "मिटाओ", extractionTimestamp = 0L)
        )
        assertEquals(1, db.devanagariOcrTextDao().searchOcrTextFts("\"मिटाओ\"").size)

        db.devanagariOcrTextDao().deleteOcrTextsByMediaIds(listOf(1L))

        assertTrue(db.devanagariOcrTextDao().searchOcrTextFts("\"मिटाओ\"").isEmpty())
    }

    @Test
    fun `base table and FTS table row counts stay equal across inserts and deletes`() = runTest {
        repeat(20) { i ->
            db.ocrTextDao().insertOcrText(
                OcrTextEntity(mediaId = i.toLong(), extractedText = "photo number $i", extractionTimestamp = 0L)
            )
        }
        db.ocrTextDao().deleteOcrTextsByMediaIds(listOf(3L, 7L, 11L))

        assertEquals(17, db.ocrTextDao().getOcrTextCount())
        // No direct "FTS count" DAO method exists in production code, so cross-check via a
        // MATCH that should hit every remaining row's shared word.
        assertEquals(17, db.ocrTextDao().searchOcrTextFts("\"photo\"").size)
    }
}
