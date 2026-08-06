package io.github.brandonscollins.yarn.settings

import android.content.Context
import java.util.UUID

/** SharedPreferences-backed Plex session state. */
class PlexPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("plex_prefs", Context.MODE_PRIVATE)

    var accountToken: String
        get() = prefs.getString(KEY_ACCOUNT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCOUNT_TOKEN, value).apply()

    /** Stable per-install identifier sent as `X-Plex-Client-Identifier`. Generated once. */
    val clientUuid: String
        get() {
            val existing = prefs.getString(KEY_CLIENT_UUID, null)
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_UUID, generated).apply()
            return generated
        }

    var serverId: String
        get() = prefs.getString(KEY_SERVER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_ID, value).apply()

    var serverToken: String
        get() = prefs.getString(KEY_SERVER_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_TOKEN, value).apply()

    var libraryId: String
        get() = prefs.getString(KEY_LIBRARY_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIBRARY_ID, value).apply()

    /** The winning connection URI from the connection race (CLAUDE.md gotcha #3). */
    var chosenServerUri: String
        get() = prefs.getString(KEY_CHOSEN_SERVER_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CHOSEN_SERVER_URI, value).apply()

    private companion object {
        const val KEY_ACCOUNT_TOKEN = "account_token"
        const val KEY_CLIENT_UUID = "client_uuid"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_LIBRARY_ID = "library_id"
        const val KEY_CHOSEN_SERVER_URI = "chosen_server_uri"
    }
}
