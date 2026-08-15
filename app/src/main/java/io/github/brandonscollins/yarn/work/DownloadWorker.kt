package io.github.brandonscollins.yarn.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a whole book's tracks into public storage (`Music/Yarn/<Book Title>/`) via MediaStore,
 * so the files are plain audio any file manager can see and manage (an explicit requirement).
 * Each finished track is marked in Room as it lands, so a retry resumes where it left off.
 *
 * ponytail: MediaStore rows are owned by the app *install* — after a reinstall the app can see but
 * not delete the old rows, so "Remove download" leaves orphans for a file manager to clean up.
 * Acceptable for a personal sideloaded app; the upgrade path is MANAGE_EXTERNAL_STORAGE or SAF.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getInt(KEY_BOOK_ID, -1)
        if (bookId < 0) return Result.failure()

        val prefs = PlexGraph.prefs(applicationContext)
        val db = PlexGraph.db(applicationContext)
        if (prefs.chosenServerUri.isEmpty()) return Result.retry()
        if (!PlexGraph.connections(applicationContext).ensureConnected()) return Result.retry()

        val book = db.bookDao().getBook(bookId).first() ?: return Result.failure()
        val tracks = db.trackDao().getTracksForBook(bookId).first()
        if (tracks.isEmpty()) return Result.failure()
        val resolver = applicationContext.contentResolver
        val token = prefs.serverToken.ifEmpty { prefs.accountToken }

        return try {
            tracks.forEachIndexed { done, track ->
                // On S+ a retry that starts from the background may not be allowed to go
                // foreground; the download still runs, just without the notification.
                runCatching { setForeground(foregroundInfo(book.title, done, tracks.size)) }
                if (track.isCached && track.localUri != null &&
                    localUriResolves(resolver, track.localUri)
                ) {
                    return@forEachIndexed // Already downloaded (resume after retry/cancel).
                }
                val url = "${prefs.chosenServerUri}${track.partKey}?download=1&X-Plex-Token=$token"
                val uri = downloadTrack(resolver, url, book.title, track)
                db.trackDao().markDownloaded(track.id, uri.toString())
            }
            db.bookDao().setCached(bookId, true)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.retry() // Completed tracks stay cached; the next run skips them.
        }
    }

    /** Streams one track into a pending MediaStore row and returns its `content://` URI. */
    private suspend fun downloadTrack(
        resolver: ContentResolver,
        url: String,
        bookTitle: String,
        track: Track,
    ): Uri =
        withContext(Dispatchers.IO) {
            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        downloadDisplayName(track.index, track.title, track.partKey),
                    )
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        "Music/Yarn/${sanitizeForFileName(bookTitle)}/",
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri =
                resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    resolver.openOutputStream(uri)?.use { out ->
                        body.byteStream().copyTo(out)
                    } ?: throw IOException("Cannot open $uri for writing")
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                uri
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) } // Don't leave a pending stub.
                throw t
            }
        }

    private fun foregroundInfo(
        bookTitle: String,
        done: Int,
        total: Int,
    ): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
        )
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading $bookTitle")
                .setContentText("Track ${done + 1} of $total")
                .setProgress(total, done, false)
                .setOngoing(true)
                .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        private const val KEY_BOOK_ID = "book_id"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 41

        // The Retrofit clients cap reads at 15s — far too short for a 100MB+ audio file on a
        // relay connection. readTimeout(0) = no limit; the between-bytes case is still bounded
        // by the socket dying, and WorkManager retries.
        private val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()

        fun uniqueWorkName(bookId: Int) = "download_book_$bookId"

        /** KEEP: re-tapping Download while one is running must not restart it. */
        fun enqueue(
            context: Context,
            bookId: Int,
        ) {
            val request =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setInputData(workDataOf(KEY_BOOK_ID to bookId))
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueWorkName(bookId), ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** True when the MediaStore row still opens — false once a file manager has deleted the file. */
fun localUriResolves(
    resolver: ContentResolver,
    uri: String,
): Boolean =
    runCatching { resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { } != null }
        .getOrDefault(false)

/**
 * "01 - Chapter Title.mp3": zero-padded index so file managers sort tracks in book order, with the
 * real extension carried over from the Plex part path.
 */
fun downloadDisplayName(
    index: Int,
    title: String,
    partKey: String,
): String {
    val extension =
        partKey.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= 4 && '/' !in it } ?: "mp3"
    return "%02d - %s.%s".format(index, sanitizeForFileName(title), extension)
}

/** Strips the characters that break file paths on Android (FAT/exFAT-hostile set included). */
fun sanitizeForFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Untitled" }
