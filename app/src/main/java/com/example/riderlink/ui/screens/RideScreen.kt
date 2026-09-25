package com.example.riderlink.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.riderlink.audio.TrackInfo
import com.example.riderlink.domain.model.AudioRoute
import com.example.riderlink.domain.model.ConnectionStatus
import com.example.riderlink.domain.model.Rider
import com.example.riderlink.domain.model.RiderState
import com.example.riderlink.theme.DangerRed
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.LiveGreen
import com.example.riderlink.theme.PageGradient
import com.example.riderlink.theme.PrivateViolet
import com.example.riderlink.theme.Surface1
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextSecondary
import com.example.riderlink.ui.components.MicButton
import com.example.riderlink.ui.components.NowPlayingStrip
import com.example.riderlink.ui.components.RiderRow
import com.example.riderlink.ui.components.SecondaryAction
import com.example.riderlink.ui.components.SectionLabel
import com.example.riderlink.ui.components.StatusHeader
import com.example.riderlink.ui.components.Waveform
import com.example.riderlink.ui.components.panel

/**
 * The rider dashboard: the screen that is glanced at, not read.
 *
 * Vertical order follows how urgently a rider needs each thing: connection state
 * at the top, then who is talking, then the roster, then the microphone within
 * thumb reach at the bottom.
 */
@Composable
fun RideScreen(
    status: ConnectionStatus,
    rideCode: String?,
    riders: List<Rider>,
    muted: Boolean,
    reconnectAttempt: Int,
    audioRoute: AudioRoute,
    privateChatWith: String?,
    localTrack: TrackInfo?,
    sharedTrack: TrackInfo?,
    error: String?,
    onDismissError: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectRider: (Rider) -> Unit,
    onReturnToGroup: () -> Unit,
    onShareTrack: () -> Unit,
    onDismissSharedTrack: () -> Unit,
    onLeaveRide: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speaker = riders.firstOrNull { it.state == RiderState.SPEAKING }
    val waveColor by animateColorAsState(
        targetValue = when {
            privateChatWith != null -> PrivateViolet
            speaker != null -> LiveGreen
            status.isLive -> Electric
            else -> TextMuted
        },
        animationSpec = tween(320),
        label = "wave",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            StatusHeader(
                status = status,
                rideCode = rideCode,
                reconnectAttempt = reconnectAttempt,
                audioRoute = audioRoute,
            )

            Spacer(Modifier.height(18.dp))

            Waveform(active = speaker != null, color = waveColor)

            Text(
                text = when {
                    privateChatWith != null -> "Private channel · $privateChatWith"
                    speaker != null -> "${speaker.displayName} is speaking"
                    status.isLive -> "Channel clear"
                    else -> "Waiting for the link"
                },
                color = if (speaker != null) LiveGreen else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ErrorBanner(
                    message = error.orEmpty(),
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            NowPlayingStrip(
                localTrack = localTrack,
                sharedTrack = sharedTrack,
                onShare = onShareTrack,
                onDismissShared = onDismissSharedTrack,
            )

            Spacer(Modifier.height(14.dp))

            RiderPanel(
                riders = riders,
                status = status,
                onSelectRider = onSelectRider,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(16.dp))

            ControlDock(
                status = status,
                muted = muted,
                inPrivateChat = privateChatWith != null,
                onToggleMute = onToggleMute,
                onReturnToGroup = onReturnToGroup,
                onLeaveRide = onLeaveRide,
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RiderPanel(
    riders: List<Rider>,
    status: ConnectionStatus,
    onSelectRider: (Rider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel(corner = 22.dp, fill = Surface1)
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Riders")
            SectionLabel(
                text = if (riders.isEmpty()) "—" else riders.size.toString(),
                color = Electric,
            )
        }

        Spacer(Modifier.height(6.dp))

        when {
            riders.isEmpty() && status.isActive -> PanelMessage("Waiting for riders to join…")
            riders.isEmpty() -> PanelMessage("No one on the channel.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                items(riders, key = { it.identity }) { rider ->
                    RiderRow(
                        rider = rider,
                        onClick = if (rider.isLocal) null else ({ onSelectRider(rider) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The bottom dock. The microphone dominates; everything else is deliberately
 * secondary so it cannot be hit by mistake while reaching for mute.
 */
@Composable
private fun ControlDock(
    status: ConnectionStatus,
    muted: Boolean,
    inPrivateChat: Boolean,
    onToggleMute: () -> Unit,
    onReturnToGroup: () -> Unit,
    onLeaveRide: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MicButton(status = status, muted = muted, onToggle = onToggleMute)

        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(
            visible = inPrivateChat,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                SecondaryAction(
                    label = "Back to group",
                    onClick = onReturnToGroup,
                    borderColor = PrivateViolet,
                    contentColor = PrivateViolet,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryAction(
                label = "Settings",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            SecondaryAction(
                label = "Leave",
                onClick = onLeaveRide,
                modifier = Modifier.weight(1f),
                borderColor = DangerRed.copy(alpha = 0.55f),
                contentColor = DangerRed,
            )
        }
    }
}
