package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.plex.model.MediaContainerResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

private const val MEDIA_TYPE_ALBUM = 9
private const val MEDIA_TYPE_TRACK = 10

/** Talks to a chosen Plex server connection. Base URL is set per-connection at call time. */
interface PlexMediaService {
    /** Lightweight liveness probe — used to race candidate connections (CLAUDE.md gotcha #3). */
    @GET("{url}/identity")
    suspend fun checkServer(
        @Path("url", encoded = true) url: String,
    ): Response<MediaContainerResponse>

    /** Library sections. Audiobook libraries are music libraries, section type "artist". */
    @GET("/library/sections")
    suspend fun retrieveLibraries(): MediaContainerResponse

    /** Books. Audiobooks are Plex music albums, `type=9` (CLAUDE.md gotcha #5). */
    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_ALBUM")
    suspend fun retrieveAllAlbums(
        @Path("libraryId") libraryId: String,
    ): MediaContainerResponse

    /** Tracks (chapters/files) for one book. */
    @GET("/library/metadata/{bookId}/children")
    suspend fun retrieveTracksForAlbum(
        @Path("bookId") bookId: Int,
    ): MediaContainerResponse

    /** One track's own metadata, with any chapters embedded in its file (Milestone 5). */
    @GET("/library/metadata/{trackId}?includeChapters=1")
    suspend fun retrieveTrackMetadata(
        @Path("trackId") trackId: Int,
    ): MediaContainerResponse

    @GET("/library/sections/{libraryId}/collections")
    suspend fun retrieveCollections(
        @Path("libraryId") libraryId: String,
    ): MediaContainerResponse

    @GET("/library/collections/{collectionId}/children")
    suspend fun retrieveBooksInCollection(
        @Path("collectionId") collectionId: Int,
    ): MediaContainerResponse

    /**
     * Starts a media session. Must be called once before [progress] updates will register
     * (CLAUDE.md gotcha #1).
     */
    @POST("/playQueues")
    suspend fun startPlayQueue(
        @Query("uri") serverUri: String,
        @Query("type") mediaType: String = "audio",
        @Query("repeat") shouldRepeat: Boolean = false,
        @Query("own") isOwnedByUser: Boolean = true,
        @Query("includeChapters") includeChapters: Boolean = true,
    )

    /**
     * Reports playback progress. Callers must pass `duration = actualDuration * 2` to dodge
     * Plex's built-in 90%-is-finished rule (CLAUDE.md gotcha #2) — finished is reported
     * explicitly via [scrobble] instead.
     */
    @GET("/:/timeline")
    suspend fun progress(
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("time") timeMs: Long,
        @Query("duration") duration: Long,
        @Query("state") playState: String,
        /** Chronicle sends `hasMDE=1` on every timeline call; Plex is happier with it present. */
        @Query("hasMDE") hasMde: Int = 1,
        @Query("playbackTime") playbackTime: Long = 0,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("playQueueItemId") playQueueItemId: Long = 0,
    )

    /** Explicitly marks an item finished. Works for both tracks and albums. */
    @GET("/:/scrobble")
    suspend fun scrobble(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
    )

    /**
     * Explicit "mark as unplayed", drained from the outbox's `unplayedPending` flag the same way
     * [scrobble] drains `finishedPending`. Plex cascades scrobble/unscrobble state from an album
     * onto its tracks, so passing the book's own ratingKey as [key] clears the whole book in one
     * call — no per-track loop needed.
     */
    @GET("/:/unscrobble")
    suspend fun unscrobble(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
    )
}
