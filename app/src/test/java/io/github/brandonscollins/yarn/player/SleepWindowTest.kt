package io.github.brandonscollins.yarn.player

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
}
