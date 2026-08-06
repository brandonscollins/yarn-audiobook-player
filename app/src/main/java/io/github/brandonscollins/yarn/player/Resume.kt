package io.github.brandonscollins.yarn.player

import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track

/** Where playback should start: an index into the book's track list plus an offset in that track. */
data class ResumePoint(
    val trackIndex: Int,
    val positionMs: Long,
)

/** Book-level absolute position: every prior track's duration plus the offset within this one. */
fun absolutePositionMs(
    tracks: List<Track>,
    trackIndex: Int,
    positionMs: Long,
): Long = tracks.take(trackIndex).sumOf { it.durationMs } + positionMs

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
