package io.github.brandonscollins.yarn.player

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.brandonscollins.yarn.work.ProgressSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Set on every MediaItem so the ledger knows which book a track belongs to. */
const val EXTRA_BOOK_ID = "yarn.bookId"

const val COMMAND_ARM_SLEEP = "yarn.ARM_SLEEP"
const val COMMAND_CANCEL_SLEEP = "yarn.CANCEL_SLEEP"
const val KEY_SLEEP_DURATION_MS = "yarn.sleepDurationMs"

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
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var prefs: PlayerPrefs
    private lateinit var ledger: PositionLedger
    private lateinit var sleepTimer: SleepTimer
    private var session: MediaSession? = null

    /** Player-thread scope: the sleep timer and the ledger tick both touch the player. */
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    private var sleepSuppressed = false
    private var pausedAtElapsedMs = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPrefs(this)
        ledger = PositionLedger(this)
        player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus= */ true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        player.setPlaybackSpeed(prefs.speed)
        player.addListener(PlayerEvents())
        sleepTimer = SleepTimer(player, playerScope)
        session = MediaSession.Builder(this, player).setCallback(Callback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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
        player.release()
        super.onDestroy()
    }

    /**
     * Captures the position synchronously on the player thread and hands it to [PositionLedger],
     * which writes Room before anything else and never waits on the network.
     */
    private fun writeLedger(state: String) {
        val item = player.currentMediaItem ?: return
        val bookId = item.mediaMetadata.extras?.getInt(EXTRA_BOOK_ID) ?: return
        val trackId = item.mediaId.toIntOrNull() ?: return
        ledger.record(bookId, trackId, player.currentPosition.coerceAtLeast(0), state)
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
            if (playWhenReady) maybeArmSleepWindow()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
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
            prefs.speed = playbackParameters.speed
        }
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(COMMAND_ARM_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_CANCEL_SLEEP, Bundle.EMPTY))
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
                    sleepTimer.arm(args.getLong(KEY_SLEEP_DURATION_MS))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_CANCEL_SLEEP -> {
                    sleepTimer.cancel()
                    // Manual cancel means "not tonight" — no auto re-arm this session.
                    sleepSuppressed = true
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else ->
                    Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                    )
            }

        /** MediaItems lose their URI crossing the session boundary; re-attach it from the request. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.mapTo(mutableListOf()) { item ->
                    item.requestMetadata.mediaUri
                        ?.let { item.buildUpon().setUri(it).build() }
                        ?: item
                },
            )
    }
}
