package io.github.brandonscollins.yarn.player

import kotlinx.coroutines.flow.MutableStateFlow

/** How long "Up next" counts down after a book ends before the next one starts. */
const val AUTO_ADVANCE_SECONDS = 10

/** The book about to start because the previous one finished, and how long until it does. */
data class PendingNext(
    val bookId: Int,
    val title: String,
    val secondsLeft: Int,
)

/**
 * Shared between [PlaybackService] (which runs the countdown) and the UI (which shows it and can
 * cancel it). Same process, so a plain flow is enough: the UI cancels by setting it to null, and
 * the service's countdown checks it every second.
 *
 * ponytail: the cancel lives only in-app. The car and the notification show no countdown and
 * can't cancel it; pressing pause after the next book starts is the out there.
 */
object AutoAdvanceState {
    val pending = MutableStateFlow<PendingNext?>(null)

    fun cancel() {
        pending.value = null
    }
}
