package com.example.riderlink.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

import android.media.AudioFocusRequest
import android.media.AudioAttributes

class BluetoothAudioRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isRoutingStarted = false
    private var activeFocusRequest: AudioFocusRequest? = null
    private var isFocusExclusive = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed callback: $focusChange")
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added, updating route")
            updateAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed, updating route")
            updateAudioRoute()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "Received Bluetooth broadcast: $action")
            updateAudioRoute()
        }
    }

    companion object {
        private const val TAG = "BluetoothAudioRouter"
    }

    fun requestAudioFocus(exclusive: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            abandonAudioFocus()

            val focusGain = if (exclusive) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            }

            val request = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            activeFocusRequest = request
            isFocusExclusive = exclusive
            val result = audioManager.requestAudioFocus(request)
            Log.d(TAG, "Requested audio focus (exclusive=$exclusive), result: $result")
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let {
                val result = audioManager.abandonAudioFocusRequest(it)
                Log.d(TAG, "Abandoned audio focus request, result: $result")
            }
            activeFocusRequest = null
            isFocusExclusive = false
        }
    }

    fun start() {
        if (isRoutingStarted) return
        isRoutingStarted = true
        Log.d(TAG, "Starting Bluetooth audio routing")

        // Force MODE_IN_COMMUNICATION for VoIP optimization
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // Request shared/ducked transient focus to allow simultaneous music playback
        requestAudioFocus(exclusive = false)

        // Register audio device callback
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        // Register receiver for Bluetooth connection changes
        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            @Suppress("DEPRECATION")
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        // Initial update
        updateAudioRoute()
    }

    fun stop() {
        if (!isRoutingStarted) return
        isRoutingStarted = false
        Log.d(TAG, "Stopping Bluetooth audio routing")

        abandonAudioFocus()

        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }

        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun updateAudioRoute() {
        if (!isRoutingStarted) return

        // Check BLUETOOTH_CONNECT permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, cannot route to Bluetooth")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // Prioritize Bluetooth SCO and BLE Headsets
            val bluetoothDevice = devices.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }

            if (bluetoothDevice != null) {
                val result = audioManager.setCommunicationDevice(bluetoothDevice)
                Log.d(TAG, "Routing to Bluetooth device: ${bluetoothDevice.productName}, type: ${bluetoothDevice.type}, success: $result")
            } else {
                Log.d(TAG, "No Bluetooth communication device found, clearing route to use default")
                audioManager.clearCommunicationDevice()
            }
        } else {
            // Deprecated fallback for older APIs
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoOn) {
                Log.d(TAG, "Starting legacy Bluetooth SCO")
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            }
        }
    }
}
