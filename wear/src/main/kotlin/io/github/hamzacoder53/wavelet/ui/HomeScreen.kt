package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
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
import io.github.hamzacoder53.wavelet.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    nowPlaying: MediaItem?,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onDownloads: () -> Unit,
    onAccount: () -> Unit,
    onSleepTimer: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPlay: (List<SongItem>, Int) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val sleepTimer = (LocalContext.current.applicationContext as WaveletApplication).sleepTimer
    val timerState by sleepTimer.state.collectAsState()
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
            suggestions?.let { picks ->
                item { ListHeader { Text(picks.title, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                itemsIndexed(picks.songs, key = { _, song -> "q:" + song.id }) { index, song ->
                    ItemButton(
                        title = song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnail = song.thumbnail,
                        onClick = { onPlay(picks.songs, index) },
                    )
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
    }
}
