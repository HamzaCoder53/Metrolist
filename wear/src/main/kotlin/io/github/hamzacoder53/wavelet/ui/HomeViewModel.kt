package io.github.hamzacoder53.wavelet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** "Quick picks" from YouTube Music's home page (personalized when signed in on the watch). */
class HomeViewModel : ViewModel() {
    data class Suggestions(val title: String, val songs: List<SongItem>)

    private val _suggestions = MutableStateFlow<Suggestions?>(null)
    val suggestions: StateFlow<Suggestions?> = _suggestions.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            YouTube.home()
                .onSuccess { page ->
                    // First section that's made of songs, e.g. "Quick picks".
                    val section =
                        page.sections.firstOrNull { section -> section.items.count { it is SongItem } >= 3 }
                    _suggestions.value =
                        section?.let { Suggestions(it.title, it.items.filterIsInstance<SongItem>().take(MAX_SONGS)) }
                }.onFailure { Timber.w(it, "Home suggestions unavailable (offline?)") }
        }
    }

    private companion object {
        const val MAX_SONGS = 10
    }
}
