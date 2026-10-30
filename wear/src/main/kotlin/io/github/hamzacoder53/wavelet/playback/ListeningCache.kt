package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import io.github.hamzacoder53.wavelet.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A song saved in the listening cache while it streamed. */
data class CachedSong(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val durationMs: Long?,
    val lastPlayed: Long,
) {
    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri?.toUri())
                    .setDurationMs(durationMs)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            ).build()
            .withPlaybackUri()
}

/**
 * Songs are saved here as they stream (like Metrolist's player cache), so recently played music
 * can be replayed offline. Least recently played songs are evicted first; downloads live in a
 * separate cache and are never evicted.
 */
@OptIn(UnstableApi::class)
class ListeningCache(context: Context, private val settings: Settings, databaseProvider: DatabaseProvider) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(context.filesDir, "listening")
    private val evictor = ResizableLruEvictor(settings.listeningCacheMb.toBytes(), context.filesDir, RESERVE_BYTES)
    private val historyFile = File(context.filesDir, "listening_history.json")

    val cache: Cache = SimpleCache(cacheDir, evictor, databaseProvider)

    private val _songs = MutableStateFlow<List<CachedSong>>(emptyList())

    /** Fully cached songs, most recently played first (partly played songs can't play offline). */
    val songs: StateFlow<List<CachedSong>> = _songs.asStateFlow()

    init {
        refresh()
    }

    /** When the cache is off, streamed audio is discarded instead of written. */
    val sinkFactory =
        DataSink.Factory {
            if (settings.listeningCacheMb != 0) CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE) else DiscardingSink()
        }

    fun setLimitMb(mb: Int) {
        settings.listeningCacheMb = mb
        scope.launch {
            evictor.resize(cache, mb.toBytes())
            refreshNow()
        }
    }

    fun clear() {
        scope.launch {
            cache.keys.toList().forEach(cache::removeResource)
            synchronized(historyFile) { historyFile.delete() }
            refreshNow()
        }
    }

    fun usedBytes(): Long = cache.cacheSpace

    /** Remembers what a played song is, so it can be listed and replayed offline later. */
    fun recordPlayed(item: MediaItem) {
        if (settings.listeningCacheMb == 0) return
        val md = item.mediaMetadata
        scope.launch {
            synchronized(historyFile) {
                val entries = readHistory().filterNot { it.id == item.mediaId }.toMutableList()
                entries.add(
                    0,
                    CachedSong(
                        id = item.mediaId,
                        title = md.title?.toString() ?: item.mediaId,
                        artist = md.artist?.toString().orEmpty(),
                        artworkUri = md.artworkUri?.toString(),
                        durationMs = md.durationMs,
                        lastPlayed = System.currentTimeMillis(),
                    ),
                )
                writeHistory(entries.take(MAX_HISTORY))
            }
        }
    }

    /** Most recently played songs (cached or not), newest first; used by the Tile. */
    fun recentHistory(limit: Int): List<CachedSong> = synchronized(historyFile) { readHistory().take(limit) }

    /** Re-checks (off the main thread) which remembered songs are still fully in the cache. */
    fun refresh() {
        scope.launch { refreshNow() }
    }

    private fun refreshNow() {
        val history = synchronized(historyFile) { readHistory() }
        _songs.value = history.filter(::isFullyCached)
    }

    private fun isFullyCached(song: CachedSong): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(song.id))
        return length > 0 && cache.isCached(song.id, 0, length)
    }

    private fun readHistory(): List<CachedSong> =
        runCatching {
            val array = JSONArray(historyFile.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                CachedSong(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    artworkUri = o.optString("artworkUri").takeIf { it.isNotEmpty() },
                    durationMs = o.optLong("durationMs").takeIf { it > 0 },
                    lastPlayed = o.optLong("lastPlayed"),
                )
            }
        }.getOrDefault(emptyList())

    private fun writeHistory(entries: List<CachedSong>) {
        val array = JSONArray()
        entries.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("artworkUri", song.artworkUri)
                    .put("durationMs", song.durationMs)
                    .put("lastPlayed", song.lastPlayed),
            )
        }
        historyFile.writeText(array.toString())
    }

    private class DiscardingSink : DataSink {
        override fun open(dataSpec: DataSpec) = Unit

        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit

        override fun close() = Unit
    }

    companion object {
        /** -1 in settings means no size limit (only the free-space reserve applies). */
        const val UNLIMITED_MB = -1

        /** Slider stops in MB: off, sizes, then unlimited. */
        val SIZE_STOPS_MB = listOf(0, 100, 250, 500, 1024, 2048, 4096, 8192, UNLIMITED_MB)
        const val DEFAULT_MB = 500

        /** Always leave this much of the watch's storage free. */
        private const val RESERVE_BYTES = 500L * 1024 * 1024
        private const val MAX_HISTORY = 1000

        private fun Int.toBytes(): Long = if (this == UNLIMITED_MB) Long.MAX_VALUE else this * 1024L * 1024L
    }
}
