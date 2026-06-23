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
import com.example.riderlink.audio.BluetoothAudioRouter
import com.example.riderlink.audio.LiveKitIntercomClient
import com.example.riderlink.audio.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class IntercomService : Service(), TextToSpeech.OnInitListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var audioRouter: BluetoothAudioRouter
    lateinit var intercomClient: LiveKitIntercomClient
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

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

    fun connectToRoom(url: String, token: String, code: String) {
        _roomCode.value = code
        val prefs = getSharedPreferences("riderlink_settings", Context.MODE_PRIVATE)
        val noiseSuppression = prefs.getBoolean("noise_suppression", true)
        val echoCancellation = prefs.getBoolean("echo_cancellation", true)
        val autoGainControl = prefs.getBoolean("auto_gain_control", true)
        val highPassFilter = prefs.getBoolean("high_pass_filter", true)
        val useVoip = prefs.getBoolean("audio_mode_voip", true)

        audioRouter.setAudioModeVoip(useVoip)

        serviceScope.launch {
            intercomClient.connect(
                url = url,
                token = token,
                noiseSuppression = noiseSuppression,
                echoCancellation = echoCancellation,
                autoGainControl = autoGainControl,
                highPassFilter = highPassFilter,
                useVoip = useVoip
            )
        }
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

        val roomCodeStr = _roomCode.value ?: "Unknown"
        val statusText = if (intercomClient.isMuted.value) "Muted" else "Listening"
        val muteText = if (intercomClient.isMuted.value) "Unmute" else "Mute"

        val track = localTrack.value
        val trackText = if (track != null) " | 🎵 ${track.title} - ${track.artist}" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RiderLink Active Intercom")
            .setContentText("Room: $roomCodeStr | Status: $statusText$trackText")
            .setSmallIcon(android.R.drawable.presence_online)
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

