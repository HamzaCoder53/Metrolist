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
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.SleepTimer
import kotlinx.coroutines.delay

private val MINUTE_OPTIONS = listOf(15, 30, 45, 60, 90)

@Composable
fun SleepTimerScreen() {
    val timer = (LocalContext.current.applicationContext as WaveletApplication).sleepTimer
    val state by timer.state.collectAsState()
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state is SleepTimer.State.At) {
            now.longValue = System.currentTimeMillis()
            delay(1000)
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.sleep_timer)) } }
            item { CenteredText(sleepTimerSummary(state, now.longValue)) }
            MINUTE_OPTIONS.forEach { minutes ->
                item {
                    RadioButton(
                        selected = false,
                        onSelect = { timer.startMinutes(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.minutes, minutes)) },
                    )
                }
            }
            item {
                RadioButton(
                    selected = state == SleepTimer.State.EndOfSong,
                    onSelect = timer::startEndOfSong,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_of_song)) },
                )
            }
            if (state != SleepTimer.State.Off) {
                item {
                    FilledTonalButton(
                        onClick = timer::cancel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.turn_off_timer)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun sleepTimerSummary(state: SleepTimer.State, nowMillis: Long): String =
    when (state) {
        SleepTimer.State.Off -> stringResource(R.string.timer_off)
        SleepTimer.State.EndOfSong -> stringResource(R.string.end_of_song)
        is SleepTimer.State.At -> {
            val remaining = ((state.endsAtMillis - nowMillis) / 1000).coerceAtLeast(0)
            stringResource(R.string.pauses_in, remaining / 60, remaining % 60)
        }
    }
