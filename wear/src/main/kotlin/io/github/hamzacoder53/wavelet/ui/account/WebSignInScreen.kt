package io.github.hamzacoder53.wavelet.ui.account

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.session.SessionCapture
import io.github.hamzacoder53.wavelet.ui.CenteredText
import kotlinx.coroutines.launch
import timber.log.Timber

/** Google sign-in in a WebView on the watch (only offered when the watch has a WebView). */
@Composable
fun WebSignInScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as WaveletApplication
    val scope = rememberCoroutineScope()
    val capture = remember { SessionCapture() }
    var status by remember { mutableStateOf<Int?>(null) }
    var extracting by remember { mutableStateOf(false) }

    fun extract(view: WebView) {
        if (extracting) return
        extracting = true
        status = R.string.signing_in
        scope.launch {
            val session = capture.capture(view)
            if (session == null) {
                Timber.w("Watch sign-in: timed out waiting for the YouTube Music session")
                status = R.string.sign_in_failed
                extracting = false
                return@launch
            }
            view.stopLoading()
            app.account.applySession(session)
            app.accountLibrary.sync()
            onDone()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    capture.attach(this, ::extract)
                }
            },
        )
        status?.let { CenteredText(stringResource(it)) }
    }
}
