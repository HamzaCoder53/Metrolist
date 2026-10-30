package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.MediaItem
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R

@Composable
fun HomeScreen(
    nowPlaying: MediaItem?,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
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
