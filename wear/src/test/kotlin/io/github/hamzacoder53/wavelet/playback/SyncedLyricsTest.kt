package io.github.hamzacoder53.wavelet.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncedLyricsTest {
    @Test
    fun parsesTimeTagsAndSortsLines() {
        val lrc =
            """
            [ar:Someone]
            [00:12.50]second line
            [00:01.00]first line
            [01:02.123][01:30]repeated line

            [00:20.00]
            """.trimIndent()

        val lines = SyncedLyrics.parseLrc(lrc)

        assertEquals(
            listOf(
                LyricLine(1_000, "first line"),
                LyricLine(12_500, "second line"),
                LyricLine(62_123, "repeated line"),
                LyricLine(90_000, "repeated line"),
            ),
            lines,
        )
    }

    @Test
    fun currentIndexFollowsPlaybackPosition() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"))
        assertEquals(-1, SyncedLyrics.currentIndex(lines, 500))
        assertEquals(0, SyncedLyrics.currentIndex(lines, 4_999))
        assertEquals(1, SyncedLyrics.currentIndex(lines, 9_000))
    }
}
