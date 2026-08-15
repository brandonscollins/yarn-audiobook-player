package io.github.brandonscollins.yarn.player

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.brandonscollins.yarn.data.local.YarnDatabase
import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import io.github.brandonscollins.yarn.data.plex.PlexConnectionManager
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.settings.PlexPrefs
import io.github.brandonscollins.yarn.work.ProgressSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Set on every MediaItem so the ledger knows which book a track belongs to. */
const val EXTRA_BOOK_ID = "yarn.bookId"

const val COMMAND_ARM_SLEEP = "yarn.ARM_SLEEP"
const val COMMAND_CANCEL_SLEEP = "yarn.CANCEL_SLEEP"
const val KEY_SLEEP_DURATION_MS = "yarn.sleepDurationMs"

/** Sentinel duration for [COMMAND_ARM_SLEEP]: stop at the end of the chapter, not after N minutes. */
const val SLEEP_END_OF_CHAPTER = -1L

const val COMMAND_SET_BOOST = "yarn.SET_BOOST"
const val COMMAND_SET_EQ_ENABLED = "yarn.SET_EQ_ENABLED"
const val COMMAND_SET_EQ_PRESET = "yarn.SET_EQ_PRESET"
const val COMMAND_SET_EQ_BAND = "yarn.SET_EQ_BAND"
const val KEY_BOOST_MB = "yarn.boostMb"
const val KEY_EQ_ENABLED = "yarn.eqEnabled"
const val KEY_EQ_PRESET = "yarn.eqPreset"
const val KEY_EQ_BAND = "yarn.eqBand"
const val KEY_EQ_BAND_LEVEL_MB = "yarn.eqBandLevelMb"

/** Insurance against process death: ledger tick while actively playing. */
private const val LEDGER_TICK_MS = 10_000L

/**
 * A manual sleep-timer cancel suppresses auto re-arming until the next playback *session*, which we
 * define pragmatically as playback having been paused/stopped this long (or the process restarting,
 * since the flag is in-memory).
 */
private const val SESSION_GAP_MS = 5 * 60_000L

/**
 * The player. One `MediaSessionService` gives us the notification, bluetooth/media buttons and most
 * of Android Auto; a book is a playlist of its tracks so ExoPlayer handles file-to-file transitions.
 *
 * Every ledger write-trigger lives in [PlayerEvents] plus [startLedgerTick] and [onDestroy].
 *
 * Mid-session reconnect: streaming URIs are resolved per-request (see [resolveStreamingUri]) rather
 * than baked into the MediaItem once, and [PlayerEvents.onPlayerError] re-races the connection and
 * retries — see both for the mechanism.
 *
 * `MediaLibraryService` (rather than plain `MediaSessionService`) is what gets Android Auto its
 * browse tree: [Callback.onGetLibraryRoot]/[Callback.onGetChildren] serve root/continue/library
 * from Room (tree shape and MediaItem shaping in `AutoLibrary.kt`), and a `book/<id>` leaf tapped
 * there resolves into a real track queue in [Callback.onSetMediaItems]/[Callback.onAddMediaItems],
 * built the same way `PlayerController.playBook` does.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var prefs: PlayerPrefs
    private lateinit var ledger: PositionLedger
    private lateinit var sleepTimer: SleepTimer
    private lateinit var effects: AudioEffects
    private lateinit var plexPrefs: PlexPrefs
    private lateinit var connectionManager: PlexConnectionManager
    private lateinit var db: YarnDatabase
    private var session: MediaLibrarySession? = null

    /** Player-thread scope: the sleep timer and the ledger tick both touch the player. */
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    private var sleepSuppressed = false
    private var pausedAtElapsedMs = 0L

    /** A queue just set by `PlayerController.playBook` arrives already positioned, rewind included. */
    private var queuePositioned = false

    /**
     * Single-flight guard for [triggerReconnect]. Plain Boolean, not Atomic/synchronized: every
     * read/write happens on this service's main-thread player callbacks or `playerScope`
     * (Main.immediate), never off-thread.
     */
    private var reconnecting = false

    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPrefs(this)
        ledger = PositionLedger(this)
        plexPrefs = PlexGraph.prefs(this)
        connectionManager = PlexGraph.connections(this)
        db = PlexGraph.db(this)
        val resolvingFactory =
            ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { dataSpec ->
                resolveStreamingUri(dataSpec)
            }
        player =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus= */ true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        player.setPlaybackSpeed(coerceSpeed(prefs.speed))
        player.addListener(PlayerEvents())
        sleepTimer = SleepTimer(player, playerScope)
        effects = AudioEffects(prefs)
        // The session id changes when the AudioTrack is recreated, so the effects follow it rather
        // than being created once. Attaching eagerly too means an EQ screen has band info before
        // the first play (the id is unset until then on some devices, which attach() ignores).
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioSessionIdChanged(
                    eventTime: AnalyticsListener.EventTime,
                    audioSessionId: Int,
                ) = effects.attach(audioSessionId)
            },
        )
        effects.attach(player.audioSessionId)
        session = MediaLibrarySession.Builder(this, player, Callback()).build()
    }

    // Non-null per the MediaLibraryService contract: the platform never calls this before onCreate
    // has run (which is what sets [session]) or after onDestroy has torn the service down.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session!!

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        writeLedger(ProgressSyncWorker.STATE_PAUSED)
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        writeLedger(ProgressSyncWorker.STATE_STOPPED)
        tickJob?.cancel()
        playerScope.cancel()
        session?.release()
        session = null
        // Effects hold the player's audio session — release them before the player goes away.
        effects.release()
        player.release()
        super.onDestroy()
    }

    /**
     * Captures the position synchronously on the player thread and hands it to [PositionLedger],
     * which writes Room before anything else and never waits on the network.
     */
    private fun writeLedger(state: String) {
        val item = player.currentMediaItem ?: return
        val bookId = currentBookId() ?: return
        val trackId = item.mediaId.toIntOrNull() ?: return
        ledger.record(bookId, trackId, player.currentPosition.coerceAtLeast(0), state)
    }

    private fun currentBookId(): Int? = player.currentMediaItem?.mediaMetadata?.extras?.getInt(EXTRA_BOOK_ID)

    /**
     * Android Auto tapped a `book/<id>` browse leaf: build the real track queue and resume point,
     * the same way `PlayerController.playBook` does (tracks from Room, [resumePoint] then the same
     * rewind-on-resume math as `PlayerController.rewound`, which is private there so it's mirrored
     * here rather than shared). Null when the book has no synced tracks yet — Auto has no UI to
     * kick off a sync, so there's nothing more useful to do than fail the request.
     *
     * Skips `PlayerController.consumePlexOffsets`: that folds a Plex `viewOffset` mirror into the
     * ledger, which is a nice-to-have write, not a read anything here depends on — the ledger row
     * is still the primary source of truth and furthest-ahead still wins on the next app open.
     */
    private suspend fun resolveBookQueue(bookId: Int): MediaSession.MediaItemsWithStartPosition? {
        val tracks = db.trackDao().getTracksForBook(bookId).first()
        if (tracks.isEmpty()) return null
        val ledgerRow = db.positionDao().getPosition(bookId)
        val resume = resumePoint(tracks, ledgerRow)
        val start = autoRewound(tracks, resume, ledgerRow)
        val items = tracks.map { it.toAutoQueueItem(bookId, plexPrefs) }
        return MediaSession.MediaItemsWithStartPosition(items, start.trackIndex, start.positionMs)
    }

    /** Mirrors `PlayerController.rewound` (private there) — see [resolveBookQueue]. */
    private fun autoRewound(
        tracks: List<Track>,
        resume: ResumePoint,
        ledgerRow: PlaybackPosition?,
    ): ResumePoint {
        if (ledgerRow == null) return resume
        val rewindMs =
            rewindOnResumeMs(
                prefs.rewindMode,
                System.currentTimeMillis() - ledgerRow.updatedAtEpochMs,
                prefs.fixedRewindSec * 1_000L,
            )
        if (rewindMs <= 0) return resume
        val absoluteMs = absolutePositionMs(tracks, resume.trackIndex, resume.positionMs)
        return resumePointAt(tracks, absoluteMs - rewindMs)
    }

    /**
     * Rewrites a Plex stream request's scheme+authority to whatever [PlexConnectionManager] has
     * most recently settled on, resolved at request time instead of once at `playBook`. This is
     * what survives a mid-session LAN->remote/relay switch (CLAUDE.md gotcha #3): the queue's
     * MediaItems never change, only where each load actually goes. Anything that isn't a Plex part
     * URL — including a downloaded track's content:// URI — passes through untouched, as does any
     * request while no connection has been chosen yet.
     */
    private fun resolveStreamingUri(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != "http" && uri.scheme != "https") return dataSpec
        if (uri.path?.contains("/library/parts/") != true) return dataSpec
        val base = plexPrefs.chosenServerUri
        if (base.isEmpty()) return dataSpec
        val baseUri = base.toUri()
        return dataSpec.withUri(
            uri.buildUpon().scheme(baseUri.scheme).authority(baseUri.authority).build(),
        )
    }

    /**
     * Re-races the Plex connection after a load failure and retries once it settles, so the very
     * next request through [resolveStreamingUri] picks up a live server. Single-flight via
     * [reconnecting]: a burst of load errors off one dead connection triggers one race, not a stack
     * of them. If the race fails (still offline), this does nothing further — the player surfaces
     * its normal error/paused state and position is already safe in the ledger. Not a retry loop:
     * each subsequent [Player.Listener.onPlayerError] call can trigger one more of these, but this
     * function itself never re-arms.
     */
    private fun triggerReconnect() {
        if (reconnecting) return
        reconnecting = true
        val resumePlaying = player.playWhenReady
        playerScope.launch {
            // PlexConnectionManager.connect() always re-races (unlike ensureConnected(), which
            // short-circuits once a connection is recorded) — exactly the "force" behavior needed
            // here, so no new API on that class.
            val winner = runCatching { connectionManager.connect() }.getOrNull()
            reconnecting = false
            if (winner != null) {
                // No setMediaItems/seekTo(0) — prepare() alone resumes from the current position.
                player.prepare()
                if (resumePlaying) player.play()
            }
        }
    }

    private fun startLedgerTick() {
        tickJob?.cancel()
        tickJob =
            playerScope.launch {
                while (true) {
                    delay(LEDGER_TICK_MS)
                    writeLedger(ProgressSyncWorker.STATE_PLAYING)
                }
            }
    }

    /**
     * Rewind-on-resume (PRD P1) for a resume inside this process, where [pausedAtElapsedMs] is the
     * pause clock — monotonic and inclusive of device sleep, so a phone in a pocket all night still
     * measures the whole night. A resume *after* the process died gets its rewind in
     * `PlayerController.playBook` instead, off the ledger row's durable timestamp.
     */
    private fun maybeRewindOnResume() {
        if (queuePositioned || pausedAtElapsedMs == 0L) return
        val rewindMs =
            rewindOnResumeMs(
                prefs.rewindMode,
                SystemClock.elapsedRealtime() - pausedAtElapsedMs,
                prefs.fixedRewindSec * 1_000L,
            )
        if (rewindMs > 0) player.seekWithinBook(-rewindMs)
    }

    /**
     * PRD "Sleep-window spec": any transition *into* playing inside the window arms a fresh timer.
     * Hooking the player state means a bluetooth or media-button resume arms it identically to
     * on-screen play — the whole point of the feature.
     */
    private fun maybeArmSleepWindow() {
        val pausedFor = SystemClock.elapsedRealtime() - pausedAtElapsedMs
        if (sleepSuppressed && pausedAtElapsedMs != 0L && pausedFor > SESSION_GAP_MS) {
            sleepSuppressed = false
        }
        if (!prefs.autoSleepEnabled || sleepSuppressed) return
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        if (isInWindow(now, prefs.windowStartMinutesOfDay, prefs.windowEndMinutesOfDay)) {
            sleepTimer.arm(prefs.defaultDurationMin * 60_000L)
        }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Ledger trigger: play and pause (whatever the source — UI, notification, bluetooth,
            // or ExoPlayer pausing us on audio-focus loss).
            writeLedger(
                if (isPlaying) ProgressSyncWorker.STATE_PLAYING else ProgressSyncWorker.STATE_PAUSED,
            )
            if (isPlaying) {
                startLedgerTick()
            } else {
                tickJob?.cancel()
                pausedAtElapsedMs = SystemClock.elapsedRealtime()
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            // Ledger trigger: audio-focus loss, recorded explicitly rather than trusting that the
            // resulting pause always reaches onIsPlayingChanged.
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                writeLedger(ProgressSyncWorker.STATE_PAUSED)
            }
            // Sleep-window arm rule lives here rather than in onIsPlayingChanged: isPlaying also
            // goes false→true on every buffering stall, and a stall over a Plex stream must not
            // silently restart a running timer. playWhenReady only moves when playback is actually
            // asked to start or stop — by the UI, the notification, a media button or bluetooth,
            // all of which reach Player.play() through the session, so one hook covers them all.
            if (playWhenReady) {
                maybeRewindOnResume()
                maybeArmSleepWindow()
                queuePositioned = false
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            // A whole new queue means the caller chose the start position; anything else (an
            // automatic roll into the next file) leaves the rewind rule alone.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) queuePositioned = true
            // Ledger trigger: track transition. The new item's position is the resume point now.
            writeLedger(
                if (player.isPlaying) {
                    ProgressSyncWorker.STATE_PLAYING
                } else {
                    ProgressSyncWorker.STATE_PAUSED
                },
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // Ledger trigger: seek (including the sleep timer's rewind to the fade start).
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                writeLedger(ProgressSyncWorker.STATE_PAUSED)
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            // Every speed change lands here whatever set it, so this is the one place per-book
            // memory has to be written. The global stays the fallback a new book starts from.
            prefs.speed = playbackParameters.speed
            currentBookId()?.let { prefs.setSpeedFor(it, playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Mid-session reconnect: only worth re-racing when the failure was an IO error (the
            // codes ExoPlayer uses for network/HTTP failures, 2000..2999) on a streaming item — a
            // downloaded track's content:// read failing is a different problem re-racing can't fix.
            val isIoError = error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..2999
            val isStreaming =
                player.currentMediaItem?.localConfiguration?.uri?.scheme
                    ?.let { it == "http" || it == "https" } == true
            if (isIoError && isStreaming) triggerReconnect()
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(COMMAND_ARM_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_CANCEL_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_SET_BOOST, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_SET_EQ_ENABLED, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_SET_EQ_PRESET, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_SET_EQ_BAND, Bundle.EMPTY))
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> =
            when (customCommand.customAction) {
                COMMAND_ARM_SLEEP -> {
                    val durationMs = args.getLong(KEY_SLEEP_DURATION_MS)
                    if (durationMs == SLEEP_END_OF_CHAPTER) {
                        sleepTimer.armEndOfChapter()
                    } else {
                        sleepTimer.arm(durationMs)
                    }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_CANCEL_SLEEP -> {
                    sleepTimer.cancel()
                    // Manual cancel means "not tonight" — no auto re-arm this session.
                    sleepSuppressed = true
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_SET_BOOST -> {
                    effects.setBoost(args.getInt(KEY_BOOST_MB))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_SET_EQ_ENABLED -> {
                    effects.setEqEnabled(args.getBoolean(KEY_EQ_ENABLED))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_SET_EQ_PRESET -> {
                    effects.setPreset(args.getInt(KEY_EQ_PRESET, EQ_PRESET_CUSTOM))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_SET_EQ_BAND -> {
                    effects.setBand(
                        args.getInt(KEY_EQ_BAND),
                        args.getShort(KEY_EQ_BAND_LEVEL_MB),
                    )
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else ->
                    Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                    )
            }

        /**
         * MediaItems lose their URI crossing the session boundary; re-attach it from the request.
         * A single `book/<id>` item (an Auto browse leaf added without a pre-resolved position)
         * is resolved into the book's real track queue instead — see [resolveBookQueue]. Anything
         * else, including the phone UI's own already-built track items, is untouched.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val bookId = mediaItems.singleOrNull()?.mediaId?.let(::parseBookMediaId)
            if (bookId == null) {
                return Futures.immediateFuture(
                    mediaItems.mapTo(mutableListOf()) { item ->
                        item.requestMetadata.mediaUri
                            ?.let { item.buildUpon().setUri(it).build() }
                            ?: item
                    },
                )
            }
            val future = SettableFuture.create<MutableList<MediaItem>>()
            playerScope.launch {
                val resolved = resolveBookQueue(bookId)
                future.set(resolved?.mediaItems?.toMutableList() ?: mutableListOf())
            }
            return future
        }

        /**
         * The phone UI's own `PlayerController.playBook` already sends real, resolved track items
         * with the start index/position it computed — those pass straight through to the default
         * behavior untouched. A single `book/<id>` item (Android Auto playing a browse leaf) is
         * resolved here instead: real tracks plus the resume point, via [resolveBookQueue].
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val bookId = mediaItems.singleOrNull()?.mediaId?.let(::parseBookMediaId)
                ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            playerScope.launch {
                val resolved = resolveBookQueue(bookId)
                if (resolved != null) {
                    future.set(resolved)
                } else {
                    future.setException(IllegalStateException("Auto: book $bookId has no synced tracks"))
                }
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(autoRootItem(), params))

        /** Root: two folders. Continue listening: the ledger, most recent first. Library: A-Z. */
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            playerScope.launch {
                val children: List<MediaItem>? =
                    when (parentId) {
                        AUTO_ROOT_ID -> listOf(autoContinueFolderItem(), autoLibraryFolderItem())
                        AUTO_CONTINUE_ID ->
                            db.positionDao().getRecent(AUTO_CONTINUE_LIMIT).first()
                                .mapNotNull { db.bookDao().getBook(it.bookId).first() }
                                .map { it.toAutoBrowseItem(plexPrefs) }
                        AUTO_LIBRARY_ID ->
                            db.bookDao().getAllBooks().first()
                                .take(AUTO_LIBRARY_LIMIT)
                                .map { it.toAutoBrowseItem(plexPrefs) }
                        else -> null
                    }
                future.set(
                    if (children != null) {
                        LibraryResult.ofItemList(children, params)
                    } else {
                        LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    },
                )
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val folder =
                when (mediaId) {
                    AUTO_ROOT_ID -> autoRootItem()
                    AUTO_CONTINUE_ID -> autoContinueFolderItem()
                    AUTO_LIBRARY_ID -> autoLibraryFolderItem()
                    else -> null
                }
            if (folder != null) return Futures.immediateFuture(LibraryResult.ofItem(folder, null))
            val bookId =
                parseBookMediaId(mediaId)
                    ?: return Futures.immediateFuture(
                        LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE),
                    )
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            playerScope.launch {
                val book = db.bookDao().getBook(bookId).first()
                future.set(
                    if (book != null) {
                        LibraryResult.ofItem(book.toAutoBrowseItem(plexPrefs), null)
                    } else {
                        LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    },
                )
            }
            return future
        }
    }
}
