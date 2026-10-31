package io.github.hamzacoder53.wavelet.playback

import androidx.media3.exoplayer.offline.Download
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DownloadSyncTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    private fun downloaded(id: String, state: Int = Download.STATE_COMPLETED) =
        DownloadedSong(id, id, "", "PL1", "Playlist", null, File(id), state, 100f, 0, 0)

    @Test
    fun addsNewSongsAndRemovesDeletedOnes() {
        val (added, removed) =
            DownloadSync.plan(listOf(downloaded("a"), downloaded("b")), listOf(song("a"), song("c")), { false })
        assertEquals(listOf("c"), added.map { it.id })
        assertEquals(listOf("b"), removed)
    }

    @Test
    fun retriesFailedDownloads() {
        val (added, removed) =
            DownloadSync.plan(listOf(downloaded("a", Download.STATE_FAILED)), listOf(song("a")), { false })
        assertEquals(listOf("a"), added.map { it.id })
        assertEquals(emptyList<String>(), removed)
    }

    @Test
    fun leavesSongsDownloadedWithAnotherPlaylist() {
        val (added, _) = DownloadSync.plan(emptyList(), listOf(song("x"), song("x")), { it == "x" })
        assertEquals(emptyList<String>(), added.map { it.id })
    }

    @Test
    fun inSyncPlaylistIsUntouched() {
        val (added, removed) = DownloadSync.plan(listOf(downloaded("a")), listOf(song("a")), { true })
        assertEquals(emptyList<String>(), added.map { it.id })
        assertEquals(emptyList<String>(), removed)
    }
}
