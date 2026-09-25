package com.example.riderlink.domain.model

/** What a rider is doing right now, in priority order of what matters to show. */
enum class RiderState {
    SPEAKING,
    PRIVATE,
    MUTED,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED;

    val label: String
        get() = when (this) {
            SPEAKING -> "Speaking"
            PRIVATE -> "Private chat"
            MUTED -> "Muted"
            CONNECTED -> "Connected"
            RECONNECTING -> "Reconnecting"
            DISCONNECTED -> "Disconnected"
        }
}

/**
 * One participant in the ride.
 *
 * @param identity the LiveKit identity, used as a stable key and for private chat targeting.
 * @param displayName what the rider is called on screen.
 * @param isLocal true for the rider holding this phone.
 */
data class Rider(
    val identity: String,
    val displayName: String,
    val isLocal: Boolean,
    val state: RiderState
) {
    /** Up to two letters for the avatar, derived from the display name. */
    val initials: String
        get() {
            val words = displayName.trim().split(Regex("[\\s\\-_]+")).filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> "?"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "${words[0].first()}${words[1].first()}".uppercase()
            }
        }
}
