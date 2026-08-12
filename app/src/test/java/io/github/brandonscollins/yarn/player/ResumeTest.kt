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

    /** Three ten-minute tracks = a thirty-minute book; the finished window is the last two minutes. */
    @Test
    fun `the finished window is the last two minutes of the book`() {
        val threeTracks = tracks(0, 0, 0)
        val lastTrack = threeTracks[2].id

        // 27:00 — three minutes left, not done.
        assertEquals(false, isBookFinished(threeTracks, THIRTY_MIN, lastTrack, 7 * 60_000L))
        // 28:00 — exactly two minutes left, done (Chronicle compares with <=).
        assertEquals(true, isBookFinished(threeTracks, THIRTY_MIN, lastTrack, 8 * 60_000L))
        // Deep into the last track.
        assertEquals(true, isBookFinished(threeTracks, THIRTY_MIN, lastTrack, TEN_MIN))
        // The same offset in an earlier track is nowhere near the end.
        assertEquals(false, isBookFinished(threeTracks, THIRTY_MIN, threeTracks[0].id, TEN_MIN))
    }

    @Test
    fun `an unknown track or unknown duration is never finished`() {
        val threeTracks = tracks(0, 0, 0)

        assertEquals(false, isBookFinished(threeTracks, THIRTY_MIN, 999, TEN_MIN))
        assertEquals(false, isBookFinished(threeTracks, 0, threeTracks[2].id, TEN_MIN))
    }

    @Test
    fun `an absolute position resolves back to its track`() {
        val threeTracks = tracks(0, 0, 0)

        assertEquals(ResumePoint(0, 0), resumePointAt(threeTracks, 0))
        assertEquals(ResumePoint(1, 1_000), resumePointAt(threeTracks, TEN_MIN + 1_000))
        // A boundary belongs to the track that starts there, not the one that ends there.
        assertEquals(ResumePoint(1, 0), resumePointAt(threeTracks, TEN_MIN))
        // Off either end clamps into the book.
        assertEquals(ResumePoint(0, 0), resumePointAt(threeTracks, -5_000))
        assertEquals(ResumePoint(2, TEN_MIN), resumePointAt(threeTracks, THIRTY_MIN + TEN_MIN))
    }

    @Test
    fun `a pause under ten seconds never rewinds`() {
        assertEquals(0L, rewindOnResumeMs(REWIND_SMART, 9_000, FIXED_MS))
        assertEquals(0L, rewindOnResumeMs(REWIND_FIXED, 9_000, FIXED_MS))
        assertEquals(0L, rewindOnResumeMs(REWIND_OFF, 60 * 60_000L, FIXED_MS))
    }

    @Test
    fun `smart rewind is a tenth of the pause, capped at a minute`() {
        assertEquals(6_000L, rewindOnResumeMs(REWIND_SMART, 60_000, FIXED_MS))
        assertEquals(30_000L, rewindOnResumeMs(REWIND_SMART, 5 * 60_000L, FIXED_MS))
        // Ten minutes reaches the cap; overnight stays there.
        assertEquals(60_000L, rewindOnResumeMs(REWIND_SMART, 10 * 60_000L, FIXED_MS))
        assertEquals(60_000L, rewindOnResumeMs(REWIND_SMART, 8 * 60 * 60_000L, FIXED_MS))
    }

    @Test
    fun `fixed rewind ignores how long the pause was`() {
        assertEquals(FIXED_MS, rewindOnResumeMs(REWIND_FIXED, 11_000, FIXED_MS))
        assertEquals(FIXED_MS, rewindOnResumeMs(REWIND_FIXED, 8 * 60 * 60_000L, FIXED_MS))
    }

    private companion object {
        const val FIXED_MS = 30_000L
        const val TEN_MIN = 600_000L
        const val THIRTY_MIN = 3 * TEN_MIN
    }
}
