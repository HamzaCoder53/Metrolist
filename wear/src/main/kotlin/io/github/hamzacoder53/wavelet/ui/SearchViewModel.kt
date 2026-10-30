package io.github.hamzacoder53.wavelet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface SearchState {
    data object Idle : SearchState

    data class Loading(val query: String) : SearchState

    data class Results(val query: String, val songs: List<SongItem>) : SearchState

    data class Failed(val query: String) : SearchState
}

class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        _state.value = SearchState.Loading(trimmed)
        searchJob =
            viewModelScope.launch {
                _state.value =
                    YouTube.search(trimmed, YouTube.SearchFilter.FILTER_SONG)
                        .map { result -> SearchState.Results(trimmed, result.items.filterIsInstance<SongItem>()) }
                        .getOrElse {
                            Timber.w(it, "Search failed")
                            SearchState.Failed(trimmed)
                        }
            }
    }
}
