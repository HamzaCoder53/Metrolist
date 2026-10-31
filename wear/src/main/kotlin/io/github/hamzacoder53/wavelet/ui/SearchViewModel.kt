package io.github.hamzacoder53.wavelet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import io.github.hamzacoder53.wavelet.WaveletApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

enum class SearchType(val filter: YouTube.SearchFilter) {
    SONGS(YouTube.SearchFilter.FILTER_SONG),
    ALBUMS(YouTube.SearchFilter.FILTER_ALBUM),
    ARTISTS(YouTube.SearchFilter.FILTER_ARTIST),
    PLAYLISTS(YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
}

sealed interface SearchState {
    data object Idle : SearchState

    data class Loading(val query: String) : SearchState

    data class Results(
        val query: String,
        val items: List<YTItem>,
        val suggestions: List<String> = emptyList(),
    ) : SearchState {
        val songs get() = items.filterIsInstance<SongItem>()
    }

    data class Failed(val query: String) : SearchState
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WaveletApplication
    private val settings = app.settings

    private val _recentSearches = MutableStateFlow(settings.recentSearches)
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

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

    fun clearRecentSearches() {
        settings.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        lastQuery = trimmed
        settings.addRecentSearch(trimmed)
        _recentSearches.value = settings.recentSearches

        searchJob?.cancel()
        _state.value = SearchState.Loading(trimmed)
        val type = _type.value
        searchJob =
            viewModelScope.launch {
                val searchDeferred = async { YouTube.search(trimmed, type.filter) }
                val suggestionsDeferred =
                    async {
                        runCatching {
                            YouTube.searchSuggestions(trimmed).getOrNull()?.queries
                                ?.filter { !it.equals(trimmed, ignoreCase = true) }
                                ?.take(5)
                                .orEmpty()
                        }.getOrDefault(emptyList())
                    }

                val searchResult = searchDeferred.await()
                val suggestions = suggestionsDeferred.await()

                _state.value =
                    searchResult
                        .map { result ->
                            SearchState.Results(
                                query = trimmed,
                                items =
                                    result.items.filter {
                                        when (type) {
                                            SearchType.SONGS -> it is SongItem
                                            SearchType.ALBUMS -> it is AlbumItem
                                            SearchType.ARTISTS -> it is ArtistItem
                                            SearchType.PLAYLISTS -> it is PlaylistItem
                                        }
                                    },
                                suggestions = suggestions,
                            )
                        }.getOrElse {
                            Timber.w(it, "Search failed")
                            SearchState.Failed(trimmed)
                        }
            }
    }
}
