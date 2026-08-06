package io.github.brandonscollins.yarn.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
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
