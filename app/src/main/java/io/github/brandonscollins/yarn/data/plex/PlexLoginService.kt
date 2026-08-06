package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.plex.model.OAuthPinResponse
import io.github.brandonscollins.yarn.data.plex.model.PlexResource
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/** Talks to plex.tv: PIN sign-in and server discovery. Always hits plex.tv, never the server. */
interface PlexLoginService {
    @POST("https://plex.tv/api/v2/pins.json?strong=true")
    suspend fun postAuthPin(): OAuthPinResponse

    @GET("https://plex.tv/api/v2/pins/{id}.json")
    suspend fun getAuthPin(
        @Path("id") id: Long,
    ): OAuthPinResponse

    /** Candidate servers/connections for the connection race (CLAUDE.md gotcha #3). */
    @GET("https://plex.tv/api/v2/resources")
    suspend fun resources(
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
    ): List<PlexResource>
}
