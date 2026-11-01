package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.LyricsState
import io.github.hamzacoder53.wavelet.playback.SyncedLyrics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** Pixels of crown rotation per volume (or seek) step. */
private const val ROTARY_STEP_PX = 48f

/** How far one crown step seeks while the crown is in seek mode. */
private const val CROWN_SEEK_STEP_MS = 5_000L

/** The crown goes back to volume after this long without turning. */
private const val CROWN_SEEK_TIMEOUT_MS = 3_000L

/** Width of the band along the screen edge where a finger drags the progress ring. */
private val RING_TOUCH_WIDTH = 32.dp

@Composable
fun NowPlayingScreen(
    player: PlayerViewModel,
    onSongOptions: () -> Unit = {},
) {
    val state by player.state.collectAsState()
    val volume by player.volume.collectAsState()
    val showLyrics by player.showLyrics.collectAsState()
    val lyricsState by player.lyrics.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val rotaryAccumulator = remember { mutableFloatStateOf(0f) }
    val volumeShownAt = remember { mutableLongStateOf(0L) }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Tap the artwork to make the crown seek instead of changing the volume.
    var crownSeeks by remember { mutableStateOf(false) }
    val lastSeekInputAt = remember { mutableLongStateOf(0L) }
    // Where a ring drag or crown seek will land; committed on release / after the crown rests.
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(volumeShownAt.longValue) {
        now.longValue = System.currentTimeMillis()
        delay(1500)
        now.longValue = System.currentTimeMillis()
    }
    val showVolume = now.longValue - volumeShownAt.longValue < 1500

    LaunchedEffect(crownSeeks, lastSeekInputAt.longValue) {
        if (crownSeeks) {
            delay(CROWN_SEEK_TIMEOUT_MS)
            crownSeeks = false
        }
    }
    LaunchedEffect(seekPreviewMs, dragging) {
        val target = seekPreviewMs ?: return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        // Crown turns arrive in bursts; seek once they pause rather than on every step.
        delay(500)
        player.seekTo(target)
        seekPreviewMs = null
    }
    LaunchedEffect(state.isSeekable) {
        if (!state.isSeekable) {
            crownSeeks = false
            seekPreviewMs = null
        }
    }

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
                            val seekState = currentState
                            if (crownSeeks && seekState.isSeekable) {
                                val base = seekPreviewMs ?: seekState.positionMs
                                seekPreviewMs = (base + direction * CROWN_SEEK_STEP_MS).coerceIn(0, seekState.durationMs)
                                lastSeekInputAt.longValue = System.currentTimeMillis()
                            } else {
                                player.adjustVolume(direction)
                                volumeShownAt.longValue = System.currentTimeMillis()
                            }
                            rotaryAccumulator.floatValue -= direction * ROTARY_STEP_PX
                        }
                        true
                    }.pointerInput(Unit) {
                        // Drag along the progress ring to scrub; touches inside the ring reach the buttons.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val seekState = currentState
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = minOf(size.width, size.height) / 2f
                            if (!seekState.isSeekable || (down.position - center).getDistance() < radius - RING_TOUCH_WIDTH.toPx()) {
                                return@awaitEachGesture
                            }
                            down.consume()
                            dragging = true
                            var fraction = ringFraction(down.position, center)
                            seekPreviewMs = (fraction * seekState.durationMs).toLong()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val next = ringFraction(change.position, center)
                                // Don't wrap from the end to the start (or back) across 12 o'clock.
                                fraction = if (abs(next - fraction) > 0.5f) (if (fraction > 0.5f) 1f else 0f) else next
                                seekPreviewMs = (fraction * seekState.durationMs).toLong()
                                change.consume()
                            }
                            dragging = false
                        }
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            val progress =
                if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            CircularProgressIndicator(
                progress = {
                    val preview = seekPreviewMs
                    when {
                        showVolume -> volume.first.toFloat() / volume.second.coerceAtLeast(1)
                        preview != null && state.durationMs > 0 -> preview.toFloat() / state.durationMs
                        else -> progress
                    }
                },
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
                            .clickable(enabled = state.isSeekable, onClickLabel = stringResource(R.string.seek_with_crown)) {
                                crownSeeks = !crownSeeks
                                lastSeekInputAt.longValue = System.currentTimeMillis()
                            }
                            .alpha(if (state.isBuffering) 0.5f else 1f),
                )
                Spacer(Modifier.height(4.dp))
                val lyricLines = (lyricsState as? LyricsState.Ready)?.lines.orEmpty()
                val seekShown = seekPreviewMs ?: state.positionMs.takeIf { crownSeeks }
                if (seekShown != null && !showVolume) {
                    Text(
                        text = stringResource(R.string.seek_position, formatTime(seekShown), formatTime(state.durationMs)),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(if (crownSeeks) R.string.crown_seeks else R.string.release_to_seek),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(0.7f),
                    )
                } else if (showLyrics && !showVolume && lyricLines.isNotEmpty()) {
                    // Synced lyrics replace title/artist: current line, then the next one dimmed.
                    val index = SyncedLyrics.currentIndex(lyricLines, state.positionMs)
                    Text(
                        text = lyricLines.getOrNull(index)?.text ?: "♪",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = lyricLines.getOrNull(index + 1)?.text.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(0.6f),
                    )
                } else {
                    Text(
                        text =
                            when {
                                showVolume -> stringResource(R.string.volume, volume.first)
                                state.error != null -> stringResource(R.string.playback_error)
                                showLyrics && lyricsState == LyricsState.Loading -> stringResource(R.string.lyrics_loading)
                                showLyrics && lyricsState == LyricsState.NotFound -> stringResource(R.string.lyrics_not_found)
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
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { player.seekBy(-10_000) },
                        enabled = state.mediaItem != null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_replay), stringResource(R.string.seek_back), Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onSongOptions,
                        enabled = state.mediaItem != null,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_more),
                            stringResource(R.string.more),
                            Modifier.size(18.dp),
                        )
                    }
                    if (player.lyricsEnabled) {
                        IconButton(
                            onClick = player::toggleLyrics,
                            enabled = state.mediaItem != null,
                            modifier = Modifier.size(32.dp).alpha(if (showLyrics) 1f else 0.6f),
                        ) {
                            Icon(painterResource(R.drawable.ic_lyrics), stringResource(R.string.lyrics), Modifier.size(18.dp))
                        }
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

/** Position on the ring as a fraction of a full turn, clockwise from 12 o'clock. */
private fun ringFraction(position: Offset, center: Offset): Float {
    val angle = atan2(position.x - center.x, center.y - position.y)
    return (((angle + 2 * PI) % (2 * PI)) / (2 * PI)).toFloat()
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
