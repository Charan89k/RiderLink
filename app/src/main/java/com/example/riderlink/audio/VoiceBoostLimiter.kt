package com.example.riderlink.audio

import kotlin.math.exp
import kotlin.math.min

/**
 * Decides how much gain is safe to apply to an incoming voice track.
 *
 * Boosting by a fixed multiplier is what makes amplified speech sound bad: quiet
 * talkers get no louder than the ceiling allows, and loud ones clip into a buzz.
 * This watches the real peak level of each audio frame and pulls the gain back
 * only as far as it has to, then lets it climb again once the loud passage ends.
 *
 * Gain drops almost instantly and recovers slowly. A limiter that released
 * quickly would audibly pump between syllables; a slow release means one shouted
 * word does not leave the next sentence quiet for long.
 *
 * This is an envelope-following limiter, not a look-ahead brickwall: it reacts
 * to a frame after measuring it, so a transient inside a single 10ms frame can
 * still pass through. It prevents the sustained clipping that actually makes
 * speech unintelligible, which is what matters here.
 *
 * Pure and frame-driven, so its behaviour is unit tested directly.
 */
class VoiceBoostLimiter(
    /** Peak the output is held under, about -1 dBFS. Leaves headroom for the mixer. */
    private val ceiling: Float = DEFAULT_CEILING,
    /** Time constant for pulling gain down. Short: clipping must stop now. */
    private val attackMs: Float = DEFAULT_ATTACK_MS,
    /** Time constant for letting gain back up. Long: avoids pumping. */
    private val releaseMs: Float = DEFAULT_RELEASE_MS,
) {

    private var currentGain = 1.0f

    /** The gain actually being applied, after limiting. */
    val gain: Float get() = currentGain

    /**
     * Folds one frame of audio into the gain decision.
     *
     * @param peak highest absolute sample in the frame, normalised to 0..1.
     * @param targetGain the gain the rider asked for.
     * @param frameMs how much audio this frame represents.
     * @return the gain to apply now.
     */
    fun process(peak: Float, targetGain: Float, frameMs: Float): Float {
        require(frameMs > 0f) { "frameMs must be positive, was $frameMs" }

        // At or below unity there is nothing to protect against: stay out of the
        // way entirely so "Normal" is bit-for-bit the unboosted experience.
        if (targetGain <= 1.0f) {
            currentGain = targetGain
            return currentGain
        }

        val safePeak = peak.coerceAtLeast(0f)
        val headroomGain = if (safePeak < SILENCE_FLOOR) {
            // Silence carries no information about how loud the talker is, so
            // hold the target rather than winding gain up against noise.
            targetGain
        } else {
            min(targetGain, ceiling / safePeak)
        }

        val timeConstant = if (headroomGain < currentGain) attackMs else releaseMs
        val alpha = 1f - exp(-frameMs / timeConstant)
        currentGain += (headroomGain - currentGain) * alpha

        currentGain = currentGain.coerceIn(MIN_GAIN, targetGain)
        return currentGain
    }

    /** Called when a track goes away, so the next one does not inherit its state. */
    fun reset() {
        currentGain = 1.0f
    }

    companion object {
        const val DEFAULT_CEILING = 0.89f
        const val DEFAULT_ATTACK_MS = 5f
        const val DEFAULT_RELEASE_MS = 600f

        /** Never attenuate below this, however loud the talker: silence is worse. */
        const val MIN_GAIN = 0.5f

        /** Below this peak a frame is treated as silence. */
        const val SILENCE_FLOOR = 0.001f
    }
}
