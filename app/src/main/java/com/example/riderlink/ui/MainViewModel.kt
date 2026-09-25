package com.example.riderlink.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riderlink.audio.TrackInfo
import com.example.riderlink.domain.model.AudioRoute
import com.example.riderlink.domain.model.ConnectionStatus
import com.example.riderlink.domain.model.Rider
import com.example.riderlink.domain.model.VoiceBoost
import com.example.riderlink.firebase.FirebaseRoomRepository
import com.example.riderlink.firebase.RoomDetails
import com.example.riderlink.service.IntercomService
import com.example.riderlink.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val roomRepository = FirebaseRoomRepository(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("riderlink_settings", Context.MODE_PRIVATE)

    // Configuration states. The LiveKit URL is no longer configured here: the
    // token server returns it alongside each token, so there is one source of truth.
    // Persisted like every other setting: a call sign that resets to a random
    // number on each launch would make riders unrecognisable to each other, and
    // a token server URL that resets would silently point at the default.
    val tokenServerUrl = MutableStateFlow(
        prefs.getString(KEY_TOKEN_SERVER, null) ?: Config.DEFAULT_TOKEN_SERVER_URL
    )
    val riderName = MutableStateFlow(
        prefs.getString(KEY_RIDER_NAME, null) ?: "Rider-${Random.nextInt(100, 1000)}"
    )

    // Service binding state
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private var intercomService: IntercomService? = null

    // Audio stream max volumes
    val maxVoiceVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
    val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private val _voiceVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL))
    val voiceVolume: StateFlow<Int> = _voiceVolume.asStateFlow()

    private val _musicVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val musicVolume: StateFlow<Int> = _musicVolume.asStateFlow()

    // Dynamic voice processing filter variables
    private val _noiseSuppressionEnabled = MutableStateFlow(prefs.getBoolean("noise_suppression", true))
    val noiseSuppressionEnabled: StateFlow<Boolean> = _noiseSuppressionEnabled.asStateFlow()

    private val _echoCancellationEnabled = MutableStateFlow(prefs.getBoolean("echo_cancellation", true))
    val echoCancellationEnabled: StateFlow<Boolean> = _echoCancellationEnabled.asStateFlow()

    private val _autoGainControlEnabled = MutableStateFlow(prefs.getBoolean("auto_gain_control", true))
    val autoGainControlEnabled: StateFlow<Boolean> = _autoGainControlEnabled.asStateFlow()

    private val _highPassFilterEnabled = MutableStateFlow(prefs.getBoolean("high_pass_filter", true))
    val highPassFilterEnabled: StateFlow<Boolean> = _highPassFilterEnabled.asStateFlow()

    private val _audioModeVoip = MutableStateFlow(prefs.getBoolean("audio_mode_voip", true))
    val audioModeVoip: StateFlow<Boolean> = _audioModeVoip.asStateFlow()

    private val _isVolumeBoostEnabled = MutableStateFlow(prefs.getBoolean("volume_boost", false))
    val isVolumeBoostEnabled: StateFlow<Boolean> = _isVolumeBoostEnabled.asStateFlow()

    /** How much to lift incoming rider voice. Set once before a ride. */
    private val _voiceBoost = MutableStateFlow(VoiceBoost.fromName(prefs.getString(KEY_VOICE_BOOST, null)))
    val voiceBoost: StateFlow<VoiceBoost> = _voiceBoost.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected to ViewModel")
            val binder = service as IntercomService.LocalBinder
            val srv = binder.getService()
            intercomService = srv
            _isServiceBound.value = true

            // Push the saved audio preferences into the service as soon as it
            // exists, so a ride started from a cold launch is already configured.
            srv.setAudioModeVoip(_audioModeVoip.value)
            srv.setVoiceBoost(_voiceBoost.value)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected from ViewModel")
            intercomService = null
            _isServiceBound.value = false
        }
    }

    // Combine service flows and exposed view model states
    val connectionStatus: StateFlow<ConnectionStatus> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.connectionStatus ?: flowOf(ConnectionStatus.IDLE)
        else flowOf(ConnectionStatus.IDLE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.IDLE)

    val reconnectAttempt: StateFlow<Int> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.reconnectAttempt ?: flowOf(0) else flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val audioRoute: StateFlow<AudioRoute> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.audioRoute ?: flowOf(AudioRoute()) else flowOf(AudioRoute())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AudioRoute())

    val roomCode: StateFlow<String?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.roomCode ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val riders: StateFlow<List<Rider>> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.riders ?: flowOf(emptyList())
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isMuted: StateFlow<Boolean> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.isMuted ?: flowOf(false)
        else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val localTrack: StateFlow<TrackInfo?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.localTrack ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sharedTrack: StateFlow<TrackInfo?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.sharedTrack ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeSpeaker: StateFlow<String?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.activeSpeaker ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val privateChatParticipant: StateFlow<String?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.privateChatParticipant ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Who the helmet gesture will call. May be set while still in group mode. */
    val privateTarget: StateFlow<String?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.privateTarget ?: flowOf(null) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isAutoPauseEnabled: StateFlow<Boolean> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.isAutoPauseEnabled ?: flowOf(false)
        else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _error = MutableStateFlow<String?>(null)

    /** Errors raised while joining, merged with errors raised mid-ride by the service. */
    val error: StateFlow<String?> = combine(
        _error,
        _isServiceBound.flatMapLatest { bound ->
            if (bound) intercomService?.sessionError ?: flowOf(null) else flowOf(null)
        }
    ) { local, fromService -> local ?: fromService }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Publishes whatever this rider is listening to, for the rest of the group. */
    fun shareCurrentTrack() {
        val track = localTrack.value ?: return
        intercomService?.intercomClient?.shareSong(track.title, track.artist)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        intercomService?.setAutoPauseEnabled(enabled)
    }

    fun clearSharedTrack() {
        intercomService?.intercomClient?.clearSharedTrack()
    }

    // Audio modification functions
    fun setVoiceVolume(volume: Int) {
        _voiceVolume.value = volume
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice volume", e)
        }
    }

    fun setMusicVolume(volume: Int) {
        _musicVolume.value = volume
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting music volume", e)
        }
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        _noiseSuppressionEnabled.value = enabled
        prefs.edit().putBoolean("noise_suppression", enabled).apply()
    }

    fun setEchoCancellationEnabled(enabled: Boolean) {
        _echoCancellationEnabled.value = enabled
        prefs.edit().putBoolean("echo_cancellation", enabled).apply()
    }

    fun setAutoGainControlEnabled(enabled: Boolean) {
        _autoGainControlEnabled.value = enabled
        prefs.edit().putBoolean("auto_gain_control", enabled).apply()
    }

    fun setHighPassFilterEnabled(enabled: Boolean) {
        _highPassFilterEnabled.value = enabled
        prefs.edit().putBoolean("high_pass_filter", enabled).apply()
    }

    fun setAudioModeVoip(useVoip: Boolean) {
        _audioModeVoip.value = useVoip
        prefs.edit().putBoolean("audio_mode_voip", useVoip).apply()
        intercomService?.setAudioModeVoip(useVoip)
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        _isVolumeBoostEnabled.value = enabled
        prefs.edit().putBoolean("volume_boost", enabled).apply()
        if (enabled) {
            // Force maximum volume on streams to bypass low background volume issues
            setVoiceVolume(maxVoiceVolume)
            setMusicVolume(maxMusicVolume)
        }
    }


    companion object {
        private const val TAG = "MainViewModel"
        private const val CODE_LENGTH = 4
        private const val SERVICE_BIND_TIMEOUT_MS = 5_000L
        private const val KEY_RIDER_NAME = "rider_name"
        private const val KEY_TOKEN_SERVER = "token_server_url"
        private const val KEY_VOICE_BOOST = "voice_boost"
    }

    init {
        bindIntercomService()

        // Persist as the rider types rather than on a save button they would
        // have to find with gloves on.
        viewModelScope.launch {
            riderName.collect { prefs.edit().putString(KEY_RIDER_NAME, it).apply() }
        }
        viewModelScope.launch {
            tokenServerUrl.collect { prefs.edit().putString(KEY_TOKEN_SERVER, it).apply() }
        }
    }

    private fun bindIntercomService() {
        val intent = Intent(context, IntercomService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun clearError() {
        _error.value = null
    }

    /** Creates a new ride and joins it. */
    fun createRoom() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val room = roomRepository.createRoom()
                beginRide(room.roomCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating ride", e)
                _error.value = "Could not create the ride. Check your connection."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Joins an existing ride by its 4-digit code. */
    fun joinRoom(code: String) {
        val cleaned = code.trim()
        if (cleaned.length != CODE_LENGTH || !cleaned.all { it.isDigit() }) {
            _error.value = "Ride codes are $CODE_LENGTH digits."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val room = roomRepository.joinRoom(cleaned)
                if (room == null) {
                    _error.value = "No ride found with code $cleaned."
                    return@launch
                }
                beginRide(room.roomCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error joining ride", e)
                _error.value = "Could not join the ride. Check your connection."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Hands the ride to the service, which owns it from here: it fetches the
     * token, connects, and rebuilds the session if the link drops mid-ride.
     */
    private suspend fun beginRide(roomCode: String) {
        startServiceForeground(roomCode)
        awaitServiceBinding()
        intercomService?.startRide(
            tokenServerUrl = tokenServerUrl.value,
            roomCode = roomCode,
            identity = riderName.value
        )
    }

    fun toggleMute() {
        intercomService?.let { service ->
            val currentMute = isMuted.value
            service.intercomClient.setMute(!currentMute)
        }
    }

    fun disconnect() {
        intercomService?.disconnect()
    }

    /** Isolates one rider's audio. Tapping the same rider again returns to the group. */
    fun startPrivateChat(identity: String) {
        intercomService?.startPrivateChatWith(identity)
    }

    fun returnToGroup() {
        intercomService?.returnToGroup()
    }

    /**
     * Chooses who the helmet gesture calls, without switching channel.
     *
     * Long-pressing a rider sets this; tapping opens the channel straight away.
     */
    fun setPrivateTarget(identity: String?) {
        intercomService?.setPrivateTarget(identity)
    }

    fun setVoiceBoost(level: VoiceBoost) {
        _voiceBoost.value = level
        prefs.edit().putString(KEY_VOICE_BOOST, level.name).apply()
        intercomService?.setVoiceBoost(level)
    }

    private fun startServiceForeground(roomCode: String) {
        val intent = Intent(context, IntercomService::class.java).apply {
            action = IntercomService.ACTION_START_FOREGROUND
            putExtra(IntercomService.EXTRA_ROOM_CODE, roomCode)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Waits for the service binding, which is asynchronous even though the
     * service is started synchronously. Bounded so a failed bind surfaces as an
     * error rather than hanging the join button forever.
     */
    private suspend fun awaitServiceBinding() {
        withTimeoutOrNull(SERVICE_BIND_TIMEOUT_MS) {
            while (intercomService == null) {
                kotlinx.coroutines.delay(50)
            }
        } ?: throw IllegalStateException("Intercom service did not start")
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (_isServiceBound.value) {
                context.unbindService(serviceConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service in onCleared", e)
        }
    }
}
