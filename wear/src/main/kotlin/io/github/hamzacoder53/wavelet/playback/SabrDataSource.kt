package io.github.hamzacoder53.wavelet.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrBootstrap
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays a SABR stream (YouTube's server-paced format, the only one that serves uploaded songs without
 * a web PoToken) as one continuous container: the initialization segment, then the media segments.
 * It isn't seekable; YouTubeDataSource routes `sabr://<videoId>` here after resolving the stream.
 */
@OptIn(UnstableApi::class, ExperimentalSabrApi::class)
class SabrDataSource(private val playbackPositionMs: (() -> Long)? = null) : BaseDataSource(true) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var producer: Job? = null
    private var chunks: Channel<ByteArray>? = null
    private var current: ByteArray? = null
    private var offset = 0
    private var uri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val videoId = dataSpec.uri.host ?: throw IOException("No video id in ${dataSpec.uri}")
        val bootstrap = sessions[videoId] ?: throw IOException("No SABR session for $videoId")
        val channel = Channel<ByteArray>(BUFFERED_CHUNKS)
        chunks = channel
        producer =
            scope.launch {
                try {
                    var sentInit = false
                    SabrAudioStream(
                        httpClient = InnerTubeXPlayer.sabrHttpClient(),
                        bootstrap = bootstrap,
                        playbackPositionMs = playbackPositionMs,
                    ).chunks().collect { chunk ->
                        // A container needs exactly one initialization segment up front.
                        if (chunk.isInitialization) {
                            if (!sentInit) channel.send(chunk.data)
                            sentInit = true
                        } else if (sentInit) {
                            channel.send(chunk.data)
                        }
                    }
                    channel.close()
                } catch (error: Throwable) {
                    channel.close(error)
                }
            }
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        // Not seekable: a retry from a byte position replays the stream and skips what was read.
        var toSkip = dataSpec.position
        val scratch = ByteArray(16 * 1024)
        while (toSkip > 0) {
            val read = read(scratch, 0, minOf(scratch.size.toLong(), toSkip).toInt())
            if (read == C.RESULT_END_OF_INPUT) throw IOException("SABR stream shorter than ${dataSpec.position}")
            toSkip -= read
        }
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var chunk = current
        if (chunk == null || this.offset >= chunk.size) {
            val channel = chunks ?: return C.RESULT_END_OF_INPUT
            val next =
                try {
                    runBlocking { channel.receiveCatching() }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException()
                }
            if (next.isClosed) {
                next.exceptionOrNull()?.let { throw IOException("SABR stream failed", it) }
                return C.RESULT_END_OF_INPUT
            }
            chunk = next.getOrThrow()
            current = chunk
            this.offset = 0
        }
        val count = minOf(length, chunk.size - this.offset)
        System.arraycopy(chunk, this.offset, buffer, offset, count)
        this.offset += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        producer?.cancel()
        chunks?.cancel()
        producer = null
        chunks = null
        current = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    companion object {
        const val SCHEME = "sabr"
        private const val BUFFERED_CHUNKS = 8

        /** Bootstraps resolved for playback, by video id; read when the player opens `sabr://<id>`. */
        val sessions = ConcurrentHashMap<String, SabrBootstrap>()

        fun uriFor(videoId: String): Uri = Uri.parse("$SCHEME://$videoId")
    }

    /** Sends `sabr://` URIs here and everything else to [default]. */
    class RoutingFactory(
        private val default: DataSource.Factory,
        private val playbackPositionMs: (() -> Long)? = null,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = Routing(default.createDataSource(), playbackPositionMs)
    }

    private class Routing(private val default: DataSource, private val playbackPositionMs: (() -> Long)?) : DataSource {
        private var active: DataSource? = null
        private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            listeners += transferListener
            default.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source =
                if (dataSpec.uri.scheme == SCHEME) {
                    SabrDataSource(playbackPositionMs).also { s -> listeners.forEach(s::addTransferListener) }
                } else {
                    default
                }
            active = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }
}
