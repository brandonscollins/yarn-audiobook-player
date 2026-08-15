package io.github.brandonscollins.yarn.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.local.nextUpNext
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.ui.common.bookRemainingMs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A book with a ledger row — the hero card, the played shelves and the finished shelf all use it. */
data class PlayedBook(
    val book: Audiobook,
    val progress: Float?,
    val remainingMs: Long?,
    val finished: Boolean,
)

/** How many books the "Recently played" shelf prints before "View more" takes over. */
private const val PLAYED_SHELF = 5

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PlexGraph.db(app)

    val continueListening: StateFlow<PlayedBook?> =
        combine(db.positionDao().getMostRecent(), db.bookDao().getAllBooks()) { position, books ->
            if (position == null) return@combine null
            played(db, listOf(position), books).firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Asks for one more than the shelf shows and drops it: the newest ledger row is by definition
     * the Continue listening card's own book, which would otherwise be reprinted directly under
     * itself.
     */
    val recentlyPlayed: StateFlow<List<PlayedBook>> =
        combine(
            db.positionDao().getRecent(PLAYED_SHELF + 1),
            db.bookDao().getAllBooks(),
        ) { positions, books -> played(db, positions.drop(1), books) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "View more" — every started book, newest first, hero book included. */
    val allPlayed: StateFlow<List<PlayedBook>> =
        combine(
            db.positionDao().getRecent(Int.MAX_VALUE),
            db.bookDao().getAllBooks(),
        ) { positions, books -> played(db, positions, books) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val finished: StateFlow<List<PlayedBook>> =
        combine(
            db.positionDao().getRecent(Int.MAX_VALUE),
            db.bookDao().getAllBooks(),
        ) { positions, books -> played(db, positions.filter { it.finished }, books) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The next unstarted book of the Continue listening book's series, found by title numbering
     * among its collection peers, then among everything by the same author, then by Plex's own
     * collection order. Most libraries answer null for most books, and the section is meant to stay
     * hidden when it does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val upNext: StateFlow<Audiobook?> =
        continueListening.flatMapLatest { current ->
            if (current == null) return@flatMapLatest flowOf(null)
            combine(
                db.collectionDao().getCollectionPeers(current.book.id),
                db.positionDao().getAll(),
                db.bookDao().getAllBooks(),
            ) { peers, positions, books ->
                val started = positions.mapTo(mutableSetOf()) { it.bookId }
                val peerIds = peers.mapTo(mutableSetOf()) { it.bookId }
                nextUpNext(
                    current = current.book,
                    collectionPeers = books.filter { it.id in peerIds },
                    sameAuthorBooks = books.filter { it.author == current.book.author },
                    crossRefs = peers,
                    startedBookIds = started,
                )?.let { id -> books.firstOrNull { it.id == id } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentlyAdded: StateFlow<List<Audiobook>> =
        db.bookDao().getAllBooks()
            .map { books -> books.sortedByDescending { it.addedAt }.take(12) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Progress is derived from what's left rather than computed separately, so a row costs one track
 * query instead of two, and a finished book costs none.
 *
 * ponytail: still one Room query per unfinished row per emission, the same shape called out for
 * `LibraryViewModel.rows()` — bounded at 5 on the shelf, unbounded on the "View more" screen. A
 * JOIN in `TrackDao` would remove it.
 */
private suspend fun played(
    db: YarnDatabase,
    positions: List<PlaybackPosition>,
    books: List<Audiobook>,
): List<PlayedBook> {
    val byId = books.associateBy { it.id }
    return positions.mapNotNull { position ->
        val book = byId[position.bookId] ?: return@mapNotNull null
        val remaining = if (position.finished) null else bookRemainingMs(db, book, position)
        PlayedBook(
            book = book,
            progress = remaining?.let { (book.durationMs - it).toFloat() / book.durationMs },
            remainingMs = remaining,
            finished = position.finished,
        )
    }
}
