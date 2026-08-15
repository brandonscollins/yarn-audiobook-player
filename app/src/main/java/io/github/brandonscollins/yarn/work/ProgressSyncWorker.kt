package io.github.brandonscollins.yarn.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.data.plex.mediaItemUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Drains the position ledger to Plex. Best-effort with retry — local Room rows are the truth
 * (CLAUDE.md "the one invariant"), so a row is marked synced only after the server accepts it.
 */
class ProgressSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = PlexGraph.prefs(applicationContext)
        if (prefs.accountToken.isEmpty() || prefs.serverId.isEmpty()) return Result.failure()
        if (!PlexGraph.connections(applicationContext).ensureConnected()) return Result.retry()

        val api = PlexGraph.api(applicationContext)
        val db = PlexGraph.db(applicationContext)
        val state = inputData.getString(KEY_STATE) ?: STATE_PAUSED

        // Timeline updates silently no-op unless a playQueue was opened for the book first
        // (CLAUDE.md gotcha #1). Once per book per run; a redundant POST is harmless.
        val queueStarted = mutableSetOf<Int>()

        return try {
            for (position in db.positionDao().getUnsynced()) {
                if (position.unplayedPending) {
                    // A "mark as unplayed" tombstone. Plex cascades unscrobble from the album to
                    // its tracks (PlexMediaService.unscrobble doc), so the book's own ratingKey is
                    // the whole call — no startPlayQueue, no per-track progress(), and skipping
                    // both is what keeps this row from ever reporting a position (gotcha #1/#2
                    // don't apply to a call that never touches /:/timeline). On success the row is
                    // gone outright: "unplayed" here is the absence of a ledger row, not a zeroed
                    // one (data/model/PlaybackPosition.kt isStartedRow).
                    api.mediaService.unscrobble(position.bookId.toString())
                    db.positionDao().clearIfUnchanged(position.bookId, position.updatedAtEpochMs)
                    continue
                }
                val track =
                    db.trackDao().getTracksForBook(position.bookId).first()
                        .firstOrNull { it.id == position.trackId } ?: continue
                if (queueStarted.add(position.bookId)) {
                    api.mediaService.startPlayQueue(mediaItemUri(prefs.serverId, position.bookId))
                }
                api.mediaService.progress(
                    ratingKey = position.trackId.toString(),
                    key = "/library/metadata/${position.trackId}",
                    timeMs = position.positionMs,
                    // Doubled duration keeps Plex's 90%-is-finished rule from ever tripping
                    // (CLAUDE.md gotcha #2); finished is reported explicitly via scrobble.
                    duration = track.durationMs * 2,
                    playState = state,
                    playbackTime = position.positionMs,
                )
                // The doubled duration above means Plex will never mark this finished on its own,
                // so the ledger's explicit verdict is the only thing that ever does (gotcha #2).
                if (position.finishedPending) {
                    api.mediaService.scrobble(position.bookId.toString())
                }
                db.positionDao().markSynced(position.bookId, position.updatedAtEpochMs)
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_STATE = "state"
        const val STATE_PLAYING = "playing"
        const val STATE_PAUSED = "paused"
        const val STATE_STOPPED = "stopped"
        private const val UNIQUE_WORK = "progress_sync"

        /**
         * Unique work with REPLACE debounces the ledger's ~10s tick into a single pending job — the
         * worker drains every unsynced row anyway, and the finished verdict rides on the row rather
         * than in this input data, so a replaced job loses nothing.
         */
        fun enqueue(
            context: Context,
            state: String = STATE_PAUSED,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(workDataOf(KEY_STATE to state))
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
