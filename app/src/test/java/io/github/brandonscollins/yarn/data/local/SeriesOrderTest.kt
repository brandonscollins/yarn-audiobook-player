package io.github.brandonscollins.yarn.data.local

import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesOrderTest {
    private fun book(
        id: Int,
        title: String,
        author: String = "Zogarth",
    ) = Audiobook(
        id = id,
        title = title,
        author = author,
        thumbPath = "",
        durationMs = 0,
        addedAt = 0,
        lastViewedAt = 0,
        viewCount = 0,
    )

    private fun assertParses(
        title: String,
        key: String,
        number: Int,
    ) = assertEquals("parsing \"$title\"", SeriesEntry(key, number), seriesEntry(title))

    // --- parsing -----------------------------------------------------------

    @Test
    fun `a bare trailing number is the book number`() {
        assertParses("The Perfect Run 2", "perfect run", 2)
        assertParses("Primal Hunter 6", "primal hunter", 6)
        assertParses("He Who Fights with Monsters 11", "he who fights with monsters", 11)
    }

    @Test
    fun `book, volume, vol and hash markers all parse`() {
        assertParses("Dungeon Crawler Carl: Book 4", "dungeon crawler carl", 4)
        assertParses("Dungeon Crawler Carl Book 4", "dungeon crawler carl", 4)
        assertParses("Dungeon Crawler Carl, Book 4", "dungeon crawler carl", 4)
        assertParses("Cradle: Volume 3", "cradle", 3)
        assertParses("Cradle Vol. 3", "cradle", 3)
        assertParses("Cradle #3", "cradle", 3)
    }

    @Test
    fun `a subtitle after the number is not part of the series key`() {
        assertParses("The Perfect Run 2 - Rewinder", "perfect run", 2)
        assertParses("Mother of Learning, Book 2: Chasing Fireflies", "mother of learning", 2)
    }

    @Test
    fun `keys normalize case, whitespace, trailing separators and a leading article`() {
        assertEquals(seriesEntry("The Perfect Run 3")?.key, seriesEntry("PERFECT   RUN 4")?.key)
        assertEquals("perfect run", seriesEntry("The Perfect Run: 5")?.key)
    }

    @Test
    fun `a title with no number is not a series entry`() {
        assertNull(seriesEntry("Ready Player One"))
        assertNull(seriesEntry("Project Hail Mary"))
        assertNull(seriesEntry(""))
    }

    @Test
    fun `a number with no series name in front of it is rejected`() {
        assertNull(seriesEntry("1984"))
        assertNull(seriesEntry("11/22/63"))
    }

    @Test
    fun `an unmarked four-digit number is a year, not a book number`() {
        assertNull(seriesEntry("Some Long Title 2005"))
        // An explicit marker overrides the year guard.
        assertParses("Some Long Title, Book 1984", "some long title", 1984)
    }

    @Test
    fun `a number followed by more words is not a book number`() {
        assertNull(seriesEntry("Apollo 13 Lost Moon"))
    }

    // --- nextInSeries ------------------------------------------------------

    private val perfectRun =
        listOf(
            book(1, "The Perfect Run"),
            book(2, "The Perfect Run 2"),
            book(3, "The Perfect Run 3"),
            book(4, "The Perfect Run 4"),
        )

    @Test
    fun `the next book is the lowest number above the current one`() {
        assertEquals(3, nextInSeries(perfectRun[1], perfectRun, emptySet()))
    }

    @Test
    fun `started books are skipped`() {
        assertEquals(4, nextInSeries(perfectRun[1], perfectRun, setOf(3)))
    }

    @Test
    fun `everything later already started means no suggestion`() {
        assertNull(nextInSeries(perfectRun[1], perfectRun, setOf(3, 4)))
    }

    @Test
    fun `the last book of a series has nothing next`() {
        assertNull(nextInSeries(perfectRun[3], perfectRun, emptySet()))
    }

    @Test
    fun `a current book with no parseable number yields nothing`() {
        // Book 1 of the series is titled without a number.
        assertNull(nextInSeries(perfectRun[0], perfectRun, emptySet()))
    }

    @Test
    fun `other series and unparseable candidates are ignored`() {
        val mixed = perfectRun + listOf(book(50, "Primal Hunter 3"), book(51, "Some Standalone"))
        assertEquals(3, nextInSeries(perfectRun[1], mixed, emptySet()))
    }

    @Test
    fun `current is never its own next book`() {
        val dupe = listOf(book(2, "The Perfect Run 2"), book(9, "The Perfect Run 2"))
        assertNull(nextInSeries(dupe[0], dupe, emptySet()))
    }

    @Test
    fun `duplicate numbers resolve to the lowest id`() {
        val editions =
            listOf(
                book(2, "The Perfect Run 2"),
                book(30, "The Perfect Run 3"),
                book(7, "Perfect Run, Book 3"),
            )
        assertEquals(7, nextInSeries(editions[0], editions, emptySet()))
    }

    // --- nextUpNext fallback chain -----------------------------------------

    @Test
    fun `collection peers answer first`() {
        val peers = perfectRun
        val author = listOf(book(99, "The Perfect Run 3"))
        assertEquals(
            3,
            nextUpNext(perfectRun[1], peers, author, emptyList(), emptySet()),
        )
    }

    @Test
    fun `same-author books cover a series that was never a collection in Plex`() {
        val author = listOf(book(1, "Primal Hunter 5"), book(2, "Primal Hunter 6"))
        assertEquals(
            2,
            nextUpNext(author[0], emptyList(), author, emptyList(), emptySet()),
        )
    }

    @Test
    fun `plex ordinals are the last resort when titles carry no numbering`() {
        val current = book(1, "Wandering Inn")
        val crossRefs =
            listOf(
                BookCollectionCrossRef(bookId = 1, collectionId = 10, ordinal = 0),
                BookCollectionCrossRef(bookId = 2, collectionId = 10, ordinal = 1),
            )
        val peers = listOf(current, book(2, "Wandering Inn Two"))
        assertEquals(2, nextUpNext(current, peers, emptyList(), crossRefs, emptySet()))
    }

    @Test
    fun `nothing anywhere means no up-next section`() {
        val current = book(1, "Project Hail Mary")
        assertNull(nextUpNext(current, listOf(current), listOf(current), emptyList(), emptySet()))
    }

    @Test
    fun `the zero-ordinal cache that broke up-next now answers from titles`() {
        // Every ordinal 0 — the legacy path's strict `>` matches nothing.
        val books = listOf(book(1, "Primal Hunter 5"), book(2, "Primal Hunter 6"))
        val crossRefs =
            books.map { BookCollectionCrossRef(bookId = it.id, collectionId = 10, ordinal = 0) }
        assertNull(nextInCollection(1, crossRefs, emptySet()))
        assertEquals(2, nextUpNext(books[0], books, emptyList(), crossRefs, emptySet()))
    }
}
