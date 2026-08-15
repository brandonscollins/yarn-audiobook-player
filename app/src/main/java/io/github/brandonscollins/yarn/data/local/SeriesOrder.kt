package io.github.brandonscollins.yarn.data.local

import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef

/** A title parsed into "which series" plus "which book of it". [key] is already normalized. */
data class SeriesEntry(
    val key: String,
    val number: Int,
)

/**
 * One pass over a title: everything before the number is the series, the number is the position.
 *
 * - optional `Book` / `Bk` / `Volume` / `Vol.` / `#` marker in front of the digits
 * - any run of `:` `,` `-` `–` `—` or whitespace separating the series from the marker
 * - the digits must end the title or be followed by a separator, so `"The Perfect Run 2 - Rewinder"`
 *   parses but `"Ready Player 1 Wins"` doesn't
 *
 * ponytail: no roman numerals. A `I`–`XX` map is small but standalone `I`/`V`/`X` at the end of a
 * title false-positives more often than it helps, and nothing in this library uses them. Add the
 * map here if a series ever needs it.
 */
private val SERIES_PATTERN =
    Regex(
        """^(.*?)[\s:,\-–—]*((?:book|bk|volume|vol)\.?\s*|#)?(\d+)\s*(?:[:,\-–—(]|$)""",
        RegexOption.IGNORE_CASE,
    )

/** Below this the "series" is punctuation or a stray letter, not a name. */
private const val MIN_KEY_LENGTH = 2

/**
 * Biggest book number we'll believe without an explicit `Book`/`Vol`/`#` marker. Three digits keeps
 * `"Primal Hunter 6"` and kills `"Some Title 2005"`, which is a year — no series runs past 999.
 * With a marker any number is fine: `"Book 1984"` means what it says.
 */
private const val MAX_UNMARKED_NUMBER = 999

/**
 * Parse a book title into its series key and number, or null when it isn't a numbered series entry.
 *
 * Rejected on purpose: a title with no digits; a series key that is empty, shorter than
 * [MIN_KEY_LENGTH], or has no letter in it — `"1984"` is a title and `"11/22/63"` is a date, not
 * book 63 of series "11/22/"; and an unmarked number over [MAX_UNMARKED_NUMBER], which is a year.
 *
 * ponytail: `"Fahrenheit 451"` still parses as series "fahrenheit" #451. Harmless — it only matters
 * if a second "fahrenheit" book with a higher number exists.
 */
fun seriesEntry(title: String): SeriesEntry? {
    val match = SERIES_PATTERN.find(title.trim()) ?: return null
    val (prefix, marker, digits) = match.destructured
    val number = digits.toIntOrNull() ?: return null
    if (marker.isEmpty() && number > MAX_UNMARKED_NUMBER) return null
    val key = normalizeSeriesKey(prefix)
    if (key.length < MIN_KEY_LENGTH || key.none { it.isLetter() }) return null
    return SeriesEntry(key, number)
}

/** Lowercase, collapse whitespace, drop trailing separators, drop a leading article. */
private fun normalizeSeriesKey(raw: String): String =
    raw.lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd(':', ',', '-', '–', '—', ' ')
        .removePrefix("the ")
        .removePrefix("a ")
        .trim()

/**
 * The book after [current] among [candidates], ordered by the number in the title rather than by
 * anything Plex told us. Only candidates whose normalized series key matches [current]'s count;
 * unparseable titles and [current] itself are ignored.
 *
 * Books already in [startedBookIds] are skipped, so "up next" never nags about something in
 * progress — if every later book has been started the answer is null, not the last one.
 *
 * Duplicate numbers (two editions of book 3) resolve to the lowest id, so the answer is stable
 * across syncs.
 */
fun nextInSeries(
    current: Audiobook,
    candidates: List<Audiobook>,
    startedBookIds: Set<Int>,
): Int? {
    val here = seriesEntry(current.title) ?: return null
    return candidates
        .asSequence()
        .filter { it.id != current.id }
        .mapNotNull { book ->
            seriesEntry(book.title)
                ?.takeIf { it.key == here.key && it.number > here.number }
                ?.let { it.number to book }
        }
        .sortedWith(compareBy({ it.first }, { it.second.id }))
        .firstOrNull { it.second.id !in startedBookIds }
        ?.second
        ?.id
}

/**
 * "Up next in series" for Home and the Player screen. Title numbering first, Plex's own collection
 * ordering only as a last resort — the ordinal column is 0 for every row written before it existed,
 * and even when populated it's release order, not reading order.
 *
 * What each argument wants (all already loaded; this stays a pure function):
 * - [collectionPeers] — the [Audiobook] rows of every book sharing a collection with [current].
 *   `CollectionDao.getCollectionPeers(current.id)` gives the cross-refs; join their `bookId`s to
 *   `books`. Includes [current] itself, which is fine.
 * - [sameAuthorBooks] — `SELECT * FROM books WHERE author = :author` for `current.author`. Catches
 *   series that were never made a collection in Plex. Passing the whole library also works (the
 *   series key gates it) but costs more rows.
 * - [crossRefs] — the raw `CollectionDao.getCollectionPeers(current.id)` rows, for the legacy path.
 * - [startedBookIds] — book ids with a ledger row, same set [nextInCollection] already takes.
 *
 * ponytail: "everything later in this collection is already started" falls through to the author
 * and ordinal passes instead of stopping at null, so a stale ordinal can still surface something
 * odd. Only reachable on a library with populated ordinals; not worth a flag until it bites.
 */
fun nextUpNext(
    current: Audiobook,
    collectionPeers: List<Audiobook>,
    sameAuthorBooks: List<Audiobook>,
    crossRefs: List<BookCollectionCrossRef>,
    startedBookIds: Set<Int>,
): Int? =
    nextInSeries(current, collectionPeers, startedBookIds)
        ?: nextInSeries(current, sameAuthorBooks, startedBookIds)
        ?: nextInCollection(current.id, crossRefs, startedBookIds)
