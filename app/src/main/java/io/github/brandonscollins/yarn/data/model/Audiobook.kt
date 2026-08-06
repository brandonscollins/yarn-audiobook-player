package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A book (Plex album, type=9). [id] is the Plex ratingKey. */
@Entity(tableName = "books")
data class Audiobook(
    @PrimaryKey val id: Int,
    val title: String,
    val author: String,
    val thumbPath: String,
    val durationMs: Long,
    val addedAt: Long,
    val lastViewedAt: Long,
    val viewCount: Int,
    val isCached: Boolean = false,
    /** Plex `originallyAvailableAt` (release date), falling back to `year`. 0 = unknown. */
    val publishedAtEpochMs: Long = 0,
)
