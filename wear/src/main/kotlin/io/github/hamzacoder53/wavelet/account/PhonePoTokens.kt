package io.github.hamzacoder53.wavelet.account

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.utils.potoken.PoTokenResult
import io.github.hamzacoder53.wavelet.session.PoTokenReply
import io.github.hamzacoder53.wavelet.session.PoTokenRequest
import io.github.hamzacoder53.wavelet.session.SessionTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gets web PoTokens from the Wavelet phone app. Most watches have no WebView to make them, and YouTube
 * only streams uploaded songs to web clients, which need them. Null when no phone answers in time.
 */
class PhonePoTokens(context: Context) {
    private val capabilities = Wearable.getCapabilityClient(context)
    private val messages = Wearable.getMessageClient(context)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<PoTokenReply>>()

    private val replyListener =
        MessageClient.OnMessageReceivedListener { event ->
            if (event.path != SessionTransfer.PATH_POTOKEN_REPLY) return@OnMessageReceivedListener
            val reply = PoTokenReply.fromBytes(event.data) ?: return@OnMessageReceivedListener
            pending[reply.id]?.complete(reply)
        }

    init {
        messages.addListener(replyListener)
    }

    suspend fun get(videoId: String, sessionId: String): PoTokenResult? {
        val phone =
            runCatching {
                capabilities.getCapability(SessionTransfer.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes
            }.getOrNull()?.firstOrNull()
        if (phone == null) {
            Timber.d("No phone with Wavelet reachable for a PoToken")
            return null
        }
        val request = PoTokenRequest(UUID.randomUUID().toString(), videoId, sessionId)
        val deferred = CompletableDeferred<PoTokenReply>()
        pending[request.id] = deferred
        return try {
            messages.sendMessage(phone.id, SessionTransfer.PATH_POTOKEN_REQUEST, request.toBytes()).await()
            val reply = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
            val player = reply?.playerToken
            val streaming = reply?.streamingToken
            if (player == null || streaming == null) {
                Timber.w("Phone gave no PoToken for %s", videoId)
                null
            } else {
                PoTokenResult(player, streaming)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PoToken request to the phone failed")
            null
        } finally {
            pending.remove(request.id)
        }
    }

    private companion object {
        // The phone's first token includes starting its WebView (a few seconds); later ones are quick.
        const val TIMEOUT_MS = 10_000L
    }
}
