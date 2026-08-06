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
import io.github.brandonscollins.yarn.ui.common.sortTitle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookRow(val book: Audiobook, val progress: Float?)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PlexGraph.db(app)
    private val syncRepo = LibrarySyncRepo(PlexGraph.prefs(app), PlexGraph.api(app), db)
    private val libraryPrefs = LibraryPrefs(app)

    private val _viewMode = MutableStateFlow(libraryPrefs.viewMode)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortMode = MutableStateFlow(libraryPrefs.sortMode)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setViewMode(mode: ViewMode) {
        libraryPrefs.viewMode = mode
        _viewMode.value = mode
    }

    fun setSortMode(mode: SortMode) {
        libraryPrefs.sortMode = mode
        _sortMode.value = mode
    }

    val allBooks: StateFlow<List<BookRow>> =
        combine(db.bookDao().getAllBooks(), db.positionDao().getAll(), sortMode) { books, positions, sort ->
            rows(db, books, positions, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<Collection>> =
        db.collectionDao().getAllCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Empty query shows nothing; otherwise debounced local title/author search. */
    val searchResults: StateFlow<List<BookRow>> =
        _searchQuery
            .debounce(150)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    combine(db.bookDao().search(query), db.positionDao().getAll(), sortMode) { books, positions, sort ->
                        rows(db, books, positions, sort)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            sortMode,
        ) { books, positions, sort -> rows(db, books, positions, sort) }
}

private suspend fun rows(
    db: YarnDatabase,
    books: List<Audiobook>,
    positions: List<PlaybackPosition>,
    sort: SortMode,
): List<BookRow> {
    val byBook = positions.associateBy { it.bookId }
    val mapped = books.map { book -> BookRow(book, byBook[book.id]?.let { bookProgress(db, book, it) }) }
    return sortRows(mapped, sort)
}

private fun sortRows(
    rows: List<BookRow>,
    sort: SortMode,
): List<BookRow> =
    when (sort) {
        SortMode.Title -> rows.sortedBy { sortTitle(it.book.title).lowercase() }
        SortMode.RecentlyAdded -> rows.sortedByDescending { it.book.addedAt }
        // 0 (unknown) sorts last for free: it's smaller than any real epoch-millis value.
        SortMode.RecentlyPublished -> rows.sortedByDescending { it.book.publishedAtEpochMs }
    }
