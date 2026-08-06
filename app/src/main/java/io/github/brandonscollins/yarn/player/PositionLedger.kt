package io.github.brandonscollins.yarn.player

import android.content.Context
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.work.ProgressSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The position ledger — CLAUDE.md "the one invariant". Every caller hands over an already-captured
 * position and returns immediately; the Room write happens on a background scope that is *not* tied
 * to the service lifecycle, so the write triggered by `onDestroy` still lands. Nothing here depends
 * on network state: [ProgressSyncWorker] carries the Plex side, with retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionLedger(context: Context) {
    private val appContext = context.applicationContext
    private val db = PlexGraph.db(appContext)
    private val dao = db.positionDao()

    // ponytail: single-threaded dispatcher so writes land in submission order — the sleep timer's
    // rewind must not be overtaken by the pause that preceded it. Per-book actors if this ever
    // needs throughput, which for one listener it never will.
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun record(
        bookId: Int,
        trackId: Int,
        positionMs: Long,
        state: String,
    ) {
        scope.launch {
            // One primary-key read, local and never network, so a finish the outbox hasn't
            // delivered yet isn't wiped by the next 10s tick's upsert.
            val previous = dao.getPosition(bookId)
            dao.upsert(
                PlaybackPosition(
                    bookId = bookId,
                    trackId = trackId,
                    positionMs = positionMs,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncedToPlex = false,
                    finishedPending = previous?.finishedPending == true,
                ),
            )
            // Everything below runs only after the position itself is on disk.
            if (state != ProgressSyncWorker.STATE_PLAYING) {
                markFinishedIfJustCrossed(bookId, trackId, positionMs, previous)
            }
            ProgressSyncWorker.enqueue(appContext, state)
        }
    }

    /**
     * Chronicle's rule: playback stopping within [BOOK_FINISHED_END_OFFSET_MS] of the end means the
     * book is done, and we say so explicitly because the doubled duration we report keeps Plex from
     * ever deciding it itself (CLAUDE.md gotcha #2).
     *
     * Only the *crossing* into that window sets the flag, so pausing five more times on the outro
     * doesn't re-scrobble. Listening to the ending again after seeking back out legitimately does.
     */
    private suspend fun markFinishedIfJustCrossed(
        bookId: Int,
        trackId: Int,
        positionMs: Long,
        previous: PlaybackPosition?,
    ) {
        val bookDurationMs = db.bookDao().getBook(bookId).first()?.durationMs ?: return
        val tracks = db.trackDao().getTracksForBook(bookId).first()
        if (!isBookFinished(tracks, bookDurationMs, trackId, positionMs)) return
        val wasAlreadyThere =
            previous != null &&
                isBookFinished(tracks, bookDurationMs, previous.trackId, previous.positionMs)
        if (wasAlreadyThere) return
        dao.markFinishedPending(bookId)
    }
}
