package io.github.hamzacoder53.wavelet

import android.content.Context
import androidx.core.content.edit
import com.metrolist.music.constants.AudioQuality

/** Small synchronous settings store; values are read on the playback thread, so no Flow/DataStore here. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("wavelet", Context.MODE_PRIVATE)

    var audioQuality: AudioQuality
        get() = prefs.getString(KEY_AUDIO_QUALITY, null)
            ?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.LOW
        set(value) = prefs.edit { putString(KEY_AUDIO_QUALITY, value.name) }

    var visitorData: String?
        get() = prefs.getString(KEY_VISITOR_DATA, null)
        set(value) = prefs.edit { putString(KEY_VISITOR_DATA, value) }

    private companion object {
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_VISITOR_DATA = "visitor_data"
    }
}

val Context.settings: Settings
    get() = (applicationContext as WaveletApplication).settings
