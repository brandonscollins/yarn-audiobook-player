package io.github.brandonscollins.yarn.player

import android.content.Context
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.work.ProgressSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    private val dao = PlexGraph.db(appContext).positionDao()

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
            dao.upsert(
                PlaybackPosition(
                    bookId = bookId,
                    trackId = trackId,
                    positionMs = positionMs,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncedToPlex = false,
                ),
            )
            ProgressSyncWorker.enqueue(appContext, state)
        }
    }
}
