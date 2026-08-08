package com.peeyupatel.phototextsearch.compose.grids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peeyupatel.phototextsearch.LocalNavController
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.compose.FolderIsEmpty
import com.peeyupatel.phototextsearch.compose.FullWidthDialogButton
import com.peeyupatel.phototextsearch.compose.ViewProperties
import com.peeyupatel.phototextsearch.compose.dialogs.LavenderDialogBase
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumEntity
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.helpers.RowPosition
import com.peeyupatel.phototextsearch.helpers.Screens
import com.peeyupatel.phototextsearch.helpers.permanentlyDeletePhotoList
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import com.peeyupatel.phototextsearch.models.curated_album.CuratedAlbumViewModel
import com.peeyupatel.phototextsearch.models.curated_album.CuratedAlbumViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen browsing view for a single user-created Curated Album -- photos are tagged
 * membership rows (CuratedAlbumPhotoEntity), never copies, so removing a photo here only
 * removes the tag, it stays untouched in the real gallery.
 */
@Composable
fun CuratedAlbumDetailView(
    albumId: Long,
    albumName: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val viewModel: CuratedAlbumViewModel = viewModel(
        factory = CuratedAlbumViewModelFactory(context, albumId)
    )

    val liveMedia by viewModel.mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groupedMedia = remember { mutableStateOf<List<MediaStoreData>>(emptyList()) }

    LaunchedEffect(liveMedia) {
        groupedMedia.value = liveMedia
        mainViewModel.setGroupedMedia(liveMedia)
    }

    val selectedItemsList = remember { mutableStateListOf<MediaStoreData>() }
    val selectedWithoutSection by remember {
        androidx.compose.runtime.derivedStateOf { selectedItemsList.filter { it.type != MediaType.Section } }
    }
    val gridState = rememberLazyGridState()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                text = albumName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(painterResource(id = R.drawable.delete), contentDescription = "Delete album")
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (groupedMedia.value.isEmpty()) {
                FolderIsEmpty(
                    emptyText = ViewProperties.CuratedAlbum.emptyText,
                    emptyIconResId = ViewProperties.CuratedAlbum.emptyIconResId
                )
            } else {
                PhotoGrid(
                    groupedMedia = groupedMedia,
                    albumInfo = AlbumInfo.createPathOnlyAlbum(emptyList()),
                    selectedItemsList = selectedItemsList,
                    viewProperties = ViewProperties.CuratedAlbum,
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (selectedWithoutSection.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            val toRemove = selectedWithoutSection
                            coroutineScope.launch(Dispatchers.IO) {
                                viewModel.removePhotos(toRemove.map { it.id })
                            }
                            groupedMedia.value = groupedMedia.value.filter { it !in toRemove }
                            selectedItemsList.clear()
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(id = R.drawable.close), contentDescription = "Remove from Album")
                    Text("Remove from Album", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showDeleteDialog) {
        LavenderDialogBase(
            onDismiss = { showDeleteDialog = false }
        ) {
            Text(
                text = "Delete \"$albumName\"?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "This is a PhotoLex album, not a real folder -- deleting it never affects albums created by other apps. Choose whether to also permanently delete the photos themselves from your gallery, or just remove this album.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            FullWidthDialogButton(
                text = "Delete Album Only",
                color = MaterialTheme.colorScheme.surfaceContainer,
                textColor = MaterialTheme.colorScheme.onSurface,
                position = RowPosition.Top
            ) {
                coroutineScope.launch(Dispatchers.IO) {
                    ClassificationDatabase.getInstance(context).curatedAlbumDao().deleteAlbumAndTags(albumId)
                }
                showDeleteDialog = false
                onDeleted()
            }

            FullWidthDialogButton(
                text = "Delete Album + Photos",
                color = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.onError,
                position = RowPosition.Middle
            ) {
                val urisToDelete = groupedMedia.value
                    .filter { it.type != MediaType.Section }
                    .map { it.uri }

                coroutineScope.launch(Dispatchers.IO) {
                    ClassificationDatabase.getInstance(context).curatedAlbumDao().deleteAlbumAndTags(albumId)
                }
                permanentlyDeletePhotoList(context, urisToDelete)
                showDeleteDialog = false
                onDeleted()
            }

            FullWidthDialogButton(
                text = "Cancel",
                color = MaterialTheme.colorScheme.surfaceContainer,
                textColor = MaterialTheme.colorScheme.onSurface,
                position = RowPosition.Bottom
            ) {
                showDeleteDialog = false
            }
        }
    }
}

/**
 * "My Albums" row shown on the Albums tab, alongside SmartAlbumsRow -- one card per
 * user-created Curated Album with a live photo count, plus a "+ New" card that lets the user
 * create an empty album up front (photos get added to it later via Find Similar or a photo
 * picker). Navigates to CuratedAlbumDetailView as a real NavHost route on tap, so this is a
 * single additive composable call from the caller's perspective.
 */
@Composable
fun CuratedAlbumsRow() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<Pair<CuratedAlbumEntity, Int>>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        val dao = ClassificationDatabase.getInstance(context).curatedAlbumDao()
        albums = dao.getAllAlbums().map { it to dao.getPhotoCount(it.id) }
    }

    CuratedAlbumsRowContent(
        albums = albums,
        onAlbumClick = {
            navController.navigate(Screens.CuratedAlbumView(albumId = it.id, albumName = it.name))
        },
        onCreateClick = { showCreateDialog = true }
    )

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newAlbumName.trim()
                    if (name.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            ClassificationDatabase.getInstance(context).curatedAlbumDao().insertAlbum(
                                CuratedAlbumEntity(name = name, createdAt = System.currentTimeMillis() / 1000)
                            )
                            refreshTrigger++
                        }
                    }
                    showCreateDialog = false
                    newAlbumName = ""
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CuratedAlbumsRowContent(
    albums: List<Pair<CuratedAlbumEntity, Int>>,
    onAlbumClick: (CuratedAlbumEntity) -> Unit,
    onCreateClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .heightIn(min = 48.dp)
                    .clickable { onCreateClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(painterResource(id = R.drawable.add), contentDescription = "New Album")
                Text(
                    text = "New Album",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        items(albums) { (album, count) ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .heightIn(min = 48.dp)
                    .clickable { onAlbumClick(album) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = album.name,
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
}
