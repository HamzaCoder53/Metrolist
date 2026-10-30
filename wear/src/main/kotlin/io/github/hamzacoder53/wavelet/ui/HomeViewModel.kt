package io.github.hamzacoder53.wavelet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Recommended music from YouTube Music's home page (Quick picks, mixes, recommended albums and
 * playlists...), personalized when signed in with a full account. Hidden while offline.
 */
class HomeViewModel : ViewModel() {
    /** A home section with only what the watch can play or open: songs, albums and playlists. */
    data class Section(val title: String, val items: List<YTItem>) {
        val songs get() = items.filterIsInstance<SongItem>()
    }

    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            YouTube.home()
                .onSuccess { page ->
                    _sections.value =
                        page.sections
                            .map { section ->
                                Section(
                                    section.title,
                                    section.items
                                        .filter { it is SongItem || it is AlbumItem || it is PlaylistItem }
                                        .take(MAX_ITEMS),
                                )
                            }.filter { it.items.size >= 2 }
                            .take(MAX_SECTIONS)
                }.onFailure { Timber.w(it, "Home recommendations unavailable (offline?)") }
        }
    }

    private companion object {
        const val MAX_SECTIONS = 5
        const val MAX_ITEMS = 6
    }
}
