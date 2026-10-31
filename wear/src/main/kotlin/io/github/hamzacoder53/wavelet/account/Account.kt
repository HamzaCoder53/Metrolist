package io.github.hamzacoder53.wavelet.account

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import com.metrolist.innertube.YouTube
import io.github.hamzacoder53.wavelet.Settings
import io.github.hamzacoder53.wavelet.session.YouTubeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

enum class AccountMode {
    /** YouTube Music web session (signed in on the watch, or sent from the phone): full account, like Metrolist. */
    WEB,
}

/** [expired]: the saved sign-in stopped working (e.g. Google expired it); the user must sign in again. */
data class AccountState(val mode: AccountMode?, val name: String?, val expired: Boolean = false) {
    val signedIn get() = mode != null
}

/** Which sign-in is active, and applying/clearing it. */
class Account(private val context: Context, private val settings: Settings) {
    init {
        // "Sign in with a code" was removed in 0.5.0; those users sign in again another way.
        if (settings.accountMode == "CODE") settings.clearAccount()
    }

    private val _state =
        MutableStateFlow(
            AccountState(settings.accountMode?.let { runCatching { AccountMode.valueOf(it) }.getOrNull() }, settings.accountName),
        )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Sign-in on the watch needs a working WebView, which many Wear OS watches don't have. */
    val webSignInAvailable: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
            runCatching { CookieManager.getInstance() }.isSuccess
    }

    /** Re-applies a saved web session to InnerTube at startup. */
    fun applySavedSession() {
        if (_state.value.mode != AccountMode.WEB) return
        YouTube.cookie = settings.cookie
        settings.dataSyncId?.let { YouTube.dataSyncId = it }
        YouTube.authUser = settings.authUser
        YouTube.useLoginForBrowse = true
    }

    /**
     * Applies a YouTube Music web session (from the watch's WebView or the phone companion) and
     * looks up the account name. Returns the name, or null if YouTube didn't accept the session.
     */
    suspend fun applySession(session: YouTubeSession): String? {
        YouTube.cookie = session.cookie
        YouTube.visitorData = session.visitorData
        YouTube.dataSyncId = session.dataSyncId
        YouTube.authUser = session.authUser
        YouTube.useLoginForBrowse = true
        val info = YouTube.accountInfo().getOrNull()
        if (info == null) {
            Timber.w("YouTube didn't accept the session")
            applySavedSession()
            return null
        }
        settings.cookie = session.cookie
        settings.visitorData = session.visitorData
        settings.dataSyncId = session.dataSyncId
        settings.authUser = session.authUser
        settings.accountName = info.name
        settings.accountMode = AccountMode.WEB.name
        _state.value = AccountState(AccountMode.WEB, info.name)
        Timber.i("Signed in with a YouTube Music session")
        return info.name
    }

    /** The saved sign-in no longer works; keep the cached library but ask the user to sign in again. */
    fun markExpired() {
        if (_state.value.signedIn && !_state.value.expired) {
            Timber.i("Sign-in expired")
            _state.update { it.copy(expired = true) }
        }
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
