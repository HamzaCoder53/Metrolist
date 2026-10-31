package io.github.hamzacoder53.wavelet.surfaces

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Invisible target for the complication tap: an activity may always be started from a watch-face
 * tap, unlike a background service. Toggles playback in place; opens Wavelet only when it needs the
 * user (no headphones) or has nothing queued, in which case it plays the most recent song.
 */
class PlayPauseActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A cold start binds the player on the main thread; an invisible window that never takes
        // focus can't be reported as "not responding" meanwhile.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        scope.launch {
            when (SurfacePlayback.toggle(this@PlayPauseActivity)) {
                SurfacePlayback.Result.PAUSED, SurfacePlayback.Result.PLAYING -> Unit
                SurfacePlayback.Result.NEEDS_OUTPUT -> openApp { putExtra(MainActivity.EXTRA_RESUME, true) }
                SurfacePlayback.Result.EMPTY -> {
                    val recent = (application as WaveletApplication).listeningCache.recentHistory(1).firstOrNull()
                    openApp { recent?.let { putExtra(MainActivity.EXTRA_PLAY_MEDIA_ID, it.id) } }
                }
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(extras: Intent.() -> Unit) =
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(extras))
}
