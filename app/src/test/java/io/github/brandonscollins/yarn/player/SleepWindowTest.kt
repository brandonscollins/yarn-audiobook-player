package io.github.brandonscollins.yarn.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWindowTest {
    private val nightStart = 21 * 60 + 30 // 21:30
    private val nightEnd = 6 * 60 // 06:00

    @Test
    fun `midnight-crossing window covers both sides of midnight`() {
        assertTrue(isInWindow(22 * 60, nightStart, nightEnd))
        assertTrue(isInWindow(0, nightStart, nightEnd))
        assertTrue(isInWindow(3 * 60, nightStart, nightEnd))
        assertFalse(isInWindow(12 * 60, nightStart, nightEnd))
        assertFalse(isInWindow(21 * 60 + 29, nightStart, nightEnd))
    }

    @Test
    fun `start is inclusive and end is exclusive across midnight`() {
        assertTrue(isInWindow(nightStart, nightStart, nightEnd))
        assertFalse(isInWindow(nightEnd, nightStart, nightEnd))
        assertTrue(isInWindow(nightEnd - 1, nightStart, nightEnd))
    }

    @Test
    fun `same-day window behaves the same way`() {
        val start = 13 * 60
        val end = 14 * 60
        assertTrue(isInWindow(start, start, end))
        assertTrue(isInWindow(end - 1, start, end))
        assertFalse(isInWindow(end, start, end))
        assertFalse(isInWindow(start - 1, start, end))
        assertFalse(isInWindow(0, start, end))
    }

    @Test
    fun `a zero-width window is never open`() {
        assertFalse(isInWindow(9 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun `end-of-chapter counts down in wall-clock time, not media time`() {
        // Ten minutes of audio left, but at 2x that is five minutes of listening.
        assertEquals(600_000L, chapterRemainingMs(1_200_000, 600_000, 1f))
        assertEquals(300_000L, chapterRemainingMs(1_200_000, 600_000, 2f))
    }

    @Test
    fun `the fade starts inside the last fade-length of the chapter`() {
        val duration = 1_200_000L
        assertTrue(chapterRemainingMs(duration, duration - FADE_MS, 1f) <= FADE_MS)
        assertFalse(chapterRemainingMs(duration, duration - FADE_MS - 1_000, 1f) <= FADE_MS)
        // Past the end (a track that overran its metadata) fades immediately rather than never.
        assertEquals(0L, chapterRemainingMs(duration, duration + 5_000, 1f))
    }

    @Test
    fun `an unknown chapter length never triggers the fade`() {
        // media3 reports TIME_UNSET until the item is prepared; stopping there would cut a chapter
        // in half, so the timer waits instead.
        assertEquals(Long.MAX_VALUE, chapterRemainingMs(C.TIME_UNSET, 0, 1f))
        assertEquals(Long.MAX_VALUE, chapterRemainingMs(0, 0, 1f))
    }
}
