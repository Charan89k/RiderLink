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
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import com.example.riderlink.domain.model.ConnectionStatus
import com.example.riderlink.domain.model.VoiceBoost
import com.example.riderlink.domain.model.Rider
import com.example.riderlink.domain.model.RiderState
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

    /** Lifts incoming rider voice for helmet speakers. See [VoiceBoostController]. */
    private val voiceBoost = VoiceBoostController(clientScope)

    /** The state the UI renders. Never derived from the existence of a Room object. */
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _riders = MutableStateFlow<List<Rider>>(emptyList())
    val riders: StateFlow<List<Rider>> = _riders.asStateFlow()

    /** Set when a disconnect was not requested by the rider, so callers can retry. */
    private val _lastFailure = MutableStateFlow<String?>(null)
    val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()

    /** Distinguishes "the rider hung up" from "the link died". */
    private var disconnectRequested = false

    /**
     * Raised when LiveKit's own reconnection has given up and the session needs
     * to be rebuilt from outside, with a fresh token. The service watches this.
     */
    private val _sessionLost = MutableStateFlow(false)
    val sessionLost: StateFlow<Boolean> = _sessionLost.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSomeoneSpeaking = MutableStateFlow(false)
    val isSomeoneSpeaking: StateFlow<Boolean> = _isSomeoneSpeaking.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking: StateFlow<Boolean> = _isRemoteSpeaking.asStateFlow()

    private val _activeSpeaker = MutableStateFlow<String?>(null)
    val activeSpeaker: StateFlow<String?> = _activeSpeaker.asStateFlow()

    private val _sharedTrack = MutableStateFlow<TrackInfo?>(null)
    val sharedTrack: StateFlow<TrackInfo?> = _sharedTrack.asStateFlow()

    var privateChatParticipantIdentity: String? = null
        private set

    companion object {
        private const val TAG = "LiveKitIntercomClient"
    }

    suspend fun connect(
        url: String, 
        token: String,
        noiseSuppression: Boolean = true,
        echoCancellation: Boolean = true,
        autoGainControl: Boolean = true,
        highPassFilter: Boolean = true,
        useVoip: Boolean = true
    ) {
        disconnect() // Clean up any existing connection
        disconnectRequested = false
        _sessionLost.value = false
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _lastFailure.value = null
        
        Log.d(TAG, "Connecting to LiveKit room at $url (noiseSuppression=$noiseSuppression, echoCancellation=$echoCancellation, AGC=$autoGainControl, HPF=$highPassFilter, useVoip=$useVoip)")

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

        val audioOutputType = if (useVoip) {
            io.livekit.android.AudioType.CallAudioType()
        } else {
            io.livekit.android.AudioType.MediaAudioType()
        }

        // Override default AudioSwitchHandler with NoAudioHandler so BluetoothAudioRouter manages Audio Focus
        val currentRoom = LiveKit.create(
            context,
            overrides = LiveKitOverrides(
                audioOptions = io.livekit.android.AudioOptions(
                    audioHandler = NoAudioHandler(),
                    audioOutputType = audioOutputType
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
                    is RoomEvent.Connected -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _lastFailure.value = null
                        _sessionLost.value = false
                        updateParticipants()
                    }
                    is RoomEvent.Reconnecting -> {
                        _connectionStatus.value = ConnectionStatus.RECONNECTING
                        updateParticipants()
                    }
                    is RoomEvent.Reconnected -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _lastFailure.value = null
                        _sessionLost.value = false
                        updateParticipants()
                    }
                    is RoomEvent.Disconnected -> {
                        // A disconnect the rider did not ask for is a failure the
                        // session supervisor should try to recover from.
                        if (disconnectRequested) {
                            _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        } else {
                            // LiveKit only emits Disconnected after its own retries
                            // have failed, so from here recovery is our problem.
                            _lastFailure.value = event.error?.message ?: "Connection lost"
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                            _sessionLost.value = true
                        }
                        _isSomeoneSpeaking.value = false
                        _isRemoteSpeaking.value = false
                        _activeSpeaker.value = null
                        _sharedTrack.value = null
                        privateChatParticipantIdentity = null
                        updateParticipants()
                    }
                    is RoomEvent.ParticipantConnected -> {
                        updateParticipants()
                        val target = privateChatParticipantIdentity
                        if (target != null) {
                            val newParticipant = event.participant
                            if (newParticipant.identity?.value != target) {
                                newParticipant.audioTrackPublications.forEach { pub ->
                                    val remotePub = pub as Any as? RemoteTrackPublication
                                    remotePub?.setSubscribed(false)
                                }
                            }
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        updateParticipants()
                    }
                    is RoomEvent.TrackMuted -> {
                        if (event.participant.isLocalIn(currentRoom)) _isMuted.value = true
                        // Without this the roster would keep showing "Connected" for
                        // a rider who has muted themselves.
                        updateParticipants()
                    }
                    is RoomEvent.TrackUnmuted -> {
                        if (event.participant.isLocalIn(currentRoom)) _isMuted.value = false
                        updateParticipants()
                    }
                    is RoomEvent.TrackPublished -> {
                        val target = privateChatParticipantIdentity
                        if (target != null) {
                            val participant = event.participant
                            if (participant.identity?.value != target) {
                                val pub = event.publication
                                val remotePub = pub as Any as? RemoteTrackPublication
                                remotePub?.setSubscribed(false)
                            }
                        }
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val target = privateChatParticipantIdentity
                        if (target != null) {
                            val participant = event.participant
                            if (participant.identity?.value != target) {
                                val pub = event.publication
                                val remotePub = pub as Any as? RemoteTrackPublication
                                remotePub?.setSubscribed(false)
                            }
                        }
                        (event.track as? RemoteAudioTrack)?.let { audioTrack ->
                            event.publication.sid?.let { sid -> voiceBoost.attach(sid, audioTrack) }
                        }
                        updateParticipants()
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        // The SDK names this field `publications`, singular object.
                        event.publications.sid?.let(voiceBoost::detach)
                        updateParticipants()
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val speakers = event.speakers
                        _isSomeoneSpeaking.value = speakers.isNotEmpty()
                        
                        val localIdentity = currentRoom.localParticipant.identity?.value
                        val hasRemoteSpeaker = speakers.any { it.identity?.value != localIdentity }
                        _isRemoteSpeaking.value = hasRemoteSpeaker
                        
                        val primarySpeaker = speakers.firstOrNull()?.identity?.value
                        _activeSpeaker.value = primarySpeaker
                        updateParticipants()
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
            _connectionStatus.value = ConnectionStatus.CONNECTED
            
            // Enable the microphone and record that intent directly.
            //
            // Reading isMicrophoneEnabled back straight after enabling returns a
            // stale false while the track is still being negotiated, which made
            // the dashboard announce MIC MUTED to a rider who was in fact
            // transmitting. The authoritative state now comes from this call
            // succeeding, and from the TrackMuted/TrackUnmuted events below.
            currentRoom.localParticipant.setMicrophoneEnabled(true)
            _isMuted.value = false
            
            updateParticipants()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to LiveKit room", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastFailure.value = e.message
            throw e
        }
    }

    /** Sets the incoming-voice boost level. Applies to tracks already playing. */
    fun setVoiceBoost(level: VoiceBoost) {
        voiceBoost.setBoost(level)
    }

    fun setMute(muted: Boolean) {
        val currentRoom = room ?: return
        clientScope.launch {
            currentRoom.localParticipant.setMicrophoneEnabled(!muted)
            _isMuted.value = muted
            updateParticipants()
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
        disconnectRequested = true
        val currentRoom = room ?: run {
            _connectionStatus.value = ConnectionStatus.IDLE
            return
        }
        Log.d(TAG, "Disconnecting from LiveKit room")
        voiceBoost.detachAll()
        clientScope.launch {
            try {
                currentRoom.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting room", e)
            }
            room = null
            _connectionStatus.value = ConnectionStatus.IDLE
            _riders.value = emptyList()
            _isMuted.value = false
            _isSomeoneSpeaking.value = false
            _isRemoteSpeaking.value = false
            _activeSpeaker.value = null
            _sharedTrack.value = null
            _lastFailure.value = null
            _sessionLost.value = false
        }
    }

    fun getRoom(): Room? = room

    fun isolateParticipant(targetIdentity: String) {
        val currentRoom = room ?: return
        privateChatParticipantIdentity = targetIdentity
        
        currentRoom.remoteParticipants.forEach { (_, participant) ->
            val isTarget = participant.identity?.value == targetIdentity
            participant.audioTrackPublications.forEach { pub ->
                val remotePub = pub as Any as? RemoteTrackPublication
                remotePub?.setSubscribed(isTarget)
            }
        }
        updateParticipants()
        Log.d(TAG, "Isolated participant: $targetIdentity")
    }

    fun resetPrivateChat() {
        val currentRoom = room ?: return
        privateChatParticipantIdentity = null
        
        currentRoom.remoteParticipants.forEach { (_, participant) ->
            participant.audioTrackPublications.forEach { pub ->
                val remotePub = pub as Any as? RemoteTrackPublication
                remotePub?.setSubscribed(true)
            }
        }
        updateParticipants()
        Log.d(TAG, "Returned to group intercom, unmuted all participant streams")
    }

    /**
     * Rebuilds the rider roster from the room's current state.
     *
     * Called on every event that can change what a rider's row should say, so the
     * list is always a snapshot of reality rather than an accumulated guess.
     */
    /** True when this event came from the rider holding the phone. */
    private fun io.livekit.android.room.participant.Participant.isLocalIn(room: Room): Boolean =
        identity?.value != null && identity?.value == room.localParticipant.identity?.value

    private fun updateParticipants() {
        val currentRoom = room
        if (currentRoom == null) {
            _riders.value = emptyList()
            return
        }

        val reconnecting = currentRoom.state == Room.State.RECONNECTING
        val privateTarget = privateChatParticipantIdentity
        val speakerIdentity = _activeSpeaker.value
        val roster = mutableListOf<Rider>()

        if (currentRoom.state != Room.State.DISCONNECTED) {
            val localIdentity = currentRoom.localParticipant.identity?.value ?: "Me"
            roster += Rider(
                identity = localIdentity,
                displayName = currentRoom.localParticipant.name?.takeIf { it.isNotBlank() } ?: localIdentity,
                isLocal = true,
                state = when {
                    reconnecting -> RiderState.RECONNECTING
                    _isMuted.value -> RiderState.MUTED
                    speakerIdentity == localIdentity -> RiderState.SPEAKING
                    else -> RiderState.CONNECTED
                }
            )
        }

        currentRoom.remoteParticipants.forEach { (_, participant) ->
            val identity = participant.identity?.value ?: return@forEach
            val muted = participant.audioTrackPublications
                .mapNotNull { it.first as? RemoteTrackPublication }
                .let { pubs -> pubs.isNotEmpty() && pubs.all { it.muted } }

            roster += Rider(
                identity = identity,
                displayName = participant.name?.takeIf { it.isNotBlank() } ?: identity,
                isLocal = false,
                state = when {
                    reconnecting -> RiderState.RECONNECTING
                    privateTarget == identity -> RiderState.PRIVATE
                    speakerIdentity == identity -> RiderState.SPEAKING
                    muted -> RiderState.MUTED
                    else -> RiderState.CONNECTED
                }
            )
        }

        // Stable, meaningful order. remoteParticipants is a Map, so without an
        // explicit sort the rows reshuffle whenever it rehashes -- rows moving
        // under a gloved thumb is how you open a private channel by accident.
        //
        // The private rider sorts above even the local row: the panel is only
        // tall enough for a row or two once the dashboard's controls have their
        // space, and you already know who you are. Who you are locked to is the
        // thing worth the top slot.
        _riders.value = roster.sortedWith(
            compareByDescending<Rider> { it.state == RiderState.PRIVATE }
                .thenByDescending { it.isLocal }
                .thenBy { it.displayName.lowercase() }
        )
    }
}

