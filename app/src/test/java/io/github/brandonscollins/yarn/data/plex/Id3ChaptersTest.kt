package io.github.brandonscollins.yarn.data.plex

import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ChapterFrame] and [TextInformationFrame] are plain classes (no Robolectric needed) — this
 * exercises [id3ChaptersFromFrames] with the real media3 types, not a stand-in.
 */
class Id3ChaptersTest {
    private fun chapterFrame(
        startMs: Int,
        title: String? = null,
    ): ChapterFrame {
        val subFrames =
            if (title != null) {
                arrayOf<Id3Frame>(TextInformationFrame("TIT2", "", title))
            } else {
                emptyArray()
            }
        return ChapterFrame("chp$startMs", startMs, startMs + 1, -1L, -1L, subFrames)
    }

    @Test
    fun `TIT2 sub-frame supplies the title when present`() {
        val result =
            id3ChaptersFromFrames(
                bookId = 1,
                perTrack =
                    listOf(
                        TrackChapterFrames(
                            trackId = 100,
                            frames = listOf(chapterFrame(0, "Prologue"), chapterFrame(60_000, "Chapter One")),
                        ),
                    ),
            )

        assertEquals(listOf("Prologue", "Chapter One"), result.map { it.title })
    }

    @Test
    fun `missing TIT2 falls back to a book-global Chapter N counter`() {
        val result =
            id3ChaptersFromFrames(
                bookId = 1,
                perTrack =
                    listOf(
                        TrackChapterFrames(100, listOf(chapterFrame(0), chapterFrame(60_000, "Named"))),
                        TrackChapterFrames(101, listOf(chapterFrame(0))),
                    ),
            )

        // The counter advances once per chapter regardless of title, same as the Plex path.
        assertEquals(listOf("Chapter 1", "Named", "Chapter 3"), result.map { it.title })
    }

    @Test
    fun `frames are sorted by start time even if the file didn't encode them in order`() {
        val result =
            id3ChaptersFromFrames(
                bookId = 1,
                perTrack =
                    listOf(
                        TrackChapterFrames(100, listOf(chapterFrame(60_000, "Second"), chapterFrame(0, "First"))),
                    ),
            )

        assertEquals(listOf("First", "Second"), result.map { it.title })
        assertEquals(listOf(0L, 60_000L), result.map { it.startMs })
        assertEquals(listOf(0, 1), result.map { it.index })
    }

    @Test
    fun `fewer than two chapters across the whole book counts as none`() {
        val single =
            id3ChaptersFromFrames(
                bookId = 1,
                perTrack = listOf(TrackChapterFrames(100, listOf(chapterFrame(0, "Only one")))),
            )
        val none =
            id3ChaptersFromFrames(bookId = 1, perTrack = listOf(TrackChapterFrames(100, emptyList())))

        assertEquals(emptyList<Any>(), single)
        assertEquals(emptyList<Any>(), none)
    }

    @Test
    fun `a track with no frames doesn't block chapters from its siblings`() {
        val result =
            id3ChaptersFromFrames(
                bookId = 1,
                perTrack =
                    listOf(
                        TrackChapterFrames(100, emptyList()),
                        TrackChapterFrames(101, listOf(chapterFrame(0, "A"), chapterFrame(60_000, "B"))),
                    ),
            )

        assertEquals(listOf(101, 101), result.map { it.trackId })
        assertEquals(listOf("A", "B"), result.map { it.title })
    }
}
