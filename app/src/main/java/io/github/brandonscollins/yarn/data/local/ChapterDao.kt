package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    /** Rough store order; the UI re-sorts by book-level absolute start after merging with tracks. */
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY trackId, `index`")
    fun getChaptersForBook(bookId: Int): Flow<List<Chapter>>

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Int)

    @Upsert
    suspend fun upsertAll(chapters: List<Chapter>)
}
