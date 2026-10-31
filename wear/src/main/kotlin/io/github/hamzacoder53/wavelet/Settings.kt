package io.github.hamzacoder53.wavelet

import android.content.Context
import androidx.core.content.edit
import com.metrolist.music.constants.AudioQuality
import io.github.hamzacoder53.wavelet.playback.ListeningCache
import org.json.JSONArray

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

    /** When the queue runs out, keep playing similar songs (YouTube Music radio). */
    var autoplay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOPLAY, value) }

    /** Even out loudness between songs using YouTube's per-track loudness value. */
    var normalizeVolume: Boolean
        get() = prefs.getBoolean(KEY_NORMALIZE, true)
        set(value) = prefs.edit { putBoolean(KEY_NORMALIZE, value) }

    var skipSilence: Boolean
        get() = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        set(value) = prefs.edit { putBoolean(KEY_SKIP_SILENCE, value) }

    /** Download songs added to (and delete songs removed from) downloaded playlists. */
    var syncDownloadedPlaylists: Boolean
        get() = prefs.getBoolean(KEY_SYNC_DOWNLOADED_PLAYLISTS, true)
        set(value) = prefs.edit { putBoolean(KEY_SYNC_DOWNLOADED_PLAYLISTS, value) }

    var lastDownloadSync: Long
        get() = prefs.getLong(KEY_LAST_DOWNLOAD_SYNC, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_DOWNLOAD_SYNC, value) }

    var saveListeningHistory: Boolean
        get() = prefs.getBoolean(KEY_SAVE_LISTENING_HISTORY, true)
        set(value) = prefs.edit { putBoolean(KEY_SAVE_LISTENING_HISTORY, value) }

    var lastUpdateCheckTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPDATE_CHECK_TIME, value) }

    var latestAvailableVersion: String?
        get() = prefs.getString(KEY_LATEST_AVAILABLE_VERSION, null)
        set(value) = prefs.edit { putString(KEY_LATEST_AVAILABLE_VERSION, value) }

    var latestReleaseUrl: String?
        get() = prefs.getString(KEY_LATEST_RELEASE_URL, null)
        set(value) = prefs.edit { putString(KEY_LATEST_RELEASE_URL, value) }

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    /** Show (and sync) songs the user uploaded to YouTube Music; needs a full-account sign-in. */
    var showUploads: Boolean
        get() = prefs.getBoolean(KEY_SHOW_UPLOADS, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_UPLOADS, value) }

    /** Play through the watch speaker when no headphones are connected. */
    var playOnSpeaker: Boolean
        get() = prefs.getBoolean(KEY_PLAY_ON_SPEAKER, false)
        set(value) = prefs.edit { putBoolean(KEY_PLAY_ON_SPEAKER, value) }

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

    var recentSearches: List<String>
        get() {
            val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it) }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(it) }
            prefs.edit { putString(KEY_RECENT_SEARCHES, array.toString()) }
        }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val list = recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableList()
        list.add(0, trimmed)
        recentSearches = list.take(MAX_RECENT_SEARCHES)
    }

    fun clearRecentSearches() {
        prefs.edit { remove(KEY_RECENT_SEARCHES) }
    }

    companion object {
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
        /** Left over from the removed code sign-in; still cleared on sign-out. */
        const val KEY_REFRESH_TOKEN = "oauth_refresh_token"
        const val KEY_SHOW_LYRICS = "show_lyrics"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_NORMALIZE = "normalize_volume"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_SAVE_LISTENING_HISTORY = "save_listening_history"
        const val KEY_SYNC_DOWNLOADED_PLAYLISTS = "sync_downloaded_playlists"
        const val KEY_LAST_DOWNLOAD_SYNC = "last_download_sync"
        const val KEY_LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        const val KEY_LATEST_AVAILABLE_VERSION = "latest_available_version"
        const val KEY_LATEST_RELEASE_URL = "latest_release_url"
        const val KEY_SHOW_UPLOADS = "show_uploads"
        const val KEY_PLAY_ON_SPEAKER = "play_on_speaker"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val MAX_RECENT_SEARCHES = 10
    }
}

val Context.settings: Settings
    get() = (applicationContext as WaveletApplication).settings
