package io.github.hamzacoder53.wavelet.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.ArtistSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface ArtistState {
    data object Loading : ArtistState

    data class Loaded(
        val artist: ArtistItem,
        val topSongs: List<SongItem>,
        val sections: List<ArtistSection>,
    ) : ArtistState

    data object Failed : ArtistState
}

class ArtistViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    val browseId: String = Uri.decode(checkNotNull(savedState["browseId"]))

    private val _state = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val state: StateFlow<ArtistState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ArtistState.Loading
        viewModelScope.launch {
            _state.value =
                YouTube.artist(browseId).map { page ->
                    val topSongs =
                        page.sections.firstOrNull { s -> s.items.any { it is SongItem } }
                            ?.items?.filterIsInstance<SongItem>()
                            ?: page.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                    ArtistState.Loaded(
                        artist = page.artist,
                        topSongs = topSongs,
                        sections = page.sections,
                    )
                }.getOrElse {
                    Timber.w(it, "Could not load artist %s", browseId)
                    ArtistState.Failed
                }
        }
    }
}
