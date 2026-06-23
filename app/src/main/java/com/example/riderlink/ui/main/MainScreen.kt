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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import kotlin.math.absoluteValue
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

    val activeSpeaker by mainViewModel.activeSpeaker.collectAsStateWithLifecycle()
    val privateChatParticipant by mainViewModel.privateChatParticipant.collectAsStateWithLifecycle()

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
                    onClearSharedTrack = { mainViewModel.clearSharedTrack() },
                    activeSpeaker = activeSpeaker,
                    privateChatParticipant = privateChatParticipant
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
fun BreathingSoundwave(
    isMuted: Boolean,
    activeSpeaker: String?,
    privateChatParticipant: String?,
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        isMuted -> Color(0xFFFF2A54)
        privateChatParticipant != null -> Color(0xFFC084FC)
        activeSpeaker != null -> {
            val hash = activeSpeaker.hashCode()
            val hue = (hash.absoluteValue % 360).toFloat()
            Color.hsl(hue = hue, saturation = 0.85f, lightness = 0.6f)
        }
        else -> Color(0xFF00F2FE)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "soundwave_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "soundwave_breathing")
    
    val duration = if (activeSpeaker != null && !isMuted) 800 else 2000
    val maxScale = if (activeSpeaker != null && !isMuted) 1.25f else 1.1f
    val minScale = if (activeSpeaker != null && !isMuted) 0.85f else 0.95f

    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "soundwave_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = scale * 1.15f
                    scaleY = scale * 1.15f
                    alpha = 0.08f
                }
                .background(animatedColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.15f
                }
                .background(animatedColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    scaleX = scale * 0.9f
                    scaleY = scale * 0.9f
                    alpha = 0.25f
                }
                .background(animatedColor, CircleShape)
        )

        Canvas(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF13171C))
                .border(1.dp, animatedColor.copy(alpha = 0.4f), CircleShape)
        ) {
            val barCount = 11
            val centerIndex = barCount / 2
            val spacing = 6.dp.toPx()
            val barWidth = 4.dp.toPx()
            val totalWidth = barCount * barWidth + (barCount - 1) * spacing
            val startX = (size.width - totalWidth) / 2
            
            for (i in 0 until barCount) {
                val distanceFromCenter = (i - centerIndex).absoluteValue
                val baseHeightFactor = (centerIndex - distanceFromCenter + 1).toFloat() / (centerIndex + 1)
                
                val speechRipple = if (activeSpeaker != null && !isMuted) {
                    val time = System.currentTimeMillis() / 200.0
                    Math.sin(time + i).toFloat().absoluteValue * 0.5f + 0.5f
                } else {
                    1.0f
                }
                
                val currentScale = scale
                val height = (40.dp.toPx() * baseHeightFactor * currentScale * speechRipple).coerceIn(4.dp.toPx(), 70.dp.toPx())
                val x = startX + i * (barWidth + spacing)
                val y = (size.height - height) / 2
                
                drawRoundRect(
                    color = animatedColor,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 180.dp)
        ) {
            val statusText = when {
                isMuted -> "MUTED"
                privateChatParticipant != null -> "PRIVATE: $privateChatParticipant"
                activeSpeaker != null -> "SPEAKING: $activeSpeaker"
                else -> "ACTIVE INTERCOM"
            }
            Text(
                text = statusText,
                color = animatedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            if (privateChatParticipant != null) {
                Text(
                    text = "Double click Vol Down to exit",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
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
    onClearSharedTrack: () -> Unit,
    activeSpeaker: String?,
    privateChatParticipant: String?
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F12))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            val stateText = when (connectionState) {
                Room.State.CONNECTED -> "SECURE MESH"
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

            Text(
                text = "ROOM $roomCode",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraLight,
                color = Color.White,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(stateColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stateText,
                    color = stateColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sharedTrack != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MintGreen.copy(alpha = 0.1f))
                        .border(0.5.dp, MintGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable {
                            val query = "${sharedTrack.artist} ${sharedTrack.title}"
                            val searchUrl = "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎵", fontSize = 12.sp)
                        Column {
                            Text(
                                text = sharedTrack.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sharedTrack.artist,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = { onClearSharedTrack() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        BreathingSoundwave(
            isMuted = isMuted,
            activeSpeaker = activeSpeaker,
            privateChatParticipant = privateChatParticipant,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .background(Color(0xFF161A1F).copy(alpha = 0.85f), RoundedCornerShape(44.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(44.dp))
                .padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) { onMuteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    val iconColor = if (isMuted) Color(0xFFFF2A54) else Color.White
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isMuted) "🔇" else "🎙️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isMuted) "Unmute" else "Mute",
                            color = iconColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) { onDisconnect() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚫", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Leave",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) {
                            if (localTrack != null) {
                                onShareSong(localTrack)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val enabled = localTrack != null
                    val alpha = if (enabled) 1.0f else 0.3f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer(alpha = alpha)
                    ) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Share",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
