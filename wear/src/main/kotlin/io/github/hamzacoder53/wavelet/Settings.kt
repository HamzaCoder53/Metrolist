package io.github.hamzacoder53.wavelet

import android.content.Context
import androidx.core.content.edit
import com.metrolist.music.constants.AudioQuality
import io.github.hamzacoder53.wavelet.playback.ListeningCache

/** Small synchronous settings store; values are read on the playback thread, so no Flow/DataStore here. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("wavelet", Context.MODE_PRIVATE)

    var audioQuality: AudioQuality
        get() = prefs.getString(KEY_AUDIO_QUALITY, null)
            ?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.LOW
        set(value) = prefs.edit { putString(KEY_AUDIO_QUALITY, value.name) }

    var downloadOnWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }

    var downloadWhileChargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_CHARGING_ONLY, value) }

    /** Listening cache limit in MB; 0 = off. */
    var listeningCacheMb: Int
        get() = prefs.getInt(KEY_LISTENING_CACHE_MB, ListeningCache.DEFAULT_MB)
        set(value) = prefs.edit { putInt(KEY_LISTENING_CACHE_MB, value) }

    // --- Account (see account/Account.kt). Stored in app-private prefs; never logged. ---
    var accountMode: String?
        get() = prefs.getString(KEY_ACCOUNT_MODE, null)
        set(value) = prefs.edit { putString(KEY_ACCOUNT_MODE, value) }

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT_NAME, null)
        set(value) = prefs.edit { putString(KEY_ACCOUNT_NAME, value) }

    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit { putString(KEY_COOKIE, value) }

    var dataSyncId: String?
        get() = prefs.getString(KEY_DATA_SYNC_ID, null)
        set(value) = prefs.edit { putString(KEY_DATA_SYNC_ID, value) }

    var authUser: String
        get() = prefs.getString(KEY_AUTH_USER, null) ?: "0"
        set(value) = prefs.edit { putString(KEY_AUTH_USER, value) }

    var oauthRefreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_REFRESH_TOKEN, value) }

    var showLyrics: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LYRICS, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_LYRICS, value) }

    fun clearAccount() =
        prefs.edit {
            listOf(KEY_ACCOUNT_MODE, KEY_ACCOUNT_NAME, KEY_COOKIE, KEY_DATA_SYNC_ID, KEY_AUTH_USER, KEY_REFRESH_TOKEN)
                .forEach(::remove)
        }

    var visitorData: String?
        get() = prefs.getString(KEY_VISITOR_DATA, null)
        set(value) = prefs.edit { putString(KEY_VISITOR_DATA, value) }

    private companion object {
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_VISITOR_DATA = "visitor_data"
        const val KEY_WIFI_ONLY = "download_wifi_only"
        const val KEY_CHARGING_ONLY = "download_charging_only"
        const val KEY_LISTENING_CACHE_MB = "listening_cache_mb"
        const val KEY_ACCOUNT_MODE = "account_mode"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_COOKIE = "innertube_cookie"
        const val KEY_DATA_SYNC_ID = "data_sync_id"
        const val KEY_AUTH_USER = "auth_user"
        const val KEY_REFRESH_TOKEN = "oauth_refresh_token"
        const val KEY_SHOW_LYRICS = "show_lyrics"
    }
}

val Context.settings: Settings
    get() = (applicationContext as WaveletApplication).settings
