package io.github.brandonscollins.yarn.player

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.brandonscollins.yarn.data.model.Audiobook
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.settings.PlexPrefs
import java.net.URLEncoder

/**
 * Android Auto's browse tree, built from Room. Pure MediaItem shaping only — no session/service
 * state — so [PlaybackService]'s `MediaLibrarySession.Callback` just calls into these.
 *
 * Tree shape:
 * ```
 * root
 * ├── continue   ("Continue listening" — books with a ledger row, most recent first, capped 20)
 * └── library    ("Library" — every book, alphabetical, capped 100)
 * ```
 * Each book is one PLAYABLE leaf, mediaId `"book/<id>"`; tapping it resolves to the book's actual
 * track queue server-side (see [PlaybackService]'s `onSetMediaItems`/`onAddMediaItems`).
 */
const val AUTO_ROOT_ID = "root"
const val AUTO_CONTINUE_ID = "continue"
const val AUTO_LIBRARY_ID = "library"
private const val AUTO_BOOK_PREFIX = "book/"

/** Room-side caps for the two root folders — plenty for a head unit, never a giant fetch. */
const val AUTO_CONTINUE_LIMIT = 20
const val AUTO_LIBRARY_LIMIT = 100

fun bookMediaId(bookId: Int): String = "$AUTO_BOOK_PREFIX$bookId"

/** Null for anything that isn't a book leaf — folders, or a mediaId this app didn't hand out. */
fun parseBookMediaId(mediaId: String): Int? =
    mediaId.takeIf { it.startsWith(AUTO_BOOK_PREFIX) }
        ?.removePrefix(AUTO_BOOK_PREFIX)
        ?.toIntOrNull()

private fun folderItem(
    id: String,
    title: String,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .build(),
        )
        .build()

fun autoRootItem(): MediaItem = folderItem(AUTO_ROOT_ID, "Yarn")

fun autoContinueFolderItem(): MediaItem = folderItem(AUTO_CONTINUE_ID, "Continue listening")

fun autoLibraryFolderItem(): MediaItem = folderItem(AUTO_LIBRARY_ID, "Library")

/**
 * Cover art URL for the browse tree. Mirrors `ui/common/CoverArt.kt`'s `thumbUri` exactly (Plex's
 * transcoder endpoint) — duplicated rather than imported so the service doesn't pull in UI code.
 */
private fun autoThumbUri(
    prefs: PlexPrefs,
    thumbPath: String,
): String? {
    if (thumbPath.isEmpty() || prefs.chosenServerUri.isEmpty()) return null
    val token = prefs.serverToken.ifEmpty { prefs.accountToken }
    val encoded = URLEncoder.encode(thumbPath, "UTF-8")
    return "${prefs.chosenServerUri}/photo/:/transcode?width=400&height=400&url=$encoded&X-Plex-Token=$token"
}

/** One book as a browsable-tree leaf: playable, not browsable — tapping it plays the whole book. */
fun Audiobook.toAutoBrowseItem(prefs: PlexPrefs): MediaItem =
    MediaItem.Builder()
        .setMediaId(bookMediaId(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .apply { autoThumbUri(prefs, thumbPath)?.let { setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

/**
 * One track as a queue entry, for the real MediaController playlist a `book/<id>` leaf resolves
 * into. Mirrors `PlayerController.toMediaItem` (private there) field for field — including the
 * [EXTRA_BOOK_ID] extra the position ledger keys off of — because that's the source of truth for
 * how a track becomes playable; keep the two in sync by hand if either changes.
 */
fun Track.toAutoQueueItem(
    bookId: Int,
    prefs: PlexPrefs,
): MediaItem {
    val token = prefs.serverToken.ifEmpty { prefs.accountToken }
    val uri =
        if (isCached && localUri != null) {
            localUri
        } else {
            "${prefs.chosenServerUri}$partKey?X-Plex-Token=$token"
        }
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri.toUri()).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setTrackNumber(index)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(bundleOf(EXTRA_BOOK_ID to bookId))
                .build(),
        )
        .build()
}
