package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.Audiobook
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title")
    fun getAllBooks(): Flow<List<Audiobook>>

    @Query(
        """
        SELECT books.* FROM books
        INNER JOIN book_collection_cross_ref ON books.id = book_collection_cross_ref.bookId
        WHERE book_collection_cross_ref.collectionId = :collectionId
        ORDER BY books.title
        """,
    )
    fun getBooksInCollection(collectionId: Int): Flow<List<Audiobook>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBook(bookId: Int): Flow<Audiobook?>

    @Upsert
    suspend fun upsertAll(books: List<Audiobook>)
}
