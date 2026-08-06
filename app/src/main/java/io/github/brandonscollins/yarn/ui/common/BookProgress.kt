package io.github.brandonscollins.yarn.ui.common

import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.player.absolutePositionMs
import kotlinx.coroutines.flow.first

/**
 * Book-level progress fraction for a ledger row, or null when we can't compute one (tracks not
 * cached yet for this book — only happens for a book that's never been opened, which also means
 * it can't have a ledger row, so this is a defensive null rather than a real case).
 */
suspend fun bookProgress(
    db: YarnDatabase,
    book: Audiobook,
    position: PlaybackPosition,
): Float? {
    if (book.durationMs <= 0) return null
    val tracks = db.trackDao().getTracksForBook(book.id).first()
    val trackIndex = tracks.indexOfFirst { it.id == position.trackId }
    if (trackIndex < 0) return null
    return (absolutePositionMs(tracks, trackIndex, position.positionMs).toFloat() / book.durationMs)
        .coerceIn(0f, 1f)
}
