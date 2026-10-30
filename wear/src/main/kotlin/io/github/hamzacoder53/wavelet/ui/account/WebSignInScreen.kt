package io.github.hamzacoder53.wavelet.ui.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.metrolist.innertube.YouTube
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.ui.CenteredText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Google sign-in in a WebView on the watch, as Metrolist does on phones: once the page reaches
 * music.youtube.com, read the session cookie and page config (visitor data, data sync id, session index).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WaveletApplication
    val scope = rememberCoroutineScope()
    val bridge = remember { PageConfigBridge() }
    var status by remember { mutableStateOf<Int?>(null) }
    var extracting by remember { mutableStateOf(false) }

    fun extract(view: WebView) {
        if (extracting) return
        extracting = true
        status = R.string.signing_in
        scope.launch {
            repeat(20) {
                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                val config = bridge.read(view)
                if ("SAPISID" in cookie && config != null && !config.visitorData.isNullOrBlank()) {
                    YouTube.cookie = cookie
                    YouTube.visitorData = config.visitorData
                    val name = YouTube.accountInfo().getOrNull()?.name
                    app.account.completeWebSignIn(
                        cookie = cookie,
                        visitorData = config.visitorData,
                        dataSyncId = config.dataSyncId.orEmpty().substringBefore("||"),
                        authUser = config.authUser.filter(Char::isDigit).ifBlank { "0" },
                        name = name,
                    )
                    view.stopLoading()
                    app.accountLibrary.sync()
                    onDone()
                    return@launch
                }
                delay(500)
            }
            Timber.w("Watch sign-in: timed out waiting for the YouTube Music session")
            status = R.string.sign_in_failed
            extracting = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    addJavascriptInterface(bridge, "Android")
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (url?.startsWith("https://music.youtube.com") == true) extract(view)
                            }
                        }
                    loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                }
            },
        )
        status?.let { CenteredText(stringResource(it)) }
    }
}

private class PageConfigBridge {
    data class Config(val visitorData: String?, val dataSyncId: String?, val authUser: String)

    private var visitorData: CompletableDeferred<String?>? = null
    private var dataSyncId: CompletableDeferred<String?>? = null
    private var authUser: CompletableDeferred<String?>? = null

    suspend fun read(view: WebView): Config? {
        visitorData = CompletableDeferred()
        dataSyncId = CompletableDeferred()
        authUser = CompletableDeferred()
        view.loadUrl("javascript:Android.onVisitorData(window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)")
        view.loadUrl("javascript:Android.onDataSyncId(window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)")
        view.loadUrl(
            "javascript:Android.onAuthUser(window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):'0')",
        )
        return withTimeoutOrNull(1_000) {
            Config(visitorData!!.await(), dataSyncId!!.await(), authUser!!.await().orEmpty())
        }
    }

    @JavascriptInterface
    fun onVisitorData(value: String?) {
        visitorData?.complete(value)
    }

    @JavascriptInterface
    fun onDataSyncId(value: String?) {
        dataSyncId?.complete(value)
    }

    @JavascriptInterface
    fun onAuthUser(value: String?) {
        authUser?.complete(value)
    }
}
