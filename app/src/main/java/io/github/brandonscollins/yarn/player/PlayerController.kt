package io.github.brandonscollins.yarn.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
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
    private val syncRepo = LibrarySyncRepo(plexPrefs, PlexGraph.api(appContext), db)

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

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    val sleepRemainingMs: StateFlow<Long?> = SleepState.remainingMs

    /** Audio effects, owned by the service; see [EffectsState]. */
    val boostMb: StateFlow<Int> = EffectsState.boostMb
    val eqEnabled: StateFlow<Boolean> = EffectsState.eqEnabled
    val eqPreset: StateFlow<Int> = EffectsState.eqPreset
    val eqBandLevels: StateFlow<ShortArray> = EffectsState.eqBandLevels

    /** Null until the effects first attach; null forever on a device with no usable equalizer. */
    val eqBandInfo: StateFlow<EqBandInfo?> = EffectsState.bandInfo

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
            // The stream URIs below are built from the chosen connection, so the race has to have
            // settled first (gotcha #3); with no reachable server there is nothing to play.
            PlexGraph.connections(appContext).ensureConnected()
            if (plexPrefs.chosenServerUri.isEmpty()) return@launch
            // A book opened for the first time may still have its track sync in flight — without
            // this, tapping Play a beat too early was a silent no-op.
            val tracks =
                db.trackDao().getTracksForBook(bookId).first().ifEmpty {
                    runCatching { syncRepo.syncTracks(bookId) }
                    db.trackDao().getTracksForBook(bookId).first()
                }
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

    /**
     * Any speed in [MIN_SPEED]..[MAX_SPEED] (out-of-range values are coerced, not rejected).
     * Persisted service-side, so the next session starts at the same speed.
     */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(coerceSpeed(speed))
    }

    /** Absolute seek within the current track — the Player screen's scrub slider. */
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    /** Jump to a track (chapters sheet), starting at its beginning. */
    fun seekToTrack(trackIndex: Int) = controller?.seekTo(trackIndex, 0L) ?: Unit

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

    /** Volume boost in millibels, 0..[MAX_BOOST_MB]; 0 leaves the effect disabled. */
    fun setBoost(mB: Int) = send(COMMAND_SET_BOOST, bundleOf(KEY_BOOST_MB to mB))

    fun setEqEnabled(enabled: Boolean) =
        send(COMMAND_SET_EQ_ENABLED, bundleOf(KEY_EQ_ENABLED to enabled))

    /** Device preset by index into [EqBandInfo.presetNames], or [EQ_PRESET_CUSTOM]. */
    fun setEqPreset(index: Int) = send(COMMAND_SET_EQ_PRESET, bundleOf(KEY_EQ_PRESET to index))

    /** Level in millibels, within the range in [EqBandInfo]. Any manual band makes it custom. */
    fun setEqBand(
        band: Int,
        levelMb: Short,
    ) = send(
        COMMAND_SET_EQ_BAND,
        bundleOf(KEY_EQ_BAND to band, KEY_EQ_BAND_LEVEL_MB to levelMb),
    )

    private fun send(
        action: String,
        args: Bundle,
    ) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
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
        _durationMs.value = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
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
