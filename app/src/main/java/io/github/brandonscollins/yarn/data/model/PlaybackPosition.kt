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
)
