package io.github.hamzacoder53.wavelet.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

/** Watch artwork is shown at most ~200 px wide; asking YouTube for small images saves data and decode time. */
const val ARTWORK_PX = 240
const val THUMBNAIL_PX = 96
const val EXTRA_ARTIST_BROWSE_ID = "artist_browse_id"

val MediaItem.artistBrowseId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_ARTIST_BROWSE_ID)

private val googleImageSize = Regex("=[wsh]\\d+[^/]*$")

fun String.sizedThumbnail(px: Int): String =
    when {
        contains("googleusercontent.com") || contains("ggpht.com") ->
            if (googleImageSize.containsMatchIn(this)) {
                replace(googleImageSize, "=w$px-h$px-l90-rj")
            } else {
                "$this=w$px-h$px-l90-rj"
            }
        else -> this
    }

fun SongItem.toMediaItem(): MediaItem {
    val artistBrowseId = artists.firstOrNull()?.id
    val extras =
        Bundle().apply {
            artistBrowseId?.let { putString(EXTRA_ARTIST_BROWSE_ID, it) }
        }
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artists.joinToString { it.name })
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail.sizedThumbnail(ARTWORK_PX).toUri())
                .setDurationMs(duration?.let { it * 1000L })
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build(),
        ).build()
        .withPlaybackUri()
}

/**
 * The URI and cache key are rebuilt from the media id because MediaController strips
 * local configuration when it sends items to the session.
 */
fun MediaItem.withPlaybackUri(): MediaItem =
    buildUpon()
        .setUri("https://music.youtube.com/watch?v=$mediaId")
        .setCustomCacheKey(mediaId)
        .build()

/**
 * Loads similar songs ("radio") for [seedId] using YouTube Music's next endpoint,
 * matching autoplay in PlaybackService.
 */
suspend fun fetchRadioItems(seedId: String): Result<List<MediaItem>> =
    YouTube.next(WatchEndpoint(videoId = seedId)).map { result ->
        result.items
            .filter { !it.isVideoSong }
            .map { it.toMediaItem() }
    }
