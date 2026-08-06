package io.github.brandonscollins.yarn.player

import io.github.brandonscollins.yarn.data.model.PlaybackPosition
import io.github.brandonscollins.yarn.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTest {
    /** Three ten-minute tracks; absolute positions are index * 10 min + offset. */
    private fun tracks(vararg viewOffsetsMs: Long) =
        viewOffsetsMs.mapIndexed { i, offset ->
            Track(
                id = 100 + i,
                bookId = 1,
                title = "Track $i",
                index = i + 1,
                durationMs = TEN_MIN,
                partKey = "/library/parts/$i/file.mp3",
                sizeBytes = 0,
                viewOffsetMs = offset,
            )
        }

    private fun ledger(
        trackId: Int,
        positionMs: Long,
    ) = PlaybackPosition(bookId = 1, trackId = trackId, positionMs = positionMs, updatedAtEpochMs = 0)

    @Test
    fun `local ledger ahead of plex wins`() {
        val point = resumePoint(tracks(0, 30_000, 0), ledger(trackId = 102, positionMs = 5_000))

        assertEquals(ResumePoint(trackIndex = 2, positionMs = 5_000), point)
    }

    @Test
    fun `plex viewOffset ahead of the local ledger wins`() {
        val point = resumePoint(tracks(0, 0, 90_000), ledger(trackId = 100, positionMs = 60_000))

        assertEquals(ResumePoint(trackIndex = 2, positionMs = 90_000), point)
    }

    @Test
    fun `equal positions resolve to local, the source of truth`() {
        val point = resumePoint(tracks(0, 42_000, 0), ledger(trackId = 101, positionMs = 42_000))

        assertEquals(ResumePoint(trackIndex = 1, positionMs = 42_000), point)
    }

    @Test
    fun `a later track beats a larger offset in an earlier one`() {
        // 9:59 into track 0 is still behind 0:01 into track 1.
        val point = resumePoint(tracks(0, 1_000, 0), ledger(trackId = 100, positionMs = 599_000))

        assertEquals(ResumePoint(trackIndex = 1, positionMs = 1_000), point)
    }

    @Test
    fun `nothing known starts the book at zero`() {
        assertEquals(ResumePoint(0, 0), resumePoint(tracks(0, 0, 0), ledger = null))
        assertEquals(ResumePoint(0, 0), resumePoint(tracks = emptyList(), ledger = ledger(100, 5_000)))
    }

    @Test
    fun `a ledger row for a track the book no longer has falls back to plex`() {
        val point = resumePoint(tracks(0, 7_000, 0), ledger(trackId = 999, positionMs = 600_000))

        assertEquals(ResumePoint(trackIndex = 1, positionMs = 7_000), point)
    }

    private companion object {
        const val TEN_MIN = 600_000L
    }
}
