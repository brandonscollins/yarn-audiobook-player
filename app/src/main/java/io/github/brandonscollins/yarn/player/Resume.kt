package io.github.brandonscollins.yarn.player

import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import kotlin.time.Duration.Companion.minutes

/** Where playback should start: an index into the book's track list plus an offset in that track. */
data class ResumePoint(
    val trackIndex: Int,
    val positionMs: Long,
)

/**
 * How close to the end counts as finished. Chronicle's `ProgressUpdater`
 * `BOOK_FINISHED_END_OFFSET_MILLIS` — two minutes, which absorbs the credits and outro that make
 * Plex's own 90% rule useless for audiobooks (CLAUDE.md gotcha #2).
 */
val BOOK_FINISHED_END_OFFSET_MS = 2.minutes.inWholeMilliseconds

/** Book-level absolute position: every prior track's duration plus the offset within this one. */
fun absolutePositionMs(
    tracks: List<Track>,
    trackIndex: Int,
    positionMs: Long,
): Long = tracks.take(trackIndex).sumOf { it.durationMs } + positionMs

/**
 * Inverse of [absolutePositionMs] — which track a book-level position lands in. Used by the
 * book-level scrub bar and by rewind-on-resume; both clamp to the book rather than running off
 * either end. An empty track list keeps the position where it is, which is what the Player screen
 * wants during the beat before Room has emitted.
 */
fun resumePointAt(
    tracks: List<Track>,
    absoluteMs: Long,
): ResumePoint {
    var left = absoluteMs.coerceAtLeast(0)
    tracks.forEachIndexed { index, track ->
        if (left < track.durationMs || index == tracks.lastIndex) {
            return ResumePoint(index, left.coerceAtMost(track.durationMs))
        }
        left -= track.durationMs
    }
    return ResumePoint(0, left)
}

/** Rewind-on-resume modes, stored in [PlayerPrefs.rewindMode]. */
const val REWIND_OFF = 0
const val REWIND_FIXED = 1
const val REWIND_SMART = 2

/** A pause this short is a stumble, not a break — nothing to re-hear, whatever the mode. */
private const val REWIND_DEADBAND_MS = 10_000L

/** Smart mode's ceiling. The PRD's "overnight → full minute". */
private const val MAX_SMART_REWIND_MS = 60_000L

/**
 * How far back to jump when playback resumes after [pausedForMs] of silence (PRD P1). Smart mode is
 * a tenth of the pause — a minute away costs six seconds, ten minutes or more costs the cap — which
 * is the cheapest curve that gets both ends of the PRD's range right. Callers clamp the result to
 * the start of the book.
 */
fun rewindOnResumeMs(
    mode: Int,
    pausedForMs: Long,
    fixedMs: Long,
): Long =
    when {
        pausedForMs <= REWIND_DEADBAND_MS -> 0
        mode == REWIND_FIXED -> fixedMs.coerceAtLeast(0)
        mode == REWIND_SMART -> (pausedForMs / 10).coerceAtMost(MAX_SMART_REWIND_MS)
        else -> 0
    }

/**
 * Is this ledger position inside the book's finished window? False when we can't tell (unknown
 * track, no duration), because guessing wrong here marks an unread book as read on the server.
 */
fun isBookFinished(
    tracks: List<Track>,
    bookDurationMs: Long,
    trackId: Int,
    positionMs: Long,
): Boolean {
    if (bookDurationMs <= 0) return false
    val trackIndex = tracks.indexOfFirst { it.id == trackId }
    if (trackIndex < 0) return false
    val remaining = bookDurationMs - absolutePositionMs(tracks, trackIndex, positionMs)
    return remaining <= BOOK_FINISHED_END_OFFSET_MS
}

/**
 * Furthest-ahead wins (CLAUDE.md "the one invariant"). Compares the local ledger row against the
 * Plex `viewOffset`s already synced onto [tracks], both converted to book-level absolute positions.
 * Ties go to local, which is the source of truth. [tracks] must be in index order.
 */
fun resumePoint(
    tracks: List<Track>,
    ledger: PlaybackPosition?,
): ResumePoint {
    if (tracks.isEmpty()) return ResumePoint(0, 0)

    val local =
        ledger?.let { row ->
            tracks.indexOfFirst { it.id == row.trackId }
                .takeIf { it >= 0 }
                ?.let { ResumePoint(it, row.positionMs) }
        }
    val plex =
        tracks.withIndex()
            .filter { it.value.viewOffsetMs > 0 }
            .maxByOrNull { absolutePositionMs(tracks, it.index, it.value.viewOffsetMs) }
            ?.let { ResumePoint(it.index, it.value.viewOffsetMs) }

    return listOfNotNull(local, plex)
        .maxByOrNull { absolutePositionMs(tracks, it.trackIndex, it.positionMs) }
        ?: ResumePoint(0, 0)
}
