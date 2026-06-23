package com.example.riderlink.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

class IntercomNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "IntercomNotificationListener"
        
        // Static callback to communicate track changes back to the running intercom service
        var onMetadataChangedListener: ((title: String, artist: String) -> Unit)? = null
    }

    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata?.let {
                val title = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                if (title.isNotEmpty()) {
                    Log.d(TAG, "Active controller metadata changed callback: $title - $artist")
                    onMetadataChangedListener?.invoke(title, artist)
                }
            }
        }
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener service connected")
        updateActiveControllers()

        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, IntercomNotificationListenerService::class.java)

        sessionListener = MediaSessionManager.OnActiveSessionsChangedListener {
            Log.d(TAG, "Active sessions changed")
            updateActiveControllers()
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener!!, componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding session listener", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener service disconnected")
    }

    private fun updateActiveControllers() {
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, IntercomNotificationListenerService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            
            Log.d(TAG, "Found ${controllers?.size ?: 0} active media controllers")
            controllers?.forEach { controller ->
                // Register for changes
                controller.unregisterCallback(callback)
                controller.registerCallback(callback)
                
                // Read current metadata immediately
                val metadata = controller.metadata
                if (metadata != null) {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    if (title.isNotEmpty()) {
                        onMetadataChangedListener?.invoke(title, artist)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying active controllers", e)
        }
    }

    override fun onDestroy() {
        sessionListener?.let {
            try {
                val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                mediaSessionManager.removeOnActiveSessionsChangedListener(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing session listener", e)
            }
        }
        super.onDestroy()
    }
}

