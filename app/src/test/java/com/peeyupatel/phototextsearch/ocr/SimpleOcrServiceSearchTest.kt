package com.peeyupatel.phototextsearch.ocr

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
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
 * Regression suite for SimpleOcrService.searchImagesByText() -- the core search-matching logic
 * behind PhotoLex's main differentiator. Seeds a real (in-memory) Room database with known Latin
 * and Devanagari OCR text and asserts on actual search results, the same way SimpleOcrService is
 * really used, rather than testing a simplified stand-in for it.
 *
 * Every test has an explicit timeout: the cross-language search step touches ML Kit's on-device
 * translation client, which has no real backing service under Robolectric -- it's expected to
 * fail fast and be caught by searchImagesByText's own try/catch (searches must degrade
 * gracefully when the translation model isn't available, which is normal/common on a real
 * device too), but the timeout guarantees a test hangs rather than blocks the whole suite if
 * that assumption is ever wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleOcrServiceSearchTest {

    private lateinit var mediaDb: MediaDatabase
    private lateinit var classificationDb: ClassificationDatabase
    private lateinit var service: SimpleOcrService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        mediaDb = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        classificationDb = Room.inMemoryDatabaseBuilder(context, ClassificationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = SimpleOcrService(context, mediaDb, classificationDb)
    }

    @After
    fun tearDown() {
        mediaDb.close()
        classificationDb.close()
    }

    private fun seedLatin(mediaId: Long, text: String) = runTest {
        mediaDb.ocrTextDao().insertOcrText(
            OcrTextEntity(mediaId = mediaId, extractedText = text, extractionTimestamp = 0L)
        )
    }

    private fun seedDevanagari(mediaId: Long, text: String) = runTest {
        mediaDb.devanagariOcrTextDao().insertOcrText(
            DevanagariOcrTextEntity(mediaId = mediaId, extractedText = text, extractionTimestamp = 0L)
        )
    }

    @Test(timeout = 15_000)
    fun `exact word search finds the right Latin photo`() = runTest {
        seedLatin(1L, "K CHANDRIKA BEN PATEL income tax department")
        seedLatin(2L, "an unrelated grocery receipt")

        val results = service.searchImagesByText("chandrika")

        assertTrue(results.contains(1L))
        assertTrue(!results.contains(2L))
    }

    @Test(timeout = 15_000)
    fun `search is case-insensitive`() = runTest {
        seedLatin(1L, "CHANDRIKA SUBHASH KALARIYA")

        assertTrue(service.searchImagesByText("chandrika").contains(1L))
        assertTrue(service.searchImagesByText("Chandrika").contains(1L))
        assertTrue(service.searchImagesByText("CHANDRIKA").contains(1L))
    }

    @Test(timeout = 15_000)
    fun `exact word search finds the right Devanagari photo`() = runTest {
        seedDevanagari(1L, "नांव चंद्रीका सूभाष कालराया")
        seedDevanagari(2L, "अनियमित पत्र")

        val results = service.searchImagesByText("चंद्रीका")

        assertTrue(results.contains(1L))
        assertTrue(!results.contains(2L))
    }

    @Test(timeout = 15_000)
    fun `a query with no matches anywhere returns empty, not a crash`() = runTest {
        seedLatin(1L, "totally unrelated text")

        val results = service.searchImagesByText("nonexistentqueryxyz")

        assertTrue(results.isEmpty())
    }

    @Test(timeout = 15_000)
    fun `blank query returns empty without touching the database`() = runTest {
        seedLatin(1L, "some text")

        assertTrue(service.searchImagesByText("").isEmpty())
        assertTrue(service.searchImagesByText("   ").isEmpty())
    }

    @Test(timeout = 15_000)
    fun `a query containing double quotes does not break the FTS MATCH syntax`() = runTest {
        // ftsPhraseQuery() strips embedded double quotes rather than escaping them, precisely
        // so a query like this can't produce malformed MATCH syntax that throws and forces
        // every search onto the slow LIKE fallback path.
        seedLatin(1L, "receipt total $42.50")

        val results = service.searchImagesByText("\"42.50\"")

        assertTrue(results.contains(1L))
    }

    @Test(timeout = 15_000)
    fun `multi-word query finds a photo matching only one of the words`() = runTest {
        seedLatin(1L, "invoice number 88213 dated today")
        seedLatin(2L, "completely different unrelated content")

        // Word-based recall: individual words length >= 2 are searched independently so a
        // photo matching only part of a multi-word query is still found.
        val results = service.searchImagesByText("invoice zzznotarealwordzzz")

        assertTrue(results.contains(1L))
        assertTrue(!results.contains(2L))
    }

    @Test(timeout = 15_000)
    fun `results are found across both Latin and Devanagari tables for the same query text`() = runTest {
        seedLatin(1L, "Elector's Name Chandrika Subhash Kalariya")
        seedDevanagari(2L, "नांव चंद्रीका सूभाष कालराया Chandrika Subhash Kalariya")

        val results = service.searchImagesByText("chandrika")

        assertTrue(results.contains(1L))
        assertTrue(results.contains(2L))
    }

    @Test(timeout = 15_000)
    fun `barcode text is searchable alongside OCR text`() = runTest {
        seedLatin(1L, "a plain photo with no barcode")
        classificationDb.barcodeDao().upsert(
            com.peeyupatel.phototextsearch.database.entities.BarcodeEntity(
                mediaId = 2L,
                barcodeText = "BOARDINGPASS-XJ4471",
                scannedAt = 0L
            )
        )

        val ocrOnlyResults = service.searchImagesByText("plain")
        val barcodeResults = service.searchImagesByText("XJ4471")

        assertTrue(ocrOnlyResults.contains(1L))
        assertTrue(barcodeResults.contains(2L))
    }
}
