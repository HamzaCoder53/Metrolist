package io.github.hamzacoder53.wavelet.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.exoplayer.offline.Download
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.DownloadedSong

/**
 * Downloads library. With [collectionId] set it shows one downloaded album/playlist.
 * Tap plays (offline); long-press deletes after confirmation.
 */
@Composable
fun LibraryScreen(
    onPlay: (List<DownloadedSong>, Int) -> Unit,
    onOpenCollection: (String) -> Unit,
    collectionId: String? = null,
) {
    val context = LocalContext.current
    val downloads = (context.applicationContext as WaveletApplication).downloads
    val all by downloads.songs.collectAsState()
    val songs = if (collectionId == null) all else all.filter { it.collectionId == collectionId }
    val waiting by downloads.waitingForRequirements.collectAsState()
    val playable = songs.filter { it.isComplete }
    var pendingDelete by remember { mutableStateOf<List<DownloadedSong>?>(null) }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Text(
                        if (collectionId == null) {
                            stringResource(R.string.downloads)
                        } else {
                            songs.firstOrNull()?.collectionTitle ?: stringResource(R.string.downloads)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (collectionId == null) {
                item {
                    CenteredText(
                        stringResource(
                            R.string.storage_used,
                            Formatter.formatShortFileSize(context, downloads.usedBytes()),
                            Formatter.formatShortFileSize(context, downloads.freeBytes()),
                        ),
                    )
                }
            }
            if (songs.isEmpty()) {
                item { CenteredText(stringResource(R.string.no_downloads)) }
            } else {
                item {
                    Button(
                        onClick = { onPlay(playable, 0) },
                        enabled = playable.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                        label = { Text(stringResource(R.string.play_all)) },
                        secondaryLabel = { Text(stringResource(R.string.song_count, playable.size)) },
                    )
                }
            }

            if (collectionId == null) {
                val collections = downloads.collections(all)
                if (collections.isNotEmpty()) {
                    item { ListHeader { Text(stringResource(R.string.albums_and_playlists)) } }
                    items(collections, key = { "c:" + it.id }) { collection ->
                        FilledTonalButton(
                            onClick = { onOpenCollection(collection.id) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_album), contentDescription = null) },
                            label = { Text(collection.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(stringResource(R.string.song_count, collection.songs.size)) },
                        )
                    }
                    item { ListHeader { Text(stringResource(R.string.songs)) } }
                }
            }

            items(songs, key = { it.id }) { song ->
                ItemButton(
                    title = song.title,
                    subtitle = statusText(song, waiting),
                    thumbnail = null,
                    artworkModel = song.artworkFile,
                    onClick = {
                        val index = playable.indexOf(song)
                        if (index >= 0) onPlay(playable, index)
                    },
                    onLongClick = { pendingDelete = listOf(song) },
                )
            }

            if (songs.isNotEmpty()) {
                item {
                    FilledTonalButton(
                        onClick = { pendingDelete = songs },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                        label = { Text(stringResource(R.string.delete_all)) },
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = pendingDelete != null,
        onDismissRequest = { pendingDelete = null },
        title = {
            Text(
                stringResource(
                    if ((pendingDelete?.size ?: 0) > 1) R.string.delete_all_title else R.string.delete_song_title,
                ),
            )
        },
        text = { pendingDelete?.singleOrNull()?.let { Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    val toDelete = pendingDelete.orEmpty()
                    if (collectionId == null && toDelete.size == all.size && toDelete.size > 1) {
                        downloads.removeAll()
                    } else {
                        toDelete.forEach { downloads.remove(it.id) }
                    }
                    pendingDelete = null
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { pendingDelete = null }) },
    )
}

@Composable
private fun statusText(song: DownloadedSong, waitingForRequirements: Boolean): String =
    when (song.state) {
        Download.STATE_COMPLETED -> song.artist
        Download.STATE_DOWNLOADING -> stringResource(R.string.downloading_percent, song.percent.toInt())
        Download.STATE_FAILED -> stringResource(R.string.download_failed)
        else ->
            if (waitingForRequirements) {
                stringResource(R.string.download_waiting)
            } else {
                stringResource(R.string.download_queued)
            }
    }
