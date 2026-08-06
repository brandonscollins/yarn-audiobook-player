package io.github.brandonscollins.yarn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import io.github.brandonscollins.yarn.data.model.Collection
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track

@Database(
    entities = [
        Audiobook::class,
        Track::class,
        Collection::class,
        BookCollectionCrossRef::class,
        PlaybackPosition::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class YarnDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun trackDao(): TrackDao

    abstract fun collectionDao(): CollectionDao

    abstract fun positionDao(): PositionDao
}
