package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE bookId = :bookId ORDER BY `index`")
    fun getTracksForBook(bookId: Int): Flow<List<Track>>

    @Upsert
    suspend fun upsertAll(tracks: List<Track>)
}
