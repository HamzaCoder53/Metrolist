package io.github.hamzacoder53.wavelet.ui

import android.app.Application
import android.content.ComponentName
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.playback.AudioOutputs
import io.github.hamzacoder53.wavelet.playback.PlaybackService
import io.github.hamzacoder53.wavelet.playback.fetchRadioItems
import io.github.hamzacoder53.wavelet.playback.toMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

data class NowPlayingState(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** False for streams that can't seek (e.g. experimental SABR uploads). */
    val isSeekable: Boolean = false,
    val needsHeadphones: Boolean = false,
    val error: String? = null,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

data class QueueEntry(
    val timelineIndex: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val thumbnail: String?,
    val isCurrent: Boolean,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _volume = MutableStateFlow(currentVolume())
    val volume: StateFlow<Pair<Int, Int>> = _volume.asStateFlow()

    private val app = application as io.github.hamzacoder53.wavelet.WaveletApplication
    val lyrics = app.lyrics.state

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()
    val lyricsEnabled: Boolean get() = app.settings.showLyrics

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
        if (_showLyrics.value) controller?.currentMediaItem?.let(::loadLyrics)
    }

    private fun loadLyrics(item: MediaItem) {
        val md = item.mediaMetadata
        app.lyrics.load(item.mediaId, md.title?.toString().orEmpty(), md.artist?.toString().orEmpty(), md.durationMs)
    }

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refresh(player)
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) {
                    refreshQueue(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (_showLyrics.value && mediaItem != null) loadLyrics(mediaItem) else app.lyrics.clear()
            }
        }

    init {
        viewModelScope.launch {
            val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
            controller = MediaController.Builder(application, token).buildAsync().await().also {
                it.addListener(listener)
                refresh(it)
                refreshQueue(it)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                controller?.let { player -> if (player.isPlaying) refresh(player) }
                // Lyrics need finer position updates than the progress ring.
                delay(if (_showLyrics.value) 250 else 1000)
            }
        }
    }

    /** Queues [songs] from [startIndex]; playback waits for headphones unless speaker playback is on. */
    fun playSongs(songs: List<SongItem>, startIndex: Int): Boolean = playItems(songs.map { it.toMediaItem() }, startIndex)

    fun playItems(items: List<MediaItem>, startIndex: Int): Boolean {
        val player = controller ?: return false
        player.setMediaItems(items, startIndex, 0)
        player.prepare()
        val canPlay = AudioOutputs.canPlay(getApplication())
        if (canPlay) player.play()
        _state.update { it.copy(needsHeadphones = !canPlay) }
        return canPlay
    }

    val isConnected: Boolean get() = controller != null

    /** Resumes the current queue, or shows the headphones prompt. */
    fun resume() {
        if (AudioOutputs.canPlay(getApplication())) controller?.play() else _state.update { it.copy(needsHeadphones = true) }
    }

    fun onHeadphonesConnected() {
        _state.update { it.copy(needsHeadphones = false) }
        controller?.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else if (AudioOutputs.canPlay(getApplication())) {
            player.play()
        } else {
            _state.update { it.copy(needsHeadphones = true) }
        }
    }

    /** Downloads the current song for offline use (metadata comes from the queue item). */
    fun downloadCurrent() {
        val item = controller?.currentMediaItem ?: return
        val md = item.mediaMetadata
        val song =
            SongItem(
                id = item.mediaId,
                title = md.title?.toString() ?: item.mediaId,
                artists = listOf(com.metrolist.innertube.models.Artist(name = md.artist?.toString().orEmpty(), id = null)),
                duration = md.durationMs?.let { (it / 1000).toInt() },
                thumbnail = md.artworkUri?.toString().orEmpty(),
            )
        (getApplication<Application>() as io.github.hamzacoder53.wavelet.WaveletApplication).downloads.download(listOf(song))
    }

    fun setShuffle(enabled: Boolean) {
        val player = controller ?: return
        player.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleModeEnabled = enabled) }
    }

    fun toggleShuffle() {
        val player = controller ?: return
        setShuffle(!player.shuffleModeEnabled)
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        val nextMode =
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
        player.repeatMode = nextMode
        _state.update { it.copy(repeatMode = nextMode) }
    }

    fun playQueueIndex(timelineIndex: Int) {
        val player = controller ?: return
        if (timelineIndex in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(timelineIndex)
            player.play()
        }
    }

    fun removeFromQueue(timelineIndex: Int) {
        val player = controller ?: return
        if (timelineIndex in 0 until player.mediaItemCount) {
            player.removeMediaItem(timelineIndex)
            refresh(player)
        }
    }

    /**
     * Keeps the current song playing and replaces everything after it with
     * YouTube Music's radio for this song.
     */
    fun startRadio(onSuccess: () -> Unit = {}) {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= player.mediaItemCount) return
        val currentSong = player.currentMediaItem ?: return
        val seedId = currentSong.mediaId

        viewModelScope.launch {
            fetchRadioItems(seedId)
                .onSuccess { items ->
                    val additions = items.filter { it.mediaId != seedId }
                    if (additions.isNotEmpty()) {
                        val count = player.mediaItemCount
                        if (count > currentIndex + 1) {
                            player.removeMediaItems(currentIndex + 1, count)
                        }
                        player.addMediaItems(additions)
                        refresh(player)
                        onSuccess()
                    }
                }.onFailure { error ->
                    Timber.w(error, "Start radio failed for %s", seedId)
                }
        }
    }

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() = controller?.seekToPrevious()

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        seekTo(player.currentPosition + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
        refresh(player)
    }

    fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
        _volume.value = currentVolume()
    }

    private fun currentVolume() =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) to
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** Upcoming songs in play order (follows shuffle); only rebuilt when the queue or its order changes. */
    private fun refreshQueue(player: Player) {
        val timeline = player.currentTimeline
        val entries = mutableListOf<QueueEntry>()
        var index = player.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index in 0 until timeline.windowCount && entries.size < timeline.windowCount) {
            val item = player.getMediaItemAt(index)
            val md = item.mediaMetadata
            entries +=
                QueueEntry(
                    timelineIndex = index,
                    mediaId = item.mediaId,
                    title = md.title?.toString() ?: item.mediaId,
                    artist = md.artist?.toString(),
                    thumbnail = md.artworkUri?.toString(),
                    isCurrent = entries.isEmpty(),
                )
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        _queue.value = entries
    }

    private fun refresh(player: Player) {
        _state.update {
            it.copy(
                mediaItem = player.currentMediaItem,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem() || player.currentPosition > 3000,
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                isSeekable = player.isCurrentMediaItemSeekable && player.duration > 0,
                // Media3 reports this suppression even when idle, so only treat it as a prompt
                // when the user actually asked to play something.
                needsHeadphones =
                    it.needsHeadphones ||
                        (
                            player.playWhenReady &&
                                player.mediaItemCount > 0 &&
                                player.playbackSuppressionReason ==
                                Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT
                        ),
                error = player.playerError?.message,
                shuffleModeEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
    }
}
