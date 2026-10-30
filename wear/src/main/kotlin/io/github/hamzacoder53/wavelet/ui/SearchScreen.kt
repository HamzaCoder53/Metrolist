package io.github.hamzacoder53.wavelet.ui

import android.app.RemoteInput
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.playback.THUMBNAIL_PX
import io.github.hamzacoder53.wavelet.playback.sizedThumbnail

private const val QUERY_KEY = "query"

@Composable
fun SearchScreen(
    onPlay: (List<SongItem>, Int) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboardLabel = stringResource(R.string.search)

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

            when (val current = state) {
                SearchState.Idle -> Unit
                is SearchState.Loading -> item { CenteredText(stringResource(R.string.searching)) }
                is SearchState.Failed -> item { CenteredText(stringResource(R.string.search_failed)) }
                is SearchState.Results -> {
                    item { ListHeader { Text(current.query, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    if (current.songs.isEmpty()) {
                        item { CenteredText(stringResource(R.string.no_results)) }
                    }
                    itemsIndexed(current.songs, key = { _, song -> song.id }) { index, song ->
                        SongButton(song = song, onClick = { onPlay(current.songs, index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SongButton(
    song: SongItem,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = {
            AsyncImage(
                model = song.thumbnail.sizedThumbnail(THUMBNAIL_PX),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        },
        label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
internal fun CenteredText(text: String) {
    Text(text = text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}
