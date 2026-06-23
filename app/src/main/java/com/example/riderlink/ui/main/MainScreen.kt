package com.example.riderlink.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riderlink.ui.MainViewModel
import com.example.riderlink.audio.TrackInfo
import io.livekit.android.room.Room

// Premium Minimalist Dark Theme Colors
private val SpaceBlack = Color(0xFF040405)
private val DeepGrey = Color(0xFF121214)
private val BorderGrey = Color(0x0FFFFFFF)
private val CardOverlay = Color(0x05FFFFFF)
private val AccentBlue = Color(0xFF00F2FE)
private val AccentCyan = Color(0xFF4FACFE)
private val MintGreen = Color(0xFF00FFB0)
private val DarkMuteRed = Color(0xFFFF2A54)
private val SoftRed = Color(0xFFFF5252)
private val SafetyOrange = Color(0xFFF59E0B)
private val MutedText = Color(0xFF71717A)

// Gradients
private val BlueGradient = Brush.horizontalGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))
private val RedGradient = Brush.horizontalGradient(listOf(Color(0xFFFF2A54), Color(0xFFFF5252)))
private val GreenGradient = Brush.horizontalGradient(listOf(Color(0xFF00FFB0), Color(0xFF00F2FE)))
private val BackgroundBrush = Brush.verticalGradient(listOf(SpaceBlack, Color(0xFF09090C)))

// Glassmorphism modifier helper
private fun Modifier.glassCard(cornerRadius: Dp = 16.dp) = this
    .background(CardOverlay, RoundedCornerShape(cornerRadius))
    .border(0.5.dp, BorderGrey, RoundedCornerShape(cornerRadius))
    .clip(RoundedCornerShape(cornerRadius))

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    val connectionState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val roomCode by mainViewModel.roomCode.collectAsStateWithLifecycle()
    val participants by mainViewModel.participants.collectAsStateWithLifecycle()
    val isMuted by mainViewModel.isMuted.collectAsStateWithLifecycle()
    val error by mainViewModel.error.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()

    val localTrack by mainViewModel.localTrack.collectAsStateWithLifecycle()
    val sharedTrack by mainViewModel.sharedTrack.collectAsStateWithLifecycle()
    val isAutoPauseEnabled by mainViewModel.isAutoPauseEnabled.collectAsStateWithLifecycle()

    var isSettingsOpen by remember { mutableStateOf(false) }

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(list.toTypedArray())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SpaceBlack
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundBrush)
        ) {
            // High-End Radial Light Leak Accent in Background - Optimized with drawWithCache
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .graphicsLayer(alpha = 0.18f)
                    .align(Alignment.TopCenter)
                    .drawWithCache {
                        val brush = Brush.radialGradient(
                            colors = listOf(AccentBlue, Color.Transparent),
                            center = Offset(size.width / 2, 0f),
                            radius = size.width * 0.8f
                        )
                        onDrawBehind {
                            drawCircle(brush = brush, center = Offset(size.width / 2, 0f), radius = size.width * 0.8f)
                        }
                    }
            )

            if (roomCode != null) {
                ActiveCallScreen(
                    roomCode = roomCode!!,
                    connectionState = connectionState,
                    participants = participants,
                    isMuted = isMuted,
                    onMuteToggle = { mainViewModel.toggleMute() },
                    onDisconnect = { mainViewModel.disconnect() },
                    localTrack = localTrack,
                    sharedTrack = sharedTrack,
                    isAutoPause = isAutoPauseEnabled,
                    onAutoPauseChange = { mainViewModel.setAutoPauseEnabled(it) },
                    onShareSong = { mainViewModel.shareSong(it.title, it.artist) },
                    onClearSharedTrack = { mainViewModel.clearSharedTrack() }
                )
            } else {
                HomeScreen(
                    mainViewModel = mainViewModel,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = {
                        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            list.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            list.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(list.toTypedArray())
                    }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpaceBlack.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                }
            }

            // Glowing glassmorphic Gear button for settings overlay (top right)
            IconButton(
                onClick = {
                    mainViewModel.refreshVolumes()
                    isSettingsOpen = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .size(48.dp)
                    .background(CardOverlay, CircleShape)
                    .border(0.5.dp, BorderGrey, CircleShape)
            ) {
                Text("⚙️", fontSize = 20.sp)
            }

            // Animated Settings Panel
            androidx.compose.animation.AnimatedVisibility(
                visible = isSettingsOpen,
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                SettingsPanel(
                    viewModel = mainViewModel,
                    onClose = { isSettingsOpen = false }
                )
            }

            error?.let { errMessage ->
                AlertDialog(
                    onDismissRequest = { mainViewModel.clearError() },
                    confirmButton = {
                        TextButton(onClick = { mainViewModel.clearError() }) {
                            Text("OK", color = AccentBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    title = { Text("Information", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text(errMessage, color = Color.White.copy(alpha = 0.8f)) },
                    containerColor = DeepGrey,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val riderName by mainViewModel.riderName.collectAsStateWithLifecycle()
    val livekitUrl by mainViewModel.livekitUrl.collectAsStateWithLifecycle()
    val apiKey by mainViewModel.apiKey.collectAsStateWithLifecycle()
    val apiSecret by mainViewModel.apiSecret.collectAsStateWithLifecycle()

    var joinCodeInput by remember { mutableStateOf("") }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header") {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "RiderLink",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraLight,
                color = Color.White,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MINIMAL GROUP INTERCOM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (!permissionsGranted) {
            item(key = "permissions") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .background(SoftRed.copy(alpha = 0.06f))
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Permissions Required",
                            color = SoftRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "RiderLink requires Microphone and Bluetooth permissions to route voice intercom stream to your headset.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant Access", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Rider Identity Card
        item(key = "identity") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "RIDER IDENTITY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f),
                        letterSpacing = 1.5.sp
                    )
                    OutlinedTextField(
                        value = riderName,
                        onValueChange = { mainViewModel.riderName.value = it },
                        label = { Text("Display Name", fontSize = 12.sp, color = MutedText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue.copy(alpha = 0.8f),
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                            focusedLabelColor = AccentBlue,
                            unfocusedLabelColor = MutedText,
                            focusedContainerColor = Color(0x02FFFFFF),
                            unfocusedContainerColor = Color(0x02FFFFFF)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Action Interface
        item(key = "intercom_options") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        text = "INTERCOM OPTIONS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f),
                        letterSpacing = 1.5.sp
                    )
                    
                    // Create Button
                    RiderLinkButton(
                        text = "Create Room",
                        onClick = { mainViewModel.createRoom() },
                        gradient = GreenGradient,
                        textColor = SpaceBlack
                    )

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(BorderGrey))
                        Text(
                            text = "OR JOIN CODE",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.White.copy(alpha = 0.25f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(BorderGrey))
                    }

                    // Join Interface
                    DigitCodeInput(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it }
                    )

                    RiderLinkButton(
                        text = "Join Room",
                        onClick = { mainViewModel.joinRoom(joinCodeInput) },
                        enabled = joinCodeInput.length == 4,
                        gradient = BlueGradient,
                        textColor = SpaceBlack
                    )
                }
            }
        }

        // Advanced Configuration
        item(key = "advanced") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    .padding(vertical = 16.dp, horizontal = 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced LiveKit Credentials",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isAdvancedExpanded) "▲" else "▼",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }
                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            OutlinedTextField(
                                value = livekitUrl,
                                onValueChange = { mainViewModel.livekitUrl.value = it },
                                label = { Text("Server URL", fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentBlue.copy(alpha = 0.8f),
                                    unfocusedBorderColor = BorderGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = AccentBlue,
                                    unfocusedLabelColor = MutedText
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { mainViewModel.apiKey.value = it },
                                label = { Text("API Key", fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentBlue.copy(alpha = 0.8f),
                                    unfocusedBorderColor = BorderGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = AccentBlue,
                                    unfocusedLabelColor = MutedText
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiSecret,
                                onValueChange = { mainViewModel.apiSecret.value = it },
                                label = { Text("API Secret", fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentBlue.copy(alpha = 0.8f),
                                    unfocusedBorderColor = BorderGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = AccentBlue,
                                    unfocusedLabelColor = MutedText
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val cn = ComponentName(context, com.example.riderlink.service.IntercomNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

@Composable
fun ActiveCallScreen(
    roomCode: String,
    connectionState: Room.State,
    participants: List<String>,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onDisconnect: () -> Unit,
    localTrack: TrackInfo?,
    sharedTrack: TrackInfo?,
    isAutoPause: Boolean,
    onAutoPauseChange: (Boolean) -> Unit,
    onShareSong: (TrackInfo) -> Unit,
    onClearSharedTrack: () -> Unit
) {
    val context = LocalContext.current
    var isNotificationAccessGranted by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        while (true) {
            isNotificationAccessGranted = isNotificationListenerEnabled(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    // Breathing/Pulsing Micro-animations for the Active Intercom Glowing Rings
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_halo")
    
    val haloScale1 by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val haloAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )

    val haloScale2 by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )
    val haloAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Room header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ACTIVE VOICE ROOM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val formattedCode = roomCode.map { "$it" }.joinToString("   ")
                Text(
                    text = formattedCode,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                val stateText = when (connectionState) {
                    Room.State.CONNECTED -> "SECURE INTERCOM"
                    Room.State.CONNECTING -> "CONNECTING..."
                    Room.State.RECONNECTING -> "RECONNECTING..."
                    Room.State.DISCONNECTED -> "DISCONNECTED"
                }
                val stateColor = when (connectionState) {
                    Room.State.CONNECTED -> MintGreen
                    Room.State.CONNECTING -> SafetyOrange
                    Room.State.RECONNECTING -> SafetyOrange
                    Room.State.DISCONNECTED -> DarkMuteRed
                }

                Box(
                    modifier = Modifier
                        .border(0.5.dp, stateColor.copy(alpha = 0.25f), RoundedCornerShape(50.dp))
                        .background(stateColor.copy(alpha = 0.05f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stateText,
                        color = stateColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        // Giant glove-friendly minimal Mute toggle with breathing rings
        item {
            val muteInteractionSource = remember { MutableInteractionSource() }
            val isMutePressed by muteInteractionSource.collectIsPressedAsState()
            val muteScale by animateFloatAsState(
                targetValue = if (isMutePressed) 0.92f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "mute_press"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .graphicsLayer {
                        scaleX = muteScale
                        scaleY = muteScale
                    }
            ) {
                val colorGlow = if (isMuted) DarkMuteRed else MintGreen
                
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            val scale = if (isMuted) 1.0f else haloScale2
                            scaleX = scale
                            scaleY = scale
                            alpha = if (isMuted) 0.05f else haloAlpha2
                        }
                        .background(colorGlow.copy(alpha = 0.4f), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            val scale = if (isMuted) 0.98f else haloScale1
                            scaleX = scale
                            scaleY = scale
                            alpha = if (isMuted) 0.12f else haloAlpha1
                        }
                        .background(colorGlow.copy(alpha = 0.5f), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .background(DeepGrey, CircleShape)
                        .border(1.dp, if (isMuted) DarkMuteRed.copy(alpha = 0.6f) else MintGreen.copy(alpha = 0.6f), CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = muteInteractionSource,
                            indication = null
                        ) { onMuteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isMuted) "MUTED" else "LIVE",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraLight,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isMuted) "TAP TO TALK" else "TAP TO MUTE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.35f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Media Mixing & Song Sharing Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎵 MEDIA MIXING & SHARING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Auto-Pause Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Auto-Pause Music",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Pauses music when someone speaks; otherwise mixes audio",
                                color = MutedText,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = isAutoPause,
                            onCheckedChange = onAutoPauseChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SpaceBlack,
                                checkedTrackColor = AccentBlue,
                                uncheckedThumbColor = MutedText,
                                uncheckedTrackColor = DeepGrey,
                                uncheckedBorderColor = BorderGrey
                            )
                        )
                    }

                    Divider(color = BorderGrey, thickness = 0.5.dp)

                    // Local music metadata reader
                    if (!isNotificationAccessGranted) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Notification Access Required",
                                color = SafetyOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant access to read active Spotify/Apple Music track details for one-tap sharing.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                            Button(
                                onClick = {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Grant Access", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        if (localTrack != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NOW PLAYING LOCAL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MutedText,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = localTrack.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = localTrack.artist,
                                        color = AccentBlue,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                Button(
                                    onClick = { onShareSong(localTrack) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Share 🚀", color = SpaceBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Play a song in Spotify / Apple Music to share...",
                                color = MutedText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Shared track display alert
                    if (sharedTrack != null) {
                        Divider(color = BorderGrey, thickness = 0.5.dp)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MintGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(1.dp, MintGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎵 SHARED TRACK RECEIVED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MintGreen,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Clear",
                                        fontSize = 10.sp,
                                        color = MutedText,
                                        modifier = Modifier.clickable { onClearSharedTrack() }
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = sharedTrack.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sharedTrack.artist,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val query = "${sharedTrack.artist} ${sharedTrack.title}"
                                        val searchUrl = "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Free Stream (YouTube)", color = SpaceBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // List of Riders in Group
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RIDERS CONNECTED (${participants.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.3f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "● Monitoring",
                    color = MintGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        if (participants.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .glassCard(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No other riders connected",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(participants, key = { it }) { participant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x02FFFFFF), RoundedCornerShape(12.dp))
                        .border(0.5.dp, BorderGrey, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x0AFFFFFF), CircleShape)
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = participant.take(2).uppercase()
                        Text(
                            text = initials,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = participant,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MintGreen, CircleShape)
                    )
                }
            }
        }

        // Disconnect button
        item {
            val disconnectInteractionSource = remember { MutableInteractionSource() }
            val isDisconnectPressed by disconnectInteractionSource.collectIsPressedAsState()
            val disconnectScale by animateFloatAsState(
                targetValue = if (isDisconnectPressed) 0.95f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "disconnect_press"
            )

            Button(
                onClick = onDisconnect,
                interactionSource = disconnectInteractionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, 
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = disconnectScale
                        scaleY = disconnectScale
                    }
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RedGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Disconnect Intercom",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun RiderLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = BlueGradient,
    textColor: Color = SpaceBlack
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "button_press"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, 
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        val currentGradient = if (enabled) gradient else Brush.horizontalGradient(listOf(Color(0xFF1B1B1F), Color(0xFF1B1B1F)))
        val currentTextColor = if (enabled) textColor else Color.White.copy(alpha = 0.2f)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = currentTextColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun DigitCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "indicator")
    val indicatorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newVal ->
                if (newVal.length <= 4 && newVal.all { it.isDigit() }) {
                    onValueChange(newVal)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(1.dp).graphicsLayer(alpha = 0f)) {
                        innerTextField()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val char = value.getOrNull(i)
                            val isCurrentChar = value.length == i
                            val isLastChar = value.length == 4 && i == 3
                            val isFocused = isCurrentChar || isLastChar
                            
                            val glowColor = if (isFocused) AccentBlue else BorderGrey
                            val bgAlpha = if (isFocused) 0.12f else 0.04f
                            
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 68.dp)
                                    .background(Color.White.copy(alpha = bgAlpha), RoundedCornerShape(14.dp))
                                    .border(
                                        1.dp, 
                                        if (isFocused) AccentBlue.copy(alpha = 0.8f) else BorderGrey, 
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char?.toString() ?: "",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White,
                                    letterSpacing = 0.sp
                                )
                                if (isCurrentChar) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 10.dp)
                                            .width(14.dp)
                                            .height(2.dp)
                                            .background(AccentBlue.copy(alpha = indicatorAlpha), RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsPanel(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val voiceVolume by viewModel.voiceVolume.collectAsStateWithLifecycle()
    val musicVolume by viewModel.musicVolume.collectAsStateWithLifecycle()
    val noiseSuppression by viewModel.noiseSuppressionEnabled.collectAsStateWithLifecycle()
    val echoCancellation by viewModel.echoCancellationEnabled.collectAsStateWithLifecycle()
    val autoGainControl by viewModel.autoGainControlEnabled.collectAsStateWithLifecycle()
    val highPassFilter by viewModel.highPassFilterEnabled.collectAsStateWithLifecycle()
    val audioModeVoip by viewModel.audioModeVoip.collectAsStateWithLifecycle()
    val isVolumeBoostEnabled by viewModel.isVolumeBoostEnabled.collectAsStateWithLifecycle()
    val isAutoPause by viewModel.isAutoPauseEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer(alpha = 0.12f)
                .align(Alignment.BottomCenter)
                .drawWithCache {
                    val brush = Brush.radialGradient(
                        colors = listOf(AccentCyan, Color.Transparent),
                        center = Offset(size.width / 2, size.height),
                        radius = size.width * 0.8f
                    )
                    onDrawBehind {
                        drawCircle(brush = brush, center = Offset(size.width / 2, size.height), radius = size.width * 0.8f)
                    }
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .glassCard(cornerRadius = 24.dp)
                .background(DeepGrey.copy(alpha = 0.9f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* prevent close */ }
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUDIO CONFIGURATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Rider & Intercom Tuning",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CardOverlay, CircleShape)
                        .border(0.5.dp, BorderGrey, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SettingsSectionHeader(title = "VOLUME CONTROLS")

                VolumeSliderItem(
                    label = "Intercom Voice Volume",
                    value = voiceVolume,
                    maxValue = viewModel.maxVoiceVolume,
                    onValueChange = { viewModel.setVoiceVolume(it) },
                    icon = "🗣️",
                    enabled = !isVolumeBoostEnabled
                )

                VolumeSliderItem(
                    label = "Background Music Volume",
                    value = musicVolume,
                    maxValue = viewModel.maxMusicVolume,
                    onValueChange = { viewModel.setMusicVolume(it) },
                    icon = "🎵",
                    enabled = !isVolumeBoostEnabled
                )

                SettingsSwitchItem(
                    title = "⚡ Extreme Volume Boost",
                    description = "Bypasses normal limits and forces maximum output. Use for heavy wind or exhaust noise.",
                    checked = isVolumeBoostEnabled,
                    onCheckedChange = { viewModel.setVolumeBoostEnabled(it) },
                    accentColor = SafetyOrange
                )

                Divider(color = BorderGrey, thickness = 0.5.dp)

                SettingsSectionHeader(title = "AUDIO ROUTING")

                SettingsSwitchItem(
                    title = "VoIP Routing Mode",
                    description = "Enabled (VoIP): Full-duplex voice & helmet mic.\nDisabled (Media Mode): High fidelity stereo music, captures microphone audio via the phone.",
                    checked = audioModeVoip,
                    onCheckedChange = { viewModel.setAudioModeVoip(it) }
                )

                Divider(color = BorderGrey, thickness = 0.5.dp)

                SettingsSectionHeader(title = "INTELLIGENT NOISE FILTERING & DSP")

                SettingsSwitchItem(
                    title = "💨 Wind Noise High-Pass Filter",
                    description = "Cuts low-frequency rumble and heavy wind turbulence under the helmet.",
                    checked = highPassFilter,
                    onCheckedChange = { viewModel.setHighPassFilterEnabled(it) }
                )

                SettingsSwitchItem(
                    title = "🚗 Exhaust Noise Suppression",
                    description = "Uses WebRTC voice isolation algorithms to remove loud exhaust notes and background hums.",
                    checked = noiseSuppression,
                    onCheckedChange = { viewModel.setNoiseSuppressionEnabled(it) }
                )

                SettingsSwitchItem(
                    title = "🔄 Acoustic Echo Cancellation",
                    description = "Suppresses feedback and echo when using speakerphones or open helmet configurations.",
                    checked = echoCancellation,
                    onCheckedChange = { viewModel.setEchoCancellationEnabled(it) }
                )

                SettingsSwitchItem(
                    title = "📈 Intelligent Auto-Gain Control",
                    description = "Dynamically boosts quiet speech and caps loud outbursts so voice levels stay balanced.",
                    checked = autoGainControl,
                    onCheckedChange = { viewModel.setAutoGainControlEnabled(it) }
                )

                Divider(color = BorderGrey, thickness = 0.5.dp)

                SettingsSectionHeader(title = "MUSIC COEXISTENCE")

                SettingsSwitchItem(
                    title = "Auto-Pause Music on Speech",
                    description = "Automatically pauses Spotify/Apple Music when any rider speaks, resuming when speech stops. If disabled, music mixes continuously in background.",
                    checked = isAutoPause,
                    onCheckedChange = { viewModel.setAutoPauseEnabled(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.4f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun VolumeSliderItem(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    icon: String,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .background(Color.White.copy(alpha = if (enabled) 0.02f else 0.01f))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = if (enabled) "$value / $maxValue" else "BOOSTED MAX",
                    color = if (enabled) AccentCyan else SafetyOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..maxValue.toFloat(),
                steps = if (maxValue > 1) maxValue - 1 else 0,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (enabled) AccentBlue else SafetyOrange,
                    activeTrackColor = if (enabled) AccentBlue else SafetyOrange,
                    inactiveTrackColor = BorderGrey,
                    disabledThumbColor = SafetyOrange.copy(alpha = 0.5f),
                    disabledActiveTrackColor = SafetyOrange.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = AccentBlue
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .background(Color.White.copy(alpha = 0.02f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SpaceBlack,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = MutedText,
                    uncheckedTrackColor = DeepGrey,
                    uncheckedBorderColor = BorderGrey
                )
            )
        }
    }
}
