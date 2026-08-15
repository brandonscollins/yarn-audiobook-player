package io.github.brandonscollins.yarn.player

import io.github.brandonscollins.yarn.data.model.Chapter
import io.github.brandonscollins.yarn.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TEN_MIN = 600_000L

class ChaptersTest {
    private fun tracks(count: Int) =
        List(count) { i ->
            Track(
                id = 100 + i,
                bookId = 1,
                title = "Track $i",
                index = i + 1,
                durationMs = TEN_MIN,
                partKey = "/library/parts/$i/file.mp3",
                sizeBytes = 0,
                viewOffsetMs = 0,
            )
        }

    private fun chapter(
        trackId: Int,
        index: Int,
        startMs: Long,
        title: String = "Ch $trackId.$index",
    ) = Chapter(trackId = trackId, bookId = 1, index = index, title = title, startMs = startMs)

    @Test
    fun `chapter offsets are per-track start plus the cumulative base of prior tracks`() {
        val result =
            bookChapters(
                tracks(3),
                listOf(
                    chapter(trackId = 100, index = 0, startMs = 0),
                    chapter(trackId = 100, index = 1, startMs = 300_000),
                    chapter(trackId = 102, index = 0, startMs = 60_000),
                ),
            )

        assertEquals(listOf(0L, 300_000L, 2 * TEN_MIN + 60_000L), result.map { it.startMs })
    }

    @Test
    fun `tracks without chapter rows still shift later tracks' chapters by their duration`() {
        // Chapters on track 1 only — track 0's full duration is still the base.
        val result = bookChapters(tracks(2), listOf(chapter(trackId = 101, index = 0, startMs = 5_000)))

        assertEquals(listOf(TEN_MIN + 5_000L), result.map { it.startMs })
    }

    @Test
    fun `a chapter whose track is unknown is dropped, not guessed`() {
        val result =
            bookChapters(
                tracks(1),
                listOf(
                    chapter(trackId = 100, index = 0, startMs = 1_000),
                    chapter(trackId = 999, index = 0, startMs = 2_000),
                ),
            )

        assertEquals(listOf(1_000L), result.map { it.startMs })
    }

    @Test
    fun `result is sorted by absolute start regardless of input order`() {
        val result =
            bookChapters(
                tracks(2),
                listOf(
                    chapter(trackId = 101, index = 0, startMs = 0),
                    chapter(trackId = 100, index = 0, startMs = 0),
                ),
            )

        assertEquals(listOf(0L, TEN_MIN), result.map { it.startMs })
    }

    @Test
    fun `no chapters means empty, so the UI falls back to track boundaries`() {
        assertEquals(emptyList<BookChapter>(), bookChapters(tracks(2), emptyList()))
    }

    @Test
    fun `next chapter is the first start ahead of the playhead, and null in the last one`() {
        val starts = listOf(0L, TEN_MIN, 2 * TEN_MIN)

        assertEquals(TEN_MIN, nextChapterStart(starts, 0))
        assertEquals(TEN_MIN, nextChapterStart(starts, TEN_MIN - 1))
        assertEquals(2 * TEN_MIN, nextChapterStart(starts, TEN_MIN))
        assertEquals(null, nextChapterStart(starts, 2 * TEN_MIN + 5_000))
    }

    @Test
    fun `previous restarts the chapter past the grace period and steps back inside it`() {
        val starts = listOf(0L, TEN_MIN, 2 * TEN_MIN)

        assertEquals(TEN_MIN, previousChapterStart(starts, TEN_MIN + CHAPTER_RESTART_GRACE_MS + 1))
        assertEquals(0L, previousChapterStart(starts, TEN_MIN + CHAPTER_RESTART_GRACE_MS))
        assertEquals(0L, previousChapterStart(starts, TEN_MIN))
    }

    @Test
    fun `previous is null at the very start, and before a first chapter that isn't at zero`() {
        assertEquals(null, previousChapterStart(listOf(0L, TEN_MIN), 0))
        assertEquals(null, previousChapterStart(listOf(0L, TEN_MIN), CHAPTER_RESTART_GRACE_MS))
        assertEquals(null, previousChapterStart(listOf(5_000L, TEN_MIN), 1_000))
        assertEquals(null, previousChapterStart(emptyList(), 1_000))
    }
}
