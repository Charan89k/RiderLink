package com.example.riderlink.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    /** Jitter removed so the shape of the backoff curve is what is under test. */
    private val noJitter: (Long) -> Long = { 0L }

    @Test
    fun `first retry is fast because most drops are momentary`() {
        assertEquals(1_000L, ReconnectPolicy.delayFor(1, noJitter))
    }

    @Test
    fun `delay doubles until it reaches the ceiling`() {
        assertEquals(1_000L, ReconnectPolicy.delayFor(1, noJitter))
        assertEquals(2_000L, ReconnectPolicy.delayFor(2, noJitter))
        assertEquals(4_000L, ReconnectPolicy.delayFor(3, noJitter))
        assertEquals(8_000L, ReconnectPolicy.delayFor(4, noJitter))
        assertEquals(16_000L, ReconnectPolicy.delayFor(5, noJitter))
    }

    @Test
    fun `delay never exceeds the ceiling however long the outage lasts`() {
        for (attempt in 6..60) {
            assertEquals(
                "attempt $attempt should be capped",
                ReconnectPolicy.MAX_DELAY_MS,
                ReconnectPolicy.delayFor(attempt, noJitter),
            )
        }
    }

    @Test
    fun `jitter stays within a quarter of the base delay`() {
        // Riders who lose signal together must not retry in lockstep, but the
        // spread has to stay small enough that recovery still feels immediate.
        repeat(200) {
            val delay = ReconnectPolicy.delayFor(3)
            assertTrue("got $delay", delay >= 4_000L)
            assertTrue("got $delay", delay < 4_000L + 1_000L)
        }
    }

    @Test
    fun `retrying stops so a dead session cannot hold the wakelock forever`() {
        assertTrue(ReconnectPolicy.shouldRetry(1))
        assertTrue(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS))
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS + 1))
    }

    @Test
    fun `the full retry window is long enough to outlast a tunnel`() {
        val total = (1..ReconnectPolicy.MAX_ATTEMPTS).sumOf { ReconnectPolicy.delayFor(it, noJitter) }
        assertTrue("retry window was only ${total / 1000}s", total >= 120_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt numbering is one-based`() {
        ReconnectPolicy.delayFor(0, noJitter)
    }
}
