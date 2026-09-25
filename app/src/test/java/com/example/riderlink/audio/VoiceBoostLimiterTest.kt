package com.example.riderlink.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBoostLimiterTest {

    private val frame = 10f

    /** Runs [frames] frames at a constant peak and returns the settled gain. */
    private fun settle(
        limiter: VoiceBoostLimiter,
        peak: Float,
        target: Float,
        frames: Int = 500,
    ): Float {
        var gain = 1f
        repeat(frames) { gain = limiter.process(peak, target, frame) }
        return gain
    }

    @Test
    fun `Normal is a true bypass`() {
        val limiter = VoiceBoostLimiter()
        // Even a signal that is already clipping must not be touched at 1.0x,
        // otherwise "Normal" would quietly differ from having no boost at all.
        assertEquals(1.0f, settle(limiter, peak = 1.0f, target = 1.0f), 0.0001f)
    }

    @Test
    fun `quiet speech receives the full requested boost`() {
        val limiter = VoiceBoostLimiter()
        // 0.2 peak boosted 1.75x is 0.35, well under the ceiling.
        assertEquals(1.75f, settle(limiter, peak = 0.2f, target = 1.75f), 0.01f)
    }

    @Test
    fun `loud speech is held under the ceiling instead of clipping`() {
        val limiter = VoiceBoostLimiter()
        val peak = 0.8f
        val gain = settle(limiter, peak, target = 1.75f)

        assertTrue("gain $gain should have been pulled below the request", gain < 1.75f)
        assertTrue(
            "output ${peak * gain} exceeded the ceiling",
            peak * gain <= VoiceBoostLimiter.DEFAULT_CEILING + 0.02f,
        )
    }

    @Test
    fun `a signal already at full scale is never amplified further`() {
        val limiter = VoiceBoostLimiter()
        val gain = settle(limiter, peak = 1.0f, target = 1.75f)
        assertTrue("gain $gain must not boost a full-scale signal", gain <= 1.0f)
    }

    @Test
    fun `gain drops within a few milliseconds of a loud onset`() {
        val limiter = VoiceBoostLimiter()
        settle(limiter, peak = 0.1f, target = 1.75f)        // quiet, gain wound up
        assertEquals(1.75f, limiter.gain, 0.01f)

        // One shout. Attack must act inside a couple of frames, or the rider
        // hears the clipped transient the limiter exists to prevent.
        repeat(3) { limiter.process(0.95f, 1.75f, frame) }
        assertTrue("gain was still ${limiter.gain} after 30ms", limiter.gain < 1.2f)
    }

    @Test
    fun `gain recovers slowly so speech does not pump between syllables`() {
        val limiter = VoiceBoostLimiter()
        settle(limiter, peak = 0.95f, target = 1.75f)
        val duringLoud = limiter.gain

        // A single quiet frame must not undo the limiting.
        limiter.process(0.05f, 1.75f, frame)
        assertTrue(
            "gain jumped from $duringLoud to ${limiter.gain} in one frame",
            limiter.gain - duringLoud < 0.1f,
        )

        // Given a real pause, it does come back.
        assertEquals(1.75f, settle(limiter, peak = 0.05f, target = 1.75f), 0.05f)
    }

    @Test
    fun `silence holds the requested gain rather than winding up against noise`() {
        val limiter = VoiceBoostLimiter()
        assertEquals(1.5f, settle(limiter, peak = 0.0f, target = 1.5f), 0.01f)
    }

    @Test
    fun `gain never falls far enough to make a rider inaudible`() {
        val limiter = VoiceBoostLimiter()
        // Absurdly hot input: attenuating towards zero would be worse than
        // letting some clipping through, because silence loses the message.
        val gain = settle(limiter, peak = 1.0f, target = 1.75f, frames = 2000)
        assertTrue("gain collapsed to $gain", gain >= VoiceBoostLimiter.MIN_GAIN)
    }

    @Test
    fun `reset clears state so one talker does not inherit another's limiting`() {
        val limiter = VoiceBoostLimiter()
        settle(limiter, peak = 0.95f, target = 1.75f)
        assertTrue(limiter.gain < 1.5f)

        limiter.reset()
        assertEquals(1.0f, limiter.gain, 0.0001f)
    }

    @Test
    fun `every preset stays under the ceiling on loud speech`() {
        for (level in com.example.riderlink.domain.model.VoiceBoost.entries) {
            val limiter = VoiceBoostLimiter()
            val peak = 0.7f
            val gain = settle(limiter, peak, level.gain)
            assertTrue(
                "${level.name}: output ${peak * gain} exceeded the ceiling",
                peak * gain <= VoiceBoostLimiter.DEFAULT_CEILING + 0.02f || level.gain <= 1f,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero-length frame is rejected rather than dividing by zero`() {
        VoiceBoostLimiter().process(0.5f, 1.5f, 0f)
    }
}
