package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Wear OS audio output helpers: Wavelet never plays through the watch speaker. */
object AudioOutputs {
    private val headphoneTypes =
        buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
            }
        }

    fun hasHeadphones(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in headphoneTypes }

    fun headphonesConnected(context: Context): Flow<Boolean> =
        callbackFlow {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val callback =
                object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                        trySend(hasHeadphones(context))
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                        trySend(hasHeadphones(context))
                    }
                }
            trySend(hasHeadphones(context))
            audioManager.registerAudioDeviceCallback(callback, null)
            awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
        }.distinctUntilChanged()

    /** Opens the Wear OS Bluetooth picker and returns to the app once a device connects. */
    fun bluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("EXTRA_CONNECTION_ONLY", true)
            .putExtra("EXTRA_CLOSE_ON_CONNECT", true)
            .putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 1)
}
