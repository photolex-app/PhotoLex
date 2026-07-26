package com.peeyupatel.phototextsearch.models.curated_album

import android.content.Context
import android.os.CancellationSignal
import android.provider.MediaStore.MediaColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumEntity
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumPhotoEntity
import com.peeyupatel.phototextsearch.datastore.SQLiteQuery
import com.peeyupatel.phototextsearch.helpers.MediaItemSortMode
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MultiAlbumDataSource
import com.peeyupatel.phototextsearch.ocr.DocumentSimilarityMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the "Find Similar" results screen: runs DocumentSimilarityMatcher against the photo
 * the user picked, then reuses the same MultiAlbumDataSource grid-loading path as Smart Albums
 * and Curated Albums so results render with the normal grid/thumbnail behavior.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FindSimilarViewModel(
    private val context: Context,
    val sourceMediaId: Long
) : ViewModel() {

    private var cancellationSignal = CancellationSignal()
    private val mediaIdsFlow = MutableStateFlow<List<Long>?>(null)

    var isLoading = MutableStateFlow(true)
        private set

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
            val matches = DocumentSimilarityMatcher.findSimilar(context, sourceMediaId)
            mediaIdsFlow.value = matches.map { it.mediaId }
            isLoading.value = false
        }
    }

    /** Creates a new curated album named [albumName] and tags [mediaIds] into it. */
    fun saveAsAlbum(albumName: String, mediaIds: List<Long>, onDone: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = ClassificationDatabase.getInstance(context).curatedAlbumDao()
            val now = System.currentTimeMillis() / 1000
            val albumId = dao.insertAlbum(
                CuratedAlbumEntity(name = albumName, createdAt = now, coverMediaId = mediaIds.firstOrNull())
            )
            dao.addPhotos(mediaIds.map { CuratedAlbumPhotoEntity(albumId = albumId, mediaId = it, addedAt = now) })
            onDone(albumId)
        }
    }

    fun cancelMediaFlow() = cancellationSignal.cancel()

    override fun onCleared() {
        cancelMediaFlow()
        super.onCleared()
    }
}

@Suppress("UNCHECKED_CAST")
class FindSimilarViewModelFactory(
    private val context: Context,
    private val sourceMediaId: Long
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == FindSimilarViewModel::class.java) {
            return FindSimilarViewModel(context, sourceMediaId) as T
        }
        throw IllegalArgumentException("FindSimilarViewModelFactory: Cannot cast ${modelClass.simpleName}")
    }
}
