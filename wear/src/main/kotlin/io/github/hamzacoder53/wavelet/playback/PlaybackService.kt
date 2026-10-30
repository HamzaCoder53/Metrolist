package io.github.hamzacoder53.wavelet.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.surfaces.NowPlayingStore
import io.github.hamzacoder53.wavelet.ui.MainActivity
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var dataSource: YouTubeDataSource
    private var retriedMediaId: String? = null

    private val app get() = application as WaveletApplication

    override fun onCreate() {
        super.onCreate()
        dataSource = YouTubeDataSource(this)

        val player =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSource.factory(app.downloads.cache, app.listeningCache)),
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                // On Wear OS this blocks the built-in speaker and opens the system output
                // switcher when play is pressed without headphones.
                .setSuppressPlaybackOnUnsuitableOutput(!AudioOutputs.speakerAllowed)
                .build()
        player.addListener(retryOnRejectedStream(player))

        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        mediaSession =
            MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .setCallback(SessionCallback)
                .build()
        setMediaNotificationProvider(OngoingMediaNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        NowPlayingStore.write(this, null, false)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** googlevideo URLs expire or get rejected with 403; re-resolve once with another client. */
    private fun retryOnRejectedStream(player: ExoPlayer) =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val mediaId = player.currentMediaItem?.mediaId ?: return
                val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                Timber.w(error, "Playback error for %s (HTTP %s)", mediaId, responseCode)
                if (retriedMediaId == mediaId) return
                if (responseCode == 403 || responseCode == 410 || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                    retriedMediaId = mediaId
                    dataSource.onStreamRejected(mediaId)
                    player.prepare()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != retriedMediaId) retriedMediaId = null
                mediaItem?.let(app.listeningCache::recordPlayed)
                // The previous song may have just finished caching; update the offline list.
                app.listeningCache.refresh()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    NowPlayingStore.write(this@PlaybackService, player.currentMediaItem, player.isPlaying)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_READY) {
                    app.listeningCache.refresh()
                }
            }
        }

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlaybackUri() }.toMutableList())
    }
}
