package io.github.brandonscollins.yarn.data.plex

import android.os.Build
import io.github.brandonscollins.yarn.settings.PlexPrefs
import okhttp3.Interceptor
import okhttp3.Response

const val PLEX_PRODUCT = "Yarn"
const val PLEX_PLATFORM = "Android"
const val PLEX_DEVICE = "$PLEX_PRODUCT $PLEX_PLATFORM"

/** Mirrors `versionName` in app/build.gradle.kts (BuildConfig generation is off). */
const val PLEX_CLIENT_VERSION = "0.1"

/**
 * Adds the `X-Plex-*` headers every Plex request needs (CLAUDE.md gotcha #4) and, for server
 * calls, rewrites [PLACEHOLDER_URL] to whichever connection the race picked (gotcha #3).
 *
 * [serverUrl] is null for the plex.tv login service: its endpoints are absolute URLs and it
 * authenticates with the account token rather than the server token.
 */
class PlexInterceptor(
    private val prefs: PlexPrefs,
    private val sessionIdentifier: String,
    private val serverUrl: (() -> String)?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString().let { original ->
            serverUrl?.let { original.replace(PLACEHOLDER_URL, it()) } ?: original
        }
        val token =
            if (serverUrl == null) {
                prefs.accountToken
            } else {
                prefs.serverToken.ifEmpty { prefs.accountToken }
            }

        val builder = request.newBuilder()
            .header("Accept", "application/json")
            .header("X-Plex-Platform", PLEX_PLATFORM)
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .header("X-Plex-Provides", "player")
            .header("X-Plex-Product", PLEX_PRODUCT)
            .header("X-Plex-Version", PLEX_CLIENT_VERSION)
            .header("X-Plex-Device", PLEX_DEVICE)
            .header("X-Plex-Device-Name", Build.MODEL)
            .header("X-Plex-Client-Identifier", prefs.clientUuid)
            .header("X-Plex-Session-Identifier", sessionIdentifier)
            .url(url)
        if (token.isNotEmpty()) builder.header("X-Plex-Token", token)

        return chain.proceed(builder.build())
    }
}
