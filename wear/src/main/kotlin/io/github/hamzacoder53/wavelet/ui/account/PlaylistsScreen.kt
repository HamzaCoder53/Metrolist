package io.github.hamzacoder53.wavelet.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.account.AccountMode
import io.github.hamzacoder53.wavelet.ui.CenteredText
import io.github.hamzacoder53.wavelet.ui.ItemButton
import kotlinx.coroutines.launch

/** Home → Playlists: liked songs, the user's playlists, and (optionally) their uploaded music. */
@Composable
fun PlaylistsScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    onOpenPlaylist: (id: String) -> Unit,
    onSignIn: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as WaveletApplication
    val accountState by app.account.state.collectAsState()
    val snapshot by app.accountLibrary.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var showUploads by remember { mutableStateOf(app.settings.showUploads) }
    var syncing by remember { mutableStateOf(false) }

    fun sync() {
        syncing = true
        scope.launch {
            app.accountLibrary.sync()
            syncing = false
        }
    }

    // Refresh in the background each time; the saved copy shows meanwhile.
    LaunchedEffect(accountState.mode) { if (accountState.signedIn) sync() }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.playlists)) } }

            if (!accountState.signedIn) {
                item { CenteredText(stringResource(R.string.playlists_sign_in)) }
                item {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                        label = { Text(stringResource(R.string.account)) },
                    )
                }
                return@TransformingLazyColumn
            }

            if (accountState.expired) {
                item { CenteredText(stringResource(R.string.sign_in_expired)) }
                item {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sign_in_again)) },
                    )
                }
            }

            item {
                Button(
                    onClick = { onPlay(snapshot.likedSongs, 0) },
                    enabled = snapshot.likedSongs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                    label = { Text(stringResource(R.string.liked_songs)) },
                    secondaryLabel = { Text(stringResource(R.string.song_count, snapshot.likedSongs.size)) },
                )
            }
            if (snapshot.playlists.isEmpty()) {
                item { CenteredText(stringResource(if (syncing) R.string.syncing else R.string.no_playlists)) }
            }
            items(snapshot.playlists, key = { "p:" + it.id }) { playlist ->
                ItemButton(
                    title = playlist.title,
                    subtitle = playlist.subtitle,
                    thumbnail = playlist.thumbnail,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }

            item { ListHeader { Text(stringResource(R.string.uploaded_music)) } }
            item {
                SwitchButton(
                    checked = showUploads,
                    onCheckedChange = {
                        showUploads = it
                        app.settings.showUploads = it
                        if (it) sync()
                    },
                    enabled = accountState.mode == AccountMode.WEB,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.show_uploaded_music)) },
                )
            }
            if (accountState.mode == AccountMode.CODE) {
                item { CenteredText(stringResource(R.string.uploads_need_full_sign_in)) }
            } else if (showUploads) {
                if (snapshot.uploads.isEmpty()) {
                    item { CenteredText(stringResource(if (syncing) R.string.syncing else R.string.no_uploads)) }
                } else {
                    item {
                        FilledTonalButton(
                            onClick = { onPlay(snapshot.uploads, 0) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                            label = { Text(stringResource(R.string.play_all)) },
                            secondaryLabel = { Text(stringResource(R.string.song_count, snapshot.uploads.size)) },
                        )
                    }
                    items(snapshot.uploads, key = { "u:" + it.id }) { song ->
                        ItemButton(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnail = song.thumbnail,
                            onClick = { onPlay(snapshot.uploads, snapshot.uploads.indexOf(song)) },
                        )
                    }
                }
            }
        }
    }
}
