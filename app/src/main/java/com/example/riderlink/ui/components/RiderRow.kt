package com.example.riderlink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.riderlink.domain.model.Rider
import com.example.riderlink.domain.model.RiderState
import com.example.riderlink.theme.DangerRed
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.Hairline
import com.example.riderlink.theme.LiveGreen
import com.example.riderlink.theme.PrivateViolet
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary
import com.example.riderlink.theme.TextSecondary
import com.example.riderlink.theme.WarnAmber

/** The colour that stands for each rider state, used by both the dot and the ring. */
fun RiderState.color(): Color = when (this) {
    RiderState.SPEAKING -> LiveGreen
    RiderState.PRIVATE -> PrivateViolet
    RiderState.MUTED -> DangerRed
    RiderState.CONNECTED -> Electric
    RiderState.RECONNECTING -> WarnAmber
    RiderState.DISCONNECTED -> TextMuted
}

/**
 * One rider in the roster.
 *
 * The avatar ring thickens and lights up while that rider is speaking, which is
 * the fastest way to answer the only question a rider actually has mid-ride:
 * who is talking. Tapping a row opens a private channel to them.
 */
@Composable
fun RiderRow(
    rider: Rider,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val stateColor = rider.state.color()
    val speaking = rider.state == RiderState.SPEAKING

    val ringWidth by animateDpAsState(
        targetValue = if (speaking) 3.dp else 1.dp,
        animationSpec = tween(220),
        label = "ring",
    )
    val ringColor by animateColorAsState(
        targetValue = if (speaking) stateColor else Hairline,
        animationSpec = tween(220),
        label = "ringColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(if (speaking) stateColor.copy(alpha = 0.06f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier.clickableTarget(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Surface2)
                .border(ringWidth, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rider.initials,
                color = if (speaking) stateColor else TextSecondary,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = if (rider.isLocal) "${rider.displayName} · You" else rider.displayName,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = rider.state.label,
                color = stateColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        StatusDot(color = stateColor, pulsing = speaking, size = 9.dp)
    }
}
