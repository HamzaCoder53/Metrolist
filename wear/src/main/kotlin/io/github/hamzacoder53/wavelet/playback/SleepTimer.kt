package io.github.hamzacoder53.wavelet.playback

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pauses playback after a delay, or at the end of the current song. */
class SleepTimer {
    sealed interface State {
        data object Off : State

        data class At(val endsAtMillis: Long) : State

        data object EndOfSong : State
    }

    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable { expire() }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Set by the playback service while it runs: pause now, or pause at the end of the current song. */
    @Volatile
    var onExpire: (() -> Unit)? = null

    @Volatile
    var onEndOfSongChanged: ((Boolean) -> Unit)? = null

    fun startMinutes(minutes: Int) {
        cancel()
        _state.value = State.At(System.currentTimeMillis() + minutes * 60_000L)
        handler.postDelayed(fire, minutes * 60_000L)
    }

    fun startEndOfSong() {
        cancel()
        _state.value = State.EndOfSong
        onEndOfSongChanged?.invoke(true)
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        if (_state.value == State.EndOfSong) onEndOfSongChanged?.invoke(false)
        _state.value = State.Off
    }

    /** Called by the service when a song ended while "end of song" was set. */
    fun endOfSongReached() {
        if (_state.value == State.EndOfSong) {
            onEndOfSongChanged?.invoke(false)
            _state.value = State.Off
        }
    }

    private fun expire() {
        _state.value = State.Off
        onExpire?.invoke()
    }
}
