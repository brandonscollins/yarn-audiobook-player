package io.github.brandonscollins.yarn.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.settings.PlexPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SEEK_STEP_MS = 30_000L
private const val POSITION_POLL_MS = 500L

/**
 * Everything the UI needs from the player and nothing more: state as StateFlows, commands as plain
 * functions. Connects to [PlaybackService] through a [MediaController], so the service stays the
 * single owner of the player.
 */
class PlayerController(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val plexPrefs = PlexGraph.prefs(appContext)
    private val db = PlexGraph.db(appContext)

    private var controller: MediaController? = null
    private var pollJob: Job? = null

    private val _bookId = MutableStateFlow<Int?>(null)
    val bookId: StateFlow<Int?> = _bookId.asStateFlow()

    private val _trackIndex = MutableStateFlow(0)
    val trackIndex: StateFlow<Int> = _trackIndex.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    val sleepRemainingMs: StateFlow<Long?> = SleepState.remainingMs

    /** Binds to the service. Safe to call again; commands issued before it lands are dropped. */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            { attach(future.get()) },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        pollJob?.cancel()
        controller?.release()
        controller = null
    }

    /** Loads the book's tracks from Room and starts at the furthest-ahead known position. */
    fun playBook(bookId: Int) {
        scope.launch {
            val tracks = db.trackDao().getTracksForBook(bookId).first()
            if (tracks.isEmpty()) return@launch
            val resume = resumePoint(tracks, db.positionDao().getPosition(bookId))
            val items = tracks.map { it.toMediaItem(bookId, plexPrefs) }
            withContext(Dispatchers.Main) {
                val c = controller ?: return@withContext
                c.setMediaItems(items, resume.trackIndex, resume.positionMs)
                c.prepare()
                c.play()
            }
        }
    }

    fun play() = controller?.play() ?: Unit

    fun pause() = controller?.pause() ?: Unit

    /** ±30s. Rewinding past the start of a track walks into the previous one. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = c.currentPosition + deltaMs
        val previousIndex = c.currentMediaItemIndex - 1
        if (target < 0 && previousIndex >= 0) {
            val previousDuration =
                c.currentTimeline.getWindow(previousIndex, Timeline.Window()).durationMs
            c.seekTo(previousIndex, (previousDuration + target).coerceAtLeast(0))
        } else {
            c.seekTo(target.coerceAtLeast(0))
        }
    }

    /** Persisted service-side, so the next session starts at the same speed. */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun armSleep(durationMs: Long) {
        controller?.sendCustomCommand(
            SessionCommand(COMMAND_ARM_SLEEP, Bundle.EMPTY),
            bundleOf(KEY_SLEEP_DURATION_MS to durationMs),
        )
    }

    fun cancelSleep() {
        controller?.sendCustomCommand(
            SessionCommand(COMMAND_CANCEL_SLEEP, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    private fun attach(mediaController: MediaController) {
        controller = mediaController
        mediaController.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) = publish()
            },
        )
        publish()
        pollJob?.cancel()
        pollJob =
            scope.launch {
                while (true) {
                    delay(POSITION_POLL_MS)
                    if (_isPlaying.value) publish()
                }
            }
    }

    private fun publish() {
        val c = controller ?: return
        _bookId.value = c.currentMediaItem?.mediaMetadata?.extras?.getInt(EXTRA_BOOK_ID)
        _trackIndex.value = c.currentMediaItemIndex
        _positionMs.value = c.currentPosition.coerceAtLeast(0)
        _isPlaying.value = c.isPlaying
        _speed.value = c.playbackParameters.speed
    }
}

/**
 * Plex accepts its token as a query param, which is why streaming needs no custom DataSource. The
 * URI also rides in [MediaItem.RequestMetadata] because MediaItems lose their local configuration
 * on the way to the service.
 */
private fun Track.toMediaItem(
    bookId: Int,
    prefs: PlexPrefs,
): MediaItem {
    val token = prefs.serverToken.ifEmpty { prefs.accountToken }
    val uri = "${prefs.chosenServerUri}$partKey?X-Plex-Token=$token"
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
