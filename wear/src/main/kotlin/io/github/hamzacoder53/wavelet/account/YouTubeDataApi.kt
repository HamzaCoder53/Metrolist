package io.github.hamzacoder53.wavelet.account

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Read-only YouTube Data API v3 calls used after "Sign in with a code". */
class YouTubeDataApi(
    private val accessToken: suspend () -> String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    data class Playlist(val id: String, val title: String, val thumbnail: String?, val count: Int?)

    /** Videos the user liked (includes music liked in YouTube Music). */
    suspend fun likedSongs(max: Int = 200): List<SongItem> =
        paged("videos", mapOf("myRating" to "like", "part" to "snippet,contentDetails"), max) { item ->
            item.toSong(item.getString("id"))
        }

    suspend fun playlists(max: Int = 100): List<Playlist> =
        paged("playlists", mapOf("mine" to "true", "part" to "snippet,contentDetails"), max) { item ->
            val snippet = item.getJSONObject("snippet")
            Playlist(
                id = item.getString("id"),
                title = snippet.optString("title"),
                thumbnail = snippet.thumbnail(),
                count = item.optJSONObject("contentDetails")?.optInt("itemCount"),
            )
        }

    suspend fun playlistSongs(playlistId: String, max: Int = 200): List<SongItem> =
        paged("playlistItems", mapOf("playlistId" to playlistId, "part" to "snippet"), max) { item ->
            val videoId = item.getJSONObject("snippet").optJSONObject("resourceId")?.optString("videoId")
            videoId?.takeIf { it.isNotEmpty() }?.let { item.toSong(it) }
        }

    private fun JSONObject.toSong(videoId: String): SongItem {
        val snippet = getJSONObject("snippet")
        // Music uploads come from "<Artist> - Topic" channels; show just the artist.
        val channel =
            (snippet.optString("videoOwnerChannelTitle").ifEmpty { snippet.optString("channelTitle") })
                .removeSuffix(" - Topic")
        return SongItem(
            id = videoId,
            title = snippet.optString("title"),
            artists = listOf(Artist(name = channel, id = null)),
            thumbnail = snippet.thumbnail().orEmpty(),
        )
    }

    private fun JSONObject.thumbnail(): String? {
        val thumbs = optJSONObject("thumbnails") ?: return null
        return listOf("medium", "default", "high")
            .firstNotNullOfOrNull { thumbs.optJSONObject(it)?.optString("url")?.takeIf(String::isNotEmpty) }
    }

    private suspend fun <T> paged(
        resource: String,
        params: Map<String, String>,
        max: Int,
        map: (JSONObject) -> T?,
    ): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val url =
                "https://www.googleapis.com/youtube/v3/$resource".toHttpUrl().newBuilder().apply {
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                    addQueryParameter("maxResults", "50")
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }.build()
            val json = get(url.toString())
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) map(items.getJSONObject(i))?.let(out::add)
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null && out.size < max)
        return out.take(max)
    }

    private suspend fun get(url: String): JSONObject {
        val token = accessToken()
        return withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("YouTube Data API ${response.code}: ${body.take(200)}")
                JSONObject(body)
            }
        }
    }
}
