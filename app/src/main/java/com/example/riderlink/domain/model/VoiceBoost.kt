package com.example.riderlink.domain.model

/**
 * How much to lift incoming rider voice before it reaches the helmet speakers.
 *
 * Helmet speakers are small and a moving bike is loud, so the stock output is
 * often not enough. These gains are applied to the LiveKit voice tracks only,
 * never to the phone's own audio, so music keeps its own level.
 *
 * The values are conservative starting points. Above roughly 1.75x the limiter
 * ends up working hard enough on normal speech that intelligibility starts to
 * suffer, which defeats the point: the goal is louder *and* clearer.
 */
enum class VoiceBoost(val gain: Float, val label: String) {
    NORMAL(1.00f, "Normal"),
    BOOST(1.25f, "Boost"),
    HIGH(1.50f, "High"),
    MAXIMUM(1.75f, "Maximum");

    /** Nothing to process at unity: the whole chain is bypassed. */
    val isActive: Boolean get() = gain > 1.0f

    /** "125%" -- more meaningful to a rider than "1.25x". */
    val percentLabel: String get() = "${(gain * 100).toInt()}%"

    companion object {
        val DEFAULT = NORMAL

        fun fromName(name: String?): VoiceBoost =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
