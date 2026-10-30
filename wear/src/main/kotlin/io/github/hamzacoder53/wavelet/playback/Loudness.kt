package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import androidx.core.content.edit
import kotlin.math.pow

/**
 * YouTube's per-track loudness (dB relative to its target), saved whenever a stream is resolved so
 * cached and downloaded songs can be normalized offline too.
 */
class Loudness(context: Context) {
    private val prefs = context.getSharedPreferences("loudness", Context.MODE_PRIVATE)

    @Volatile
    var onChanged: ((mediaId: String) -> Unit)? = null

    fun record(mediaId: String, loudnessDb: Double?) {
        if (loudnessDb == null) return
        prefs.edit { putFloat(mediaId, loudnessDb.toFloat()) }
        onChanged?.invoke(mediaId)
    }

    /**
     * Player volume (0..1) that brings this track to YouTube's reference level. Only turns loud
     * tracks down; quiet ones stay at full volume rather than being boosted (which could clip).
     */
    fun gainFor(mediaId: String): Float {
        if (!prefs.contains(mediaId)) return 1f
        val db = prefs.getFloat(mediaId, 0f)
        if (db <= 0f) return 1f
        return 10.0.pow(-db / 20.0).toFloat().coerceIn(MIN_GAIN, 1f)
    }

    private companion object {
        const val MIN_GAIN = 0.25f
    }
}
