package com.example.riderlink.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.riderlink.audio.TrackInfo
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.PrivateViolet
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary

/**
 * What is playing, and what another rider has shared.
 *
 * Music keeps running underneath the intercom, so the dashboard shows the track
 * rather than pretending the app has taken the phone over. Long titles marquee
 * instead of wrapping, which would reflow the dashboard mid-ride.
 */
@Composable
fun NowPlayingStrip(
    localTrack: TrackInfo?,
    sharedTrack: TrackInfo?,
    onShare: () -> Unit,
    onDismissShared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        AnimatedVisibility(
            visible = localTrack != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TrackRow(
                accent = Electric,
                caption = "Now playing",
                track = localTrack,
                actionLabel = "Share",
                onAction = onShare,
            )
        }

        AnimatedVisibility(
            visible = sharedTrack != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TrackRow(
                accent = PrivateViolet,
                caption = "Shared by a rider",
                track = sharedTrack,
                actionLabel = "Clear",
                onAction = onDismissShared,
            )
        }
    }
}

@Composable
private fun TrackRow(
    accent: androidx.compose.ui.graphics.Color,
    caption: String,
    track: TrackInfo?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(100))
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("♪", color = accent, style = MaterialTheme.typography.labelMedium)
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = caption.uppercase(),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = listOfNotNull(track?.title, track?.artist?.takeIf { it.isNotBlank() })
                    .joinToString(" — "),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        }

        Text(
            text = actionLabel.uppercase(),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .clickableTarget(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
