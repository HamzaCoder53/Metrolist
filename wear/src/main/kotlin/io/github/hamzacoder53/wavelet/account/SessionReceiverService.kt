package io.github.hamzacoder53.wavelet.account

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.session.SessionTransfer
import io.github.hamzacoder53.wavelet.session.YouTubeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Receives a YouTube Music session from the Wavelet phone companion over the Wear Data Layer.
 * The Data Layer only connects paired devices running apps with the same package and signing key.
 */
class SessionReceiverService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != SessionTransfer.PATH_SESSION) return
        val app = application as WaveletApplication
        val messages = Wearable.getMessageClient(this)
        scope.launch {
            val session = YouTubeSession.fromBytes(event.data)
            val name = session?.let { app.account.applySession(it) }
            runCatching {
                if (name != null) {
                    messages.sendMessage(event.sourceNodeId, SessionTransfer.PATH_SESSION_OK, name.toByteArray()).await()
                } else {
                    messages.sendMessage(
                        event.sourceNodeId,
                        SessionTransfer.PATH_SESSION_FAILED,
                        "YouTube didn't accept this sign-in".toByteArray(),
                    ).await()
                }
            }.onFailure { Timber.w(it, "Couldn't reply to the phone") }
            if (name != null) app.accountLibrary.sync()
        }
    }
}
