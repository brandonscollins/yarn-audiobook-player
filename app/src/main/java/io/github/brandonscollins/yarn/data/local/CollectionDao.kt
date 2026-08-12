package io.github.brandonscollins.yarn.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import io.github.brandonscollins.yarn.data.model.Collection
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY title")
    fun getAllCollections(): Flow<List<Collection>>

    @Upsert
    suspend fun upsertAll(collections: List<Collection>)

    /** Every cross-ref of every collection [bookId] sits in — raw material for [nextInCollection]. */
    @Query(
        """
        SELECT * FROM book_collection_cross_ref
        WHERE collectionId IN (
            SELECT collectionId FROM book_collection_cross_ref WHERE bookId = :bookId
        )
        """,
    )
    fun getCollectionPeers(bookId: Int): Flow<List<BookCollectionCrossRef>>

    @Upsert
    suspend fun upsertCrossRefs(crossRefs: List<BookCollectionCrossRef>)
}

/**
 * Home's "Up next": the book after [bookId] in a collection they share, skipping anything with a
 * ledger row. Null — no section — is the normal answer for a library whose collections aren't
 * series, and for the last book of one.
 *
 * ponytail: a book in several collections answers from whichever [peers] lists first, and equal
 * ordinals (a cache that predates the ordinal column) never match `>` so they answer nothing. Both
 * are fine for one reader; a collection picker in Settings is the upgrade.
 */
fun nextInCollection(
    bookId: Int,
    peers: List<BookCollectionCrossRef>,
    startedBookIds: Set<Int>,
): Int? =
    peers.groupBy { it.collectionId }.values.firstNotNullOfOrNull { rows ->
        val here = rows.firstOrNull { it.bookId == bookId } ?: return@firstNotNullOfOrNull null
        rows.filter { it.ordinal > here.ordinal && it.bookId !in startedBookIds }
            .minByOrNull { it.ordinal }
            ?.bookId
    }
