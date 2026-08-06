package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.PlaybackPosition

@Dao
interface PositionDao {
    /** Synchronous local write — the position ledger. Called before anything else. */
    @Upsert
    suspend fun upsert(position: PlaybackPosition)

    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    suspend fun getPosition(bookId: Int): PlaybackPosition?

    @Query("SELECT * FROM playback_positions WHERE syncedToPlex = 0")
    suspend fun getUnsynced(): List<PlaybackPosition>
}
