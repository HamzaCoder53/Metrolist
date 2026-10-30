package io.github.hamzacoder53.wavelet.ui

import android.content.Context
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.ListeningCache
import io.github.hamzacoder53.wavelet.settings
import kotlin.math.abs

/** Pixels of crown rotation per slider stop (about one emulator crown click). */
private const val ROTARY_STEP_PX = 120f

/** Listening-cache size: drag/tap the slider or turn the crown, from Off to Unlimited. */
@Composable
fun CacheSizeScreen() {
    val context = LocalContext.current
    val listeningCache = (context.applicationContext as WaveletApplication).listeningCache
    val stops = ListeningCache.SIZE_STOPS_MB
    var index by remember {
        mutableIntStateOf(stops.indexOf(context.settings.listeningCacheMb).coerceAtLeast(0))
    }
    val cachedSongs by listeningCache.songs.collectAsState()
    val used = remember(cachedSongs, index) { listeningCache.usedBytes() }
    val focusRequester = remember { FocusRequester() }
    val rotary = remember { mutableFloatStateOf(0f) }

    val view = LocalView.current

    fun select(newIndex: Int) {
        val clamped = newIndex.coerceIn(stops.indices)
        if (clamped == index) return
        index = clamped
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listeningCache.setLimitMb(stops[clamped])
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ScreenScaffold { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    // Keep the title below the clock on round screens.
                    .padding(top = 20.dp, start = 12.dp, end = 12.dp)
                    .onRotaryScrollEvent { event ->
                        // The crown moves the slider one stop per notch of rotation.
                        rotary.floatValue += event.verticalScrollPixels
                        while (abs(rotary.floatValue) >= ROTARY_STEP_PX) {
                            val direction = if (rotary.floatValue > 0) 1 else -1
                            select(index + direction)
                            rotary.floatValue -= direction * ROTARY_STEP_PX
                        }
                        true
                    }.focusRequester(focusRequester)
                    .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.listening_cache), style = MaterialTheme.typography.titleSmall)
            Text(
                text = sizeLabel(context, stops[index]),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = index,
                onValueChange = ::select,
                valueProgression = stops.indices,
                modifier = Modifier.fillMaxWidth(),
                decreaseIcon = { Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.smaller)) },
                increaseIcon = { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.bigger)) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cache_used, Formatter.formatShortFileSize(context, used)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun sizeLabel(context: Context, mb: Int): String =
    when (mb) {
        0 -> context.getString(R.string.cache_off)
        ListeningCache.UNLIMITED_MB -> context.getString(R.string.cache_unlimited)
        // Label the stops as chosen (binary MB/GB); Formatter would show 500 MB as "524 MB".
        else -> if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"
    }
