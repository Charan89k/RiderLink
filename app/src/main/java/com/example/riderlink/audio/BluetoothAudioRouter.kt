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
import com.example.riderlink.domain.model.AudioRoute
import com.example.riderlink.domain.model.AudioRouteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothAudioRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isRoutingStarted = false
    private var activeFocusRequest: AudioFocusRequest? = null

    /**
     * Where audio is actually going right now.
     *
     * A rider cannot tell a silent intercom from one playing into the phone's
     * earpiece inside a pocket, so this has to be visible on screen.
     */
    private val _audioRoute = MutableStateFlow(AudioRoute())
    val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

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
        }
    }

    private var preferredAudioMode = AudioManager.MODE_IN_COMMUNICATION

    fun setAudioModeVoip(useVoip: Boolean) {
        val newMode = if (useVoip) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        preferredAudioMode = newMode
        if (isRoutingStarted) {
            audioManager.mode = newMode
            Log.d(TAG, "Dynamic audio mode updated to: $newMode")
        }
    }

    fun start() {
        if (isRoutingStarted) return
        isRoutingStarted = true
        Log.d(TAG, "Starting Bluetooth audio routing with mode: $preferredAudioMode")

        // Set the preferred audio mode (VoIP or Normal)
        audioManager.mode = preferredAudioMode

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
        ContextCompat.registerReceiver(context, bluetoothReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

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
        _audioRoute.value = AudioRoute()
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
                _audioRoute.value = AudioRoute(
                    type = AudioRouteType.BLUETOOTH_HEADSET,
                    deviceName = bluetoothDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                )
            } else {
                Log.d(TAG, "No Bluetooth communication device found, clearing route to use default")
                audioManager.clearCommunicationDevice()
                _audioRoute.value = AudioRoute(type = classifyFallbackRoute(devices))
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
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bluetooth = devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            _audioRoute.value = if (bluetooth != null) {
                AudioRoute(AudioRouteType.BLUETOOTH_HEADSET, bluetooth.productName?.toString())
            } else {
                AudioRoute(classifyFallbackRoute(devices))
            }
        }
    }

    /** Wired headset beats the phone speaker when no helmet is present. */
    private fun classifyFallbackRoute(devices: Array<AudioDeviceInfo>): AudioRouteType {
        val wired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        return if (wired) AudioRouteType.WIRED_HEADSET else AudioRouteType.PHONE
    }
}
