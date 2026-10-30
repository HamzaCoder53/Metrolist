package io.github.hamzacoder53.wavelet.playback

import com.metrolist.lrclib.LrcLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** One timed line of LRC lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface LyricsState {
    data object None : LyricsState

    data object Loading : LyricsState

    /** Synced lines; [lines] is empty if only unsynced lyrics exist (they're not shown on the watch). */
    data class Ready(val lines: List<LyricLine>) : LyricsState

    data object NotFound : LyricsState
}

/**
 * Fetches synced lyrics from LrcLib (via Metrolist's shared :lyrics module) for the current song
 * and keeps the last few results in memory.
 */
class SyncedLyrics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>?) = size > 20
    }
    private var job: Job? = null

    private val _state = MutableStateFlow<LyricsState>(LyricsState.None)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    fun load(mediaId: String, title: String, artist: String, durationMs: Long?) {
        job?.cancel()
        cache[mediaId]?.let {
            _state.value = LyricsState.Ready(it)
            return
        }
        _state.value = LyricsState.Loading
        job =
            scope.launch {
                val duration = durationMs?.let { (it / 1000).toInt() } ?: -1
                _state.value =
                    LrcLib.getLyrics(title, artist, duration)
                        .map(::parseLrc)
                        .fold(
                            onSuccess = { lines ->
                                Timber.d("Lyrics for %s: %d synced lines", mediaId, lines.size)
                                synchronized(cache) { cache[mediaId] = lines }
                                if (lines.isEmpty()) LyricsState.NotFound else LyricsState.Ready(lines)
                            },
                            onFailure = {
                                Timber.d("No lyrics for %s: %s", mediaId, it.message)
                                LyricsState.NotFound
                            },
                        )
            }
    }

    fun clear() {
        job?.cancel()
        _state.value = LyricsState.None
    }

    companion object {
        private val timeTag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** Parses LRC text; lines may carry several time tags. Returns lines sorted by time. */
        fun parseLrc(lrc: String): List<LyricLine> =
            lrc.lineSequence()
                .flatMap { raw ->
                    val tags = timeTag.findAll(raw).toList()
                    val text = raw.replace(timeTag, "").trim()
                    tags.map { match ->
                        val (min, sec, frac) = match.destructured
                        val fracMs =
                            when (frac.length) {
                                0 -> 0
                                1 -> frac.toInt() * 100
                                2 -> frac.toInt() * 10
                                else -> frac.take(3).toInt()
                            }
                        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, text)
                    }
                }.filter { it.text.isNotEmpty() }
                .sortedBy { it.timeMs }
                .toList()

        /** Index of the line playing at [positionMs], or -1 before the first line. */
        fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int = lines.indexOfLast { it.timeMs <= positionMs }
    }
}
