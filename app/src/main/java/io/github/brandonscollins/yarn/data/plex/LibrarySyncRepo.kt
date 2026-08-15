package io.github.brandonscollins.yarn.data.plex

import android.content.Context
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.BookCollectionCrossRef
import io.github.brandonscollins.yarn.data.model.Chapter
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
    /**
     * Optional so every existing call site keeps compiling untouched (PlayerController and the
     * three ViewModels that construct this repo are out of scope here). All four already have a
     * Context in hand at their call site — passing it through is a one-line follow-up wherever
     * the ID3 fallback below should actually go live; until then it stays a no-op.
     */
    private val context: Context? = null,
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

        // Position in the response is the collection's own order on the server (release date by
        // default, or whatever the user dragged it into) — the only series order Plex will tell us.
        val crossRefs =
            collections.flatMap { collection ->
                api.mediaService.retrieveBooksInCollection(collection.id).mediaContainer.metadata
                    .mapNotNull { it.ratingKey.toIntOrNull() }
                    .mapIndexed { ordinal, bookId ->
                        BookCollectionCrossRef(bookId, collection.id, ordinal)
                    }
            }
        db.collectionDao().upsertCrossRefs(crossRefs)
    }

    suspend fun syncTracks(bookId: Int) {
        val cached =
            db.trackDao().getTracksForBook(bookId).first()
                .filter { it.isCached }
                .associateBy { it.id }
        val tracks =
            api.mediaService.retrieveTracksForAlbum(bookId).mediaContainer.metadata
                .mapNotNull { meta ->
                    meta.ratingKey.toIntOrNull()?.let { meta.toTrack(it, bookId, cached[it]) }
                }
        db.trackDao().upsertAll(tracks)
    }

    /**
     * Embedded chapters, as Plex reports them with `includeChapters=1` on per-track metadata.
     * One request per track, so this is called lazily at book open — never from [sync], which
     * would turn every library refresh into N-per-book requests. All-or-nothing: a failed fetch
     * throws before the delete, leaving whatever was cached.
     *
     * When Plex itself has zero chapters across every track, falls back to reading embedded ID3
     * `CHAP` frames straight out of the files (Milestone 5 path 2 — see next_steps.md "Chapter
     * compatibility"). That fallback needs a Context, which not every caller supplies (see
     * [context]'s doc); with none, this behaves exactly as before.
     */
    suspend fun syncChapters(bookId: Int) {
        val tracks = db.trackDao().getTracksForBook(bookId).first()
        var n = 0
        val plexChapters =
            tracks.flatMap { track ->
                api.mediaService.retrieveTrackMetadata(track.id).mediaContainer.metadata
                    .firstOrNull()?.chapters.orEmpty()
                    .mapIndexed { i, chapter ->
                        n += 1
                        Chapter(
                            trackId = track.id,
                            bookId = bookId,
                            index = i,
                            title = chapter.tag.ifBlank { "Chapter $n" },
                            startMs = chapter.startTimeOffset,
                        )
                    }
            }
        val appContext = context
        val chapters =
            if (plexChapters.isNotEmpty() || appContext == null) {
                plexChapters
            } else {
                retrieveId3Chapters(appContext, bookId, tracks, ::streamUri)
            }
        db.chapterDao().deleteForBook(bookId)
        db.chapterDao().upsertAll(chapters)
    }

    /** [Track.localUri] (downloaded, cheap and local) wins; otherwise the same stream URL shape
     * playback uses: server + partKey + token. */
    private fun streamUri(track: Track): String {
        track.localUri?.let { return it }
        val token = prefs.serverToken.ifEmpty { prefs.accountToken }
        return "${prefs.chosenServerUri}${track.partKey}?X-Plex-Token=$token"
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

/** [cached] is the pre-sync row when it was downloaded, so download state survives the upsert. */
private fun PlexMetadata.toTrack(
    id: Int,
    bookId: Int,
    cached: Track?,
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
        isCached = cached != null,
        localUri = cached?.localUri,
    )
}
