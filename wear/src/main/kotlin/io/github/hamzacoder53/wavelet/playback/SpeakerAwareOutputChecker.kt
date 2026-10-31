package io.github.hamzacoder53.wavelet.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SuitableOutputChecker
import io.github.hamzacoder53.wavelet.Settings

/**
 * Like Media3's default Wear OS check (headphones only), but also accepts the watch speaker while
 * [Settings.playOnSpeaker] is on, so the setting takes effect without rebuilding the player.
 * The interface is library-group restricted but public; Media3 is pinned, so a change shows up at compile time.
 */
@SuppressLint("RestrictedApi")
@OptIn(UnstableApi::class)
class SpeakerAwareOutputChecker(private val settings: Settings) : SuitableOutputChecker {
    private var context: Context? = null
    private var handler: Handler? = null
    private var callback: SuitableOutputChecker.Callback? = null

    @Volatile private var suitable = true

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = update()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = update()
        }

    // SharedPreferences holds listeners weakly, so keep a field reference.
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val handler = handler
            if (key != Settings.KEY_PLAY_ON_SPEAKER || handler == null) return@OnSharedPreferenceChangeListener
            // Update before a play() queued right after the toggle reaches the player.
            if (handler.looper.isCurrentThread) update() else handler.post { update() }
        }

    override fun enable(
        callback: SuitableOutputChecker.Callback,
        context: Context,
        callbackLooper: Looper,
        backgroundLooper: Looper,
        clock: Clock,
    ) {
        this.context = context
        this.callback = callback
        val handler = Handler(callbackLooper).also { this.handler = it }
        suitable = AudioOutputs.canPlay(context)
        context.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(deviceCallback, handler)
        settings.registerListener(settingsListener)
    }

    override fun disable() {
        context?.getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback)
        settings.unregisterListener(settingsListener)
        handler?.removeCallbacksAndMessages(null)
        context = null
        handler = null
        callback = null
    }

    override fun isSelectedOutputSuitableForPlayback(): Boolean = suitable

    private fun update() {
        val now = AudioOutputs.canPlay(context ?: return)
        if (now == suitable) return
        suitable = now
        callback?.onSelectedOutputSuitabilityChanged(now)
    }
}
