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

    /**
     * Local-only title/author search. Takes a ready-made pattern from [likePattern] rather than the
     * raw query, so a typed `%` or `_` doesn't act as a wildcard. SQLite's LIKE is case-insensitive
     * for ASCII by default.
     */
    @Query(
        """
        SELECT * FROM books
        WHERE title LIKE :pattern ESCAPE '\' OR author LIKE :pattern ESCAPE '\'
        """,
    )
    fun search(pattern: String): Flow<List<Audiobook>>

    /** Book-level download flag: true once every track is downloaded, false on remove. */
    @Query("UPDATE books SET isCached = :cached WHERE id = :bookId")
    suspend fun setCached(
        bookId: Int,
        cached: Boolean,
    )

    @Upsert
    suspend fun upsertAll(books: List<Audiobook>)
}

/**
 * Wraps [query] in `%…%` for [BookDao.search], escaping LIKE's own metacharacters so they match
 * themselves. Backslash goes first — escaping it after `%`/`_` would double their escapes.
 */
fun likePattern(query: String): String =
    "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
