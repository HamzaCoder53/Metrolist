package io.github.hamzacoder53.wavelet.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.hamzacoder53.wavelet.playback.AudioOutputs
import io.github.hamzacoder53.wavelet.playback.PlaybackService
import kotlinx.coroutines.guava.await
import timber.log.Timber

/** Play/pause for surfaces outside the app UI (Tile, complication). Never starts the speaker unless allowed. */
object SurfacePlayback {
    enum class Result { PAUSED, PLAYING, NEEDS_OUTPUT, EMPTY }

    suspend fun toggle(context: Context): Result =
        runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                when {
                    controller.isPlaying -> {
                        controller.pause()
                        Result.PAUSED
                    }
                    controller.mediaItemCount == 0 -> Result.EMPTY
                    AudioOutputs.canPlay(context) -> {
                        controller.play()
                        Result.PLAYING
                    }
                    else -> Result.NEEDS_OUTPUT
                }
            } finally {
                controller.release()
            }
        }.getOrElse {
            Timber.w(it, "Surface play/pause failed")
            Result.EMPTY
        }
}
