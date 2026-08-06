package io.github.brandonscollins.yarn.data.plex

import io.github.brandonscollins.yarn.data.plex.model.PlexConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRaceTest {
    private val local = PlexConnection(uri = "http://192.168.1.5:32400", local = true)
    private val remote = PlexConnection(uri = "https://remote.plex.direct:32400")

    @Test
    fun `first success wins even though the local candidate is tried first`() =
        runTest {
            val winner =
                raceConnections(listOf(local, remote)) { uri ->
                    delay(if (uri == local.uri) 5_000 else 100)
                    true
                }

            assertEquals(remote.uri, winner)
            // Proves the slow sibling was cancelled rather than awaited.
            assertEquals(100L, currentTime)
        }

    @Test
    fun `all candidates failing returns null`() =
        runTest {
            val winner =
                raceConnections(listOf(local, remote)) { uri ->
                    delay(50)
                    if (uri == remote.uri) throw IOException("unreachable") else false
                }

            assertNull(winner)
        }

    @Test
    fun `race gives up at the timeout`() =
        runTest {
            val winner =
                raceConnections(listOf(local, remote), timeoutMs = CONNECT_TIMEOUT_MS) {
                    delay(60_000)
                    true
                }

            assertNull(winner)
            assertEquals(CONNECT_TIMEOUT_MS, currentTime)
        }
}
