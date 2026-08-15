package io.github.brandonscollins.yarn.data.plex.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every Plex JSON response is wrapped in a top-level `MediaContainer`. */
@Serializable
data class MediaContainerResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexMediaContainer = PlexMediaContainer(),
)

@Serializable
data class PlexMediaContainer(
    val size: Int = 0,
    val totalSize: Int = 0,
    val offset: Int = 0,
    val machineIdentifier: String = "",
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
    @SerialName("Directory") val directories: List<PlexMetadata> = emptyList(),
)

/**
 * A single item in a `MediaContainer` — book (type=9, "album"), track (type=10), or
 * collection, depending on which endpoint returned it. One shape covers all three because
 * Plex reuses it.
 */
@Serializable
data class PlexMetadata(
    val ratingKey: String = "",
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val parentRatingKey: String = "",
    val parentTitle: String = "",
    val thumb: String = "",
    val summary: String = "",
    val duration: Long = 0,
    val index: Int = 0,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val lastViewedAt: Long = 0,
    val viewCount: Int = 0,
    val viewOffset: Long = 0,
    /** "YYYY-MM-DD" release date on the album; preferred over [year] when present. */
    val originallyAvailableAt: String = "",
    val year: Int = 0,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
    @SerialName("Genre") val genres: List<PlexTag> = emptyList(),
    @SerialName("Collection") val collections: List<PlexTag> = emptyList(),
    /** Embedded chapters on a track — only present when requested with `includeChapters=1`. */
    @SerialName("Chapter") val chapters: List<PlexChapter> = emptyList(),
)

/** One embedded chapter within a track's file. Every attribute is optional on the wire. */
@Serializable
data class PlexChapter(
    val id: Long = 0,
    val index: Int = 0,
    /** The chapter title; Plex leaves it absent or blank when the file has none. */
    val tag: String = "",
    /** Start offset in ms, within the file — not the book. */
    val startTimeOffset: Long = 0,
    val endTimeOffset: Long = 0,
    val thumb: String = "",
)

/** The "Media" element of a track — only its file parts matter here. */
@Serializable
data class PlexMedia(
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
)

/** The `/library/parts/...` path used to stream or download a track's file. */
@Serializable
data class PlexPart(
    val key: String = "",
    val size: Long = 0,
)

@Serializable
data class PlexTag(
    val tag: String = "",
)
