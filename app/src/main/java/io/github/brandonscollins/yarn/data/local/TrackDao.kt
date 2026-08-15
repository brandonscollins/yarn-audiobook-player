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

    @Query("UPDATE tracks SET isCached = 1, localUri = :localUri WHERE id = :trackId")
    suspend fun markDownloaded(
        trackId: Int,
        localUri: String,
    )

    /** One track's local copy is gone (deleted behind our back) — back to streaming. */
    @Query("UPDATE tracks SET isCached = 0, localUri = NULL WHERE id = :trackId")
    suspend fun clearDownload(trackId: Int)

    @Query("UPDATE tracks SET isCached = 0, localUri = NULL WHERE bookId = :bookId")
    suspend fun clearDownloadsForBook(bookId: Int)

    @Upsert
    suspend fun upsertAll(tracks: List<Track>)
}
