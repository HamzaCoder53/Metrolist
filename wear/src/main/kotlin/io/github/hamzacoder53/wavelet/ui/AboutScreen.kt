package io.github.hamzacoder53.wavelet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.BuildConfig
import io.github.hamzacoder53.wavelet.R

@Composable
fun AboutScreen() {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}") } }
            item { CenteredText(stringResource(R.string.about_based_on)) }
            item { CenteredText("github.com/MetrolistGroup/Metrolist") }
            item { CenteredText(stringResource(R.string.about_license)) }
            item { CenteredText(stringResource(R.string.about_disclaimer)) }
        }
    }
}
