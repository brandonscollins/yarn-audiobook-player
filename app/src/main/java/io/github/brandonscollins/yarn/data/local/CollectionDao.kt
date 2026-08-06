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

    @Upsert
    suspend fun upsertCrossRefs(crossRefs: List<BookCollectionCrossRef>)
}
