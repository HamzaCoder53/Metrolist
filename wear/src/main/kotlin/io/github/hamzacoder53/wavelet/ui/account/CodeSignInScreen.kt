package io.github.hamzacoder53.wavelet.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.account.DeviceCodeAuth
import timber.log.Timber

/** Shows a short code to approve at google.com/device, then finishes sign-in on approval. */
@Composable
fun CodeSignInScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as WaveletApplication
    val auth = remember { DeviceCodeAuth() }
    var code by remember { mutableStateOf<DeviceCodeAuth.DeviceCode?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val deviceCode = auth.requestCode().also { code = it }
            val tokens = auth.awaitApproval(deviceCode)
            val refresh = requireNotNull(tokens.refreshToken) { "No refresh token returned" }
            app.accountLibrary.primeAccessToken(tokens.accessToken, tokens.expiresInSeconds)
            app.account.completeCodeSignIn(refresh, name = null)
            app.accountLibrary.sync()
        }.onSuccess { onDone() }
            .onFailure {
                Timber.w(it, "Code sign-in failed")
                error = true
            }
    }

    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val current = code
            when {
                error -> Text(stringResource(R.string.sign_in_failed), textAlign = TextAlign.Center)
                current == null -> Text(stringResource(R.string.loading))
                else -> {
                    Text(
                        stringResource(R.string.code_sign_in_go_to, current.verificationUrl.removePrefix("https://")),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(current.userCode, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.code_sign_in_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
