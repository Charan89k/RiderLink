package com.example.riderlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.PageGradient
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary
import com.example.riderlink.ui.MainViewModel
import com.example.riderlink.ui.components.PrimaryAction
import com.example.riderlink.ui.screens.LobbyScreen
import com.example.riderlink.ui.screens.RideScreen
import com.example.riderlink.ui.screens.SettingsActions
import com.example.riderlink.ui.screens.SettingsScreen
import com.example.riderlink.ui.screens.SettingsState

/**
 * Application shell: permissions, then routing.
 *
 * Navigation follows the connection rather than the back stack alone -- joining
 * a ride moves to the dashboard and leaving returns to the lobby, so the screen
 * always matches whether audio is flowing.
 */
@Composable
fun RiderLinkApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()

    val requiredPermissions = remember { requiredPermissions() }
    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all { hasPermission(context, it) })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // POST_NOTIFICATIONS can be declined without blocking the intercom, so
        // only the permissions the audio path genuinely needs gate the app.
        permissionsGranted = essentialPermissions().all { results[it] != false && hasPermission(context, it) }
    }

    if (!permissionsGranted) {
        PermissionGate(onGrant = { permissionLauncher.launch(requiredPermissions.toTypedArray()) })
        return
    }

    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Lobby)

    LaunchedEffect(status.isActive) {
        val onRide = backStack.lastOrNull() == Ride
        if (status.isActive && !onRide) {
            backStack.add(Ride)
        } else if (!status.isActive && onRide) {
            backStack.removeLastOrNull()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Lobby> {
                val riderName by viewModel.riderName.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val error by viewModel.error.collectAsStateWithLifecycle()

                LobbyScreen(
                    riderName = riderName,
                    onRiderNameChange = { viewModel.riderName.value = it },
                    isLoading = isLoading,
                    error = error,
                    onDismissError = viewModel::clearError,
                    onCreateRide = viewModel::createRoom,
                    onJoinRide = viewModel::joinRoom,
                    onOpenSettings = { backStack.add(Settings) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }

            entry<Ride> {
                val rideCode by viewModel.roomCode.collectAsStateWithLifecycle()
                val riders by viewModel.riders.collectAsStateWithLifecycle()
                val muted by viewModel.isMuted.collectAsStateWithLifecycle()
                val attempt by viewModel.reconnectAttempt.collectAsStateWithLifecycle()
                val route by viewModel.audioRoute.collectAsStateWithLifecycle()
                val privateChat by viewModel.privateChatParticipant.collectAsStateWithLifecycle()
                val error by viewModel.error.collectAsStateWithLifecycle()

                RideScreen(
                    status = status,
                    rideCode = rideCode,
                    riders = riders,
                    muted = muted,
                    reconnectAttempt = attempt,
                    audioRoute = route,
                    privateChatWith = privateChat,
                    error = error,
                    onDismissError = viewModel::clearError,
                    onToggleMute = viewModel::toggleMute,
                    onSelectRider = { viewModel.startPrivateChat(it.identity) },
                    onReturnToGroup = viewModel::returnToGroup,
                    onLeaveRide = viewModel::disconnect,
                    onOpenSettings = { backStack.add(Settings) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }

            entry<Settings> {
                val riderName by viewModel.riderName.collectAsStateWithLifecycle()
                val tokenServer by viewModel.tokenServerUrl.collectAsStateWithLifecycle()
                val voiceVolume by viewModel.voiceVolume.collectAsStateWithLifecycle()
                val musicVolume by viewModel.musicVolume.collectAsStateWithLifecycle()
                val noiseSuppression by viewModel.noiseSuppressionEnabled.collectAsStateWithLifecycle()
                val echoCancellation by viewModel.echoCancellationEnabled.collectAsStateWithLifecycle()
                val autoGain by viewModel.autoGainControlEnabled.collectAsStateWithLifecycle()
                val highPass by viewModel.highPassFilterEnabled.collectAsStateWithLifecycle()
                val voip by viewModel.audioModeVoip.collectAsStateWithLifecycle()
                val boost by viewModel.isVolumeBoostEnabled.collectAsStateWithLifecycle()
                val autoPause by viewModel.isAutoPauseEnabled.collectAsStateWithLifecycle()

                SettingsScreen(
                    state = SettingsState(
                        riderName = riderName,
                        tokenServerUrl = tokenServer,
                        voiceVolume = voiceVolume,
                        maxVoiceVolume = viewModel.maxVoiceVolume,
                        musicVolume = musicVolume,
                        maxMusicVolume = viewModel.maxMusicVolume,
                        noiseSuppression = noiseSuppression,
                        echoCancellation = echoCancellation,
                        autoGainControl = autoGain,
                        highPassFilter = highPass,
                        voipAudioMode = voip,
                        volumeBoost = boost,
                        pauseMusicWhileTalking = autoPause,
                        appVersion = BuildConfig.VERSION_NAME,
                    ),
                    actions = SettingsActions(
                        onRiderName = { viewModel.riderName.value = it },
                        onTokenServerUrl = { viewModel.tokenServerUrl.value = it },
                        onVoiceVolume = viewModel::setVoiceVolume,
                        onMusicVolume = viewModel::setMusicVolume,
                        onNoiseSuppression = viewModel::setNoiseSuppressionEnabled,
                        onEchoCancellation = viewModel::setEchoCancellationEnabled,
                        onAutoGainControl = viewModel::setAutoGainControlEnabled,
                        onHighPassFilter = viewModel::setHighPassFilterEnabled,
                        onVoipAudioMode = viewModel::setAudioModeVoip,
                        onVolumeBoost = viewModel::setVolumeBoostEnabled,
                        onPauseMusicWhileTalking = viewModel::setAutoPauseEnabled,
                        onBack = { backStack.removeLastOrNull() },
                    ),
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}

/**
 * Explains why the app needs the microphone before asking for it.
 *
 * A bare system dialog on first launch gets denied; a sentence of context first
 * does not.
 */
@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageGradient)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "RIDERLINK",
                color = Electric,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Before your first ride",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "RiderLink needs your microphone to carry your voice, and Bluetooth " +
                    "access to route audio into your helmet headset. Notifications keep the " +
                    "intercom running while your phone is in a pocket.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryAction(label = "Continue", onClick = onGrant)
        }
    }
}

/** Permissions requested at launch, including the optional notification one. */
private fun requiredPermissions(): List<String> = buildList {
    addAll(essentialPermissions())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Permissions without which the intercom genuinely cannot work. */
private fun essentialPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
