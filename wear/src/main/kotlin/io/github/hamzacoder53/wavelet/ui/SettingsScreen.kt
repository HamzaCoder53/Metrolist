package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.metrolist.music.constants.AudioQuality
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.settings

@Composable
fun SettingsScreen() {
    val settings = LocalContext.current.settings
    var quality by remember { mutableStateOf(settings.audioQuality) }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.audio_quality)) } }
            AudioQuality.entries.forEach { option ->
                item {
                    RadioButton(
                        selected = quality == option,
                        onSelect = {
                            settings.audioQuality = option
                            quality = option
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        AudioQuality.LOW -> R.string.quality_low
                                        AudioQuality.AUTO -> R.string.quality_auto
                                        AudioQuality.HIGH -> R.string.quality_high
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
