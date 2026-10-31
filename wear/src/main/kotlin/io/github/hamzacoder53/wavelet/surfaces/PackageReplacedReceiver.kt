package io.github.hamzacoder53.wavelet.surfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** An app update cancels our PendingIntents, leaving the complication's tap action dead until it's refreshed. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) NowPlayingStore.requestSurfaceUpdates(context)
    }
}
