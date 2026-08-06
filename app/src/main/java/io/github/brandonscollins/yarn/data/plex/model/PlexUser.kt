package io.github.brandonscollins.yarn.data.plex.model

import kotlinx.serialization.Serializable

@Serializable
data class PlexUser(
    val id: Long = 0,
    val uuid: String = "",
    val title: String = "",
    val username: String? = null,
    val authToken: String? = null,
)
