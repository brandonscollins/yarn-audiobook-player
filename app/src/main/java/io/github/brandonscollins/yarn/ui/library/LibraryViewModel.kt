package io.github.brandonscollins.yarn.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.Collection
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.bookProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookRow(val book: Audiobook, val progress: Float?)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PlexGraph.db(app)
    private val syncRepo = LibrarySyncRepo(PlexGraph.prefs(app), PlexGraph.api(app), db)

    val allBooks: StateFlow<List<BookRow>> =
        combine(db.bookDao().getAllBooks(), db.positionDao().getAll()) { books, positions ->
            rows(db, books, positions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<Collection>> =
        db.collectionDao().getAllCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { syncRepo.sync() }
            _isRefreshing.value = false
        }
    }

    fun booksInCollection(collectionId: Int): Flow<List<BookRow>> =
        combine(
            db.bookDao().getBooksInCollection(collectionId),
            db.positionDao().getAll(),
        ) { books, positions -> rows(db, books, positions) }
}

private suspend fun rows(
    db: YarnDatabase,
    books: List<Audiobook>,
    positions: List<PlaybackPosition>,
): List<BookRow> {
    val byBook = positions.associateBy { it.bookId }
    return books.map { book -> BookRow(book, byBook[book.id]?.let { bookProgress(db, book, it) }) }
}
