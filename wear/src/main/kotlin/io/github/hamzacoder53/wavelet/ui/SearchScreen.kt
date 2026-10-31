package io.github.hamzacoder53.wavelet.ui

import android.app.RemoteInput
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.THUMBNAIL_PX
import io.github.hamzacoder53.wavelet.playback.sizedThumbnail

private const val QUERY_KEY = "query"

@Composable
fun SearchScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    onOpenCollection: (kind: String, id: String) -> Unit,
    onOpenArtist: (browseId: String) -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val context = LocalContext.current
    val downloads = (context.applicationContext as WaveletApplication).downloads
    val state by viewModel.state.collectAsState()
    val type by viewModel.type.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val keyboardLabel = stringResource(R.string.search)
    val downloadingText = stringResource(R.string.download)

    val voiceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let(viewModel::search)
        }
    val keyboardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data
                ?.let { RemoteInput.getResultsFromIntent(it) }
                ?.getCharSequence(QUERY_KEY)
                ?.let { viewModel.search(it.toString()) }
        }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.search)) } }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilledIconButton(
                        onClick = {
                            voiceLauncher.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    ),
                            )
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice_search))
                    }
                    FilledIconButton(
                        onClick = {
                            val remoteInput =
                                RemoteInput.Builder(QUERY_KEY)
                                    .setLabel(keyboardLabel)
                                    .wearableExtender {
                                        setEmojisAllowed(false)
                                        setInputActionType(EditorInfo.IME_ACTION_SEARCH)
                                    }.build()
                             val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                            keyboardLauncher.launch(intent)
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_keyboard), stringResource(R.string.keyboard_search))
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    // Icon-only so all fit on small round screens; the selected one is filled.
                    SearchType.entries.forEach { option ->
                        val label = stringResource(option.labelRes())
                        val icon =
                            when (option) {
                                SearchType.SONGS -> R.drawable.ic_music_note
                                SearchType.ALBUMS -> R.drawable.ic_album
                                SearchType.ARTISTS -> R.drawable.ic_account
                                SearchType.PLAYLISTS -> R.drawable.ic_playlist
                            }
                        if (option == type) {
                            FilledIconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                                Icon(painterResource(icon), label, Modifier.size(20.dp))
                            }
                        } else {
                            FilledTonalIconButton(onClick = { viewModel.setType(option) }, modifier = Modifier.size(38.dp)) {
                                Icon(painterResource(icon), label, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            when (val current = state) {
                SearchState.Idle -> {
                    if (recentSearches.isNotEmpty()) {
                        item { ListHeader { Text(stringResource(R.string.recent_searches)) } }
                        items(recentSearches, key = { "recent_$it" }) { query ->
                            FilledTonalButton(
                                onClick = { viewModel.search(query) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                                label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                        item {
                            FilledTonalButton(
                                onClick = viewModel::clearRecentSearches,
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                label = { Text(stringResource(R.string.clear_recent_searches)) },
                            )
                        }
                    }
                }
                is SearchState.Loading -> item { CenteredText(stringResource(R.string.searching)) }
                is SearchState.Failed -> item { CenteredText(stringResource(R.string.search_failed)) }
                is SearchState.Results -> {
                    item {
                        ListHeader {
                            Text(
                                "${stringResource(type.labelRes())}: ${current.query}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (current.items.isEmpty()) {
                        item { CenteredText(stringResource(R.string.no_results)) }
                    }
                    val songs = current.songs
                    items(current.items, key = { it.id }) { item ->
                        when (item) {
                            is SongItem ->
                                ItemButton(
                                    title = item.title,
                                    subtitle = item.artists.joinToString { it.name },
                                    thumbnail = item.thumbnail,
                                    onClick = { onPlay(songs, songs.indexOf(item)) },
                                    onLongClick = {
                                        downloads.download(listOf(item))
                                        Toast.makeText(context, "$downloadingText: ${item.title}", Toast.LENGTH_SHORT).show()
                                    },
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
                            is ArtistItem ->
                                ItemButton(
                                    title = item.title,
                                    subtitle = stringResource(R.string.artist),
                                    thumbnail = item.thumbnail,
                                    onClick = { onOpenArtist(item.id) },
                                )
                            else -> Unit
                        }
                    }
                    if (current.suggestions.isNotEmpty()) {
                        item { ListHeader { Text(stringResource(R.string.related_searches)) } }
                        items(current.suggestions, key = { "suggestion_$it" }) { suggestion ->
                            FilledTonalButton(
                                onClick = { viewModel.search(suggestion) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                                label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SearchType.labelRes(): Int =
    when (this) {
        SearchType.SONGS -> R.string.songs
        SearchType.ALBUMS -> R.string.albums
        SearchType.ARTISTS -> R.string.artists
        SearchType.PLAYLISTS -> R.string.playlists
    }

@Composable
internal fun ItemButton(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    artworkModel: Any? = thumbnail?.sizedThumbnail(THUMBNAIL_PX),
) {
    FilledTonalButton(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        icon = {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        },
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(subtitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
internal fun CenteredText(text: String) {
    // Inset so long lines stay clear of the round display's curved edges.
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}
