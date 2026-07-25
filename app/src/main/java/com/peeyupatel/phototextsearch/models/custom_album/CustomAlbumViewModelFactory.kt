package com.peeyupatel.phototextsearch.models.custom_album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.helpers.MediaItemSortMode

@Suppress("UNCHECKED_CAST")
class CustomAlbumViewModelFactory(
	private val context: Context,
	private val albumInfo: AlbumInfo,
	private val sortBy: MediaItemSortMode
) : ViewModelProvider.NewInstanceFactory() {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass == CustomAlbumViewModel::class.java) {
			return CustomAlbumViewModel(context, albumInfo, sortBy) as T
		}
		throw IllegalArgumentException("MultiAlbumViewModel: Cannot cast ${modelClass.simpleName} as ${CustomAlbumViewModel::class.java.simpleName}!! This should never happen!!")
	}
}


