package com.example.riderlink.audio

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.track.LocalAudioTrackOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.livekit.android.events.collect
import kotlinx.coroutines.launch

class LiveKitIntercomClient(private val context: Context) {

    private var room: Room? = null
    private val clientJob = SupervisorJob()
    private val clientScope = CoroutineScope(Dispatchers.Main + clientJob)

    private val _connectionState = MutableStateFlow(Room.State.DISCONNECTED)
    val connectionState: StateFlow<Room.State> = _connectionState.asStateFlow()

    private val _participants = MutableStateFlow<List<String>>(emptyList())
    val participants: StateFlow<List<String>> = _participants.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    companion object {
        private const val TAG = "LiveKitIntercomClient"
    }

    suspend fun connect(url: String, token: String) {
        disconnect() // Clean up any existing connection
        
        Log.d(TAG, "Connecting to LiveKit room at $url")

        // Enable Echo Cancellation, Noise Suppression, Automatic Gain Control, and High Pass Filter
        val options = RoomOptions(
            audioTrackCaptureDefaults = LocalAudioTrackOptions(
                noiseSuppression = true,
                echoCancellation = true,
                autoGainControl = true,
                highPassFilter = true,
                typingNoiseDetection = false
            )
        )

        val currentRoom = LiveKit.create(context, options = options)
        room = currentRoom

        // Listen for events to track connection changes and participant listing
        clientScope.launch {
            currentRoom.events.collect { event ->
                Log.d(TAG, "LiveKit room event: $event")
                when (event) {
                    is RoomEvent.Connected,
                    is RoomEvent.Disconnected,
                    is RoomEvent.Reconnecting,
                    is RoomEvent.Reconnected -> {
                        _connectionState.value = currentRoom.state
                        updateParticipants()
                    }
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected -> {
                        updateParticipants()
                    }
                    else -> {}
                }
            }
        }

        try {
            currentRoom.connect(url, token)
            Log.d(TAG, "Successfully connected to LiveKit room")
            _connectionState.value = currentRoom.state
            
            // Enable microphone by default and configure voice processing
            currentRoom.localParticipant.setMicrophoneEnabled(true)
            _isMuted.value = !currentRoom.localParticipant.isMicrophoneEnabled
            
            updateParticipants()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to LiveKit room", e)
            _connectionState.value = Room.State.DISCONNECTED
            throw e
        }
    }

    fun setMute(muted: Boolean) {
        val currentRoom = room ?: return
        clientScope.launch {
            currentRoom.localParticipant.setMicrophoneEnabled(!muted)
            _isMuted.value = muted
            Log.d(TAG, "Mute state updated: $muted")
        }
    }

    fun disconnect() {
        val currentRoom = room ?: return
        Log.d(TAG, "Disconnecting from LiveKit room")
        clientScope.launch {
            try {
                currentRoom.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting room", e)
            }
            room = null
            _connectionState.value = Room.State.DISCONNECTED
            _participants.value = emptyList()
            _isMuted.value = false
        }
    }

    private fun updateParticipants() {
        val currentRoom = room ?: return
        val list = mutableListOf<String>()
        // Include local participant if connected
        if (currentRoom.state == Room.State.CONNECTED) {
            val localIdentity = currentRoom.localParticipant.identity?.value ?: "Me"
            list.add("$localIdentity (Me)")
        }
        // Include remote participants
        currentRoom.remoteParticipants.forEach { (_, participant) ->
            val identity = participant.identity?.value ?: "Rider"
            list.add(identity)
        }
        _participants.value = list
        Log.d(TAG, "Updated participants: $list")
    }
}
