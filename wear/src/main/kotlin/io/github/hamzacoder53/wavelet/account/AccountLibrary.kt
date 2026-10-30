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
    val syncedAt: Long = 0,
)

/**
 * Syncs liked songs and playlists for the signed-in account and keeps the last result on disk,
 * so the library still lists offline. Songs themselves play offline only if downloaded or cached.
 */
class AccountLibrary(context: Context, private val settings: Settings, private val account: Account) {
    private val file = File(context.filesDir, "account_library.json")
    private val deviceAuth = DeviceCodeAuth()
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiry = 0L

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val dataApi = YouTubeDataApi(accessToken = ::validAccessToken)

    suspend fun sync(): Result<Unit> =
        mutex.withLock {
            runCatching {
                val fresh =
                    when (account.state.value.mode) {
                        AccountMode.WEB -> syncInnerTube()
                        AccountMode.CODE -> syncDataApi()
                        null -> LibrarySnapshot()
                    }
                _snapshot.value = fresh
                withContext(Dispatchers.IO) { save(fresh) }
            }.onFailure { error ->
                Timber.w(error, "Library sync failed")
                if (isExpiredSignIn(error)) account.markExpired()
            }
        }

    /**
     * Code sign-in: Google says invalid_grant / 401. Web session: YouTube rejects the account
     * lookup while a public request still works (so being offline isn't mistaken for expiry).
     */
    private suspend fun isExpiredSignIn(error: Throwable): Boolean =
        when (account.state.value.mode) {
            AccountMode.CODE -> error is SignInExpiredException
            AccountMode.WEB -> YouTube.accountInfo().isFailure && YouTube.visitorData().isSuccess
            null -> false
        }

    /** Songs of one library playlist, using the matching API. */
    suspend fun playlistSongs(playlistId: String): List<SongItem> =
        when (account.state.value.mode) {
            AccountMode.CODE -> dataApi.playlistSongs(playlistId)
            else -> {
                val page = YouTube.playlist(playlistId).getOrThrow()
                val songs = page.songs.toMutableList()
                var continuation = page.songsContinuation ?: page.continuation
                while (continuation != null && songs.size < 200) {
                    val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                    songs += next.songs
                    continuation = next.continuation
                }
                songs.distinctBy { it.id }
            }
        }

    fun clear() {
        _snapshot.value = LibrarySnapshot()
        file.delete()
        accessToken = null
    }

    private suspend fun syncInnerTube(): LibrarySnapshot {
        val liked = playlistSongs(LIKED_MUSIC_PLAYLIST)
        val playlists =
            YouTube.library("FEmusic_liked_playlists").getOrThrow().items
                .filterIsInstance<PlaylistItem>()
                .filter { it.id != LIKED_MUSIC_PLAYLIST }
                .map { LibraryPlaylist(it.id, it.title, it.thumbnail, it.songCountText ?: it.author?.name) }
        return LibrarySnapshot(liked, playlists, System.currentTimeMillis())
    }

    private suspend fun syncDataApi(): LibrarySnapshot =
        LibrarySnapshot(
            likedSongs = dataApi.likedSongs(),
            playlists = dataApi.playlists().map { LibraryPlaylist(it.id, it.title, it.thumbnail, it.count?.let { n -> "$n songs" }) },
            syncedAt = System.currentTimeMillis(),
        )

    private suspend fun validAccessToken(): String {
        accessToken?.takeIf { System.currentTimeMillis() < accessTokenExpiry }?.let { return it }
        val refreshToken = settings.oauthRefreshToken ?: error("Not signed in with a code")
        val tokens = deviceAuth.refresh(refreshToken)
        accessToken = tokens.accessToken
        accessTokenExpiry = System.currentTimeMillis() + (tokens.expiresInSeconds - 60) * 1000L
        return tokens.accessToken
    }

    /** First access token after code sign-in, so the first sync doesn't need a refresh. */
    fun primeAccessToken(token: String, expiresInSeconds: Int) {
        accessToken = token
        accessTokenExpiry = System.currentTimeMillis() + (expiresInSeconds - 60) * 1000L
    }

    private fun save(snapshot: LibrarySnapshot) {
        val json =
            JSONObject()
                .put("syncedAt", snapshot.syncedAt)
                .put(
                    "liked",
                    JSONArray().apply {
                        snapshot.likedSongs.forEach { song ->
                            put(
                                JSONObject()
                                    .put("id", song.id)
                                    .put("title", song.title)
                                    .put("artist", song.artists.joinToString { it.name })
                                    .put("thumbnail", song.thumbnail)
                                    .put("duration", song.duration ?: 0),
                            )
                        }
                    },
                ).put(
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
            val liked = json.getJSONArray("liked")
            val playlists = json.getJSONArray("playlists")
            LibrarySnapshot(
                likedSongs =
                    (0 until liked.length()).map { i ->
                        val o = liked.getJSONObject(i)
                        SongItem(
                            id = o.getString("id"),
                            title = o.optString("title"),
                            artists = listOf(Artist(o.optString("artist"), null)),
                            thumbnail = o.optString("thumbnail"),
                            duration = o.optInt("duration").takeIf { it > 0 },
                        )
                    },
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
    }
}
