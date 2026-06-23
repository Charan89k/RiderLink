package com.example.riderlink.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riderlink.audio.TokenGenerator
import com.example.riderlink.audio.TrackInfo
import com.example.riderlink.firebase.FirebaseRoomRepository
import com.example.riderlink.firebase.RoomDetails
import com.example.riderlink.service.IntercomService
import com.example.riderlink.Config
import io.livekit.android.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val roomRepository = FirebaseRoomRepository(context)

    // Configuration states
    val livekitUrl = MutableStateFlow(Config.DEFAULT_LIVEKIT_URL)
    val apiKey = MutableStateFlow(Config.DEFAULT_LIVEKIT_API_KEY)
    val apiSecret = MutableStateFlow(Config.DEFAULT_LIVEKIT_API_SECRET)
    val riderName = MutableStateFlow("Rider-${Random.nextInt(100, 1000)}")

    // Service binding state
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private var intercomService: IntercomService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected to ViewModel")
            val binder = service as IntercomService.LocalBinder
            intercomService = binder.getService()
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected from ViewModel")
            intercomService = null
            _isServiceBound.value = false
        }
    }

    // Combine service flows and exposed view model states
    val connectionState: StateFlow<Room.State> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.connectionState ?: flowOf(Room.State.DISCONNECTED)
        else flowOf(Room.State.DISCONNECTED)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Room.State.DISCONNECTED)

    val roomCode: StateFlow<String?> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.roomCode ?: flowOf(null)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val participants: StateFlow<List<String>> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.intercomClient?.participants ?: flowOf(emptyList())
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

    val isAutoPauseEnabled: StateFlow<Boolean> = _isServiceBound.flatMapLatest { bound ->
        if (bound) intercomService?.isAutoPauseEnabled ?: flowOf(false)
        else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun shareSong(title: String, artist: String) {
        intercomService?.intercomClient?.shareSong(title, artist)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        intercomService?.setAutoPauseEnabled(enabled)
    }

    fun clearSharedTrack() {
        intercomService?.intercomClient?.clearSharedTrack()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        bindIntercomService()
    }

    private fun bindIntercomService() {
        val intent = Intent(context, IntercomService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun clearError() {
        _error.value = null
    }

    fun createRoom() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 1. Create Room document in Firebase/Simulation
                val roomDetails = roomRepository.createRoom(
                    livekitUrl = livekitUrl.value,
                    apiKey = apiKey.value,
                    apiSecret = apiSecret.value
                )

                // 2. Generate a connection token for host
                val token = TokenGenerator.generateToken(
                    apiKey = roomDetails.livekitApiKey,
                    apiSecret = roomDetails.livekitApiSecret,
                    roomName = roomDetails.roomCode,
                    identity = riderName.value
                )

                // 3. Start foreground service
                startServiceForeground(roomDetails.roomCode)

                // 4. Connect to Room
                connectServiceToRoom(roomDetails.livekitUrl, token, roomDetails.roomCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room", e)
                _error.value = "Failed to create room: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinRoom(code: String) {
        if (code.length != 4) {
            _error.value = "Room code must be exactly 4 digits"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 1. Query Room from Firebase/Simulation
                val roomDetails = roomRepository.joinRoom(code)
                if (roomDetails == null) {
                    _error.value = "Room not found. Check the code."
                    return@launch
                }

                // 2. Generate connection token for joining participant
                val token = TokenGenerator.generateToken(
                    apiKey = roomDetails.livekitApiKey,
                    apiSecret = roomDetails.livekitApiSecret,
                    roomName = roomDetails.roomCode,
                    identity = riderName.value
                )

                // 3. Start foreground service
                startServiceForeground(roomDetails.roomCode)

                // 4. Connect to Room
                connectServiceToRoom(roomDetails.livekitUrl, token, roomDetails.roomCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error joining room", e)
                _error.value = "Failed to join room: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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

    private fun connectServiceToRoom(url: String, token: String, roomCode: String) {
        viewModelScope.launch {
            // Wait for service binding if needed
            while (intercomService == null) {
                kotlinx.coroutines.delay(100)
            }
            intercomService?.connectToRoom(url, token, roomCode)
        }
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
