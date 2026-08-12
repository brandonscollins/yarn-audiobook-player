package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Plex collection/series grouping books. */
@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey val id: Int,
    val title: String,
)

/**
 * [ordinal] is the book's slot in the collection as Plex itself returned it — for a series
 * collection that is reading order, which alphabetical title sort gets wrong the moment a series
 * reaches book 10. 0 for every row until the next sync fills them in.
 */
@Entity(tableName = "book_collection_cross_ref", primaryKeys = ["bookId", "collectionId"])
data class BookCollectionCrossRef(
    val bookId: Int,
    val collectionId: Int,
    val ordinal: Int = 0,
)
