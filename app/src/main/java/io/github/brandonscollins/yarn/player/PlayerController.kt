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
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.settings.PlexPrefs
import io.github.brandonscollins.yarn.work.localUriResolves
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
    private val playerPrefs = PlayerPrefs(appContext)
    private val db = PlexGraph.db(appContext)
    private val syncRepo = LibrarySyncRepo(plexPrefs, PlexGraph.api(appContext), db, appContext)

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
            // A fully downloaded book needs no server at all — don't make offline playback wait
            // out the connection race. Anything that will stream still races first (gotcha #3),
            // because those URIs are built from the chosen connection.
            val known = db.trackDao().getTracksForBook(bookId).first().map { verifiedLocal(bookId, it) }
            val allLocal = known.isNotEmpty() && known.all { it.isCached && it.localUri != null }
            if (!allLocal) {
                PlexGraph.connections(appContext).ensureConnected()
                if (plexPrefs.chosenServerUri.isEmpty()) return@launch
            }
            // A book opened for the first time may still have its track sync in flight — without
            // this, tapping Play a beat too early was a silent no-op.
            val tracks =
                known.ifEmpty {
                    runCatching { syncRepo.syncTracks(bookId) }
                    db.trackDao().getTracksForBook(bookId).first()
                }
            if (tracks.isEmpty()) return@launch
            // A "mark unplayed" tombstone (ledger.unplayedPending) still resolves correctly here:
            // its own position is (first track, 0) and markUnplayed already zeroed every track's
            // viewOffsetMs, so resumePoint has nothing further-ahead to prefer — the book starts
            // over from 0, which is exactly what playing it after marking it unplayed should do.
            val ledger = db.positionDao().getPosition(bookId)
            val resume = resumePoint(tracks, ledger)
            val start = rewound(tracks, resume, ledger)
            consumePlexOffsets(bookId, tracks, resume, ledger)
            val items = tracks.map { it.toMediaItem(bookId, plexPrefs) }
            withContext(Dispatchers.Main) {
                val c = controller ?: return@withContext
                c.setMediaItems(items, start.trackIndex, start.positionMs)
                c.setPlaybackSpeed(coerceSpeed(playerPrefs.speedFor(bookId)))
                c.prepare()
                c.play()
            }
            // Embedded chapters (one metadata request per track), fetched once per book — after
            // play() so a slow server never delays the audio. Failure or a genuinely chapterless
            // book leaves no rows, which just re-probes on the next open.
            if (db.chapterDao().getChaptersForBook(bookId).first().isEmpty()) {
                runCatching { syncRepo.syncChapters(bookId) }
            }
        }
    }

    /**
     * A downloaded track whose file was deleted behind our back (file managers can — the files are
     * deliberately public) falls back to streaming, and Room is corrected so the UI agrees. Never
     * touches the position ledger: where you were survives the source flipping either way.
     */
    private suspend fun verifiedLocal(
        bookId: Int,
        track: Track,
    ): Track {
        if (track.localUri == null || !track.isCached) return track
        if (localUriResolves(appContext.contentResolver, track.localUri)) return track
        db.trackDao().clearDownload(track.id)
        db.bookDao().setCached(bookId, false)
        return track.copy(isCached = false, localUri = null)
    }

    fun play() = controller?.play() ?: Unit

    fun pause() = controller?.pause() ?: Unit

    /** ±30s. Rewinding past the start of a track walks into the previous one. */
    fun seekBy(deltaMs: Long) {
        controller?.seekWithinBook(deltaMs)
    }

    /**
     * Folds the Plex `viewOffset` mirror into the ledger and clears it, so the furthest-ahead rule
     * in [resumePoint] decides each conflict exactly once. Nothing local ever writes that mirror —
     * only a sync from Plex does — so leaving it set means it keeps winning later comparisons even
     * after the ledger has moved on. Any deliberate move *backwards* would then be undone on the
     * next resume: rewind-on-resume would rewind, and the following resume would snap forward to
     * the old offset again until playback passed it.
     *
     * Order is the invariant: the ledger takes the position before the mirror gives it up, so a
     * kill between the two writes loses nothing.
     */
    private suspend fun consumePlexOffsets(
        bookId: Int,
        tracks: List<Track>,
        resume: ResumePoint,
        ledger: PlaybackPosition?,
    ) {
        val mirrored = tracks.filter { it.viewOffsetMs > 0 }
        if (mirrored.isEmpty()) return
        db.positionDao().upsert(
            PlaybackPosition(
                bookId = bookId,
                trackId = tracks[resume.trackIndex].id,
                positionMs = resume.positionMs,
                updatedAtEpochMs = System.currentTimeMillis(),
                syncedToPlex = false,
                finishedPending = ledger?.finishedPending == true,
                finished = ledger?.finished == true,
            ),
        )
        db.trackDao().upsertAll(mirrored.map { it.copy(viewOffsetMs = 0) })
    }

    /**
     * Where playback should actually start after a pause: the resume point, pulled back by
     * rewind-on-resume and never past the start of the book. The ledger row's timestamp is the
     * pause clock that survives process death — the in-session case is [PlaybackService]'s.
     */
    private fun rewound(
        tracks: List<Track>,
        resume: ResumePoint,
        ledger: PlaybackPosition?,
    ): ResumePoint {
        if (ledger == null) return resume
        val rewindMs =
            rewindOnResumeMs(
                playerPrefs.rewindMode,
                System.currentTimeMillis() - ledger.updatedAtEpochMs,
                playerPrefs.fixedRewindSec * 1_000L,
            )
        if (rewindMs <= 0) return resume
        val absoluteMs = absolutePositionMs(tracks, resume.trackIndex, resume.positionMs)
        return resumePointAt(tracks, absoluteMs - rewindMs)
    }

    /**
     * Any speed in [MIN_SPEED]..[MAX_SPEED] (out-of-range values are coerced, not rejected).
     * Persisted service-side, so the next session starts at the same speed.
     */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(coerceSpeed(speed))
    }

    /** Jump to a track — the chapters sheet, and where the book-level scrub bar lands. */
    fun seekToTrack(
        trackIndex: Int,
        positionMs: Long = 0L,
    ) = controller?.seekTo(trackIndex, positionMs) ?: Unit

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
 * Seek by [deltaMs] treating the book as continuous: rewinding past the start of a track walks into
 * the previous one instead of stopping at zero. Shared by the ±30s pills and by the service's
 * rewind-on-resume, which needs the same walk for a resume near the top of a file.
 */
fun Player.seekWithinBook(deltaMs: Long) {
    val target = currentPosition + deltaMs
    val previousIndex = currentMediaItemIndex - 1
    if (target < 0 && previousIndex >= 0) {
        val previousDuration = currentTimeline.getWindow(previousIndex, Timeline.Window()).durationMs
        seekTo(previousIndex, (previousDuration + target).coerceAtLeast(0))
    } else {
        seekTo(target.coerceAtLeast(0))
    }
}

/**
 * The base URL baked in here is only a starting point: [PlaybackService] installs a
 * `ResolvingDataSource` that rewrites each request's scheme+authority to whatever
 * `PlexConnectionManager` has most recently settled on, so a mid-session LAN->remote/relay switch
 * (CLAUDE.md gotcha #3) doesn't leave the queue pointing at a dead server — the MediaItem itself
 * never needs rebuilding. The URI also rides in [MediaItem.RequestMetadata] because MediaItems lose
 * their local configuration on the way to the service. A downloaded track plays from its MediaStore
 * copy instead — no network, no token, and nothing for the resolver to rewrite.
 */
private fun Track.toMediaItem(
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
