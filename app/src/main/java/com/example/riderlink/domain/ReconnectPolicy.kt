package com.example.riderlink.domain

import kotlin.math.min
import kotlin.random.Random

/**
 * How long to wait before each reconnection attempt.
 *
 * A rider loses signal in tunnels, under bridges and in valleys, so retrying has
 * to be persistent. It also has to stop: an unbounded retry loop would hold a
 * wakelock and the microphone for the rest of the battery's life. This backs off
 * exponentially, caps the interval, and gives up after a bounded window.
 *
 * Pure and deterministic apart from jitter, so it is unit tested directly.
 */
object ReconnectPolicy {

    /** First retry is almost immediate: most drops are momentary. */
    const val INITIAL_DELAY_MS = 1_000L

    /** Never wait longer than this between attempts. */
    const val MAX_DELAY_MS = 30_000L

    /**
     * After this many attempts the session stops retrying and reports an error.
     * With the delays below that is a little over four minutes of trying.
     */
    const val MAX_ATTEMPTS = 12

    /**
     * Delay before [attempt], counting from 1.
     *
     * A small random jitter is added so that a group of riders who all lost
     * signal in the same tunnel do not stampede the token server in lockstep.
     */
    fun delayFor(attempt: Int, jitter: (Long) -> Long = { Random.nextLong(it) }): Long {
        require(attempt >= 1) { "attempt is 1-based, got $attempt" }
        val exponential = INITIAL_DELAY_MS shl (attempt - 1).coerceAtMost(20)
        val capped = min(exponential, MAX_DELAY_MS)
        return capped + jitter(capped / 4)
    }

    /** False once the session should stop retrying and surface an error. */
    fun shouldRetry(attempt: Int): Boolean = attempt <= MAX_ATTEMPTS
}
