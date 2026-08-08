package com.peeyupatel.phototextsearch.models.album_grid

import android.content.Context
import android.os.CancellationSignal
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.datastore.MainGalleryView
import com.peeyupatel.phototextsearch.mediastore.AlbumStoreDataSource
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class AlbumsViewModel(context: Context, var albumInfo: List<AlbumInfo>) : ViewModel() {
    private var cancellationSignal = CancellationSignal()
    private val mediaStoreDataSource = mutableStateOf(initDataSource(context = context, albums = albumInfo))

    val mediaFlow by derivedStateOf {
        getMediaDataFlow().value.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private fun getMediaDataFlow(): State<Flow<List<Pair<AlbumInfo, MediaStoreData>>>> = derivedStateOf {
    	mediaStoreDataSource.value.loadMediaStoreData().flowOn(Dispatchers.IO)
   	}

    fun refresh(
        context: Context,
        albums: List<AlbumInfo>
    ) {
        if (albums.toSet() == albumInfo.toSet()) return

        cancellationSignal.cancel()
        cancellationSignal = CancellationSignal()

        mediaStoreDataSource.value = initDataSource(context = context, albums = albums)
    }

    private fun initDataSource(
        context: Context,
        albums: List<AlbumInfo>
    ) = run {
        albumInfo = albums
        val queries = albums.map { album ->
            val query = mainViewModel.settings.MainGalleryView.getSQLiteQuery(albums = album.paths)
            Pair(album, query)
        }

        AlbumStoreDataSource(
            context = context,
            albumQueryPairs = queries,
            cancellationSignal = cancellationSignal
        )
    }
}


