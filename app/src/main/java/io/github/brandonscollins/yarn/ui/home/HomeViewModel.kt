package io.github.brandonscollins.yarn.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.bookProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ContinueListening(val book: Audiobook, val progress: Float?)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PlexGraph.db(app)

    val continueListening: StateFlow<ContinueListening?> =
        combine(db.positionDao().getMostRecent(), db.bookDao().getAllBooks()) { position, books ->
            if (position == null) return@combine null
            val book = books.find { it.id == position.bookId } ?: return@combine null
            ContinueListening(book, bookProgress(db, book, position))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentlyAdded: StateFlow<List<Audiobook>> =
        db.bookDao().getAllBooks()
            .map { books -> books.sortedByDescending { it.addedAt }.take(12) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
