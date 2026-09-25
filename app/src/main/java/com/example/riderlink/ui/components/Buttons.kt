package com.example.riderlink.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.riderlink.theme.ElectricGradient
import com.example.riderlink.theme.HairlineStrong
import com.example.riderlink.theme.Obsidian
import com.example.riderlink.theme.TextPrimary

/**
 * Minimum height for anything a rider might press wearing gloves.
 *
 * Material's 48dp default assumes a bare fingertip. A gloved thumb on a moving
 * bike needs considerably more, so every primary control in this app is at least
 * this tall.
 */
val GloveTargetHeight = 88.dp

/**
 * Filled action button. Scales down slightly on press: a visible acknowledgement
 * matters when a rider cannot feel a click through a glove.
 */
@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    brush: Brush = ElectricGradient,
    contentColor: Color = Obsidian,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(),
        label = "pressScale",
    )
    val active = enabled && !loading

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GloveTargetHeight)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) brush else Brush.horizontalGradient(listOf(HairlineStrong, HairlineStrong)))
            .clickableTarget(enabled = active, interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = label.uppercase(),
                color = if (active) contentColor else TextPrimary.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Outlined counterpart for the secondary choice on a screen. */
@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = HairlineStrong,
    contentColor: Color = TextPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(),
        label = "pressScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GloveTargetHeight)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(22.dp))
            .clickableTarget(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
