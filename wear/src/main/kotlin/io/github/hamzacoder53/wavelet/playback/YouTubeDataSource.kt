package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.playback.CachedStreamUrl
import com.metrolist.music.playback.StreamUrlCache
import com.metrolist.music.playback.withResolvedStream
import com.metrolist.music.utils.InnerTubeXPlayer
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException

/**
 * Resolves a media id into a playable googlevideo URL using Metrolist's shared
 * InnerTubeX extraction (:streaming), so upstream cipher and client fixes apply here too.
 */
@OptIn(UnstableApi::class)
class YouTubeDataSource(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    val urlCache = StreamUrlCache(maxEntries = 100)

    /**
     * Read order: [downloadCache] (read-only), then [listeningCache] (filled while streaming), then
     * the stream resolver. Both caches are keyed by media id, so cached songs play offline without
     * resolving a stream.
     */
    fun factory(downloadCache: Cache, listeningCache: ListeningCache): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(listeningCache.cache)
                    .setCacheWriteDataSinkFactory(listeningCache.sinkFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .setUpstreamDataSourceFactory(resolvingFactory()),
            )

    private fun resolvingFactory(): DataSource.Factory {
        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(YouTube.proxy).build())
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(appContext, http)) { dataSpec ->
            val mediaId = dataSpec.key ?: throw IOException("No media id")
            urlCache[mediaId]?.let { return@Factory dataSpec.withResolvedStream(it) }

            val generation = urlCache.generation(mediaId)
            val playback =
                runBlocking(Dispatchers.IO) {
                    InnerTubeXPlayer.playerResponseForPlayback(
                        videoId = mediaId,
                        audioQuality = appContext.settings.audioQuality,
                        connectivityManager = connectivityManager,
                    )
                }.getOrElse { throw IOException("Could not resolve stream for $mediaId", it) }
            Timber.d("Resolved %s via %s (itag %d)", mediaId, playback.streamClient, playback.format.itag)
            (appContext as WaveletApplication).loudness.record(mediaId, playback.audioConfig?.loudnessDb)

            urlCache.put(
                mediaId = mediaId,
                url = playback.streamUrl,
                requestHeaders = playback.streamHeaders,
                clientName = playback.streamClient,
                expiresInSeconds = playback.streamExpiresInSeconds,
                requireBoundedRange = playback.requireBoundedRange,
                rangeChunkSizeBytes = playback.rangeChunkSizeBytes,
                useRangeChunks = playback.useRangeChunks,
                expectedGeneration = generation,
            )
            dataSpec.withResolvedStream(
                CachedStreamUrl(
                    url = playback.streamUrl,
                    requestHeaders = playback.streamHeaders,
                    clientName = playback.streamClient,
                    requireBoundedRange = playback.requireBoundedRange,
                    rangeChunkSizeBytes = playback.rangeChunkSizeBytes,
                    useRangeChunks = playback.useRangeChunks,
                ),
            )
        }
    }

    /** Called after YouTube rejects a stream URL: forget it and avoid that client for a while. */
    fun onStreamRejected(mediaId: String) {
        urlCache.clientName(mediaId)?.let { InnerTubeXPlayer.markStreamClientFailed(mediaId, it) }
        urlCache.invalidate(mediaId)
    }
}
