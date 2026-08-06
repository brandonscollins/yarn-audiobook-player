package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.plex.model.PlexResource
import io.github.brandonscollins.yarn.settings.PlexPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val POLL_INTERVAL_MS = 2_000L
private const val POLL_TIMEOUT_MS = 5 * 60_000L

/** A PIN sign-in attempt. Open [authUrl] in a browser, then poll with [PlexAuthRepo.awaitToken]. */
data class PlexPin(
    val id: Long,
    val code: String,
    val authUrl: String,
)

/**
 * plex.tv sign-in and server pick. Single-user server, so Plex Home user switching is skipped
 * entirely — the account token is the only identity we deal with.
 */
class PlexAuthRepo(
    private val prefs: PlexPrefs,
    private val api: PlexApi,
) {
    val isSignedIn: Boolean get() = prefs.accountToken.isNotEmpty()

    val hasServer: Boolean get() = prefs.serverId.isNotEmpty()

    suspend fun requestPin(): PlexPin {
        val pin = api.loginService.postAuthPin()
        return PlexPin(id = pin.id, code = pin.code, authUrl = authUrl(prefs.clientUuid, pin.code))
    }

    /** Polls the pin until the browser sign-in completes. True if a token arrived and was saved. */
    suspend fun awaitToken(
        pinId: Long,
        timeoutMs: Long = POLL_TIMEOUT_MS,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            var token = ""
            while (token.isEmpty()) {
                token = runCatching { api.loginService.getAuthPin(pinId).authToken }.getOrNull().orEmpty()
                if (token.isEmpty()) delay(POLL_INTERVAL_MS)
            }
            prefs.accountToken = token
            true
        } ?: false

    /** Servers on the account, each carrying the connection candidates for the race. */
    suspend fun fetchServers(): List<PlexResource> =
        api.loginService.resources().filter { it.provides.contains("server") }

    /** Persists the pick: machine identifier, server token, and the race's candidates. */
    fun chooseServer(server: PlexResource) {
        prefs.serverId = server.clientIdentifier
        prefs.serverToken = server.accessToken.orEmpty()
        prefs.serverConnections = server.connections
        prefs.chosenServerUri = ""
    }
}

private fun authUrl(
    clientId: String,
    code: String,
): String =
    (
        "https://app.plex.tv/auth#?code=$code" +
            "&context[device][product]=$PLEX_PRODUCT" +
            "&context[device][platform]=$PLEX_PLATFORM" +
            "&context[device][device]=$PLEX_DEVICE" +
            "&context[device][environment]=bundled" +
            "&context[device][layout]=desktop" +
            "&clientID=$clientId"
    )
        .replace("[", "%5B")
        .replace("]", "%5D")
