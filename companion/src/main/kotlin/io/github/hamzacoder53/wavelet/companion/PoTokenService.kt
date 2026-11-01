package io.github.hamzacoder53.wavelet.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.utils.potoken.PoTokenGenerator
import io.github.hamzacoder53.wavelet.session.PoTokenReply
import io.github.hamzacoder53.wavelet.session.PoTokenRequest
import io.github.hamzacoder53.wavelet.session.SessionTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Makes web PoTokens for the watch, which has no WebView. YouTube only streams uploaded songs to web
 * clients, and those need these tokens. Runs in the background; the phone app needn't be open.
 */
class PoTokenService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != SessionTransfer.PATH_POTOKEN_REQUEST) return
        val request = PoTokenRequest.fromBytes(event.data) ?: return
        val messages = Wearable.getMessageClient(this)
        val generator = generator(this)
        scope.launch {
            val token =
                runCatching { generator.getWebClientPoToken(request.videoId, request.sessionId) }
                    .onFailure { Log.w(TAG, "PoToken generation failed", it) }
                    .getOrNull()
            val reply = PoTokenReply(request.id, token?.playerRequestPoToken, token?.streamingDataPoToken)
            runCatching { messages.sendMessage(event.sourceNodeId, SessionTransfer.PATH_POTOKEN_REPLY, reply.toBytes()).await() }
                .onFailure { Log.w(TAG, "Couldn't reply to the watch", it) }
        }
    }

    private companion object {
        const val TAG = "PoTokenService"

        // One generator per process keeps its WebView and streaming token warm between songs.
        @Volatile private var shared: PoTokenGenerator? = null

        fun generator(context: Context): PoTokenGenerator =
            shared ?: synchronized(this) { shared ?: PoTokenGenerator(context).also { shared = it } }
    }
}
