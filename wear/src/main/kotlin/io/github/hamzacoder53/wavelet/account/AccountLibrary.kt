package io.github.hamzacoder53.wavelet.account

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** A playlist in the user's library, whichever sign-in it came from. */
data class LibraryPlaylist(val id: String, val title: String, val thumbnail: String?, val subtitle: String?)

data class LibrarySnapshot(
    val likedSongs: List<SongItem> = emptyList(),
    val playlists: List<LibraryPlaylist> = emptyList(),
    /** Songs the user uploaded to YouTube Music (only synced when "Show uploaded music" is on). */
    val uploads: List<SongItem> = emptyList(),
    val syncedAt: Long = 0,
)

/**
 * Syncs liked songs and playlists for the signed-in account and keeps the last result on disk,
 * so the library still lists offline. Songs themselves play offline only if downloaded or cached.
 */
class AccountLibrary(context: Context, private val settings: Settings, private val account: Account) {
    private val file = File(context.filesDir, "account_library.json")
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow(if (account.state.value.signedIn) load() else LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    suspend fun sync(): Result<Unit> =
        mutex.withLock {
            runCatching {
                val fresh =
                    when (account.state.value.mode) {
                        AccountMode.WEB -> syncInnerTube()
                        null -> LibrarySnapshot()
                    }
                _snapshot.value = fresh
                withContext(Dispatchers.IO) { save(fresh) }
            }.onFailure { error ->
                Timber.w(error, "Library sync failed")
                if (isExpiredSignIn()) account.markExpired()
            }
        }

    /**
     * YouTube rejects the account lookup while a public request still works
     * (so being offline isn't mistaken for expiry).
     */
    private suspend fun isExpiredSignIn(): Boolean =
        account.state.value.signedIn && YouTube.accountInfo().isFailure && YouTube.visitorData().isSuccess

    suspend fun playlistSongs(playlistId: String): List<SongItem> {
        val page = YouTube.playlist(playlistId).getOrThrow()
        val songs = page.songs.toMutableList()
        var continuation = page.songsContinuation ?: page.continuation
        while (continuation != null && songs.size < 200) {
            val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            songs += next.songs
            continuation = next.continuation
        }
        return songs.distinctBy { it.id }
    }

    fun clear() {
        _snapshot.value = LibrarySnapshot()
        file.delete()
    }

    /**
     * Updates the local snapshot when a song is liked/unliked and persists it to disk,
     * without needing a full network sync.
     */
    suspend fun updateLiked(song: SongItem, liked: Boolean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = _snapshot.value
                val updatedLiked =
                    if (liked) {
                        if (current.likedSongs.any { it.id == song.id }) {
                            current.likedSongs
                        } else {
                            listOf(song) + current.likedSongs
                        }
                    } else {
                        current.likedSongs.filterNot { it.id == song.id }
                    }
                val fresh = current.copy(likedSongs = updatedLiked)
                _snapshot.value = fresh
                runCatching { save(fresh) }.onFailure { Timber.w(it, "Failed to persist updated liked songs") }
            }
        }

    /** Uploaded songs need a playback hint (InnerTubeX ContentHints.isUploaded) to resolve. */
    fun isUploaded(mediaId: String): Boolean = _snapshot.value.uploads.any { it.id == mediaId }

    private suspend fun syncInnerTube(): LibrarySnapshot {
        val liked = playlistSongs(LIKED_MUSIC_PLAYLIST)
        val playlists =
            YouTube.library("FEmusic_liked_playlists").getOrThrow().items
                .filterIsInstance<PlaylistItem>()
                .filter { it.id != LIKED_MUSIC_PLAYLIST }
                .map { LibraryPlaylist(it.id, it.title, it.thumbnail, it.songCountText ?: it.author?.name) }
        val uploads = if (settings.showUploads) uploadedSongs() else emptyList()
        return LibrarySnapshot(liked, playlists, uploads, System.currentTimeMillis())
    }

    /** "Uploads" tab of the YouTube Music library (same browse id and tab as Metrolist's sync). */
    private suspend fun uploadedSongs(): List<SongItem> {
        val page = YouTube.library(UPLOADED_SONGS_BROWSE_ID, tabIndex = 1).getOrThrow()
        val songs = page.items.filterIsInstance<SongItem>().toMutableList()
        var continuation = page.continuation
        while (continuation != null && songs.size < MAX_UPLOADS) {
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            songs += next.items.filterIsInstance<SongItem>()
            continuation = next.continuation
        }
        return songs.distinctBy { it.id }.take(MAX_UPLOADS)
    }

    private fun List<SongItem>.toJson() =
        JSONArray().apply {
            forEach { song ->
                put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("artist", song.artists.joinToString { it.name })
                        .put("thumbnail", song.thumbnail)
                        .put("duration", song.duration ?: 0),
                )
            }
        }

    private fun JSONArray?.toSongs(): List<SongItem> =
        if (this == null) {
            emptyList()
        } else {
            (0 until length()).map { i ->
                val o = getJSONObject(i)
                SongItem(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    artists = listOf(Artist(o.optString("artist"), null)),
                    thumbnail = o.optString("thumbnail"),
                    duration = o.optInt("duration").takeIf { it > 0 },
                )
            }
        }

    private fun save(snapshot: LibrarySnapshot) {
        val json =
            JSONObject()
                .put("syncedAt", snapshot.syncedAt)
                .put("liked", snapshot.likedSongs.toJson())
                .put("uploads", snapshot.uploads.toJson())
                .put(
                    "playlists",
                    JSONArray().apply {
                        snapshot.playlists.forEach { p ->
                            put(JSONObject().put("id", p.id).put("title", p.title).put("thumbnail", p.thumbnail).put("subtitle", p.subtitle))
                        }
                    },
                )
        file.writeText(json.toString())
    }

    private fun load(): LibrarySnapshot =
        runCatching {
            val json = JSONObject(file.readText())
            val playlists = json.getJSONArray("playlists")
            LibrarySnapshot(
                likedSongs = json.optJSONArray("liked").toSongs(),
                uploads = json.optJSONArray("uploads").toSongs(),
                playlists =
                    (0 until playlists.length()).map { i ->
                        val o = playlists.getJSONObject(i)
                        LibraryPlaylist(
                            o.getString("id"),
                            o.optString("title"),
                            o.optString("thumbnail").takeIf { it.isNotEmpty() && it != "null" },
                            o.optString("subtitle").takeIf { it.isNotEmpty() && it != "null" },
                        )
                    },
                syncedAt = json.optLong("syncedAt"),
            )
        }.getOrDefault(LibrarySnapshot())

    companion object {
        /** YouTube Music's "Liked music" auto playlist. */
        const val LIKED_MUSIC_PLAYLIST = "LM"

        /** Collection id of downloaded liked songs. */
        const val LIKED_COLLECTION_ID = "account:liked"
        private const val UPLOADED_SONGS_BROWSE_ID = "FEmusic_library_privately_owned_tracks"
        private const val MAX_UPLOADS = 300
    }
}
