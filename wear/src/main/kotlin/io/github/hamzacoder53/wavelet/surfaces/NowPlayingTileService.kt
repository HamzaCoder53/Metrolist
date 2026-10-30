package io.github.hamzacoder53.wavelet.surfaces

import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.playback.AudioOutputs
import io.github.hamzacoder53.wavelet.playback.PlaybackService
import io.github.hamzacoder53.wavelet.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import timber.log.Timber

/** Tile: what's playing with play/pause, plus the two most recent songs (tap to play). */
class NowPlayingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        if (requestParams.currentState.lastClickableId == TOGGLE_ID) {
            return scope.future {
                val playing = togglePlayback()
                buildTile(NowPlayingStore.read(this@NowPlayingTileService).copy(isPlaying = playing))
            }
        }
        return Futures.immediateFuture(buildTile(NowPlayingStore.read(this)))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Returns whether playback is now (expected to be) playing. Never starts the speaker. */
    private suspend fun togglePlayback(): Boolean =
        runCatching {
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val controller = MediaController.Builder(this, token).buildAsync().await()
            try {
                when {
                    controller.isPlaying -> {
                        controller.pause()
                        false
                    }
                    controller.mediaItemCount > 0 && AudioOutputs.hasHeadphones(this) -> {
                        controller.play()
                        true
                    }
                    else -> false
                }
            } finally {
                controller.release()
            }
        }.getOrElse {
            Timber.w(it, "Tile play/pause failed")
            false
        }

    private fun buildTile(nowPlaying: NowPlayingStore.NowPlaying): TileBuilders.Tile {
        val recent =
            (application as WaveletApplication).listeningCache.recentHistory(3)
                .filter { it.id != nowPlaying.mediaId }
                .take(2)

        val column =
            LayoutElementBuilders.Column.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(getString(R.string.now_playing), 12f, GREY))
                .addContent(text(nowPlaying.title ?: getString(R.string.nothing_playing), 16f, WHITE, bold = true))
                .addContent(text(nowPlaying.artist.orEmpty(), 12f, GREY))
                .addContent(spacer(6f))

        if (nowPlaying.mediaId != null) {
            column.addContent(playPauseButton(nowPlaying.isPlaying)).addContent(spacer(6f))
        }
        if (recent.isNotEmpty()) {
            column.addContent(text(getString(R.string.recent_music), 11f, GREY))
            recent.forEach { song -> column.addContent(recentChip(song.id, song.title)) }
        } else if (nowPlaying.mediaId == null) {
            column.addContent(openAppChip())
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(column.build()))
            .build()
    }

    private fun playPauseButton(isPlaying: Boolean): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(48f))
            .setHeight(dp(48f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(ACCENT))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(24f)).build())
                            .build(),
                    ).setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(TOGGLE_ID)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    ).setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(getString(if (isPlaying) R.string.pause else R.string.play))
                            .build(),
                    ).build(),
            ).addContent(text(if (isPlaying) "❚❚" else "▶", 18f, DARK, bold = true))
            .build()

    private fun recentChip(mediaId: String, title: String): LayoutElementBuilders.LayoutElement =
        chip(title, launchApp(mediaId))

    private fun openAppChip(): LayoutElementBuilders.LayoutElement = chip(getString(R.string.app_name), launchApp(null))

    private fun chip(label: String, action: ActionBuilders.Action): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(150f))
            .setHeight(dp(30f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setTop(dp(2f)).setBottom(dp(2f)).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(CHIP))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(15f)).build())
                            .build(),
                    ).setClickable(
                        ModifiersBuilders.Clickable.Builder().setId("open:$label").setOnClick(action).build(),
                    ).build(),
            ).addContent(text(label, 12f, WHITE))
            .build()

    private fun launchApp(mediaId: String?): ActionBuilders.Action {
        val activity =
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(MainActivity::class.java.name)
        if (mediaId != null) {
            activity.addKeyToExtraMapping(
                MainActivity.EXTRA_PLAY_MEDIA_ID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(mediaId).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
                    ).build(),
            ).build()

    private fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val TOGGLE_ID = "toggle"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFFBDBDBD.toInt()
        const val DARK = 0xFF1B1B1F.toInt()
        const val ACCENT = 0xFFD0BCFF.toInt()
        const val CHIP = 0xFF2B2930.toInt()
    }
}
