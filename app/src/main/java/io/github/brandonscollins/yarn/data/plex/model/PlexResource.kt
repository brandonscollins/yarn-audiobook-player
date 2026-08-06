package io.github.brandonscollins.yarn.data.plex.model

import kotlinx.serialization.Serializable

/** A `<Device/>` from `/api/v2/resources` — a candidate server for the connection race. */
@Serializable
data class PlexResource(
    val name: String = "",
    val provides: String = "",
    val clientIdentifier: String = "",
    val accessToken: String? = null,
    val owned: Boolean = true,
    val connections: List<PlexConnection> = emptyList(),
)

@Serializable
data class PlexConnection(
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
)
