package io.github.brandonscollins.yarn.data.download

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One book's download, as the menus render it: running (with [done]/[total] tracks landed), fully
 * [downloaded], or neither. `downloaded` is derived from the tracks rather than `books.isCached` —
 * the worker sets both together, and one flow is cheaper than two.
 */
data class DownloadState(
    val downloading: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
) {
    val downloaded: Boolean get() = total > 0 && done == total
}

/**
 * The download actions, shared by the Book detail and Player menus — the plumbing is the same on
 * both, and only one of them owns a ViewModel keyed to the book.
 */
object Downloads {
    fun start(
        context: Context,
        bookId: Int,
    ) = DownloadWorker.enqueue(context, bookId)

    /** Completed tracks stay downloaded (resume-safe), so a cancel needs no cleanup of its own. */
    fun cancel(
        context: Context,
        bookId: Int,
    ) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(DownloadWorker.uniqueWorkName(bookId))
    }

    /**
     * Deletes the public MediaStore copies and clears the download columns. Per-row delete is
     * best-effort — a file manager may already have removed the file, which counts as done.
     * The position ledger is untouched: removing a download never moves your place in the book.
     */
    suspend fun remove(
        context: Context,
        bookId: Int,
    ) {
        val db = PlexGraph.db(context)
        val resolver = context.applicationContext.contentResolver
        db.trackDao().getTracksForBook(bookId).first()
            .mapNotNull { it.localUri }
            .forEach { uri -> runCatching { resolver.delete(Uri.parse(uri), null, null) } }
        db.trackDao().clearDownloadsForBook(bookId)
        db.bookDao().setCached(bookId, false)
    }

    /** WorkManager for "is it running", Room for how far it got. Both change while a menu is open. */
    fun observe(
        context: Context,
        bookId: Int,
    ): Flow<DownloadState> =
        combine(
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(DownloadWorker.uniqueWorkName(bookId))
                .map { infos -> infos.any { !it.state.isFinished } },
            PlexGraph.db(context).trackDao().getTracksForBook(bookId),
        ) { downloading, tracks ->
            DownloadState(downloading, tracks.count { it.isCached }, tracks.size)
        }
}
