package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The position ledger — see CLAUDE.md "one invariant". Local Room write happens before
 * anything else on every pause/interruption/track-change/focus-loss. [syncedToPlex] tracks
 * whether the WorkManager outbox has pushed this position to the server yet.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPosition(
    @PrimaryKey val bookId: Int,
    val trackId: Int,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val syncedToPlex: Boolean = false,
    /**
     * "This book finished and Plex hasn't been told yet." Lives on the row rather than in
     * WorkManager input data so the signal survives `ExistingWorkPolicy.REPLACE` dropping a
     * pending job; the outbox clears it once `/:/scrobble` is accepted.
     */
    val finishedPending: Boolean = false,
    /**
     * "This book is done" — durable, unlike [finishedPending], which the outbox clears the moment
     * Plex accepts the scrobble. The UI reads this one; without it the app forgets a book is
     * finished as soon as it successfully says so.
     */
    val finished: Boolean = false,
    /**
     * "Mark as unplayed" tombstone, not yet told to Plex. Unlike [finishedPending] this row has no
     * durable counterpart to survive alongside — once `/:/unscrobble` is accepted the outbox
     * deletes the row outright, since "unplayed" for this app is defined as no row at all
     * ([io.github.brandonscollins.yarn.ui.library.matchesFilter], [isStartedRow]).
     */
    val unplayedPending: Boolean = false,
)

/**
 * "Started" means a real ledger row — a mark-unplayed tombstone ([PlaybackPosition.unplayedPending])
 * is still a row in the table (so the outbox has something to drain) but must read everywhere else
 * as if the book had never been opened, until the drain deletes it for real.
 */
fun isStartedRow(position: PlaybackPosition): Boolean = !position.unplayedPending
