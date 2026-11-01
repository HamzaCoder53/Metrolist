package io.github.hamzacoder53.wavelet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import io.github.hamzacoder53.wavelet.WaveletApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface CollectionState {
    data object Loading : CollectionState

    data class Loaded(val title: String, val subtitle: String?, val songs: List<SongItem>) : CollectionState

    data object Failed : CollectionState
}

/** Loads an album (browseId), a public playlist, or a playlist from the signed-in account. */
class CollectionViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    val kind: String = checkNotNull(savedState["kind"])
    val id: String = checkNotNull(savedState["id"])

    private val _state = MutableStateFlow<CollectionState>(CollectionState.Loading)
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = CollectionState.Loading
        viewModelScope.launch {
            _state.value =
                runCatching {
                    when (kind) {
                        KIND_ALBUM -> loadAlbum()
                        KIND_ACCOUNT_PLAYLIST -> loadAccountPlaylist()
                        else -> loadPlaylist()
                    }
                }.getOrElse {
                    Timber.w(it, "Could not load %s %s", kind, id)
                    CollectionState.Failed
                }
        }
    }

    private suspend fun loadAlbum(): CollectionState {
        val page = YouTube.album(id).getOrThrow()
        if (id.contains(UPLOADED_ALBUM_ID)) {
            getApplication<WaveletApplication>().accountLibrary.rememberUploaded(page.songs.map { it.id })
        }
        return CollectionState.Loaded(
            title = page.album.title,
            subtitle = page.album.artists?.joinToString { it.name },
            songs = page.songs,
        )
    }

    private suspend fun loadPlaylist(): CollectionState {
        val page = YouTube.playlist(id).getOrThrow()
        val songs = page.songs.toMutableList()
        var continuation = page.songsContinuation ?: page.continuation
        // Cap at a size a watch can sensibly store and scroll through.
        while (continuation != null && songs.size < MAX_PLAYLIST_SONGS) {
            val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            songs += next.songs
            continuation = next.continuation
        }
        return CollectionState.Loaded(
            title = page.playlist.title,
            subtitle = page.playlist.author?.name,
            songs = songs.distinctBy { it.id }.take(MAX_PLAYLIST_SONGS),
        )
    }

    private suspend fun loadAccountPlaylist(): CollectionState {
        val library = getApplication<WaveletApplication>().accountLibrary
        val playlist = library.snapshot.value.playlists.firstOrNull { it.id == id }
        return CollectionState.Loaded(
            title = playlist?.title ?: id,
            subtitle = playlist?.subtitle,
            songs = library.playlistSongs(id).take(MAX_PLAYLIST_SONGS),
        )
    }

    companion object {
        const val KIND_ALBUM = "album"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ACCOUNT_PLAYLIST = "account"
        private const val MAX_PLAYLIST_SONGS = 200
        private const val UPLOADED_ALBUM_ID = "FEmusic_library_privately_owned_release_detail"
    }
}
