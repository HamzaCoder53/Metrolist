package io.github.hamzacoder53.wavelet.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Tiny "now playing" record written by the playback service and read by the Tile and complication,
 * which run outside the app UI and shouldn't have to bind to the player just to draw.
 */
object NowPlayingStore {
    data class NowPlaying(val mediaId: String?, val title: String?, val artist: String?, val isPlaying: Boolean)

    private const val PREFS = "now_playing"

    fun read(context: Context): NowPlaying {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NowPlaying(
            p.getString("id", null),
            p.getString("title", null),
            p.getString("artist", null),
            p.getBoolean("playing", false),
        )
    }

    fun write(context: Context, item: MediaItem?, isPlaying: Boolean) {
        val current = read(context)
        val md = item?.mediaMetadata
        val next = NowPlaying(item?.mediaId, md?.title?.toString(), md?.artist?.toString(), isPlaying)
        if (next == current) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("id", next.mediaId)
            putString("title", next.title)
            putString("artist", next.artist)
            putBoolean("playing", next.isPlaying)
        }
        requestSurfaceUpdates(context)
    }

    fun requestSurfaceUpdates(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(NowPlayingTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, NowPlayingComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
