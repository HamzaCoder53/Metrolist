package io.github.hamzacoder53.wavelet.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication

@Composable
fun ArtistScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    onOpenCollection: (kind: String, id: String) -> Unit,
    onOpenArtist: (browseId: String) -> Unit = {},
    viewModel: ArtistViewModel = viewModel(),
) {
    val context = LocalContext.current
    val downloads = (context.applicationContext as WaveletApplication).downloads
    val downloaded by downloads.songs.collectAsState()
    val state by viewModel.state.collectAsState()
    val downloadingText = stringResource(R.string.download)

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            when (val current = state) {
                ArtistState.Loading -> item { CenteredText(stringResource(R.string.loading)) }
                ArtistState.Failed -> {
                    item { CenteredText(stringResource(R.string.load_failed)) }
                    item {
                        FilledTonalButton(
                            onClick = viewModel::load,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.retry)) },
                        )
                    }
                }
                is ArtistState.Loaded -> {
                    item(key = "header") {
                        ListHeader {
                            Text(current.artist.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (current.topSongs.isNotEmpty()) {
                        item(key = "play") {
                            Button(
                                onClick = { onPlay(current.topSongs, 0) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                                label = { Text(stringResource(R.string.play)) },
                            )
                        }
                        item(key = "shuffle") {
                            FilledTonalButton(
                                onClick = { onPlay(current.topSongs.shuffled(), 0) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null) },
                                label = { Text(stringResource(R.string.shuffle)) },
                            )
                        }
                    }

                    current.sections.forEachIndexed { sectionIndex, section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "s:$sectionIndex") {
                                ListHeader {
                                    Text(section.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            val songs = section.items.filterIsInstance<SongItem>()
                            items(section.items, key = { "s:$sectionIndex:" + it.id }) { item ->
                                when (item) {
                                    is SongItem -> {
                                        val index = songs.indexOf(item)
                                        ItemButton(
                                            title = item.title,
                                            subtitle = item.artists.joinToString { it.name },
                                            thumbnail = item.thumbnail,
                                            onClick = { onPlay(songs, if (index >= 0) index else 0) },
                                            onLongClick = {
                                                downloads.download(listOf(item))
                                                Toast.makeText(context, "$downloadingText: ${item.title}", Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                    is AlbumItem -> {
                                        ItemButton(
                                            title = item.title,
                                            subtitle = item.year?.toString() ?: item.artists?.joinToString { it.name },
                                            thumbnail = item.thumbnail,
                                            onClick = { onOpenCollection(CollectionViewModel.KIND_ALBUM, item.browseId) },
                                        )
                                    }
                                    is PlaylistItem -> {
                                        ItemButton(
                                            title = item.title,
                                            subtitle = item.author?.name,
                                            thumbnail = item.thumbnail,
                                            onClick = { onOpenCollection(CollectionViewModel.KIND_PLAYLIST, item.id) },
                                        )
                                    }
                                    is ArtistItem -> {
                                        ItemButton(
                                            title = item.title,
                                            subtitle = stringResource(R.string.artist),
                                            thumbnail = item.thumbnail,
                                            onClick = { onOpenArtist(item.id) },
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
