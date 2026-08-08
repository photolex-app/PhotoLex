package com.peeyupatel.phototextsearch.compose.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumEntity
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Combined "create a new album" + "add to an existing album" picker for a fixed set of
 * [mediaIds]. Used wherever photos need to be tagged into a Curated Album -- both at
 * creation time and later, so adding more photos to an already-existing album never needs a
 * separate flow. Adding to an existing album is a no-op for photos already tagged in it
 * (CuratedAlbumDao.addPhotos uses OnConflictStrategy.IGNORE).
 */
@Composable
fun AddToCuratedAlbumDialog(
    mediaIds: List<Long>,
    onDismiss: () -> Unit,
    onAdded: (albumName: String, count: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<Pair<CuratedAlbumEntity, Int>>>(emptyList()) }
    var isLoadingAlbums by remember { mutableStateOf(true) }
    var newAlbumName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val dao = ClassificationDatabase.getInstance(context).curatedAlbumDao()
        albums = dao.getAllAlbums().map { it to dao.getPhotoCount(it.id) }
        isLoadingAlbums = false
    }

    fun createAndAdd() {
        val name = newAlbumName.trim()
        if (name.isEmpty()) return
        coroutineScope.launch(Dispatchers.IO) {
            val dao = ClassificationDatabase.getInstance(context).curatedAlbumDao()
            val now = System.currentTimeMillis() / 1000
            val albumId = dao.insertAlbum(
                CuratedAlbumEntity(name = name, createdAt = now, coverMediaId = mediaIds.firstOrNull())
            )
            dao.addPhotos(mediaIds.map { CuratedAlbumPhotoEntity(albumId = albumId, mediaId = it, addedAt = now) })
            withContext(Dispatchers.Main) {
                onAdded(name, mediaIds.size)
            }
        }
    }

    fun addToExisting(album: CuratedAlbumEntity) {
        coroutineScope.launch(Dispatchers.IO) {
            val dao = ClassificationDatabase.getInstance(context).curatedAlbumDao()
            val now = System.currentTimeMillis() / 1000
            dao.addPhotos(mediaIds.map { CuratedAlbumPhotoEntity(albumId = album.id, mediaId = it, addedAt = now) })
            withContext(Dispatchers.Main) {
                onAdded(album.name, mediaIds.size)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Album") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("New album name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { createAndAdd() },
                        enabled = newAlbumName.isNotBlank()
                    ) {
                        Icon(painterResource(id = R.drawable.add), contentDescription = "Create new album")
                    }
                }

                if (albums.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "Or add to an existing album",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn {
                        items(albums) { (album, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addToExisting(album) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = album.name, modifier = Modifier.weight(1f))
                                Text(
                                    text = "$count photo${if (count == 1) "" else "s"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!isLoadingAlbums) {
                    Text(
                        text = "No existing albums yet -- create one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
