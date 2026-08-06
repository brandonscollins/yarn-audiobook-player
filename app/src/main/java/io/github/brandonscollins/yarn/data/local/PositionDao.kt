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

    /** Flags the book as finished-but-not-yet-scrobbled. See [PlaybackPosition.finishedPending]. */
    @Query("UPDATE playback_positions SET finishedPending = 1 WHERE bookId = :bookId")
    suspend fun markFinishedPending(bookId: Int)

    /**
     * Compare-and-set on [PlaybackPosition.updatedAtEpochMs]: a row the ledger has rewritten since
     * the outbox read it is left alone, so a newer position (or a newer finish) is never clobbered.
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

    /** For Library grid progress bars. */
    @Query("SELECT * FROM playback_positions")
    fun getAll(): Flow<List<PlaybackPosition>>
}
