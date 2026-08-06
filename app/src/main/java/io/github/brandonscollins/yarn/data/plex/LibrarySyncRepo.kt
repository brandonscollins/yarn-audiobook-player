package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.model.Collection as BookCollection
import io.github.brandonscollins.yarn.data.plex.model.PlexMetadata
import io.github.brandonscollins.yarn.settings.PlexPrefs
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneOffset

/** Audiobook libraries are Plex *music* libraries, whose section type is "artist". */
private const val LIBRARY_TYPE_MUSIC = "artist"

data class PlexLibrary(
    val id: String,
    val title: String,
)

/** Pulls the library into the Room cache. Upserts only — a sync never wipes local rows. */
class LibrarySyncRepo(
    private val prefs: PlexPrefs,
    private val api: PlexApi,
    private val db: YarnDatabase,
) {
    suspend fun fetchLibraries(): List<PlexLibrary> =
        api.mediaService.retrieveLibraries().mediaContainer.directories
            .filter { it.type == LIBRARY_TYPE_MUSIC }
            .map { PlexLibrary(id = it.key, title = it.title) }

    fun chooseLibrary(libraryId: String) {
        prefs.libraryId = libraryId
    }

    /** Books and collections. Tracks are fetched lazily by [syncTracks] when a book is opened. */
    suspend fun sync() {
        syncBooks()
        syncCollections()
    }

    suspend fun syncBooks() {
        val libraryId = requireLibrary()
        // Upsert replaces whole rows, so local-only download state is carried across the sync.
        val cached =
            db.bookDao().getAllBooks().first().filter { it.isCached }.mapTo(mutableSetOf()) { it.id }
        val books =
            api.mediaService.retrieveAllAlbums(libraryId).mediaContainer.metadata
                .mapNotNull { meta ->
                    meta.ratingKey.toIntOrNull()?.let { meta.toAudiobook(it, it in cached) }
                }
        db.bookDao().upsertAll(books)
    }

    suspend fun syncCollections() {
        val libraryId = requireLibrary()
        val collections =
            api.mediaService.retrieveCollections(libraryId).mediaContainer.metadata
                .mapNotNull { meta ->
                    meta.ratingKey.toIntOrNull()?.let { BookCollection(id = it, title = meta.title) }
                }
        db.collectionDao().upsertAll(collections)

        val crossRefs =
            collections.flatMap { collection ->
                api.mediaService.retrieveBooksInCollection(collection.id).mediaContainer.metadata
                    .mapNotNull { it.ratingKey.toIntOrNull() }
                    .map { BookCollectionCrossRef(bookId = it, collectionId = collection.id) }
            }
        db.collectionDao().upsertCrossRefs(crossRefs)
    }

    suspend fun syncTracks(bookId: Int) {
        val cached =
            db.trackDao().getTracksForBook(bookId).first()
                .filter { it.isCached }
                .mapTo(mutableSetOf()) { it.id }
        val tracks =
            api.mediaService.retrieveTracksForAlbum(bookId).mediaContainer.metadata
                .mapNotNull { meta ->
                    meta.ratingKey.toIntOrNull()?.let { meta.toTrack(it, bookId, it in cached) }
                }
        db.trackDao().upsertAll(tracks)
    }

    private fun requireLibrary(): String =
        prefs.libraryId.ifEmpty { error("No library chosen — call chooseLibrary() first") }
}

private fun PlexMetadata.toAudiobook(
    id: Int,
    isCached: Boolean,
) = Audiobook(
    id = id,
    title = title,
    author = parentTitle,
    thumbPath = thumb,
    durationMs = duration,
    addedAt = addedAt,
    lastViewedAt = lastViewedAt,
    viewCount = viewCount,
    isCached = isCached,
    publishedAtEpochMs = publishedAtEpochMs(originallyAvailableAt, year),
)

/** [originallyAvailableAt] is "YYYY-MM-DD"; [year] is a bare release year. Either can be blank/0. */
private fun publishedAtEpochMs(
    originallyAvailableAt: String,
    year: Int,
): Long {
    runCatching { LocalDate.parse(originallyAvailableAt) }.getOrNull()?.let {
        return it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    if (year > 0) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    return 0L
}

private fun PlexMetadata.toTrack(
    id: Int,
    bookId: Int,
    isCached: Boolean,
): Track {
    val part = media.firstOrNull()?.parts?.firstOrNull()
    return Track(
        id = id,
        bookId = bookId,
        title = title,
        index = index,
        durationMs = duration,
        partKey = part?.key.orEmpty(),
        sizeBytes = part?.size ?: 0,
        viewOffsetMs = viewOffset,
        isCached = isCached,
    )
}
