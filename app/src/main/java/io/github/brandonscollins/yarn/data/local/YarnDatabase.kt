package io.github.brandonscollins.yarn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import io.github.brandonscollins.yarn.data.model.Chapter
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
        Chapter::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class YarnDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun trackDao(): TrackDao

    abstract fun collectionDao(): CollectionDao

    abstract fun positionDao(): PositionDao

    abstract fun chapterDao(): ChapterDao
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

/** Adds `publishedAtEpochMs` (0 = unknown) for the "recently published" sort. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE books ADD COLUMN publishedAtEpochMs INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

/** Adds the durable `finished` flag. Real migration for the same reason as [MIGRATION_1_2]. */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE playback_positions ADD COLUMN finished INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

/**
 * Adds `ordinal` for collection order. Existing rows default to 0, which reads as "no order known"
 * and simply suppresses "Up next" until the next library sync rewrites them.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE book_collection_cross_ref ADD COLUMN ordinal INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

/** Adds `localUri` (MediaStore URI of a downloaded track); NULL means not downloaded. */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN localUri TEXT")
        }
    }

/** Adds the `chapters` cache — embedded chapters as Plex reports them, fetched lazily per book. */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chapters` (" +
                    "`trackId` INTEGER NOT NULL, `bookId` INTEGER NOT NULL, " +
                    "`index` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`startMs` INTEGER NOT NULL, PRIMARY KEY(`trackId`, `index`))",
            )
        }
    }

/**
 * Adds `unplayedPending`, mirroring [MIGRATION_1_2]'s `finishedPending` — "mark as unplayed" now
 * writes a tombstone row drained by the outbox via `/:/unscrobble` instead of a best-effort sweep.
 */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE playback_positions " +
                    "ADD COLUMN unplayedPending INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
