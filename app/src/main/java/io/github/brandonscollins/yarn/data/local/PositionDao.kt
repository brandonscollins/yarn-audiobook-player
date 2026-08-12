package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
    /** Synchronous local write — the position ledger. Called before anything else. */
    @Upsert
    suspend fun upsert(position: PlaybackPosition)

    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    suspend fun getPosition(bookId: Int): PlaybackPosition?

    @Query("SELECT * FROM playback_positions WHERE syncedToPlex = 0")
    suspend fun getUnsynced(): List<PlaybackPosition>

    /**
     * The one finish path — auto-finish and a manual "mark finished" both land here. Sets the
     * durable flag and, in the same statement, the outbox flag that gets Plex told via
     * `/:/scrobble`; clearing `syncedToPlex` is what makes the outbox look at an already-synced
     * row again. Unmarking cancels an undelivered scrobble rather than leaving it queued.
     */
    @Query(
        """
        UPDATE playback_positions SET finished = :finished, finishedPending = :finished,
            syncedToPlex = 0
        WHERE bookId = :bookId
        """,
    )
    suspend fun setFinished(
        bookId: Int,
        finished: Boolean,
    )

    /** "Mark as unplayed" — the ledger row is the progress, so dropping it is the whole operation. */
    @Query("DELETE FROM playback_positions WHERE bookId = :bookId")
    suspend fun clearPosition(bookId: Int)

    /**
     * Compare-and-set on [PlaybackPosition.updatedAtEpochMs]: a row the ledger has rewritten since
     * the outbox read it is left alone, so a newer position (or a newer finish) is never clobbered.
     * Only the outbox flag clears here — [PlaybackPosition.finished] is durable.
     */
    @Query(
        """
        UPDATE playback_positions SET syncedToPlex = 1, finishedPending = 0
        WHERE bookId = :bookId AND updatedAtEpochMs = :updatedAtEpochMs
        """,
    )
    suspend fun markSynced(
        bookId: Int,
        updatedAtEpochMs: Long,
    )

    /** For Home's "Continue listening" card. */
    @Query("SELECT * FROM playback_positions ORDER BY updatedAtEpochMs DESC LIMIT 1")
    fun getMostRecent(): Flow<PlaybackPosition?>

    /** For Home's "recently played" row and its view-all screen. */
    @Query("SELECT * FROM playback_positions ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<PlaybackPosition>>

    /** For Library grid progress bars. */
    @Query("SELECT * FROM playback_positions")
    fun getAll(): Flow<List<PlaybackPosition>>
}
