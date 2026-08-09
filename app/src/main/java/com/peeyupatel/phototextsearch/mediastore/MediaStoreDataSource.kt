package com.peeyupatel.phototextsearch.mediastore

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.peeyupatel.phototextsearch.helpers.MediaItemSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

abstract class MediaStoreDataSource
internal constructor(
    val context: Context,
    private val neededPath: String,
    val sortBy: MediaItemSortMode,
    private val cancellationSignal: CancellationSignal
) {
    companion object {
        val MEDIA_STORE_FILE_URI: Uri =
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }

    // A single logical change (e.g. trashing one photo) reliably fires onChange() several times
    // in a row -- confirmed live via added logging: one delete produced 4-6 near-simultaneous
    // onChange calls, sometimes followed by further bursts up to a minute later (other
    // background writers like OCR/classification touching MediaStore rows independently trigger
    // the same broad content:// observer). Before debouncing, EVERY single one of those
    // independently launched its own full re-query of the entire library (20,000+ rows, each
    // doing EXIF/date-taken fallback work) -- confirmed live causing visible scroll jank/freezing
    // right after a delete. Debouncing the trigger (not the query result) coalesces a whole burst
    // into a single re-query after things settle down, rather than paying for each one.
    private val REQUERY_DEBOUNCE_MS = 300L

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    open fun loadMediaStoreData(): Flow<List<MediaStoreData>> = callbackFlow {
        val contentObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    trySend(Unit)
                }
            }

        context.contentResolver.registerContentObserver(
            MEDIA_STORE_FILE_URI,
            true,
            contentObserver
        )

        trySend(Unit)

        cancellationSignal.setOnCancelListener {
            try {
                cancel("Cancelling MediaStoreDataSource $neededPath channel because of exit signal...")
            } catch (e: Throwable) {
                Log.e("MEDIA_STORE_DATASOURCE", e.toString())
            }
        }

        awaitClose {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }
        .debounce(REQUERY_DEBOUNCE_MS)
        .map { query() }
        .flowOn(Dispatchers.IO)
        .conflate()

    abstract fun query() : List<MediaStoreData>
}


