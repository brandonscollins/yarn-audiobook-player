package io.github.brandonscollins.yarn.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.local.likePattern
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
class LibraryViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val db = PlexGraph.db(app)
    private val syncRepo = LibrarySyncRepo(PlexGraph.prefs(app), PlexGraph.api(app), db)
    private val libraryPrefs = LibraryPrefs(app)

    private val _viewMode = MutableStateFlow(libraryPrefs.viewMode)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortMode = MutableStateFlow(libraryPrefs.sortMode)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _filterMode = MutableStateFlow(libraryPrefs.filterMode)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    fun setViewMode(mode: ViewMode) {
        libraryPrefs.viewMode = mode
        _viewMode.value = mode
    }

    fun setSortMode(mode: SortMode) {
        libraryPrefs.sortMode = mode
        _sortMode.value = mode
    }

    fun setFilterMode(mode: FilterMode) {
        libraryPrefs.filterMode = mode
        _filterMode.value = mode
    }

    val allBooks: StateFlow<List<BookRow>> =
        combine(
            db.bookDao().getAllBooks(),
            db.positionDao().getAll(),
            sortMode,
            filterMode,
        ) { books, positions, sort, filter ->
            rows(db, books, positions, sort, filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<Collection>> =
        db.collectionDao().getAllCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Seeded from the route when the library is opened on an author (tapping one on a book). */
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("query").orEmpty())
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
                    combine(
                        db.bookDao().search(likePattern(query)),
                        db.positionDao().getAll(),
                        sortMode,
                        filterMode,
                    ) { books, positions, sort, filter ->
                        rows(db, books, positions, sort, filter)
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
            filterMode,
        ) { books, positions, sort, filter -> rows(db, books, positions, sort, filter) }
}

/** Filtering runs before [bookProgress], which is a Room query per started book (next_steps.md). */
private suspend fun rows(
    db: YarnDatabase,
    books: List<Audiobook>,
    positions: List<PlaybackPosition>,
    sort: SortMode,
    filter: FilterMode,
): List<BookRow> {
    val byBook = positions.associateBy { it.bookId }
    val mapped =
        books.filter { matchesFilter(filter, byBook[it.id]) && (filter != FilterMode.Downloaded || it.isCached) }
            .map { book -> BookRow(book, byBook[book.id]?.let { bookProgress(db, book, it) }) }
    return sortRows(mapped, sort)
}

/**
 * No ledger row means the book was never opened, so "not started" is the absence of a row rather
 * than a position of zero. Finished is the durable column (ADR-008), not a guess from the offset.
 * Downloaded isn't position-based, so it's checked separately in [rows] against [Audiobook.isCached].
 */
internal fun matchesFilter(
    filter: FilterMode,
    position: PlaybackPosition?,
): Boolean =
    when (filter) {
        FilterMode.All -> true
        FilterMode.InProgress -> position != null && !position.finished
        FilterMode.NotStarted -> position == null
        FilterMode.Finished -> position?.finished == true
        FilterMode.Downloaded -> true
    }

private fun sortRows(
    rows: List<BookRow>,
    sort: SortMode,
): List<BookRow> =
    when (sort) {
        SortMode.Title -> rows.sortedBy { sortTitle(it.book.title).lowercase() }
        SortMode.RecentlyAdded -> rows.sortedByDescending { it.book.addedAt }
        // 0 (unknown) has to sort last explicitly: a pre-1970 publication date is *negative*
        // epoch-millis, and plenty of audiobooks carry the original book's year.
        SortMode.RecentlyPublished ->
            rows.sortedByDescending {
                it.book.publishedAtEpochMs.takeIf { ms -> ms != 0L } ?: Long.MIN_VALUE
            }
    }
