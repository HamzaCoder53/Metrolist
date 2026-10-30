package io.github.hamzacoder53.wavelet.session

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Captures a YouTube Music session from a WebView after the user signs in to Google, the way
 * Metrolist's LoginScreen does: wait for the SAPISID cookie on music.youtube.com, then read
 * VISITOR_DATA, DATASYNC_ID and SESSION_INDEX from the page's ytcfg via a JavaScript bridge.
 */
class SessionCapture {
    private val bridge = PageConfigBridge()

    /** Sets up [webView] and loads Google sign-in; [onMusicPageLoaded] fires once music.youtube.com loads. */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun attach(webView: WebView, onMusicPageLoaded: (WebView) -> Unit) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(bridge, "Android")
        webView.webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.startsWith(MUSIC_URL) == true) onMusicPageLoaded(view)
                }
            }
        webView.loadUrl(SIGN_IN_URL)
    }

    /** Polls for up to ~10 s; returns null if the page never shows a signed-in session. */
    suspend fun capture(view: WebView): YouTubeSession? {
        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty()
            val config = bridge.read(view)
            if ("SAPISID" in cookie && config != null && !config.visitorData.isNullOrBlank()) {
                return YouTubeSession(
                    cookie = cookie,
                    visitorData = config.visitorData,
                    dataSyncId = config.dataSyncId.orEmpty().substringBefore("||"),
                    authUser = config.authUser.filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }
        return null
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

    companion object {
        const val MUSIC_URL = "https://music.youtube.com"
        const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
    }
}
