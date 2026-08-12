package io.github.brandonscollins.yarn.data.local

import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Book 1, 2 and 3 of one series, listed out of order to prove ordinal beats list position. */
private val series =
    listOf(
        BookCollectionCrossRef(bookId = 30, collectionId = 7, ordinal = 2),
        BookCollectionCrossRef(bookId = 10, collectionId = 7, ordinal = 0),
        BookCollectionCrossRef(bookId = 20, collectionId = 7, ordinal = 1),
    )

class NextInCollectionTest {
    @Test
    fun `picks the nearest later book`() {
        assertEquals(20, nextInCollection(bookId = 10, peers = series, startedBookIds = setOf(10)))
    }

    @Test
    fun `skips started books`() {
        assertEquals(30, nextInCollection(10, series, startedBookIds = setOf(10, 20)))
    }

    @Test
    fun `never looks backwards`() {
        assertNull(nextInCollection(30, series, startedBookIds = setOf(30)))
    }

    @Test
    fun `no collection means no suggestion`() {
        assertNull(nextInCollection(99, emptyList(), startedBookIds = setOf(99)))
    }

    @Test
    fun `a cache with no ordinals yet suggests nothing`() {
        val flat = series.map { it.copy(ordinal = 0) }
        assertNull(nextInCollection(10, flat, startedBookIds = setOf(10)))
    }
}
