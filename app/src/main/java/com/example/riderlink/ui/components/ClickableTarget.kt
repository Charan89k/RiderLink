package com.example.riderlink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.example.riderlink.theme.Electric

/**
 * Clickable with a ripple tinted to the app accent.
 *
 * Wrapped in one place so no screen accidentally falls back to Material's
 * default ripple colour, which reads as grey on these surfaces.
 */
fun Modifier.clickableTarget(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remembered()
    clickable(
        interactionSource = source,
        indication = ripple(color = Electric),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun remembered(): MutableInteractionSource =
    androidx.compose.runtime.remember { MutableInteractionSource() }
