package com.example.riderlink.audio

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.room.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.livekit.android.events.collect
import kotlinx.coroutines.launch

data class TrackInfo(val title: String, val artist: String)

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

    private val _isSomeoneSpeaking = MutableStateFlow(false)
    val isSomeoneSpeaking: StateFlow<Boolean> = _isSomeoneSpeaking.asStateFlow()

    private val _sharedTrack = MutableStateFlow<TrackInfo?>(null)
    val sharedTrack: StateFlow<TrackInfo?> = _sharedTrack.asStateFlow()

    companion object {
        private const val TAG = "LiveKitIntercomClient"
    }

    suspend fun connect(
        url: String, 
        token: String,
        noiseSuppression: Boolean = true,
        echoCancellation: Boolean = true,
        autoGainControl: Boolean = true,
        highPassFilter: Boolean = true
    ) {
        disconnect() // Clean up any existing connection
        
        Log.d(TAG, "Connecting to LiveKit room at $url (noiseSuppression=$noiseSuppression, echoCancellation=$echoCancellation, AGC=$autoGainControl, HPF=$highPassFilter)")

        // Enable Echo Cancellation, Noise Suppression, Automatic Gain Control, and High Pass Filter dynamically
        val options = RoomOptions(
            audioTrackCaptureDefaults = LocalAudioTrackOptions(
                noiseSuppression = noiseSuppression,
                echoCancellation = echoCancellation,
                autoGainControl = autoGainControl,
                highPassFilter = highPassFilter,
                typingNoiseDetection = false
            )
        )

        // Override default AudioSwitchHandler with NoAudioHandler so BluetoothAudioRouter manages Audio Focus
        val currentRoom = LiveKit.create(
            context,
            overrides = LiveKitOverrides(
                audioOptions = io.livekit.android.AudioOptions(
                    audioHandler = NoAudioHandler()
                )
            ),
            options = options
        )
        room = currentRoom

        // Listen for events to track connection changes, participant listing, speaking, and shared tracks
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
                        if (currentRoom.state == Room.State.DISCONNECTED) {
                            _isSomeoneSpeaking.value = false
                            _sharedTrack.value = null
                        }
                    }
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected -> {
                        updateParticipants()
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        // Mark speaking if any participant is actively speaking
                        val speakers = event.speakers
                        _isSomeoneSpeaking.value = speakers.isNotEmpty()
                        Log.d(TAG, "Active speakers: ${speakers.map { it.identity?.value }}")
                    }
                    is RoomEvent.DataReceived -> {
                        val payload = event.data.toString(Charsets.UTF_8)
                        Log.d(TAG, "Data payload received: $payload")
                        if (payload.startsWith("song_share:")) {
                            val parts = payload.substringAfter("song_share:").split("|")
                            if (parts.size >= 2) {
                                val title = parts[0]
                                val artist = parts[1]
                                _sharedTrack.value = TrackInfo(title, artist)
                                Log.d(TAG, "Received track sync: $title - $artist")
                            }
                        }
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

    fun shareSong(title: String, artist: String) {
        val currentRoom = room ?: return
        if (currentRoom.state != Room.State.CONNECTED) {
            Log.w(TAG, "Cannot share song, room is not connected")
            return
        }
        clientScope.launch {
            try {
                val payload = "song_share:$title|$artist"
                val data = payload.toByteArray(Charsets.UTF_8)
                currentRoom.localParticipant.publishData(
                    data = data,
                    reliability = DataPublishReliability.RELIABLE
                )
                Log.d(TAG, "Successfully published song share: $title - $artist")
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing song share", e)
            }
        }
    }

    fun clearSharedTrack() {
        _sharedTrack.value = null
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
            _isSomeoneSpeaking.value = false
            _sharedTrack.value = null
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

