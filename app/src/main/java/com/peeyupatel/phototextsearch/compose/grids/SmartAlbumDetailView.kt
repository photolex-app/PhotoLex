package com.peeyupatel.phototextsearch.compose.grids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.compose.FolderIsEmpty
import com.peeyupatel.phototextsearch.compose.ViewProperties
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.models.smart_album.SmartAlbumViewModel
import com.peeyupatel.phototextsearch.models.smart_album.SmartAlbumViewModelFactory
import com.peeyupatel.phototextsearch.ocr.PhotoCategoryClassifier
import kotlinx.coroutines.delay

/**
 * Full-screen Smart Album browsing view -- shows photos auto-classified into a category
 * (Bills & Receipts, ID Cards, Screenshots, Documents), a new browsing mode additive to normal
 * folder albums and full-gallery search, not a replacement for either.
 */
@Composable
fun SmartAlbumDetailView(
    category: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SmartAlbumViewModel = viewModel(
        factory = SmartAlbumViewModelFactory(context, category)
    )

    val liveMedia by viewModel.mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groupedMedia = remember { mutableStateOf<List<MediaStoreData>>(emptyList()) }

    LaunchedEffect(liveMedia) {
        groupedMedia.value = liveMedia
        // Same mechanism SearchPage.kt/FavouritesGridView.kt use for non-folder-based photo
        // lists -- SinglePhotoView reads from mainViewModel.groupedMedia when
        // loadsFromMainViewModel=true (wired for ViewProperties.SmartAlbum in PhotoGridView.kt),
        // since there's no real folder path here for it to reconstruct the list from.
        mainViewModel.setGroupedMedia(liveMedia)
    }

    val selectedItemsList = remember { mutableStateListOf<MediaStoreData>() }
    val gridState = rememberLazyGridState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(id = R.drawable.back_arrow), contentDescription = "Back")
            }
            Text(
                text = PhotoCategoryClassifier.categoryDisplayName(category),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (groupedMedia.value.isEmpty()) {
                FolderIsEmpty(
                    emptyText = ViewProperties.SmartAlbum.emptyText,
                    emptyIconResId = ViewProperties.SmartAlbum.emptyIconResId
                )
            } else {
                PhotoGrid(
                    groupedMedia = groupedMedia,
                    albumInfo = AlbumInfo.createPathOnlyAlbum(emptyList()),
                    selectedItemsList = selectedItemsList,
                    viewProperties = ViewProperties.SmartAlbum,
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Self-contained "Smart Albums" row shown at the top of the Albums tab -- one card per
 * auto-detected category, with a live count. Manages its own selected-category state and
 * renders the SmartAlbumDetailView as a full-screen Dialog on tap, so this is a single
 * additive composable call from the caller's perspective (no state/wiring needed there).
 * Renders nothing until at least one photo has been categorized, to avoid showing an empty
 * row before background indexing has produced any categories yet.
 */
@Composable
fun SmartAlbumsRow() {
    val context = LocalContext.current
    var categoryCounts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    // rememberSaveable, not remember -- tapping a photo inside SmartAlbumDetailView's grid
    // navigates to a SinglePhotoView on top of this screen, which tears down and later
    // recreates this composition on back-navigation. Plain remember would reset to null on
    // that recreation, silently closing the album view instead of reappearing.
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val dao = ClassificationDatabase.getInstance(context).photoClassificationDao()
        while (true) {
            val categories = dao.getDistinctCategories()
            categoryCounts = categories.map { it to dao.getCategoryCount(it) }
            delay(10_000L) // refresh periodically as background indexing categorizes more photos
        }
    }

    if (categoryCounts.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
    ) {
        items(categoryCounts) { (category, count) ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { selectedCategory = category }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = PhotoCategoryClassifier.categoryDisplayName(category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count photo${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    selectedCategory?.let { category ->
        Dialog(
            onDismissRequest = { selectedCategory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SmartAlbumDetailView(
                category = category,
                onBack = { selectedCategory = null }
            )
        }
    }
}
