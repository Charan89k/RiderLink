package com.example.riderlink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.riderlink.domain.model.ConnectionStatus
import com.example.riderlink.theme.DangerRed
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.LiveGreen
import com.example.riderlink.theme.Obsidian
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.WarnAmber

/**
 * The single most important control in the app.
 *
 * It is 132dp across -- far beyond any touch-target guideline -- because it has
 * to be findable and hittable with a gloved thumb without looking down. Its
 * colour alone tells the rider the state of the intercom, so it stays readable
 * in peripheral vision.
 */
@Composable
fun MicButton(
    status: ConnectionStatus,
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = status.isLive

    val (ringColor, caption) = when {
        status == ConnectionStatus.CONNECTING -> Electric to "CONNECTING"
        status == ConnectionStatus.RECONNECTING -> WarnAmber to "RECONNECTING"
        status == ConnectionStatus.ERROR -> DangerRed to "ERROR"
        !status.isLive -> TextMuted to "OFFLINE"
        muted -> DangerRed to "MIC MUTED"
        else -> LiveGreen to "MIC ON"
    }

    val animatedRing by animateColorAsState(ringColor, tween(300), label = "ring")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(),
        label = "press",
    )

    // A live, unmuted mic breathes. Muted or offline, it is deliberately still.
    val breathing = rememberInfiniteTransition(label = "mic")
    val halo by breathing.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled && !muted) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "halo",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(BUTTON_SIZE)
                    .scale(halo)
                    .clip(CircleShape)
                    .background(animatedRing.copy(alpha = 0.08f)),
            )
            Box(
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .scale(pressScale)
                    .clip(CircleShape)
                    .background(if (enabled && !muted) animatedRing else Surface2)
                    .border(2.dp, animatedRing.copy(alpha = 0.7f), CircleShape)
                    .clickableTarget(enabled = enabled, interactionSource = interaction, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                MicGlyph(
                    muted = muted,
                    tint = if (enabled && !muted) Obsidian else animatedRing,
                )
            }
        }

        Text(
            text = caption,
            color = animatedRing,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Microphone glyph drawn from primitives rather than shipped as an asset, so it
 * scales cleanly and picks up the button's tint without a second drawable.
 */
@Composable
private fun MicGlyph(muted: Boolean, tint: Color) {
    Box(contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier
                    .size(width = 22.dp, height = 38.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Box(
                Modifier
                    .size(width = 34.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        if (muted) {
            // A single diagonal bar: the universal "off" mark, readable at a glance.
            Box(
                Modifier
                    .size(width = 62.dp, height = 5.dp)
                    .rotate(-45f)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

private val BUTTON_SIZE = 132.dp
