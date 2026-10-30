package io.github.hamzacoder53.wavelet.account

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import com.metrolist.innertube.YouTube
import io.github.hamzacoder53.wavelet.BuildConfig
import io.github.hamzacoder53.wavelet.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

enum class AccountMode {
    /** Signed in on the watch (YouTube Music cookies): full account, like Metrolist. */
    WEB,

    /** Signed in with a code (OAuth device flow): liked songs and playlists via the YouTube Data API. */
    CODE,
}

data class AccountState(val mode: AccountMode?, val name: String?) {
    val signedIn get() = mode != null
}

/** Which sign-in is active, and applying/clearing it. */
class Account(private val context: Context, private val settings: Settings) {
    private val _state =
        MutableStateFlow(
            AccountState(settings.accountMode?.let { runCatching { AccountMode.valueOf(it) }.getOrNull() }, settings.accountName),
        )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Code sign-in needs an OAuth client compiled in from a local file; see wear/README.md. */
    val codeSignInAvailable: Boolean get() = BuildConfig.OAUTH_CLIENT_ID.isNotEmpty()

    /** Sign-in on the watch needs a working WebView, which many Wear OS watches don't have. */
    val webSignInAvailable: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
            runCatching { CookieManager.getInstance() }.isSuccess
    }

    /** Re-applies a saved watch session to InnerTube at startup. */
    fun applySavedSession() {
        if (_state.value.mode != AccountMode.WEB) return
        YouTube.cookie = settings.cookie
        settings.dataSyncId?.let { YouTube.dataSyncId = it }
        YouTube.authUser = settings.authUser
        YouTube.useLoginForBrowse = true
    }

    fun completeWebSignIn(cookie: String, visitorData: String, dataSyncId: String, authUser: String, name: String?) {
        settings.cookie = cookie
        settings.visitorData = visitorData
        settings.dataSyncId = dataSyncId
        settings.authUser = authUser
        settings.accountName = name
        settings.accountMode = AccountMode.WEB.name
        YouTube.visitorData = visitorData
        applySavedSession()
        _state.value = AccountState(AccountMode.WEB, name)
        Timber.i("Signed in on watch")
    }

    fun completeCodeSignIn(refreshToken: String, name: String?) {
        settings.oauthRefreshToken = refreshToken
        settings.accountName = name
        settings.accountMode = AccountMode.CODE.name
        _state.value = AccountState(AccountMode.CODE, name)
        Timber.i("Signed in with code")
    }

    fun signOut() {
        if (_state.value.mode == AccountMode.WEB) {
            YouTube.cookie = null
            YouTube.dataSyncId = null
            YouTube.authUser = "0"
            runCatching { CookieManager.getInstance().removeAllCookies(null) }
        }
        settings.clearAccount()
        _state.value = AccountState(null, null)
    }
}
