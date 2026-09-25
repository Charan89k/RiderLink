package com.example.riderlink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.riderlink.MainActivity
import com.example.riderlink.R
import com.example.riderlink.audio.BluetoothAudioRouter
import com.example.riderlink.audio.LiveKitIntercomClient
import com.example.riderlink.audio.TokenService
import com.example.riderlink.audio.TrackInfo
import com.example.riderlink.data.NetworkMonitor
import com.example.riderlink.domain.ReconnectPolicy
import com.example.riderlink.domain.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class IntercomService : Service(), TextToSpeech.OnInitListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var audioRouter: BluetoothAudioRouter

    /** Where intercom audio is going, for the helmet indicator. */
    val audioRoute get() = audioRouter.audioRoute
    lateinit var intercomClient: LiveKitIntercomClient
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    /**
     * Everything needed to rebuild the session after a drop. Held by the service
     * rather than the ViewModel because the ride outlives the Activity.
     */
    private data class RideSession(
        val tokenServerUrl: String,
        val roomCode: String,
        val identity: String
    )

    private var session: RideSession? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private lateinit var networkMonitor: NetworkMonitor

    /** Surfaced so the dashboard can show which attempt is in flight. */
    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _reconnectAttempt.asStateFlow()

    private val _sessionError = MutableStateFlow<String?>(null)
    val sessionError: StateFlow<String?> = _sessionError.asStateFlow()

    val isAutoPauseEnabled = MutableStateFlow(false)
    val localTrack = MutableStateFlow<TrackInfo?>(null)

    // Advanced features
    private var mediaSession: MediaSession? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _privateChatParticipant = MutableStateFlow<String?>(null)
    val privateChatParticipant: StateFlow<String?> = _privateChatParticipant.asStateFlow()

    private var privateChatParticipantIdentity: String? = null

    private var originalMusicVolume: Int? = null
    private lateinit var audioManager: AudioManager
    private var maxMusicVolume: Int = 0

    // Click counter engine variables
    private var nextClickJob: kotlinx.coroutines.Job? = null
    private var prevClickJob: kotlinx.coroutines.Job? = null
    private var nextClickCount = 0
    private var prevClickCount = 0
    private var isDispatchingInternal = false

    inner class LocalBinder : Binder() {
        fun getService(): IntercomService = this@IntercomService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating IntercomService")
        audioRouter = BluetoothAudioRouter(this)
        intercomClient = LiveKitIntercomClient(this)
        networkMonitor = NetworkMonitor(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize and configure MediaSession
        mediaSession = MediaSession(this, "RiderLinkIntercomSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    if (event.action != KeyEvent.ACTION_DOWN) {
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                    val keyCode = event.keyCode
                    Log.d(TAG, "Media button event intercepted: keyCode = $keyCode")
                    
                    if (isDispatchingInternal) {
                        Log.d(TAG, "Passing through internally dispatched event")
                        return false
                    }
                    
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                        handleMediaNextClick()
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                        handleMediaPreviousClick()
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        handleMediaPlayPauseClick()
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            val state = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
            setPlaybackState(state)
            isActive = true
        }

        val prefs = getSharedPreferences("riderlink_settings", Context.MODE_PRIVATE)
        isAutoPauseEnabled.value = prefs.getBoolean("auto_pause", false)

        // Listen to client connection, mute states, and speaking states to update notifications and audio focus
        serviceScope.launch {
            launch {
                intercomClient.connectionState.collect {
                    updateNotification()
                }
            }
            launch {
                intercomClient.isMuted.collect {
                    updateNotification()
                }
            }
            launch {
                intercomClient.isSomeoneSpeaking.collect { someoneSpeaking ->
                    Log.d(TAG, "isSomeoneSpeaking flow changed: $someoneSpeaking, autoPause=${isAutoPauseEnabled.value}")
                    if (someoneSpeaking && isAutoPauseEnabled.value) {
                        audioRouter.requestAudioFocus(exclusive = true)
                    } else {
                        audioRouter.requestAudioFocus(exclusive = false)
                    }
                }
            }
            launch {
                intercomClient.isRemoteSpeaking.collect { remoteSpeaking ->
                    Log.d(TAG, "isRemoteSpeaking flow changed: $remoteSpeaking, autoPause=${isAutoPauseEnabled.value}")
                    if (remoteSpeaking) {
                        duckMusicVolume()
                    } else {
                        restoreMusicVolume()
                    }
                }
            }
        }

        // Rebuild the session whenever LiveKit's own retries have run out.
        serviceScope.launch {
            intercomClient.sessionLost.collect { lost ->
                if (lost) startReconnectLoop() else cancelReconnectLoop()
            }
        }

        // Capture metadata changes from the Notification Listener Service
        IntercomNotificationListenerService.onMetadataChangedListener = { title, artist ->
            Log.d(TAG, "Notification listener track change: $title by $artist")
            localTrack.value = TrackInfo(title, artist)
            updateNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                val code = intent.getStringExtra(EXTRA_ROOM_CODE) ?: ""
                _roomCode.value = code
                acquireWakeLock()
                startForegroundServiceCompat()
                audioRouter.start()
            }
            ACTION_STOP_SERVICE -> {
                disconnect()
            }
            ACTION_TOGGLE_MUTE -> {
                val currentMute = intercomClient.isMuted.value
                intercomClient.setMute(!currentMute)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Starts a ride and keeps it alive.
     *
     * The service, not the ViewModel, fetches the access token: it is the only
     * component that survives the Activity, so it is the only one that can
     * re-authenticate after a drop halfway through a ride.
     */
    fun startRide(tokenServerUrl: String, roomCode: String, identity: String) {
        session = RideSession(tokenServerUrl, roomCode, identity)
        _roomCode.value = roomCode
        _sessionError.value = null
        _reconnectAttempt.value = 0

        serviceScope.launch {
            runCatching { openConnection() }
                .onFailure { error ->
                    Log.e(TAG, "Initial connection failed", error)
                    _sessionError.value = friendlyError(error)
                }
        }
    }

    /** Fetches a fresh token and opens the room with the rider's saved audio settings. */
    private suspend fun openConnection() {
        val active = session ?: error("No ride session")
        val prefs = getSharedPreferences("riderlink_settings", Context.MODE_PRIVATE)
        val useVoip = prefs.getBoolean("audio_mode_voip", true)
        audioRouter.setAudioModeVoip(useVoip)

        val credentials = TokenService.fetchCredentials(
            tokenServerUrl = active.tokenServerUrl,
            roomCode = active.roomCode,
            identity = active.identity
        )

        intercomClient.connect(
            url = credentials.serverUrl,
            token = credentials.token,
            noiseSuppression = prefs.getBoolean("noise_suppression", true),
            echoCancellation = prefs.getBoolean("echo_cancellation", true),
            autoGainControl = prefs.getBoolean("auto_gain_control", true),
            highPassFilter = prefs.getBoolean("high_pass_filter", true),
            useVoip = useVoip
        )
    }

    /**
     * Retries the connection with backoff for as long as the policy allows,
     * waiting for the radio to come back rather than failing against a dead one.
     */
    private fun startReconnectLoop() {
        if (reconnectJob?.isActive == true) return
        if (session == null) return

        reconnectJob = serviceScope.launch {
            var attempt = 1
            while (ReconnectPolicy.shouldRetry(attempt)) {
                _reconnectAttempt.value = attempt
                updateNotification()

                delay(ReconnectPolicy.delayFor(attempt))

                // No point dialling out with the radio down: wait for it.
                networkMonitor.isOnline.first { it }

                Log.d(TAG, "Reconnect attempt $attempt")
                val result = runCatching { openConnection() }
                if (result.isSuccess) {
                    Log.d(TAG, "Reconnected on attempt $attempt")
                    _reconnectAttempt.value = 0
                    _sessionError.value = null
                    updateNotification()
                    return@launch
                }
                Log.w(TAG, "Reconnect attempt $attempt failed", result.exceptionOrNull())
                attempt++
            }

            Log.e(TAG, "Giving up after ${ReconnectPolicy.MAX_ATTEMPTS} attempts")
            _sessionError.value = "Could not restore the ride. Check your signal and rejoin."
            _reconnectAttempt.value = 0
            updateNotification()
        }
    }

    private fun cancelReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectAttempt.value = 0
    }

    /** Turns an exception into something worth showing a rider at 80km/h. */
    private fun friendlyError(error: Throwable): String = when {
        error is java.net.UnknownHostException -> "No internet connection."
        error is java.net.SocketTimeoutException -> "The token server did not respond."
        error.message?.contains("Token server returned 4") == true -> "This ride code was rejected."
        else -> "Could not join the ride. Please try again."
    }

    fun setAudioModeVoip(useVoip: Boolean) {
        audioRouter.setAudioModeVoip(useVoip)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        isAutoPauseEnabled.value = enabled
        val prefs = getSharedPreferences("riderlink_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_pause", enabled).apply()
        
        // Immediately adjust audio focus based on current speaker state
        val someoneSpeaking = intercomClient.isSomeoneSpeaking.value
        Log.d(TAG, "Auto-Pause changed to $enabled. Current speaking state: $someoneSpeaking")
        if (someoneSpeaking && enabled) {
            audioRouter.requestAudioFocus(exclusive = true)
            restoreMusicVolume()
        } else {
            audioRouter.requestAudioFocus(exclusive = false)
            if (intercomClient.isRemoteSpeaking.value && !enabled) {
                duckMusicVolume()
            }
        }
    }

    fun disconnect() {
        cancelReconnectLoop()
        session = null
        _sessionError.value = null
        intercomClient.disconnect()
        privateChatParticipantIdentity = null
        _privateChatParticipant.value = null
        restoreMusicVolume()
        _roomCode.value = null
        audioRouter.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RiderLink::IntercomCpuWakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L) // 10 hours max hold
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        val currentLock = wakeLock
        if (currentLock != null && currentLock.isHeld) {
            currentLock.release()
            Log.d(TAG, "WakeLock released")
        }
        wakeLock = null
    }

    private fun startForegroundServiceCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground service started with microphone type")
    }

    private fun updateNotification() {
        if (_roomCode.value != null) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val muteActionIntent = Intent(this, IntercomService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, 1, muteActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopActionIntent = Intent(this, IntercomService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val roomCodeStr = _roomCode.value ?: "—"
        val muted = intercomClient.isMuted.value
        val muteText = if (muted) "Unmute" else "Mute"

        // The notification is often the only surface a rider sees, so it must
        // never claim the intercom is live when it is not.
        val attempt = _reconnectAttempt.value
        val statusText = when (intercomClient.connectionStatus.value) {
            ConnectionStatus.CONNECTING -> "Connecting…"
            ConnectionStatus.RECONNECTING ->
                if (attempt > 0) "Reconnecting (attempt $attempt)…" else "Reconnecting…"
            ConnectionStatus.CONNECTED -> if (muted) "Muted" else "Listening"
            ConnectionStatus.ERROR -> "Connection failed"
            ConnectionStatus.DISCONNECTED, ConnectionStatus.IDLE -> "Disconnected"
        }

        val track = localTrack.value
        val trackText = if (track != null) " · ♪ ${track.title}" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RiderLink · Ride $roomCodeStr")
            .setContentText("$statusText$trackText")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, muteText, mutePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RiderLink Intercom Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for background intercom call processing"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying IntercomService")
        IntercomNotificationListenerService.onMetadataChangedListener = null
        intercomClient.disconnect()
        audioRouter.stop()
        releaseWakeLock()
        
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false

        serviceScope.cancel()
        super.onDestroy()
    }

    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS Language is not supported or missing data")
            } else {
                isTtsReady = true
                Log.d(TAG, "TTS Initialized successfully")
            }
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    private fun speakTts(text: String) {
        Log.d(TAG, "speakTts: $text")
        if (isTtsReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "RiderLinkTTS")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        } else {
            Log.w(TAG, "TTS not ready yet")
        }
    }

    private fun handleMediaNextClick() {
        nextClickCount++
        nextClickJob?.cancel()
        if (nextClickCount >= 2) {
            nextClickCount = 0
            cyclePrivateChat()
        } else {
            nextClickJob = serviceScope.launch {
                kotlinx.coroutines.delay(1200)
                nextClickCount = 0
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }
    }

    private fun handleMediaPreviousClick() {
        prevClickCount++
        prevClickJob?.cancel()
        if (prevClickCount >= 2) {
            prevClickCount = 0
            returnToGroupChat()
        } else {
            prevClickJob = serviceScope.launch {
                kotlinx.coroutines.delay(1200)
                prevClickCount = 0
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
    }

    private var playPauseClickJob: kotlinx.coroutines.Job? = null
    private var playPauseClickCount = 0

    private fun handleMediaPlayPauseClick() {
        playPauseClickCount++
        Log.d(TAG, "handleMediaPlayPauseClick count = $playPauseClickCount")
        
        if (playPauseClickCount == 1) {
            playPauseClickJob = serviceScope.launch {
                kotlinx.coroutines.delay(800)
                val clicks = playPauseClickCount
                playPauseClickCount = 0
                Log.d(TAG, "Play/Pause click window expired. Clicks: $clicks")
                repeat(clicks) { index ->
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    if (clicks > 1 && index < clicks - 1) {
                        kotlinx.coroutines.delay(150)
                    }
                }
            }
        } else if (playPauseClickCount >= 3) {
            playPauseClickJob?.cancel()
            playPauseClickCount = 0
            
            val currentMute = intercomClient.isMuted.value
            val newMuteState = !currentMute
            intercomClient.setMute(newMuteState)
            
            val announcement = if (newMuteState) "Microphone Muted" else "Microphone Active"
            speakTts(announcement)
            Log.d(TAG, "Triple play/pause click detected. Mute state toggled to: $newMuteState")
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        isDispatchingInternal = true
        try {
            Log.d(TAG, "dispatchMediaKey internal: $keyCode")
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
        } finally {
            serviceScope.launch {
                kotlinx.coroutines.delay(200)
                isDispatchingInternal = false
            }
        }
    }

    private fun cyclePrivateChat() {
        val currentRoom = intercomClient.getRoom()
        if (currentRoom == null) {
            speakTts("Not connected to room")
            return
        }
        val remoteParticipants = currentRoom.remoteParticipants.values.toList()
        if (remoteParticipants.isEmpty()) {
            speakTts("No riders available for private chat")
            return
        }
        
        val currentIndex = remoteParticipants.indexOfFirst { it.identity?.value == privateChatParticipantIdentity }
        val nextIndex = (currentIndex + 1) % remoteParticipants.size
        val nextParticipant = remoteParticipants[nextIndex]
        val name = nextParticipant.identity?.value ?: "Rider"
        
        privateChatParticipantIdentity = name
        _privateChatParticipant.value = name
        
        intercomClient.isolateParticipant(name)
        speakTts("Private chat with $name")
    }

    /**
     * Opens a private channel with one specific rider, from a tap on the roster.
     *
     * The helmet-button gesture cycles through riders blind because there is no
     * screen to look at; tapping a row is the deliberate version of the same thing.
     */
    fun startPrivateChatWith(identity: String) {
        if (privateChatParticipantIdentity == identity) {
            returnToGroupChat()
            return
        }
        privateChatParticipantIdentity = identity
        _privateChatParticipant.value = identity
        intercomClient.isolateParticipant(identity)
        speakTts("Private chat with $identity")
    }

    /** Public entry point for the dashboard's "back to group" control. */
    fun returnToGroup() = returnToGroupChat()

    private fun returnToGroupChat() {
        if (privateChatParticipantIdentity == null) {
            speakTts("Already in group intercom")
            return
        }
        privateChatParticipantIdentity = null
        _privateChatParticipant.value = null
        intercomClient.resetPrivateChat()
        speakTts("Returned to group intercom")
    }

    private fun duckMusicVolume() {
        if (isAutoPauseEnabled.value) return
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (originalMusicVolume == null) {
                originalMusicVolume = currentVolume
                val ducked = (currentVolume * 0.20).toInt().coerceIn(1, maxMusicVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ducked, 0)
                Log.d(TAG, "Ducked STREAM_MUSIC volume from $currentVolume to $ducked")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ducking volume", e)
        }
    }

    private fun restoreMusicVolume() {
        try {
            originalMusicVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                Log.d(TAG, "Restored STREAM_MUSIC volume to $it")
                originalMusicVolume = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring volume", e)
        }
    }

    companion object {
        private const val TAG = "IntercomService"
        const val CHANNEL_ID = "riderlink_intercom_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FOREGROUND = "com.example.riderlink.action.START_FOREGROUND"
        const val ACTION_STOP_SERVICE = "com.example.riderlink.action.STOP_SERVICE"
        const val ACTION_TOGGLE_MUTE = "com.example.riderlink.action.TOGGLE_MUTE"
        const val EXTRA_ROOM_CODE = "com.example.riderlink.extra.ROOM_CODE"
    }
}

