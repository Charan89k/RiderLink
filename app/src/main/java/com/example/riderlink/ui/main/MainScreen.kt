package com.example.riderlink.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.riderlink.ui.MainViewModel
import io.livekit.android.room.Room

// Modern High-Contrast Color Palette for Riders
private val DarkCharcoal = Color(0xFF121212)
private val CardBackground = Color(0xFF1E1E1E)
private val ElectricCyan = Color(0xFF00E5FF)
private val NeonLime = Color(0xFFCCFF00)
private val SafetyOrange = Color(0xFFFF9100)
private val MutedText = Color(0xFF888888)
private val ActiveGreen = Color(0xFF00E676)
private val MuteRed = Color(0xFFFF1744)

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    // State bindings
    val connectionState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val roomCode by mainViewModel.roomCode.collectAsStateWithLifecycle()
    val participants by mainViewModel.participants.collectAsStateWithLifecycle()
    val isMuted by mainViewModel.isMuted.collectAsStateWithLifecycle()
    val error by mainViewModel.error.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()

    // Permission tracking
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

    // Launch permission request on startup
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
        color = DarkCharcoal
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            if (roomCode != null) {
                // Active session screen
                ActiveCallScreen(
                    roomCode = roomCode!!,
                    connectionState = connectionState,
                    participants = participants,
                    isMuted = isMuted,
                    onMuteToggle = { mainViewModel.toggleMute() },
                    onDisconnect = { mainViewModel.disconnect() }
                )
            } else {
                // Config and create/join screen
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

            // Global Loading Indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }

            // Global Error Display
            error?.let { errMessage ->
                AlertDialog(
                    onDismissRequest = { mainViewModel.clearError() },
                    confirmButton = {
                        TextButton(onClick = { mainViewModel.clearError() }) {
                            Text("OK", color = ElectricCyan)
                        }
                    },
                    title = { Text("Error", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text(errMessage, color = Color.White) },
                    containerColor = CardBackground,
                    shape = RoundedCornerShape(16.dp)
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Branding Header
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "RiderLink",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = ElectricCyan,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Group Voice Intercom",
                fontSize = 16.sp,
                color = MutedText,
                textAlign = TextAlign.Center
            )
        }

        // Permission status warning banner
        if (!permissionsGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafetyOrange.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Microphone & Bluetooth permission required!",
                            color = SafetyOrange,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Text("Grant Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Rider Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("RIDER PROFILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                    OutlinedTextField(
                        value = riderName,
                        onValueChange = { mainViewModel.riderName.value = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            focusedLabelColor = ElectricCyan,
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Create Room Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "START NEW GROUP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { mainViewModel.createRoom() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp), // Large glove-friendly height
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "CREATE ROOM",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Join Room Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "JOIN EXISTING GROUP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                joinCodeInput = it
                            }
                        },
                        placeholder = { Text("Enter 4-Digit Code", fontSize = 18.sp, color = MutedText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = MutedText,
                            unfocusedPlaceholderColor = MutedText
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { mainViewModel.joinRoom(joinCodeInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        enabled = joinCodeInput.length == 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "JOIN ROOM",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Advanced Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced LiveKit Settings", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isAdvancedExpanded) "▲" else "▼",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            OutlinedTextField(
                                value = livekitUrl,
                                onValueChange = { mainViewModel.livekitUrl.value = it },
                                label = { Text("Server URL") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedTextColor = Color.White, focusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { mainViewModel.apiKey.value = it },
                                label = { Text("API Key") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedTextColor = Color.White, focusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiSecret,
                                onValueChange = { mainViewModel.apiSecret.value = it },
                                label = { Text("API Secret") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedTextColor = Color.White, focusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
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
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Room header info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("ACTIVE INTERCOM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
            Spacer(modifier = Modifier.height(4.dp))
            // Big room code
            Text(
                text = roomCode,
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )
            // Connection state indicator
            val stateText = when (connectionState) {
                Room.State.CONNECTED -> "CONNECTED"
                Room.State.CONNECTING -> "CONNECTING..."
                Room.State.RECONNECTING -> "RECONNECTING..."
                Room.State.DISCONNECTED -> "DISCONNECTED"
                else -> "RECONNECTING..."
            }
            val stateColor = when (connectionState) {
                Room.State.CONNECTED -> ActiveGreen
                Room.State.CONNECTING -> SafetyOrange
                Room.State.RECONNECTING -> SafetyOrange
                Room.State.DISCONNECTED -> MuteRed
                else -> SafetyOrange
            }
            Text(
                text = stateText,
                color = stateColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Giant glove-friendly Mute button
        Button(
            onClick = onMuteToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMuted) MuteRed else ActiveGreen
            ),
            modifier = Modifier
                .size(220.dp)
                .border(8.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            shape = CircleShape
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isMuted) "MUTED" else "LIVE",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isMuted) "TAP TO TALK" else "TAP TO MUTE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // List of Riders in Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RIDERS IN GROUP (${participants.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedText
                )
                Text(
                    text = "● Live",
                    color = ActiveGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (participants.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No other riders connected", color = MutedText)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(participants) { participant ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkCharcoal, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ActiveGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = participant,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Disconnect button
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = MuteRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "DISCONNECT",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}
