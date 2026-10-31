package io.github.hamzacoder53.wavelet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
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
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun HomeScreen(
    nowPlaying: MediaItem?,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit,
    onAccount: () -> Unit,
    onSleepTimer: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPlay: (List<SongItem>, Int) -> Unit,
    onOpenCollection: (kind: String, id: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val sections by viewModel.sections.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as WaveletApplication
    val sleepTimer = app.sleepTimer
    val timerState by sleepTimer.state.collectAsState()
    val updateInfo by app.updateChecker.updateAvailable.collectAsState()
    val scope = rememberCoroutineScope()
    var failedUrl by remember { mutableStateOf<String?>(null) }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerState) {
        while (timerState is SleepTimer.State.At) {
            now.longValue = System.currentTimeMillis()
            delay(1000)
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.app_name)) } }
            updateInfo?.let { update ->
                item {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val helper = RemoteActivityHelper(context)
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                    helper.startRemoteActivity(intent).await()
                                } catch (e: Exception) {
                                    Timber.w(e, "Couldn't open update URL on paired phone")
                                    failedUrl = update.releaseUrl
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                        label = { Text(stringResource(R.string.update_available, update.version)) },
                        secondaryLabel = failedUrl?.let { url ->
                            { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                    )
                }
            }
            item {
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    label = { Text(stringResource(R.string.search)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onNowPlaying,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_music_note), contentDescription = null) },
                    label = { Text(stringResource(R.string.now_playing)) },
                    secondaryLabel = {
                        Text(
                            text = nowPlaying?.mediaMetadata?.title?.toString()
                                ?: stringResource(R.string.nothing_playing),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onPlaylists,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_playlist), contentDescription = null) },
                    label = { Text(stringResource(R.string.playlists)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onDownloads,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                    label = { Text(stringResource(R.string.downloads)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onAccount,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                    label = { Text(stringResource(R.string.account)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onSleepTimer,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_timer), contentDescription = null) },
                    label = { Text(stringResource(R.string.sleep_timer)) },
                    secondaryLabel = { Text(sleepTimerSummary(timerState, now.longValue)) },
                )
            }
            // Recommended music: each YouTube Music home section with its songs, albums and playlists.
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "s:$sectionIndex") {
                    ListHeader { Text(section.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                items(section.items, key = { "s:$sectionIndex:" + it.id }) { item ->
                    when (item) {
                        is SongItem ->
                            ItemButton(
                                title = item.title,
                                subtitle = item.artists.joinToString { it.name },
                                thumbnail = item.thumbnail,
                                onClick = { onPlay(section.songs, section.songs.indexOf(item)) },
                            )
                        is AlbumItem ->
                            ItemButton(
                                title = item.title,
                                subtitle = item.artists?.joinToString { it.name },
                                thumbnail = item.thumbnail,
                                onClick = { onOpenCollection(CollectionViewModel.KIND_ALBUM, item.browseId) },
                            )
                        is PlaylistItem ->
                            ItemButton(
                                title = item.title,
                                subtitle = item.author?.name,
                                thumbnail = item.thumbnail,
                                onClick = { onOpenCollection(CollectionViewModel.KIND_PLAYLIST, item.id) },
                            )
                        else -> Unit
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onAbout,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                    label = { Text(stringResource(R.string.about)) },
                )
            }
        }
        if (failedUrl != null) {
            AlertDialog(
                visible = true,
                onDismissRequest = { failedUrl = null },
                title = { Text(stringResource(R.string.update_release_url)) },
                text = { Text(failedUrl.orEmpty()) },
                confirmButton = {
                    AlertDialogDefaults.ConfirmButton(onClick = { failedUrl = null })
                },
            )
        }
    }
}
