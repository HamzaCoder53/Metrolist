package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
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

/** An album or playlist from search: play it, or download every song for offline use. */
@Composable
fun CollectionScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    viewModel: CollectionViewModel = viewModel(),
) {
    val downloads = (LocalContext.current.applicationContext as WaveletApplication).downloads
    val downloaded by downloads.songs.collectAsState()
    val state by viewModel.state.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            when (val current = state) {
                CollectionState.Loading -> item { CenteredText(stringResource(R.string.loading)) }
                CollectionState.Failed -> {
                    item { CenteredText(stringResource(R.string.load_failed)) }
                    item {
                        FilledTonalButton(
                            onClick = viewModel::load,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.search)) },
                        )
                    }
                }
                is CollectionState.Loaded -> {
                    item { ListHeader { Text(current.title, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
                    current.subtitle?.let { subtitle -> item { CenteredText(subtitle) } }
                    item {
                        Button(
                            onClick = { onPlay(current.songs, 0) },
                            enabled = current.songs.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                            label = { Text(stringResource(R.string.play_all)) },
                            secondaryLabel = { Text(stringResource(R.string.song_count, current.songs.size)) },
                        )
                    }
                    item {
                        val allDownloaded =
                            current.songs.isNotEmpty() &&
                                current.songs.all { song -> downloaded.any { it.id == song.id && it.isComplete } }
                        FilledTonalButton(
                            onClick = { downloads.download(current.songs, viewModel.id, current.title) },
                            enabled = current.songs.isNotEmpty() && !allDownloaded,
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                Icon(
                                    painterResource(if (allDownloaded) R.drawable.ic_download_done else R.drawable.ic_download),
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(stringResource(if (allDownloaded) R.string.downloaded else R.string.download_all))
                            },
                        )
                    }
                    itemsIndexed(current.songs, key = { _, song -> song.id }) { index, song ->
                        ItemButton(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnail = song.thumbnail,
                            onClick = { onPlay(current.songs, index) },
                        )
                    }
                }
            }
        }
    }
}
