package com.example.riderlink.domain.model

/**
 * The connection state the rider is actually in.
 *
 * This is deliberately separate from LiveKit's `Room.State`: the UI must never
 * show "Connected" because a Room object exists. Every value here corresponds to
 * something a rider can act on.
 */
enum class ConnectionStatus {
    /** No ride joined. The lobby is showing. */
    IDLE,

    /** Fetching a token and opening the room. */
    CONNECTING,

    /** Live. Audio is flowing. */
    CONNECTED,

    /** The link dropped and we are trying to restore it without ending the ride. */
    RECONNECTING,

    /** The ride ended, deliberately or otherwise. */
    DISCONNECTED,

    /** Something failed and needs the rider's attention. */
    ERROR;

    /** True while the session is alive, including while it is being repaired. */
    val isActive: Boolean
        get() = this == CONNECTING || this == CONNECTED || this == RECONNECTING

    /** True only when audio can actually be heard and sent. */
    val isLive: Boolean
        get() = this == CONNECTED

    val label: String
        get() = when (this) {
            IDLE -> "OFFLINE"
            CONNECTING -> "CONNECTING"
            CONNECTED -> "CONNECTED"
            RECONNECTING -> "RECONNECTING"
            DISCONNECTED -> "DISCONNECTED"
            ERROR -> "ERROR"
        }
}
