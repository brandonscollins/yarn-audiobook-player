package io.github.brandonscollins.yarn.data.plex.model

import kotlinx.serialization.Serializable

/** Response from `POST/GET /api/v2/pins` — the PIN sign-in flow. */
@Serializable
data class OAuthPinResponse(
    val id: Long = 0,
    val code: String = "",
    val clientIdentifier: String = "",
    val authToken: String? = null,
)
