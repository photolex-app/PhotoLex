package com.peeyupatel.phototextsearch.models.curated_album

import android.content.Context
import android.os.CancellationSignal
import android.provider.MediaStore.MediaColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
 * Loads photos tagged into a user-created curated album -- a virtual album backed by tag
 * membership (CuratedAlbumPhotoEntity) rather than a real folder path, mirroring
 * SmartAlbumViewModel's approach of reusing MultiAlbumDataSource with an `_ID IN (...)` query.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CuratedAlbumViewModel(
    private val context: Context,
    val albumId: Long
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = ClassificationDatabase.getInstance(context).curatedAlbumDao().getMediaIds(albumId)
            mediaIdsFlow.value = ids
        }
    }

    fun removePhotos(mediaIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ClassificationDatabase.getInstance(context).curatedAlbumDao().removePhotos(albumId, mediaIds)
            refresh()
        }
    }

    fun cancelMediaFlow() = cancellationSignal.cancel()

    override fun onCleared() {
        cancelMediaFlow()
        super.onCleared()
    }
}

@Suppress("UNCHECKED_CAST")
class CuratedAlbumViewModelFactory(
    private val context: Context,
    private val albumId: Long
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == CuratedAlbumViewModel::class.java) {
            return CuratedAlbumViewModel(context, albumId) as T
        }
        throw IllegalArgumentException("CuratedAlbumViewModelFactory: Cannot cast ${modelClass.simpleName}")
    }
}
