package com.example.riderlink.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.riderlink.domain.model.AudioRoute
import com.example.riderlink.domain.model.ConnectionStatus
import com.example.riderlink.theme.DangerRed
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.LiveGreen
import com.example.riderlink.theme.RideCodeStyle
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary
import com.example.riderlink.theme.WarnAmber

/**
 * The top of the dashboard: what the connection is doing, and the ride code.
 *
 * The status line is driven by [ConnectionStatus] rather than by whether a room
 * object exists, so it cannot say CONNECTED while the link is dead.
 */
@Composable
fun StatusHeader(
    status: ConnectionStatus,
    rideCode: String?,
    reconnectAttempt: Int,
    audioRoute: AudioRoute,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (status) {
        ConnectionStatus.CONNECTED -> LiveGreen
        ConnectionStatus.CONNECTING -> Electric
        ConnectionStatus.RECONNECTING -> WarnAmber
        ConnectionStatus.ERROR -> DangerRed
        ConnectionStatus.IDLE, ConnectionStatus.DISCONNECTED -> TextMuted
    }
    val animatedStatus by animateColorAsState(statusColor, tween(300), label = "status")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(
                color = animatedStatus,
                pulsing = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECONNECTING,
            )
            Text(
                text = if (status == ConnectionStatus.RECONNECTING && reconnectAttempt > 0) {
                    "RECONNECTING · ${reconnectAttempt}"
                } else {
                    status.label
                },
                color = animatedStatus,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        AnimatedVisibility(
            visible = rideCode != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel("Ride code")
                Text(
                    text = rideCode.orEmpty(),
                    color = TextPrimary,
                    style = RideCodeStyle,
                    textAlign = TextAlign.Center,
                )
            }
        }

        AudioRouteChip(route = audioRoute)
    }
}

/**
 * Helmet indicator.
 *
 * Amber rather than red when audio is on the phone: it is a warning that the
 * rider will not hear the intercom in their helmet, not a failure of the app.
 */
@Composable
fun AudioRouteChip(route: AudioRoute, modifier: Modifier = Modifier) {
    val color = if (route.isHelmetConnected) LiveGreen else WarnAmber

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(color = color, size = 6.dp)
        Text(
            text = if (route.isHelmetConnected) route.label else "${route.label} · helmet not connected",
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
