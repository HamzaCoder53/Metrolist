package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.playback.AudioOutputs
import io.github.hamzacoder53.wavelet.settings

/** Shown when play is pressed without headphones; resumes once headphones connect or the speaker is allowed. */
@Composable
fun HeadphonesScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    val connectedFlow = remember { AudioOutputs.headphonesConnected(context) }
    val connected by connectedFlow.collectAsState(initial = false)

    var playOnSpeaker by remember { mutableStateOf(context.settings.playOnSpeaker) }

    LaunchedEffect(connected) { if (connected) onConnected() }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.connect_headphones)) } }
            item { CenteredText(stringResource(R.string.connect_headphones_body)) }
            item {
                Button(
                    onClick = { context.startActivity(AudioOutputs.bluetoothSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null) },
                    label = { Text(stringResource(R.string.open_bluetooth)) },
                )
            }
            item {
                SwitchButton(
                    checked = playOnSpeaker,
                    onCheckedChange = {
                        context.settings.playOnSpeaker = it
                        playOnSpeaker = it
                        if (it) onConnected()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.play_on_speaker)) },
                )
            }
        }
    }
}
