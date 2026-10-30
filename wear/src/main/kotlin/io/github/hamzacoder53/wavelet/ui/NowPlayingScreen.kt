package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import io.github.hamzacoder53.wavelet.R
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Pixels of crown rotation per volume step. */
private const val ROTARY_STEP_PX = 48f

@Composable
fun NowPlayingScreen(player: PlayerViewModel) {
    val state by player.state.collectAsState()
    val volume by player.volume.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val rotaryAccumulator = remember { mutableFloatStateOf(0f) }
    val volumeShownAt = remember { mutableLongStateOf(0L) }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(volumeShownAt.longValue) {
        now.longValue = System.currentTimeMillis()
        delay(1500)
        now.longValue = System.currentTimeMillis()
    }
    val showVolume = now.longValue - volumeShownAt.longValue < 1500

    ScreenScaffold {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        // The crown controls volume on this screen, as in the system media controls.
                        rotaryAccumulator.floatValue += event.verticalScrollPixels
                        while (abs(rotaryAccumulator.floatValue) >= ROTARY_STEP_PX) {
                            val direction = if (rotaryAccumulator.floatValue > 0) 1 else -1
                            player.adjustVolume(direction)
                            rotaryAccumulator.floatValue -= direction * ROTARY_STEP_PX
                            volumeShownAt.longValue = System.currentTimeMillis()
                        }
                        true
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            val progress =
                if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            CircularProgressIndicator(
                progress = { if (showVolume) volume.first.toFloat() / volume.second.coerceAtLeast(1) else progress },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )

            val metadata = state.mediaItem?.mediaMetadata
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                AsyncImage(
                    model = metadata?.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .alpha(if (state.isBuffering) 0.5f else 1f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        when {
                            showVolume -> stringResource(R.string.volume, volume.first)
                            state.error != null -> stringResource(R.string.playback_error)
                            else -> metadata?.title?.toString() ?: stringResource(R.string.nothing_playing)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = metadata?.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = player::skipPrevious, enabled = state.hasPrevious) {
                        Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.previous))
                    }
                    FilledIconButton(onClick = player::togglePlayPause, enabled = state.mediaItem != null) {
                        Icon(
                            painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        )
                    }
                    IconButton(onClick = player::skipNext, enabled = state.hasNext) {
                        Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.next))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(
                        onClick = { player.seekBy(-10_000) },
                        enabled = state.mediaItem != null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_replay), stringResource(R.string.seek_back), Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { player.seekBy(10_000) },
                        enabled = state.mediaItem != null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_forward), stringResource(R.string.seek_forward), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
