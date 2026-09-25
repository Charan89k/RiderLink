package com.example.riderlink.domain.model

/** Where the intercom audio is currently going. */
enum class AudioRouteType {
    /** A Bluetooth helmet headset: the intended setup. */
    BLUETOOTH_HEADSET,

    /** Wired earphones plugged into the phone. */
    WIRED_HEADSET,

    /** The phone's own speaker and mic -- usable, but not what this app is for. */
    PHONE,

    /** Nothing determined yet. */
    UNKNOWN
}

/**
 * The active audio route, with the device name when the system tells us one.
 *
 * Riders need to know at a glance whether their helmet is actually carrying the
 * audio, because a silent intercom and a misrouted intercom look identical.
 */
data class AudioRoute(
    val type: AudioRouteType = AudioRouteType.UNKNOWN,
    val deviceName: String? = null
) {
    val isHelmetConnected: Boolean
        get() = type == AudioRouteType.BLUETOOTH_HEADSET

    val label: String
        get() = when (type) {
            AudioRouteType.BLUETOOTH_HEADSET -> deviceName ?: "Helmet connected"
            AudioRouteType.WIRED_HEADSET -> "Wired headset"
            AudioRouteType.PHONE -> "Phone speaker"
            AudioRouteType.UNKNOWN -> "Checking audio…"
        }
}
