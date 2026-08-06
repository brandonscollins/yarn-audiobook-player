package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Plex collection/series grouping books. */
@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey val id: Int,
    val title: String,
)

@Entity(tableName = "book_collection_cross_ref", primaryKeys = ["bookId", "collectionId"])
data class BookCollectionCrossRef(
    val bookId: Int,
    val collectionId: Int,
)
