package io.github.hamzacoder53.wavelet.ui

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.artistBrowseId
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SongOptionsScreen(
    player: PlayerViewModel,
    onQueue: () -> Unit,
    onGoToArtist: (browseId: String) -> Unit = {},
) {
    val state by player.state.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as WaveletApplication
    val accountState by app.account.state.collectAsState()
    val librarySnapshot by app.accountLibrary.snapshot.collectAsState()
    val downloaded by app.downloads.songs.collectAsState()
    val scope = rememberCoroutineScope()

    val currentMediaItem = state.mediaItem
    val currentId = currentMediaItem?.mediaId
    val md = currentMediaItem?.mediaMetadata
    val title = md?.title?.toString() ?: currentId.orEmpty()
    val artist = md?.artist?.toString().orEmpty()
    val artistBrowseId = currentMediaItem?.artistBrowseId

    var likeError by remember { mutableStateOf<String?>(null) }
    var isUpdatingLike by remember { mutableStateOf(false) }

    LaunchedEffect(currentId) {
        likeError = null
        isUpdatingLike = false
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.song_options)) } }

            if (currentMediaItem == null || currentId == null) {
                item { CenteredText(stringResource(R.string.nothing_playing)) }
            } else {
                // A2. Like / Unlike (only when signed in)
                if (accountState.signedIn) {
                    val isLiked = librarySnapshot.likedSongs.any { it.id == currentId }
                    item {
                        FilledTonalButton(
                            onClick = {
                                if (isUpdatingLike) return@FilledTonalButton
                                isUpdatingLike = true
                                likeError = null
                                val targetLiked = !isLiked
                                val songToUpdate =
                                    librarySnapshot.likedSongs.firstOrNull { it.id == currentId }
                                        ?: SongItem(
                                            id = currentId,
                                            title = title,
                                            artists = listOf(Artist(name = artist, id = artistBrowseId)),
                                            duration = md?.durationMs?.let { (it / 1000).toInt() },
                                            thumbnail = md?.artworkUri?.toString().orEmpty(),
                                        )
                                scope.launch {
                                    YouTube.likeVideo(currentId, targetLiked)
                                        .onSuccess {
                                            app.accountLibrary.updateLiked(songToUpdate, targetLiked)
                                        }
                                        .onFailure { error ->
                                            Timber.w(error, "Failed to %s song %s", if (targetLiked) "like" else "unlike", currentId)
                                            likeError = context.getString(R.string.like_failed)
                                        }
                                    isUpdatingLike = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                Icon(
                                    painterResource(if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(stringResource(if (isLiked) R.string.unlike else R.string.like))
                            },
                            secondaryLabel = likeError?.let { { Text(it) } },
                        )
                    }
                }

                // A3. Up next -> queue screen
                item {
                    FilledTonalButton(
                        onClick = onQueue,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_queue), contentDescription = null) },
                        label = { Text(stringResource(R.string.up_next)) },
                    )
                }

                // Shuffle SwitchButton bound to player shuffle mode
                item {
                    SwitchButton(
                        checked = state.shuffleModeEnabled,
                        onCheckedChange = { player.setShuffle(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.shuffle)) },
                    )
                }

                // Repeat button cycling Off -> All -> One
                item {
                    val repeatLabel =
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
                            Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
                            else -> stringResource(R.string.repeat_off)
                        }
                    FilledTonalButton(
                        onClick = player::cycleRepeatMode,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_repeat), contentDescription = null) },
                        label = { Text(stringResource(R.string.repeat)) },
                        secondaryLabel = { Text(repeatLabel) },
                    )
                }

                // A4. Start radio
                item {
                    FilledTonalButton(
                        onClick = {
                            player.startRadio(
                                onSuccess = {
                                    Toast.makeText(context, R.string.radio_started, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_radio), contentDescription = null) },
                        label = { Text(stringResource(R.string.start_radio)) },
                    )
                }

                // B1 hook: Go to artist (only if current song has an artist browse id)
                if (artistBrowseId != null) {
                    item {
                        FilledTonalButton(
                            onClick = { onGoToArtist(artistBrowseId) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                            label = { Text(stringResource(R.string.go_to_artist)) },
                            secondaryLabel =
                                if (artist.isNotEmpty()) {
                                    { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                } else {
                                    null
                                },
                        )
                    }
                }

                // Download (existing download action moved here)
                item {
                    val downloadState = downloaded.firstOrNull { it.id == currentId }
                    val isDownloaded = downloadState?.isComplete == true
                    val isDownloading = downloadState != null && !isDownloaded
                    FilledTonalButton(
                        onClick = player::downloadCurrent,
                        enabled = downloadState == null,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(
                                painterResource(if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download),
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                stringResource(
                                    when {
                                        isDownloaded -> R.string.downloaded
                                        isDownloading -> R.string.download_queued
                                        else -> R.string.download
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
