package io.github.hamzacoder53.wavelet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

enum class SearchType(val filter: YouTube.SearchFilter) {
    SONGS(YouTube.SearchFilter.FILTER_SONG),
    ALBUMS(YouTube.SearchFilter.FILTER_ALBUM),
    PLAYLISTS(YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
}

sealed interface SearchState {
    data object Idle : SearchState

    data class Loading(val query: String) : SearchState

    data class Results(val query: String, val items: List<YTItem>) : SearchState {
        val songs get() = items.filterIsInstance<SongItem>()
    }

    data class Failed(val query: String) : SearchState
}

class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private val _type = MutableStateFlow(SearchType.SONGS)
    val type: StateFlow<SearchType> = _type.asStateFlow()
    private var lastQuery: String? = null
    private var searchJob: Job? = null

    fun setType(type: SearchType) {
        if (_type.value == type) return
        _type.value = type
        lastQuery?.let(::search)
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        lastQuery = trimmed
        searchJob?.cancel()
        _state.value = SearchState.Loading(trimmed)
        val type = _type.value
        searchJob =
            viewModelScope.launch {
                _state.value =
                    YouTube.search(trimmed, type.filter)
                        .map { result ->
                            SearchState.Results(
                                trimmed,
                                result.items.filter {
                                    when (type) {
                                        SearchType.SONGS -> it is SongItem
                                        SearchType.ALBUMS -> it is AlbumItem
                                        SearchType.PLAYLISTS -> it is PlaylistItem
                                    }
                                },
                            )
                        }.getOrElse {
                            Timber.w(it, "Search failed")
                            SearchState.Failed(trimmed)
                        }
            }
    }
}
