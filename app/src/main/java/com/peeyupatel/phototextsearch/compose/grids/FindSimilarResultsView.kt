package com.peeyupatel.phototextsearch.compose.grids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.compose.ViewProperties
import com.peeyupatel.phototextsearch.compose.dialogs.AddToCuratedAlbumDialog
import com.peeyupatel.phototextsearch.compose.dialogs.ConfirmationDialogWithBody
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarController
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarEvents
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.helpers.permanentlyDeletePhotoList
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import com.peeyupatel.phototextsearch.models.curated_album.FindSimilarViewModel
import com.peeyupatel.phototextsearch.models.curated_album.FindSimilarViewModelFactory
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.launch

/**
 * Full-screen "photos similar to this one" results, driven by DocumentSimilarityMatcher. The
 * "+" action and, once a selection is made, a bottom bar both open the same combined
 * create-new-or-add-to-existing album picker -- they act on the current selection when one
 * exists, or the whole result set otherwise.
 */
@Composable
fun FindSimilarResultsView(
    sourceMediaId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FindSimilarViewModel = viewModel(
        factory = FindSimilarViewModelFactory(context, sourceMediaId)
    )

    val liveMedia by viewModel.mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
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
    var showAddToAlbumDialog by remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val snackbarScope = androidx.compose.runtime.rememberCoroutineScope()

    // Frozen at the moment the action is tapped, not re-derived while a dialog is open --
    // otherwise a recomposition mid-dialog (e.g. triggered by the keyboard opening for the
    // album-name field) can re-read selectedWithoutSection as transiently empty and silently
    // fall back to "the whole result set", tagging/deleting far more than the user selected.
    var pendingAlbumTargetIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var pendingDeleteTargets by remember { mutableStateOf<List<MediaStoreData>>(emptyList()) }

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
                text = if (selectedWithoutSection.isEmpty()) "Similar Documents" else "${selectedWithoutSection.size} selected",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            if (groupedMedia.value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        pendingAlbumTargetIds = (selectedWithoutSection.ifEmpty {
                            groupedMedia.value.filter { it.type != MediaType.Section }
                        }).map { it.id }
                        showAddToAlbumDialog = true
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(painterResource(id = R.drawable.add), contentDescription = "Add to Album")
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                groupedMedia.value.isEmpty() -> {
                    Text(
                        text = "No similar documents found -- this works best once more of your gallery has been indexed.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
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

        if (selectedWithoutSection.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            pendingAlbumTargetIds = selectedWithoutSection.map { it.id }
                            showAddToAlbumDialog = true
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(id = R.drawable.add), contentDescription = "Add to Album")
                    Text("Add to Album", style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            pendingDeleteTargets = selectedWithoutSection
                            showDeleteDialog.value = true
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.delete),
                        contentDescription = "Delete",
                        // Destructive action gets the semantic error color -- see SinglePhotoView.kt.
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showAddToAlbumDialog) {
        AddToCuratedAlbumDialog(
            mediaIds = pendingAlbumTargetIds,
            onDismiss = { showAddToAlbumDialog = false },
            onAdded = { albumName, count ->
                showAddToAlbumDialog = false
                selectedItemsList.clear()
                snackbarScope.launch {
                    LavenderSnackbarController.pushEvent(
                        LavenderSnackbarEvents.MessageEvent(
                            message = "Added $count photo${if (count == 1) "" else "s"} to \"$albumName\"",
                            iconResId = R.drawable.check_item,
                            duration = SnackbarDuration.Short
                        )
                    )
                }
            }
        )
    }

    ConfirmationDialogWithBody(
        showDialog = showDeleteDialog,
        dialogTitle = "Permanently delete these items?",
        dialogBody = "This action cannot be undone!",
        confirmButtonLabel = "Delete"
    ) {
        val toDelete = pendingDeleteTargets
        permanentlyDeletePhotoList(context, toDelete.map { it.uri })
        groupedMedia.value = groupedMedia.value.filter { it !in toDelete }
        selectedItemsList.clear()
    }
}
