package com.peeyupatel.phototextsearch.models.smart_album

import android.content.Context
import android.os.CancellationSignal
import android.provider.MediaStore.MediaColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.datastore.SQLiteQuery
import com.peeyupatel.phototextsearch.helpers.MediaItemSortMode
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MultiAlbumDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Loads photos belonging to a Smart Album category (e.g. "receipt", "id_card") -- a virtual
 * album backed by the ClassificationDatabase rather than a real folder path. Reuses
 * MultiAlbumDataSource (the same MediaStore query engine regular folder albums use) by
 * constructing an `_ID IN (...)` SQLiteQuery from the category's media IDs, so this gets the
 * same tested query/thumbnail/grouping behavior as any other album for free.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SmartAlbumViewModel(
    private val context: Context,
    private val category: String
) : ViewModel() {

    private var cancellationSignal = CancellationSignal()
    private val mediaIdsFlow = MutableStateFlow<List<Long>?>(null)

    val mediaFlow: Flow<List<MediaStoreData>> = mediaIdsFlow.flatMapLatest { ids ->
        if (ids.isNullOrEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val query = SQLiteQuery(
                query = "AND ${MediaColumns._ID} IN (${ids.joinToString(",") { "?" }})",
                paths = ids.map { it.toString() }
            )
            MultiAlbumDataSource(
                context = context,
                queryString = query,
                sortBy = MediaItemSortMode.DateTaken,
                cancellationSignal = cancellationSignal,
                isGridView = true
            ).loadMediaStoreData()
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = ClassificationDatabase.getInstance(context)
                .photoClassificationDao()
                .getMediaIdsByCategory(category)
            mediaIdsFlow.value = ids
        }
    }

    fun cancelMediaFlow() = cancellationSignal.cancel()

    override fun onCleared() {
        cancelMediaFlow()
        super.onCleared()
    }
}
