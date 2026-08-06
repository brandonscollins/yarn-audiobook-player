package io.github.brandonscollins.yarn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false,
)
abstract class YarnDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun trackDao(): TrackDao

    abstract fun collectionDao(): CollectionDao

    abstract fun positionDao(): PositionDao
}

/**
 * Adds `finishedPending`. A real migration rather than a destructive fallback: the table it touches
 * is the position ledger, and losing it is the one thing this app must never do.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE playback_positions " +
                    "ADD COLUMN finishedPending INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
