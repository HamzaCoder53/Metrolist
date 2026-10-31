package io.github.hamzacoder53.wavelet.surfaces

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.github.hamzacoder53.wavelet.R

/** Watch-face complication: play/pause from the watch face, with the current song where there's room. */
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val now = NowPlayingStore.read(this)
        return build(request.complicationType, now.title, now.artist, now.isPlaying)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Song title", "Artist", false)

    private fun build(type: ComplicationType, title: String?, artist: String?, isPlaying: Boolean): ComplicationData? {
        val label = title ?: getString(R.string.app_name)
        val icon =
            MonochromaticImage.Builder(
                Icon.createWithResource(this, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            ).build()
        val tap =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, PlayPauseActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val description = PlainComplicationText.Builder(listOfNotNull(title, artist).joinToString(" · ").ifEmpty { label }).build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(label.take(SHORT_TEXT_MAX)).build(), description)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(PlainComplicationText.Builder(label).build(), description)
                    .apply { artist?.let { setTitle(PlainComplicationText.Builder(it).build()) } }
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    icon,
                    PlainComplicationText.Builder(getString(if (isPlaying) R.string.pause else R.string.play)).build(),
                ).setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private companion object {
        const val SHORT_TEXT_MAX = 7
    }
}
