package com.peeyupatel.phototextsearch.ocr

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume

private const val TAG = "BlockingCallHardTimeout"

/**
 * Runs blocking, non-cancellable calls (raw ContentResolver/BitmapFactory I/O, which don't
 * respond to Kotlin coroutine cancellation) on a small dedicated thread pool, separate from the
 * shared Dispatchers.IO pool the rest of the app uses. A plain `withTimeoutOrNull { blockingCall() }`
 * only makes the *caller* stop waiting -- the underlying thread keeps running the blocking call to
 * completion (or forever, for a genuinely pathological input) on whatever pool it was dispatched
 * to. Confirmed live: over a long indexing session, enough of these orphaned hangs accumulated on
 * the shared Dispatchers.IO pool that it was fully exhausted, silently blocking *all* future OCR
 * work (worker stopped starting at all, not just hanging on one image) until the app was killed
 * and the process restarted. Isolating them to this small dedicated pool means a hung call can
 * still exhaust up to [POOL_SIZE] threads here, but can no longer starve the shared pool everything
 * else in the app depends on.
 */
object BlockingCallHardTimeout {
    private const val POOL_SIZE = 4

    private val executor = Executors.newFixedThreadPool(POOL_SIZE) { runnable ->
        Thread(runnable, "ocr-blocking-io").apply { isDaemon = true }
    }

    /**
     * Runs [block] on the dedicated pool and waits up to [timeoutMs]. On timeout, attempts to
     * interrupt the underlying thread (a real Thread.interrupt(), not just abandoning it) and
     * returns null -- interruption isn't guaranteed to actually stop raw file I/O, but it's a
     * genuine attempt rather than none at all, and the thread is at least contained to this
     * bounded pool instead of the shared one.
     */
    suspend fun <T> runWithHardTimeout(timeoutMs: Long, block: () -> T): T? =
        suspendCancellableCoroutine { continuation ->
            val future = executor.submit<T> { block() }

            continuation.invokeOnCancellation { future.cancel(true) }

            try {
                val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                if (continuation.isActive) continuation.resume(result)
            } catch (e: TimeoutException) {
                Log.w(TAG, "Blocking call exceeded ${timeoutMs}ms, interrupting and giving up on it")
                future.cancel(true)
                if (continuation.isActive) continuation.resume(null)
            } catch (e: Exception) {
                Log.w(TAG, "Blocking call failed: ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }
}
