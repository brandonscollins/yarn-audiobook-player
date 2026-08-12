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
import io.github.brandonscollins.yarn.data.plex.mediaItemUri
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
    private val syncRepo = LibrarySyncRepo(prefs, api, db)

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

    /**
     * "Mark as unplayed". Three copies of the progress have to go or it comes straight back: the
     * ledger row, the Plex `viewOffset`s already synced onto the local tracks (`resumePoint` takes
     * whichever of the two is furthest ahead), and Plex's own copy, which the next `syncTracks`
     * would otherwise pull back down over the cleared ones.
     *
     * ponytail: the server half is a best-effort timeline sweep at time=0 rather than
     * `/:/unscrobble` drained by the outbox — the endpoint and an `unplayedPending` column both
     * live in the data layer this screen doesn't own. Ceiling: marked unplayed with the server
     * unreachable, the local clear holds only until the next `syncTracks` restores the offsets.
     * Upgrade path: add `unscrobble` to `PlexMediaService` plus a row flag, and drain it in
     * `ProgressSyncWorker` exactly like `finishedPending`.
     */
    fun markUnplayed() {
        viewModelScope.launch {
            val bookTracks = db.trackDao().getTracksForBook(bookId).first()
            db.positionDao().clearPosition(bookId)
            db.trackDao().upsertAll(bookTracks.map { it.copy(viewOffsetMs = 0) })
            _position.value = null
            runCatching {
                // Timeline updates no-op without a session first (gotcha #1); the doubled duration
                // keeps the 90% rule out of it (gotcha #2) even on the way back down to zero.
                api.mediaService.startPlayQueue(mediaItemUri(prefs.serverId, bookId))
                bookTracks.forEach { track ->
                    api.mediaService.progress(
                        ratingKey = track.id.toString(),
                        key = "/library/metadata/${track.id}",
                        timeMs = 0,
                        duration = track.durationMs * 2,
                        playState = ProgressSyncWorker.STATE_STOPPED,
                    )
                }
            }
        }
    }
}
