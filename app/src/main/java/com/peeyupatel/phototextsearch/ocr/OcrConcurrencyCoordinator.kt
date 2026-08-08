package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

enum class OcrPipeline { LATIN, DEVANAGARI }

/**
 * Coordinates OCR concurrency between the Latin and Devanagari indexing workers, which run as
 * two independent WorkManager workers in the same process and can be active at the same time
 * (e.g. Devanagari OCR enabled while a Latin re-index is still catching up). Each worker used to
 * compute its own core-scaled concurrency cap in isolation -- on an 8-core device that meant two
 * workers each claiming up to 6 concurrent threads simultaneously, a 12-desired-on-8-real-cores
 * oversubscription that measurably starved both pipelines (Latin dropped from ~2/sec running
 * alone to ~1.2/sec once Devanagari joined, well under the ~9/sec the concurrency cap alone
 * should allow). Splitting the shared budget when both are active fixes that without touching
 * either worker's own thermal-throttle logic.
 */
object OcrConcurrencyCoordinator {
    private const val TAG = "OcrConcurrencyCoordinator"

    private val latinActive = AtomicBoolean(false)
    private val devanagariActive = AtomicBoolean(false)

    fun markActive(pipeline: OcrPipeline) {
        activeFlagFor(pipeline).set(true)
    }

    fun markInactive(pipeline: OcrPipeline) {
        activeFlagFor(pipeline).set(false)
    }

    private fun activeFlagFor(pipeline: OcrPipeline): AtomicBoolean = when (pipeline) {
        OcrPipeline.LATIN -> latinActive
        OcrPipeline.DEVANAGARI -> devanagariActive
    }

    /**
     * Concurrency for [pipeline]: the same core-scaled cap (leaving one core free, capped at 6)
     * and thermal throttle (dropped to 1 under moderate-or-worse thermal status) each worker
     * computed independently before, except the core-scaled cap is halved (minimum 1) whenever
     * both pipelines are marked active at once, so together they never request more total
     * concurrent OCR threads than a single pipeline running alone would have.
     */
    fun concurrencyFor(pipeline: OcrPipeline, context: Context): Int {
        if (isThermalThrottled(context)) {
            Log.d(TAG, "Thermal status elevated, throttling $pipeline OCR concurrency to 1")
            return 1
        }

        val coreBased = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 6)
        val bothActive = latinActive.get() && devanagariActive.get()
        if (!bothActive) return coreBased

        val shared = (coreBased / 2).coerceAtLeast(1)
        Log.d(TAG, "Both OCR pipelines active, splitting concurrency budget: $pipeline gets $shared (of $coreBased total)")
        return shared
    }

    private fun isThermalThrottled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read thermal status, assuming not throttled", e)
            false
        }
    }
}
