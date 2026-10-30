package io.github.hamzacoder53.wavelet.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.common.collect.ImmutableList
import io.github.hamzacoder53.wavelet.R

/**
 * Media notification that also carries a Wear OS Ongoing Activity, so playback shows on
 * the watch face and in the recents/launcher "ongoing" slot.
 */
@OptIn(UnstableApi::class)
class OngoingMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        ensureChannel()
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val title = metadata.title ?: context.getString(R.string.app_name)

        fun action(icon: Int, label: Int, command: Int) =
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, icon),
                context.getString(label),
                command,
            )

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(metadata.artist)
                .setContentIntent(mediaSession.sessionActivity)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .addAction(action(R.drawable.ic_skip_previous, R.string.previous, Player.COMMAND_SEEK_TO_PREVIOUS))
                .addAction(
                    if (player.isPlaying) {
                        action(R.drawable.ic_pause, R.string.pause, Player.COMMAND_PLAY_PAUSE)
                    } else {
                        action(R.drawable.ic_play, R.string.play, Player.COMMAND_PLAY_PAUSE)
                    },
                ).addAction(action(R.drawable.ic_skip_next, R.string.next, Player.COMMAND_SEEK_TO_NEXT))
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2),
                )

        mediaSession.sessionActivity?.let { touchIntent ->
            OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_music_note)
                .setTouchIntent(touchIntent)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setStatus(Status.Builder().addTemplate(title.toString()).build())
                .build()
                .apply(context)
        }
        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
        )

    private fun ensureChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel))
                .setShowBadge(false)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
    }
}
