package io.github.hamzacoder53.wavelet.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.ui.CenteredText
import io.github.hamzacoder53.wavelet.ui.ItemButton

/** Synced liked songs and playlists for the signed-in account. */
@Composable
fun YourLibraryScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    onOpenPlaylist: (id: String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WaveletApplication
    val snapshot by app.accountLibrary.snapshot.collectAsState()

    // Refresh in the background each time the library opens; the saved copy shows meanwhile.
    LaunchedEffect(Unit) { app.accountLibrary.sync() }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.your_library)) } }
            if (snapshot.likedSongs.isEmpty() && snapshot.playlists.isEmpty()) {
                item { CenteredText(stringResource(R.string.syncing)) }
            }
            if (snapshot.likedSongs.isNotEmpty()) {
                item {
                    Button(
                        onClick = { onPlay(snapshot.likedSongs, 0) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                        label = { Text(stringResource(R.string.liked_songs)) },
                        secondaryLabel = { Text(stringResource(R.string.song_count, snapshot.likedSongs.size)) },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = {
                            app.downloads.download(
                                snapshot.likedSongs,
                                LIKED_COLLECTION_ID,
                                app.getString(R.string.liked_songs),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                        label = { Text(stringResource(R.string.download_liked)) },
                    )
                }
            }
            if (snapshot.playlists.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.playlists)) } }
                items(snapshot.playlists, key = { it.id }) { playlist ->
                    ItemButton(
                        title = playlist.title,
                        subtitle = playlist.subtitle,
                        thumbnail = playlist.thumbnail,
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }
            if (snapshot.likedSongs.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.liked_songs)) } }
                items(snapshot.likedSongs, key = { "l:" + it.id }) { song ->
                    ItemButton(
                        title = song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnail = song.thumbnail,
                        onClick = { onPlay(snapshot.likedSongs, snapshot.likedSongs.indexOf(song)) },
                    )
                }
            }
        }
    }
}

private const val LIKED_COLLECTION_ID = "account:liked"
