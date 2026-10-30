package io.github.hamzacoder53.wavelet.ui

import android.app.Application
import android.content.ComponentName
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.playback.AudioOutputs
import io.github.hamzacoder53.wavelet.playback.PlaybackService
import io.github.hamzacoder53.wavelet.playback.toMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlayingState(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val needsHeadphones: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(currentVolume())
    val volume: StateFlow<Pair<Int, Int>> = _volume.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh(player)
        }

    init {
        viewModelScope.launch {
            val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
            controller = MediaController.Builder(application, token).buildAsync().await().also {
                it.addListener(listener)
                refresh(it)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                controller?.let { player -> if (player.isPlaying) refresh(player) }
                delay(1000)
            }
        }
    }

    /** Queues [songs] from [startIndex]; playback only starts once headphones are connected. */
    fun playSongs(songs: List<SongItem>, startIndex: Int): Boolean = playItems(songs.map { it.toMediaItem() }, startIndex)

    fun playItems(items: List<MediaItem>, startIndex: Int): Boolean {
        val player = controller ?: return false
        player.setMediaItems(items, startIndex, 0)
        player.prepare()
        val canPlay = AudioOutputs.hasHeadphones(getApplication())
        if (canPlay) player.play()
        _state.update { it.copy(needsHeadphones = !canPlay) }
        return canPlay
    }

    fun onHeadphonesConnected() {
        _state.update { it.copy(needsHeadphones = false) }
        controller?.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else if (AudioOutputs.hasHeadphones(getApplication())) {
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

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() = controller?.seekToPrevious()

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0)))
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
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
    }
}
