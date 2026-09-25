package com.example.riderlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBoostTest {

    @Test
    fun `Normal is unity and is not treated as active processing`() {
        assertEquals(1.0f, VoiceBoost.NORMAL.gain, 0.0001f)
        assertFalse(VoiceBoost.NORMAL.isActive)
    }

    @Test
    fun `every other preset actually boosts`() {
        for (level in VoiceBoost.entries - VoiceBoost.NORMAL) {
            assertTrue("${level.name} should boost", level.isActive)
            assertTrue("${level.name} should exceed unity", level.gain > 1f)
        }
    }

    @Test
    fun `presets increase in order`() {
        val gains = VoiceBoost.entries.map { it.gain }
        assertEquals(gains.sorted(), gains)
    }

    @Test
    fun `the top preset stays within a range a limiter can hold`() {
        // Beyond roughly 2x the limiter is attenuating through most normal
        // speech, which costs clarity for very little extra loudness.
        assertTrue(VoiceBoost.entries.maxOf { it.gain } <= 2.0f)
    }

    @Test
    fun `percent labels read the way a rider expects`() {
        assertEquals("100%", VoiceBoost.NORMAL.percentLabel)
        assertEquals("125%", VoiceBoost.BOOST.percentLabel)
        assertEquals("150%", VoiceBoost.HIGH.percentLabel)
        assertEquals("175%", VoiceBoost.MAXIMUM.percentLabel)
    }

    @Test
    fun `an unknown or missing stored value falls back to Normal`() {
        assertEquals(VoiceBoost.NORMAL, VoiceBoost.fromName(null))
        assertEquals(VoiceBoost.NORMAL, VoiceBoost.fromName(""))
        assertEquals(VoiceBoost.NORMAL, VoiceBoost.fromName("LUDICROUS"))
    }

    @Test
    fun `a stored value round-trips`() {
        for (level in VoiceBoost.entries) {
            assertEquals(level, VoiceBoost.fromName(level.name))
        }
    }
}
