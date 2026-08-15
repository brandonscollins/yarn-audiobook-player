package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A chapter/file within a book (Plex track, type=10). [id] is the Plex ratingKey. */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Int,
    val bookId: Int,
    val title: String,
    val index: Int,
    val durationMs: Long,
    /** The `/library/parts/...` path used to stream or download this file. */
    val partKey: String,
    val sizeBytes: Long,
    val viewOffsetMs: Long,
    val isCached: Boolean = false,
    /** MediaStore `content://` URI of the downloaded copy; set together with [isCached]. */
    val localUri: String? = null,
)
