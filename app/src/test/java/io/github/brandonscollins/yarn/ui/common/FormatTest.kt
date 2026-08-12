package io.github.brandonscollins.yarn.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `remaining rounds down and drops a zero minutes part`() {
        assertEquals("4h 20m left", formatRemaining(4 * 3_600_000L + 20 * 60_000L + 59_000L))
        assertEquals("45m left", formatRemaining(45 * 60_000L + 59_000L))
        assertEquals("1m left", formatRemaining(119_000L))
        assertEquals("4h left", formatRemaining(4 * 3_600_000L))
    }

    @Test
    fun `remaining collapses the last minute`() {
        assertEquals("under a minute left", formatRemaining(59_000L))
        assertEquals("under a minute left", formatRemaining(0L))
        assertEquals("under a minute left", formatRemaining(-5_000L))
    }

    @Test
    fun `strips leading The`() {
        assertEquals("Hobbit", sortTitle("The Hobbit"))
    }

    @Test
    fun `strips leading A or An`() {
        assertEquals("Wrinkle in Time", sortTitle("A Wrinkle in Time"))
        assertEquals("Anthropologist on Mars", sortTitle("An Anthropologist on Mars"))
    }

    @Test
    fun `leaves titles without a leading article unchanged`() {
        assertEquals("Dune", sortTitle("Dune"))
        assertEquals("Anthology", sortTitle("Anthology"))
    }
}
