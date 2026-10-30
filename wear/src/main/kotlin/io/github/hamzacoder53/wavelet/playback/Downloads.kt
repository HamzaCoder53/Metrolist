package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.playback.CachedStreamUrl
import com.metrolist.music.playback.withResolvedStream
import com.metrolist.music.utils.InnerTubeXPlayer
import io.github.hamzacoder53.wavelet.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** Song details stored inside each Media3 download request, so the library needs no database. */
data class DownloadedSong(
    val id: String,
    val title: String,
    val artist: String,
    val collectionId: String?,
    val collectionTitle: String?,
    val durationMs: Long?,
    val artworkFile: File,
    val state: Int,
    val percent: Float,
    val bytes: Long,
    val stopReason: Int,
) {
    val isComplete get() = state == Download.STATE_COMPLETED

    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(collectionTitle)
                    .setArtworkUri(artworkFile.takeIf { it.isFile }?.toUri())
                    .setDurationMs(durationMs)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            ).build()
            .withPlaybackUri()
}

/** A downloaded album or playlist, grouped from its songs. */
data class DownloadedCollection(val id: String, val title: String, val songs: List<DownloadedSong>)

@OptIn(UnstableApi::class)
class Downloads(context: Context, private val settings: Settings) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val http = OkHttpClient.Builder().proxy(YouTube.proxy).build()
    private val artworkDir = File(appContext.filesDir, "artwork").apply { mkdirs() }
    /** Shared with the listening cache; Media3 keys each cache's tables by its directory. */
    val databaseProvider = StandaloneDatabaseProvider(appContext)

    val cache: Cache = SimpleCache(File(appContext.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)

    private val _songs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val songs: StateFlow<List<DownloadedSong>> = _songs.asStateFlow()

    /** True while the Wi-Fi-only / charging-only rules are holding downloads back. */
    private val _waitingForRequirements = MutableStateFlow(false)
    val waitingForRequirements: StateFlow<Boolean> = _waitingForRequirements.asStateFlow()

    val manager: DownloadManager =
        DownloadManager(
            appContext,
            databaseProvider,
            cache,
            ResolvingDataSource.Factory(OkHttpDataSource.Factory(http), ::resolveForDownload),
            Executors.newFixedThreadPool(2),
        ).apply {
            maxParallelDownloads = 2
            addListener(
                object : DownloadManager.Listener {
                    override fun onInitialized(downloadManager: DownloadManager) {
                        refresh()
                        resumePending()
                    }

                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        if (finalException != null) Timber.w(finalException, "Download %s failed", download.request.id)
                        refresh()
                    }

                    override fun onRequirementsStateChanged(
                        downloadManager: DownloadManager,
                        requirements: Requirements,
                        notMetRequirements: Int,
                    ) {
                        _waitingForRequirements.value = notMetRequirements != 0
                    }

                    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                        File(artworkDir, "${download.request.id}.jpg").delete()
                        refresh()
                    }
                },
            )
        }

    init {
        manager.requirements = requirements()
        _waitingForRequirements.value = manager.notMetRequirements != 0
    }

    private fun requirements(): Requirements {
        var flags = if (settings.downloadOnWifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
        if (settings.downloadWhileChargingOnly) flags = flags or Requirements.DEVICE_CHARGING
        return Requirements(flags)
    }

    /**
     * Wi-Fi-only and charging-only rules. Sent through the service so it starts (and reschedules its
     * restart job) rather than leaving queued downloads idle until something else wakes it.
     */
    fun applyRequirements() {
        DownloadService.sendSetRequirements(appContext, WaveletDownloadService::class.java, requirements(), false)
        _waitingForRequirements.value = manager.notMetRequirements != 0
    }

    /** Called when the app opens: restarts the service if downloads are still pending. */
    fun resumePending() {
        if (_songs.value.any { !it.isComplete && it.state != Download.STATE_FAILED } || manager.currentDownloads.isNotEmpty()) {
            runCatching { DownloadService.sendResumeDownloads(appContext, WaveletDownloadService::class.java, false) }
                .onFailure { Timber.w(it, "Could not resume downloads") }
        }
    }

    fun isDownloaded(mediaId: String): Boolean = _songs.value.any { it.id == mediaId && it.isComplete }

    fun download(songs: List<SongItem>, collectionId: String? = null, collectionTitle: String? = null) {
        songs.forEach { song ->
            val data =
                JSONObject()
                    .put("title", song.title)
                    .put("artist", song.artists.joinToString { it.name })
                    .put("collectionId", collectionId)
                    .put("collectionTitle", collectionTitle ?: song.album?.name)
                    .put("durationMs", song.duration?.let { it * 1000L })
            val request =
                DownloadRequest.Builder(song.id, "https://music.youtube.com/watch?v=${song.id}".toUri())
                    .setCustomCacheKey(song.id)
                    .setData(data.toString().toByteArray())
                    .build()
            DownloadService.sendAddDownload(appContext, WaveletDownloadService::class.java, request, false)
            scope.launch { saveArtwork(song.id, song.thumbnail.sizedThumbnail(ARTWORK_PX)) }
        }
    }

    fun remove(mediaId: String) =
        DownloadService.sendRemoveDownload(appContext, WaveletDownloadService::class.java, mediaId, false)

    fun removeAll() = DownloadService.sendRemoveAllDownloads(appContext, WaveletDownloadService::class.java, false)

    fun collections(songs: List<DownloadedSong>): List<DownloadedCollection> =
        songs
            .filter { it.collectionId != null }
            .groupBy { it.collectionId!! }
            .map { (id, items) -> DownloadedCollection(id, items.first().collectionTitle ?: id, items) }

    fun usedBytes(): Long = cache.cacheSpace + artworkDir.listFiles().orEmpty().sumOf { it.length() }

    fun freeBytes(): Long = appContext.filesDir.usableSpace

    private fun refresh() {
        val list = mutableListOf<DownloadedSong>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) list += cursor.download.toDownloadedSong()
        }
        _songs.value = list.sortedByDescending { it.state == Download.STATE_COMPLETED }
    }

    private fun Download.toDownloadedSong(): DownloadedSong {
        val data = runCatching { JSONObject(String(request.data)) }.getOrDefault(JSONObject())
        return DownloadedSong(
            id = request.id,
            title = data.optString("title", request.id),
            artist = data.optString("artist"),
            collectionId = data.optString("collectionId").takeIf { it.isNotEmpty() && it != "null" },
            collectionTitle = data.optString("collectionTitle").takeIf { it.isNotEmpty() && it != "null" },
            durationMs = data.optLong("durationMs").takeIf { it > 0 },
            artworkFile = File(artworkDir, "${request.id}.jpg"),
            state = state,
            percent = percentDownloaded.coerceAtLeast(0f),
            bytes = bytesDownloaded,
            stopReason = stopReason,
        )
    }

    private fun saveArtwork(mediaId: String, url: String) {
        val target = File(artworkDir, "$mediaId.jpg")
        if (target.isFile) return
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (response.isSuccessful && body != null) {
                    body.byteStream().use { input -> target.outputStream().use(input::copyTo) }
                }
            }
        }.onFailure { Timber.w(it, "Artwork download failed for %s", mediaId) }
    }

    /**
     * Downloads use full-file requests (no bounded range chunks). googlevideo throttles unbounded
     * requests to roughly playback speed, so a range covering the whole file is appended, as Metrolist does.
     */
    private fun resolveForDownload(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val mediaId = dataSpec.key ?: throw IOException("No media id")
        val playback =
            runBlocking(Dispatchers.IO) {
                InnerTubeXPlayer.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = settings.audioQuality,
                    connectivityManager = connectivityManager,
                    allowBoundedRange = false,
                )
            }.getOrElse { throw IOException("Could not resolve stream for $mediaId", it) }
        val contentLength = playback.format.contentLength?.takeIf { it > 0 } ?: probeContentLength(playback.streamUrl, playback.streamHeaders)
        val url =
            if (contentLength != null && "&range=" !in playback.streamUrl) {
                "${playback.streamUrl}&range=0-${contentLength - 1}"
            } else {
                playback.streamUrl
            }
        return dataSpec.withResolvedStream(
            CachedStreamUrl(
                url = url,
                requestHeaders = playback.streamHeaders,
                clientName = playback.streamClient,
                requireBoundedRange = playback.requireBoundedRange,
                rangeChunkSizeBytes = playback.rangeChunkSizeBytes,
                useRangeChunks = playback.useRangeChunks,
            ),
        )
    }

    private fun probeContentLength(url: String, headers: Map<String, String>): Long? =
        runCatching {
            val request =
                Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=0-0")
                    .build()
            http.newCall(request).execute().use { response ->
                response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { response.code == 200 }
            }
        }.getOrNull()
}
