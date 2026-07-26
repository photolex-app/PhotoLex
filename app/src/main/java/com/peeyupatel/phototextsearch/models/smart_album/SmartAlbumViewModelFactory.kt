package com.peeyupatel.phototextsearch.models.smart_album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Suppress("UNCHECKED_CAST")
class SmartAlbumViewModelFactory(
    private val context: Context,
    private val category: String
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == SmartAlbumViewModel::class.java) {
            return SmartAlbumViewModel(context, category) as T
        }
        throw IllegalArgumentException("SmartAlbumViewModelFactory: Cannot cast ${modelClass.simpleName}")
    }
}
