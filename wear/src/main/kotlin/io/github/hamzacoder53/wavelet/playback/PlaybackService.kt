package io.github.hamzacoder53.wavelet.playback

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import io.github.hamzacoder53.wavelet.Settings
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.surfaces.NowPlayingStore
import io.github.hamzacoder53.wavelet.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var dataSource: YouTubeDataSource
    private var retriedMediaId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoplayJob: Job? = null

    private val app get() = application as WaveletApplication

    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val player = mediaSession?.player as? ExoPlayer ?: return@OnSharedPreferenceChangeListener
            when (key) {
                Settings.KEY_SKIP_SILENCE -> player.skipSilenceEnabled = app.settings.skipSilence
                Settings.KEY_NORMALIZE -> applyNormalization(player)
                Settings.KEY_AUTOPLAY -> maybeExtendQueue(player)
            }
        }

    @SuppressLint("RestrictedApi") // setSuitableOutputChecker, see SpeakerAwareOutputChecker
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
                // switcher when play is pressed without headphones, unless speaker playback is on.
                .setSuppressPlaybackOnUnsuitableOutput(true)
                .setSuitableOutputChecker(SpeakerAwareOutputChecker(app.settings))
                .build()
        player.skipSilenceEnabled = app.settings.skipSilence
        player.addListener(playerListener(player))
        app.settings.registerListener(settingsListener)

        // Loudness arrives when a stream resolves, i.e. just after the song starts loading.
        app.loudness.onChanged = { mediaId ->
            mainHandler.post { if (player.currentMediaItem?.mediaId == mediaId) applyNormalization(player) }
        }
        app.sleepTimer.onExpire = { mainHandler.post { player.pause() } }
        app.sleepTimer.onEndOfSongChanged = { enabled -> mainHandler.post { player.pauseAtEndOfMediaItems = enabled } }

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
        app.settings.unregisterListener(settingsListener)
        app.loudness.onChanged = null
        app.sleepTimer.onExpire = null
        app.sleepTimer.onEndOfSongChanged = null
        app.sleepTimer.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun applyNormalization(player: ExoPlayer) {
        val id = player.currentMediaItem?.mediaId
        player.volume = if (app.settings.normalizeVolume && id != null) app.loudness.gainFor(id) else 1f
        Timber.d("Normalization for %s: volume %.2f", id, player.volume)
    }

    /**
     * Autoplay ("radio"): once the last queued song starts, append YouTube Music's up-next list for it
     * so playback keeps going. Needs a connection; offline it simply stops at the end as before.
     */
    private fun maybeExtendQueue(player: ExoPlayer) {
        if (!app.settings.autoplay || autoplayJob?.isActive == true) return
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < player.mediaItemCount - 1) return
        val seedId = player.currentMediaItem?.mediaId ?: return
        autoplayJob =
            scope.launch {
                YouTube.next(WatchEndpoint(videoId = seedId))
                    .onSuccess { result ->
                        val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                        val additions =
                            result.items
                                .filter { it.id !in queued && !it.isVideoSong }
                                .take(AUTOPLAY_BATCH)
                                .map { it.toMediaItem() }
                        if (additions.isNotEmpty()) {
                            player.addMediaItems(additions)
                            Timber.d("Autoplay added %d songs after %s", additions.size, seedId)
                        }
                    }.onFailure { Timber.w(it, "Autoplay: couldn't load similar songs") }
            }
    }

    private fun playerListener(player: ExoPlayer) =
        object : Player.Listener {
            /** googlevideo URLs expire or get rejected with 403; re-resolve once with another client. */
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
                applyNormalization(player)
                maybeExtendQueue(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "Sleep at end of song" pauses here; the timer is then done.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    app.sleepTimer.endOfSongReached()
                }
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

    private companion object {
        const val AUTOPLAY_BATCH = 15
    }
}
