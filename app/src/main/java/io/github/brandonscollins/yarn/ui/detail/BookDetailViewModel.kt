package io.github.brandonscollins.yarn.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.download.DownloadState
import io.github.brandonscollins.yarn.data.download.Downloads
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.work.ProgressSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val bookId: Int = checkNotNull(savedStateHandle.get<Int>("bookId"))
    private val db = PlexGraph.db(app)
    private val prefs = PlexGraph.prefs(app)
    private val api = PlexGraph.api(app)
    private val syncRepo = LibrarySyncRepo(prefs, api, db, app)

    val book: StateFlow<Audiobook?> =
        db.bookDao().getBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<Track>> =
        db.trackDao().getTracksForBook(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _position = MutableStateFlow<PlaybackPosition?>(null)
    val position: StateFlow<PlaybackPosition?> = _position.asStateFlow()

    /** Queued/running plus how many tracks have landed; drives the menu wording and the ring. */
    val downloadState: StateFlow<DownloadState> =
        Downloads.observe(app, bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState())

    init {
        viewModelScope.launch {
            runCatching { syncRepo.syncTracks(bookId) }
            _position.value = db.positionDao().getPosition(bookId)
        }
    }

    /**
     * The deliberate manual finish of CLAUDE.md gotcha #2 — Plex's own 90% rule stays defeated, so
     * this is the only thing that ever marks an audiobook done. [PositionDao.setFinished] is an
     * UPDATE, so a book that was never played needs a ledger row parked at the end of its last
     * track first; that row is also what makes the progress bars and the Finished filter agree.
     */
    fun setFinished(finished: Boolean) {
        viewModelScope.launch {
            if (finished && db.positionDao().getPosition(bookId) == null) {
                val last = db.trackDao().getTracksForBook(bookId).first().lastOrNull() ?: return@launch
                db.positionDao().upsert(
                    PlaybackPosition(
                        bookId = bookId,
                        trackId = last.id,
                        positionMs = last.durationMs,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            db.positionDao().setFinished(bookId, finished)
            ProgressSyncWorker.enqueue(getApplication())
            _position.value = db.positionDao().getPosition(bookId)
        }
    }

    fun download() = Downloads.start(getApplication(), bookId)

    fun cancelDownload() = Downloads.cancel(getApplication(), bookId)

    fun removeDownload() {
        viewModelScope.launch { Downloads.remove(getApplication(), bookId) }
    }

    /**
     * "Mark as unplayed". Two copies of the progress have to go or it comes straight back: the
     * Plex `viewOffset`s already synced onto the local tracks (`resumePoint` takes whichever of
     * local vs. Plex is furthest ahead, so those clear immediately, same as before), and Plex's own
     * copy of the position.
     *
     * The server half used to be a best-effort timeline sweep at time=0 — offline-unsafe, since the
     * next `syncTracks` would pull the server's old progress back down over it. Now it's a
     * tombstone ledger row (`unplayedPending`, positionMs 0 at the book's first track) that
     * survives exactly like a `finishedPending` row does: it rides in the outbox, drained by
     * `ProgressSyncWorker` via `/:/unscrobble`, and every "has this book been started" read
     * (`isStartedRow`) treats it as no row at all in the meantime. `_position` is set to null
     * rather than the tombstone for the same reason — this screen's Resume/Play label and progress
     * bar read `position != null`.
     */
    fun markUnplayed() {
        viewModelScope.launch {
            val bookTracks = db.trackDao().getTracksForBook(bookId).first()
            val firstTrack = bookTracks.firstOrNull() ?: return@launch
            db.trackDao().upsertAll(bookTracks.map { it.copy(viewOffsetMs = 0) })
            db.positionDao().upsert(
                PlaybackPosition(
                    bookId = bookId,
                    trackId = firstTrack.id,
                    positionMs = 0,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    unplayedPending = true,
                ),
            )
            _position.value = null
            ProgressSyncWorker.enqueue(getApplication())
        }
    }
}
