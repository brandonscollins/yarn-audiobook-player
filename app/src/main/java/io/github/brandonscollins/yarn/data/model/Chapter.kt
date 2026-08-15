package io.github.brandonscollins.yarn.data.model

import androidx.room.Entity

/**
 * An embedded chapter inside one track's file, as Plex reports it (`includeChapters=1`).
 * [index] is the chapter's position within its track, not Plex's own index attribute —
 * Plex's can be absent, and the primary key must not collide.
 */
@Entity(tableName = "chapters", primaryKeys = ["trackId", "index"])
data class Chapter(
    val trackId: Int,
    val bookId: Int,
    val index: Int,
    val title: String,
    /** Start offset in ms within its own track's file — not the book. */
    val startMs: Long,
)
