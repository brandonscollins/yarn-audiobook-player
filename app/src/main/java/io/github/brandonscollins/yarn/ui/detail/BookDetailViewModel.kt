package io.github.brandonscollins.yarn.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val bookId: Int = checkNotNull(savedStateHandle.get<Int>("bookId"))
    private val db = PlexGraph.db(app)
    private val syncRepo = LibrarySyncRepo(PlexGraph.prefs(app), PlexGraph.api(app), db)

    val book: StateFlow<Audiobook?> =
        db.bookDao().getBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<Track>> =
        db.trackDao().getTracksForBook(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _position = MutableStateFlow<PlaybackPosition?>(null)
    val position: StateFlow<PlaybackPosition?> = _position.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { syncRepo.syncTracks(bookId) }
            _position.value = db.positionDao().getPosition(bookId)
        }
    }
}
