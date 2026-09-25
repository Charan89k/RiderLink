package com.example.riderlink.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * The ride's pulse.
 *
 * Idle, it is a slow shallow ripple that says the app is alive without demanding
 * attention. When someone speaks it rises into a taller, faster waveform in that
 * speaker's colour. The amplitude is animated rather than switched so speakers
 * hand over smoothly instead of the bar snapping between states.
 */
@Composable
fun Waveform(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 42,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (active) 1100 else 3600), RepeatMode.Restart),
        label = "phase",
    )

    val amplitude by animateFloatAsState(
        targetValue = if (active) 1f else 0.16f,
        animationSpec = tween(320),
        label = "amplitude",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val barWidth = size.width / (barCount * 2f)
        val centerY = size.height / 2f

        repeat(barCount) { index ->
            val fraction = index / (barCount - 1f)

            // Taper towards both ends so the waveform reads as a single shape
            // rather than a row of unrelated bars.
            val envelope = sin(fraction * Math.PI).toFloat()
            val wave = sin(phase + fraction * 6f)
            val magnitude = abs(wave) * envelope * amplitude

            val barHeight = (size.height * 0.9f * magnitude).coerceAtLeast(3f)
            val x = index * (size.width / barCount) + barWidth / 2f

            drawRoundRectBar(
                x = x,
                centerY = centerY,
                width = barWidth,
                height = barHeight,
                color = color.copy(alpha = 0.35f + 0.65f * envelope),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectBar(
    x: Float,
    centerY: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, centerY - height / 2f),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, width / 2f),
    )
}
