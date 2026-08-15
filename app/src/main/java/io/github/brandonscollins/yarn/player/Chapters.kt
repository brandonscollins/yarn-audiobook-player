package io.github.brandonscollins.yarn.player

import io.github.brandonscollins.yarn.data.model.Chapter
import io.github.brandonscollins.yarn.data.model.Track

/** A chapter resolved to a book-level absolute start — what the ticks and the sheet consume. */
data class BookChapter(
    val title: String,
    val startMs: Long,
)

/**
 * Chapters at book-level absolute offsets: each track's cumulative base (sum of prior track
 * durations) plus the chapter's offset within that track's file. Works when only some tracks of
 * a multi-file book carry chapters; a chapter whose track isn't in [tracks] is dropped rather
 * than guessed. Empty in, empty out — the UI falls back to track boundaries.
 */
fun bookChapters(
    tracks: List<Track>,
    chapters: List<Chapter>,
): List<BookChapter> {
    if (chapters.isEmpty()) return emptyList()
    val bases = mutableMapOf<Int, Long>()
    var acc = 0L
    tracks.forEach { track ->
        bases[track.id] = acc
        acc += track.durationMs
    }
    return chapters
        .mapNotNull { chapter ->
            bases[chapter.trackId]?.let { BookChapter(chapter.title, it + chapter.startMs) }
        }
        .sortedBy { it.startMs }
}

/** Inside this much of a chapter, "previous" means the one before it rather than a restart. */
const val CHAPTER_RESTART_GRACE_MS = 3_000L

/**
 * Where the next-chapter button lands, or null when there is no chapter ahead — the last chapter,
 * where the button is disabled rather than silently doing nothing. [starts] is ascending and
 * book-absolute: either the embedded chapters or, for a multi-file book without them, its tracks.
 */
fun nextChapterStart(
    starts: List<Long>,
    absoluteMs: Long,
): Long? = starts.firstOrNull { it > absoluteMs }

/**
 * Where the previous-chapter button lands. Past [CHAPTER_RESTART_GRACE_MS] into a chapter it
 * restarts that chapter (the audiobook convention, and the only way back to a chapter's opening
 * line); inside the grace it steps back one. Null at the start of the first chapter, and before it
 * on a book whose first chapter doesn't begin at zero.
 */
fun previousChapterStart(
    starts: List<Long>,
    absoluteMs: Long,
): Long? {
    val current = starts.lastOrNull { it <= absoluteMs } ?: return null
    if (absoluteMs - current > CHAPTER_RESTART_GRACE_MS) return current
    return starts.lastOrNull { it < current }
}
