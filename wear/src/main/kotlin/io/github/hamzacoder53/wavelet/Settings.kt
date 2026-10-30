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

    var visitorData: String?
        get() = prefs.getString(KEY_VISITOR_DATA, null)
        set(value) = prefs.edit { putString(KEY_VISITOR_DATA, value) }

    private companion object {
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_VISITOR_DATA = "visitor_data"
        const val KEY_WIFI_ONLY = "download_wifi_only"
        const val KEY_CHARGING_ONLY = "download_charging_only"
        const val KEY_LISTENING_CACHE_MB = "listening_cache_mb"
    }
}

val Context.settings: Settings
    get() = (applicationContext as WaveletApplication).settings
