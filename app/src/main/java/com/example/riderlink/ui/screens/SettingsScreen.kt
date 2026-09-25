package com.example.riderlink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.LiveGreen
import com.example.riderlink.theme.WarnAmber
import com.example.riderlink.theme.HairlineSubtle
import com.example.riderlink.theme.Obsidian
import com.example.riderlink.theme.PageGradient
import com.example.riderlink.theme.Surface1
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary
import com.example.riderlink.theme.TextSecondary
import com.example.riderlink.ui.components.SecondaryAction
import com.example.riderlink.ui.components.clickableTarget
import com.example.riderlink.ui.components.SectionLabel
import com.example.riderlink.ui.components.panel

/** Everything the settings screen can change. Grouped as a data holder so the
 *  screen stays a pure function of state and does not reach into the ViewModel. */
data class SettingsState(
    val riderName: String,
    val tokenServerUrl: String,
    val voiceVolume: Int,
    val maxVoiceVolume: Int,
    val musicVolume: Int,
    val maxMusicVolume: Int,
    val noiseSuppression: Boolean,
    val echoCancellation: Boolean,
    val autoGainControl: Boolean,
    val highPassFilter: Boolean,
    val voipAudioMode: Boolean,
    val volumeBoost: Boolean,
    val pauseMusicWhileTalking: Boolean,
    val appVersion: String,
    val notificationAccessGranted: Boolean,
)

data class SettingsActions(
    val onRiderName: (String) -> Unit,
    val onTokenServerUrl: (String) -> Unit,
    val onVoiceVolume: (Int) -> Unit,
    val onMusicVolume: (Int) -> Unit,
    val onNoiseSuppression: (Boolean) -> Unit,
    val onEchoCancellation: (Boolean) -> Unit,
    val onAutoGainControl: (Boolean) -> Unit,
    val onHighPassFilter: (Boolean) -> Unit,
    val onVoipAudioMode: (Boolean) -> Unit,
    val onVolumeBoost: (Boolean) -> Unit,
    val onPauseMusicWhileTalking: (Boolean) -> Unit,
    val onOpenNotificationAccess: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * Settings.
 *
 * Only controls that are actually wired to behaviour appear here. Where a
 * setting cannot take effect until the next connection, the row says so rather
 * than silently doing nothing.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageGradient),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            item {
                SettingsGroup("Rider") {
                    TextSetting(
                        label = "Call sign",
                        value = state.riderName,
                        placeholder = "Rider",
                        onValueChange = actions.onRiderName,
                    )
                    GroupDivider()
                    TextSetting(
                        label = "Token server",
                        value = state.tokenServerUrl,
                        placeholder = "https://…",
                        supporting = "Issues access tokens. No LiveKit keys are stored on this phone.",
                        onValueChange = actions.onTokenServerUrl,
                    )
                }
            }

            item {
                SettingsGroup("Audio") {
                    SliderSetting(
                        label = "Voice volume",
                        value = state.voiceVolume,
                        max = state.maxVoiceVolume,
                        onValueChange = actions.onVoiceVolume,
                    )
                    GroupDivider()
                    SliderSetting(
                        label = "Music volume",
                        value = state.musicVolume,
                        max = state.maxMusicVolume,
                        onValueChange = actions.onMusicVolume,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "Pause music while talking",
                        supporting = "Off: music ducks to a lower volume instead of pausing.",
                        checked = state.pauseMusicWhileTalking,
                        onCheckedChange = actions.onPauseMusicWhileTalking,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "Maximum volume",
                        supporting = "Forces voice and music streams to full volume.",
                        checked = state.volumeBoost,
                        onCheckedChange = actions.onVolumeBoost,
                    )
                }
            }

            item {
                SettingsGroup("Voice processing") {
                    SwitchSetting(
                        label = "Noise suppression",
                        supporting = "Cuts wind and engine noise.",
                        checked = state.noiseSuppression,
                        onCheckedChange = actions.onNoiseSuppression,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "Echo cancellation",
                        checked = state.echoCancellation,
                        onCheckedChange = actions.onEchoCancellation,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "Automatic gain control",
                        checked = state.autoGainControl,
                        onCheckedChange = actions.onAutoGainControl,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "High-pass filter",
                        checked = state.highPassFilter,
                        onCheckedChange = actions.onHighPassFilter,
                    )
                    GroupDivider()
                    SwitchSetting(
                        label = "VoIP audio mode",
                        supporting = "Routes through the call audio path. Turn off if your helmet sounds muffled.",
                        checked = state.voipAudioMode,
                        onCheckedChange = actions.onVoipAudioMode,
                    )
                    GroupDivider()
                    Note("Voice processing changes apply the next time you join a ride.")
                }
            }

            item {
                SettingsGroup("Helmet buttons") {
                    GestureRow("Triple-press play/pause", "Mute or unmute your microphone")
                    GroupDivider()
                    GestureRow("Double long-press volume up", "Open a private channel")
                    GroupDivider()
                    GestureRow("Double long-press volume down", "Return to the group")
                    GroupDivider()
                    NotificationAccessRow(
                        granted = state.notificationAccessGranted,
                        onOpen = actions.onOpenNotificationAccess,
                    )
                }
            }

            item {
                SettingsGroup("About") {
                    InfoRow("Version", state.appVersion)
                    GroupDivider()
                    InfoRow("Voice transport", "LiveKit · WebRTC")
                    GroupDivider()
                    Note(
                        "RiderLink transmits voice over your mobile data connection. " +
                            "Audio is not recorded or stored."
                    )
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SecondaryAction(label = "Done", onClick = actions.onBack)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title, modifier = Modifier.padding(start = 4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .panel(corner = 20.dp, fill = Surface1),
        ) {
            content()
        }
    }
}

@Composable
private fun GroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HairlineSubtle),
    )
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = Electric,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = HairlineSubtle,
            ),
        )
    }
}

@Composable
private fun SliderSetting(label: String, value: Int, max: Int, onValueChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (max > 0) "${value * 100 / max}%" else "—",
                color = Electric,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..max.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Electric,
                activeTrackColor = Electric,
                inactiveTrackColor = Surface2,
            ),
        )
    }
}

@Composable
private fun TextSetting(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Electric),
            cursorBrush = SolidColor(Electric),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                inner()
            },
        )
        if (supporting != null) {
            Text(supporting, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GestureRow(gesture: String, result: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(gesture, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Text(result, color = Electric, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Notification access gates the helmet-button gestures and the track readout.
 *
 * It cannot be requested with a runtime permission dialog -- only the system
 * settings screen can grant it -- so this row states plainly whether it is on
 * and takes the rider there.
 */
@Composable
private fun NotificationAccessRow(granted: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickableTarget(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Notification access", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) {
                    "Granted. Helmet buttons and track info are active."
                } else {
                    "Not granted. Helmet buttons and track info will not work."
                },
                color = if (granted) LiveGreen else WarnAmber,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = if (granted) "CHANGE" else "GRANT",
            color = Electric,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = TextMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
    )
}

