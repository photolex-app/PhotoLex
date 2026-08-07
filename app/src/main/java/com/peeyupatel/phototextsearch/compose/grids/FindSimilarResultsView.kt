package com.peeyupatel.phototextsearch.compose.grids

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarController
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarEvents
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.datastore.AlbumInfo
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.models.curated_album.FindSimilarViewModel
import com.peeyupatel.phototextsearch.models.curated_album.FindSimilarViewModelFactory
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.launch

/**
 * Full-screen "photos similar to this one" results, driven by DocumentSimilarityMatcher.
 * Lets the user save the whole match set as a new named Curated Album (tag-only, no photo
 * copies) via the top-bar "+" action.
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
    val gridState = rememberLazyGridState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var albumName by remember { mutableStateOf("") }
    val snackbarScope = androidx.compose.runtime.rememberCoroutineScope()

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
                text = "Similar Documents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            if (groupedMedia.value.isNotEmpty()) {
                IconButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(painterResource(id = R.drawable.add), contentDescription = "Save as Album")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save as Album") },
            text = {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = albumName.trim()
                        if (name.isNotEmpty()) {
                            val mediaIds = groupedMedia.value.map { it.id }
                            viewModel.saveAsAlbum(name, mediaIds) {
                                snackbarScope.launch {
                                    LavenderSnackbarController.pushEvent(
                                        LavenderSnackbarEvents.MessageEvent(
                                            message = "Saved \"$name\" with ${mediaIds.size} photos",
                                            iconResId = R.drawable.check_item,
                                            duration = SnackbarDuration.Short
                                        )
                                    )
                                }
                            }
                            showSaveDialog = false
                            albumName = ""
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
