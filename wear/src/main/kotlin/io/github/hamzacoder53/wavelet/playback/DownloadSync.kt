package io.github.hamzacoder53.wavelet.playback

import androidx.media3.exoplayer.offline.Download
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.Settings
import io.github.hamzacoder53.wavelet.account.AccountLibrary
import io.github.hamzacoder53.wavelet.account.AccountState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Keeps downloaded playlists (and downloaded liked songs) matching YouTube Music: songs added there
 * are downloaded, songs removed there are deleted. Albums don't change, so they're skipped.
 */
class DownloadSync(
    private val settings: Settings,
    private val downloads: Downloads,
    private val library: AccountLibrary,
    private val account: StateFlow<AccountState>,
) {
    private val mutex = Mutex()

    suspend fun syncIfDue() {
        if (System.currentTimeMillis() - settings.lastDownloadSync < INTERVAL_MS) return
        sync()
    }

    suspend fun sync() {
        mutex.withLock {
            if (!settings.syncDownloadedPlaylists) return
            val collections = downloads.collections(downloads.songs.value).filterNot { it.id.startsWith(ALBUM_ID_PREFIX) }
            if (collections.isEmpty()) return
            for (collection in collections) {
                // A failed or empty fetch (offline, private playlist) must never delete anything.
                val fresh =
                    runCatching { songsOf(collection.id) }
                        .onFailure { Timber.w(it, "Couldn't sync downloaded playlist %s", collection.id) }
                        .getOrNull()
                if (fresh.isNullOrEmpty()) continue

                val (added, removed) = plan(collection.songs, fresh, downloads::isDownloaded)
                if (added.isNotEmpty()) downloads.download(added, collection.id, collection.title)
                removed.forEach { downloads.remove(it) }
                if (added.isNotEmpty() || removed.isNotEmpty()) {
                    Timber.i("Synced %s: +%d -%d", collection.id, added.size, removed.size)
                }
            }
            settings.lastDownloadSync = System.currentTimeMillis()
        }
    }

    private suspend fun songsOf(collectionId: String): List<SongItem>? =
        when {
            collectionId == AccountLibrary.LIKED_COLLECTION_ID ->
                if (account.value.signedIn) library.playlistSongs(AccountLibrary.LIKED_MUSIC_PLAYLIST) else null
            else -> library.playlistSongs(collectionId)
        }

    internal companion object {
        private const val INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val ALBUM_ID_PREFIX = "MPREb"

        /**
         * Songs to (re)download and ids to delete so [downloaded] matches [fresh]. Failed downloads are
         * retried; songs already downloaded with another playlist stay where they are.
         */
        fun plan(
            downloaded: List<DownloadedSong>,
            fresh: List<SongItem>,
            isDownloadedElsewhere: (String) -> Boolean,
        ): Pair<List<SongItem>, List<String>> {
            val kept = downloaded.filter { it.state != Download.STATE_FAILED }.mapTo(HashSet()) { it.id }
            val added = fresh.filter { it.id !in kept && !isDownloadedElsewhere(it.id) }.distinctBy { it.id }
            val freshIds = fresh.mapTo(HashSet()) { it.id }
            return added to downloaded.map { it.id }.filter { it !in freshIds }
        }
    }
}
