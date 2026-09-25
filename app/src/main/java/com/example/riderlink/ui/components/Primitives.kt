package com.example.riderlink.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.riderlink.theme.Hairline
import com.example.riderlink.theme.Surface1
import com.example.riderlink.theme.TextMuted

/**
 * The one surface treatment used throughout: a hairline border over a barely
 * lighter fill. Panels are distinguished by their border, not by a drop shadow,
 * which would be invisible on a near-black background anyway.
 */
fun Modifier.panel(
    corner: Dp = 20.dp,
    fill: Color = Surface1,
    border: Color = Hairline,
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .border(1.dp, border, RoundedCornerShape(corner))

/** Small tracked-out uppercase caption used to label instruments. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextMuted,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * Status dot. Pulses only when [pulsing] is set, so a steady dot genuinely means
 * a steady state and motion always carries information.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulsing: Boolean = false,
) {
    if (!pulsing) {
        Box(modifier.size(size).clip(CircleShape).background(color))
        return
    }

    val transition = rememberInfiniteTransition(label = "statusDot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    val fade by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "fade",
    )

    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .alpha(1f - (fade * 0.6f))
                .clip(CircleShape)
                .background(color),
        )
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}
