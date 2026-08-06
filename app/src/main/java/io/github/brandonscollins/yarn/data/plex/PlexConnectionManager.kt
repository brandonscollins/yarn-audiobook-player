package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.plex.model.PlexConnection
import io.github.brandonscollins.yarn.settings.PlexPrefs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

const val CONNECT_TIMEOUT_MS = 15_000L

/**
 * Races every candidate connection and returns the first one that answers, or null if they all
 * fail or [timeoutMs] elapses. Local candidates start first, but the race decides — see
 * CLAUDE.md gotcha #3. Kept free of Retrofit/Android so it can be tested directly.
 */
suspend fun raceConnections(
    candidates: List<PlexConnection>,
    timeoutMs: Long = CONNECT_TIMEOUT_MS,
    probe: suspend (String) -> Boolean,
): String? {
    if (candidates.isEmpty()) return null
    val ordered = candidates.sortedByDescending { it.local }
    return withTimeoutOrNull(timeoutMs) {
        coroutineScope {
            val attempts: List<Deferred<String?>> =
                ordered.map { connection ->
                    async {
                        val reached = runCatching { probe(connection.uri) }.getOrDefault(false)
                        if (reached) connection.uri else null
                    }
                }
            try {
                val pending = attempts.toMutableList()
                while (pending.isNotEmpty()) {
                    val (finished, uri) =
                        select<Pair<Deferred<String?>, String?>> {
                            pending.forEach { attempt -> attempt.onAwait { attempt to it } }
                        }
                    if (uri != null) return@coroutineScope uri
                    pending.remove(finished)
                }
                null
            } finally {
                // First success (or the timeout) kills the losing attempts.
                attempts.forEach { it.cancel() }
            }
        }
    }
}

/** Owns the chosen server connection: runs the race, publishes and persists the winner. */
class PlexConnectionManager(
    private val prefs: PlexPrefs,
    private val api: PlexApi,
) {
    private val _chosenUri = MutableStateFlow(prefs.chosenServerUri)
    val chosenUri: StateFlow<String> = _chosenUri.asStateFlow()

    /** Whether the race has run in *this* process. A persisted URI proves nothing about now. */
    @Volatile
    private var raced = false

    /** Re-runs the race. Returns the winning URI, or null if no candidate answered. */
    suspend fun connect(): String? {
        val winner =
            raceConnections(prefs.serverConnections) { uri ->
                api.mediaService.checkServer(uri).isSuccessful
            }
        if (winner != null) {
            api.serverUrl = winner
            prefs.chosenServerUri = winner
            _chosenUri.value = winner
            raced = true
        }
        return winner
    }

    /**
     * Races once per process. The persisted URI is a warm-start hint, not evidence: the LAN address
     * that won last night is unreachable on cellular this morning, and treating it as connected
     * meant the race never re-ran and the app never found the relay (gotcha #3).
     */
    suspend fun ensureConnected(): Boolean = raced || connect() != null
}
