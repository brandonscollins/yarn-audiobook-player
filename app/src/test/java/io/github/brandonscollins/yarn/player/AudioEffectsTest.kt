package io.github.brandonscollins.yarn.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEffectsTest {
    @Test
    fun `boost gain clamps to the 0 to 1200 mB range`() {
        assertEquals(0, coerceBoostMb(-500))
        assertEquals(0, coerceBoostMb(0))
        assertEquals(600, coerceBoostMb(600))
        assertEquals(MAX_BOOST_MB, coerceBoostMb(MAX_BOOST_MB))
        assertEquals(MAX_BOOST_MB, coerceBoostMb(9_000))
    }

    @Test
    fun `speed clamps to the engine range instead of being rejected`() {
        assertEquals(MIN_SPEED, coerceSpeed(0.1f), 0f)
        assertEquals(MAX_SPEED, coerceSpeed(4f), 0f)
        // Any value in between survives — the engine is continuous, not preset-only.
        assertEquals(1.37f, coerceSpeed(1.37f), 0f)
    }

    @Test
    fun `band levels round-trip through the csv encoding`() {
        val levels = shortArrayOf(-1500, 0, 300, 1500, -75)
        assertArrayEquals(levels, decodeBandLevels(encodeBandLevels(levels)))
    }

    @Test
    fun `empty band levels round-trip as empty`() {
        assertArrayEquals(ShortArray(0), decodeBandLevels(encodeBandLevels(ShortArray(0))))
        assertArrayEquals(ShortArray(0), decodeBandLevels(""))
    }

    @Test
    fun `junk in stored band levels is dropped rather than crashing`() {
        assertArrayEquals(shortArrayOf(100, 200), decodeBandLevels("100,oops,200,999999"))
    }
}
