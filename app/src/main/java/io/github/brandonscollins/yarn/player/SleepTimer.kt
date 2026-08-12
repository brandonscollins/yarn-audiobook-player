package io.github.brandonscollins.yarn.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Fade length before the timer pauses — PRD says ~20–30s. */
const val FADE_MS = 25_000L
private const val FADE_STEPS = 50
private const val TICK_MS = 1_000L

/**
 * Remaining sleep-timer ms, or null when unarmed.
 *
 * ponytail: process-global StateFlow rather than session-extras IPC — the service and the UI share
 * one process. If Yarn ever runs the service in its own process, publish it via session extras.
 */
object SleepState {
    val remainingMs = MutableStateFlow<Long?>(null)
}

/**
 * Is [nowMinutesOfDay] inside the window? Start inclusive, end exclusive, and windows that cross
 * midnight (21:30 → 06:00) are the normal case for this feature.
 */
fun isInWindow(
    nowMinutesOfDay: Int,
    startMinutesOfDay: Int,
    endMinutesOfDay: Int,
): Boolean =
    if (startMinutesOfDay <= endMinutesOfDay) {
        nowMinutesOfDay >= startMinutesOfDay && nowMinutesOfDay < endMinutesOfDay
    } else {
        nowMinutesOfDay >= startMinutesOfDay || nowMinutesOfDay < endMinutesOfDay
    }

/**
 * Wall-clock ms left in the current chapter at [speed] — the "end of chapter" countdown, and what
 * it compares against [FADE_MS] to decide when to start fading. An unknown duration (media3's
 * `TIME_UNSET`, or a track still preparing) never triggers the fade, because guessing here would
 * stop playback in the middle of a chapter.
 */
fun chapterRemainingMs(
    durationMs: Long,
    positionMs: Long,
    speed: Float,
): Long {
    if (durationMs <= 0 || speed <= 0f) return Long.MAX_VALUE
    return ((durationMs - positionMs).coerceAtLeast(0) / speed).toLong()
}

/**
 * Counts down, then fades out and pauses, rewinding to where the fade began so no half-heard
 * sentence is lost (PRD "Sleep-window spec"). [scope] must dispatch on the player's thread.
 */
class SleepTimer(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    val remainingMs: StateFlow<Long?> = SleepState.remainingMs

    private var job: Job? = null

    /** Arms a fresh timer, replacing any running one. */
    fun arm(durationMs: Long) {
        job?.cancel()
        player.volume = 1f
        SleepState.remainingMs.value = durationMs
        job =
            scope.launch {
                var left = durationMs
                while (left > 0) {
                    delay(TICK_MS)
                    // The timer measures listening time, so a pause freezes it rather than
                    // burning down to a fade over silence.
                    if (!player.isPlaying) continue
                    left -= TICK_MS
                    SleepState.remainingMs.value = left.coerceAtLeast(0)
                }
                fadeAndPause()
                SleepState.remainingMs.value = null
            }
    }

    /**
     * Same ending as [arm] — fade, pause, rewind to the fade start — but the deadline is the end of
     * whatever chapter is playing rather than a duration, recomputed each tick so a seek or a skip
     * to another chapter moves it.
     */
    fun armEndOfChapter() {
        job?.cancel()
        player.volume = 1f
        publishChapterRemaining()
        job =
            scope.launch {
                while (true) {
                    delay(TICK_MS)
                    if (!player.isPlaying) continue
                    if (publishChapterRemaining() <= FADE_MS) break
                }
                fadeAndPause()
                SleepState.remainingMs.value = null
            }
    }

    /** Publishes the countdown; a chapter whose length isn't known yet reads 0:00 for one tick. */
    private fun publishChapterRemaining(): Long {
        val left =
            chapterRemainingMs(
                player.duration,
                player.currentPosition,
                player.playbackParameters.speed,
            )
        SleepState.remainingMs.value = if (left == Long.MAX_VALUE) 0L else left
        return left
    }

    /** Aborts the timer and undoes any fade in progress. */
    fun cancel() {
        job?.cancel()
        job = null
        player.volume = 1f
        SleepState.remainingMs.value = null
    }

    private suspend fun fadeAndPause() {
        val trackIndex = player.currentMediaItemIndex
        val positionBeforeFade = player.currentPosition
        try {
            repeat(FADE_STEPS) { step ->
                player.volume = 1f - (step + 1) / FADE_STEPS.toFloat()
                delay(FADE_MS / FADE_STEPS)
            }
            player.pause()
            player.seekTo(trackIndex, positionBeforeFade)
        } finally {
            player.volume = 1f
        }
    }
}
