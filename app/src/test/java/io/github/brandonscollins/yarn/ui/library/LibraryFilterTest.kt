package io.github.brandonscollins.yarn.ui.library

import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun position(finished: Boolean) =
        PlaybackPosition(
            bookId = 1,
            trackId = 100,
            positionMs = 60_000,
            updatedAtEpochMs = 0,
            finished = finished,
        )

    @Test
    fun `All keeps every book whatever its progress`() {
        assertEquals(true, matchesFilter(FilterMode.All, null))
        assertEquals(true, matchesFilter(FilterMode.All, position(finished = false)))
        assertEquals(true, matchesFilter(FilterMode.All, position(finished = true)))
    }

    @Test
    fun `a book with no ledger row is the only not-started one`() {
        assertEquals(true, matchesFilter(FilterMode.NotStarted, null))
        assertEquals(false, matchesFilter(FilterMode.NotStarted, position(finished = false)))
        assertEquals(false, matchesFilter(FilterMode.NotStarted, position(finished = true)))
    }

    /** The split that needs the durable column: started and finished are both "has a row". */
    @Test
    fun `in progress is started but not finished`() {
        assertEquals(true, matchesFilter(FilterMode.InProgress, position(finished = false)))
        assertEquals(false, matchesFilter(FilterMode.InProgress, position(finished = true)))
        assertEquals(false, matchesFilter(FilterMode.InProgress, null))
    }

    @Test
    fun `finished reads the durable column, not the offset`() {
        assertEquals(true, matchesFilter(FilterMode.Finished, position(finished = true)))
        assertEquals(false, matchesFilter(FilterMode.Finished, position(finished = false)))
        assertEquals(false, matchesFilter(FilterMode.Finished, null))
    }
}
