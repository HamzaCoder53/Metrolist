package io.github.hamzacoder53.wavelet.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.metrolist.music.constants.AudioQuality
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.settings

@Composable
fun SettingsScreen(onOpenCacheSize: () -> Unit) {
    val context = LocalContext.current
    val settings = context.settings
    val app = context.applicationContext as WaveletApplication
    val downloads = app.downloads
    val listeningCache = app.listeningCache
    var quality by remember { mutableStateOf(settings.audioQuality) }
    var wifiOnly by remember { mutableStateOf(settings.downloadOnWifiOnly) }
    var chargingOnly by remember { mutableStateOf(settings.downloadWhileChargingOnly) }
    var confirmClear by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(settings.showLyrics) }
    var autoplay by remember { mutableStateOf(settings.autoplay) }
    var normalize by remember { mutableStateOf(settings.normalizeVolume) }
    var skipSilence by remember { mutableStateOf(settings.skipSilence) }
    // Re-read size and limit whenever the cached song list changes (after clearing, resizing or playing).
    val cachedSongs by listeningCache.songs.collectAsState()
    val cacheUsed = remember(cachedSongs) { listeningCache.usedBytes() }
    val cacheLimit = remember(cachedSongs) { settings.listeningCacheMb }

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

            item { ListHeader { Text(stringResource(R.string.playback_section)) } }
            item {
                SwitchButton(
                    checked = autoplay,
                    onCheckedChange = {
                        settings.autoplay = it
                        autoplay = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.autoplay)) },
                )
            }
            item {
                SwitchButton(
                    checked = normalize,
                    onCheckedChange = {
                        settings.normalizeVolume = it
                        normalize = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.normalize_volume)) },
                )
            }
            item {
                SwitchButton(
                    checked = skipSilence,
                    onCheckedChange = {
                        settings.skipSilence = it
                        skipSilence = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.skip_silence)) },
                )
            }

            item { ListHeader { Text(stringResource(R.string.lyrics)) } }
            item {
                SwitchButton(
                    checked = showLyrics,
                    onCheckedChange = {
                        settings.showLyrics = it
                        showLyrics = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.show_lyrics)) },
                )
            }

            item { ListHeader { Text(stringResource(R.string.downloads_section)) } }
            item {
                SwitchButton(
                    checked = wifiOnly,
                    onCheckedChange = {
                        settings.downloadOnWifiOnly = it
                        wifiOnly = it
                        downloads.applyRequirements()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wifi_only)) },
                )
            }
            item {
                SwitchButton(
                    checked = chargingOnly,
                    onCheckedChange = {
                        settings.downloadWhileChargingOnly = it
                        chargingOnly = it
                        downloads.applyRequirements()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.charging_only)) },
                )
            }

            item { ListHeader { Text(stringResource(R.string.listening_cache)) } }
            item { CenteredText(stringResource(R.string.listening_cache_info)) }
            item {
                FilledTonalButton(
                    onClick = onOpenCacheSize,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text("${stringResource(R.string.cache_size)}: ${sizeLabel(context, cacheLimit)}") },
                    secondaryLabel = {
                        Text(stringResource(R.string.cache_used, Formatter.formatShortFileSize(context, cacheUsed)))
                    },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                    label = { Text(stringResource(R.string.clear_cache)) },
                )
            }
        }
    }

    AlertDialog(
        visible = confirmClear,
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_cache_title)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    listeningCache.clear()
                    confirmClear = false
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirmClear = false }) },
    )
}
