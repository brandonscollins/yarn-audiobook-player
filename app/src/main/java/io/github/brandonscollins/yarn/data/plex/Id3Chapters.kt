package io.github.brandonscollins.yarn.data.plex

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.brandonscollins.yarn.data.model.Chapter
import io.github.brandonscollins.yarn.data.model.Track
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Generous but bounded — a dead stream must not hang book-open forever. */
private const val ID3_PER_TRACK_TIMEOUT_MS = 30_000L

/**
 * Milestone 5 path 2 (next_steps.md "Chapter compatibility"): when Plex itself has no chapter
 * data for a book, read embedded ID3 `CHAP` frames straight out of the files with media3's
 * [MetadataRetriever]. Only runs when the Plex pass in [LibrarySyncRepo.syncChapters] found zero
 * chapters — Plex chapters always win when present.
 */
internal suspend fun retrieveId3Chapters(
    context: Context,
    bookId: Int,
    tracks: List<Track>,
    uriForTrack: (Track) -> String,
): List<Chapter> {
    val perTrack =
        tracks.map { track ->
            // A track that fails or times out contributes no chapters rather than aborting the book.
            val frames =
                runCatching {
                    withTimeoutOrNull(ID3_PER_TRACK_TIMEOUT_MS) {
                        chapterFramesForUri(context, uriForTrack(track))
                    }.orEmpty()
                }.getOrDefault(emptyList())
            TrackChapterFrames(track.id, frames)
        }
    return id3ChaptersFromFrames(bookId, perTrack)
}

private suspend fun chapterFramesForUri(
    context: Context,
    uri: String,
): List<ChapterFrame> {
    val trackGroups: TrackGroupArray =
        MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(uri)).await()
    val frames = mutableListOf<ChapterFrame>()
    for (g in 0 until trackGroups.length) {
        val group = trackGroups.get(g)
        for (f in 0 until group.length) {
            val metadata = group.getFormat(f).metadata ?: continue
            for (e in 0 until metadata.length()) {
                (metadata.get(e) as? ChapterFrame)?.let(frames::add)
            }
        }
    }
    return frames
}

/** kotlinx-coroutines-guava isn't a dependency (CLAUDE.md: no new deps) — bridge it directly. */
private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            MoreExecutors.directExecutor(),
        )
        cont.invokeOnCancellation { cancel(false) }
    }

/** One track's raw CHAP frames, not yet sorted or numbered. */
internal data class TrackChapterFrames(
    val trackId: Int,
    val frames: List<ChapterFrame>,
)

/**
 * Pure mapping (no media3 I/O) so it's unit-testable on the JVM: sorts each track's frames by
 * start time, numbers "Chapter N" as a book-global counter incrementing per chapter regardless
 * of title (mirroring the Plex path in [LibrarySyncRepo.syncChapters]), and — since a single CHAP
 * frame across the whole book is just "the file starts", not real structure — collapses any
 * result under 2 chapters to empty, same as Plex reporting nothing.
 */
internal fun id3ChaptersFromFrames(
    bookId: Int,
    perTrack: List<TrackChapterFrames>,
): List<Chapter> {
    var n = 0
    val chapters =
        perTrack.flatMap { (trackId, frames) ->
            frames.sortedBy { it.startTimeMs }.mapIndexed { i, frame ->
                n += 1
                Chapter(
                    trackId = trackId,
                    bookId = bookId,
                    index = i,
                    title = frame.tit2Title() ?: "Chapter $n",
                    startMs = frame.startTimeMs.toLong(),
                )
            }
        }
    return if (chapters.size < 2) emptyList() else chapters
}

/** The chapter's title lives in a TIT2 sub-frame; absent or blank falls back to "Chapter N". */
private fun ChapterFrame.tit2Title(): String? {
    for (i in 0 until subFrameCount) {
        val sub = getSubFrame(i)
        if (sub is TextInformationFrame && sub.id == "TIT2") {
            return sub.value.takeIf { it.isNotBlank() }
        }
    }
    return null
}
