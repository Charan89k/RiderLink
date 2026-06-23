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

class IntercomService : Service() {

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

    inner class LocalBinder : Binder() {
        fun getService(): IntercomService = this@IntercomService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating IntercomService")
        audioRouter = BluetoothAudioRouter(this)
        intercomClient = LiveKitIntercomClient(this)

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
        serviceScope.launch {
            intercomClient.connect(url, token)
        }
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
        } else {
            audioRouter.requestAudioFocus(exclusive = false)
        }
    }

    fun disconnect() {
        intercomClient.disconnect()
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
        serviceScope.cancel()
        super.onDestroy()
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

