package com.example.riderlink.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

/**
 * Tap and long-press on one target, with the same accent ripple.
 *
 * Long-press chooses the private target without switching channel, so a rider
 * can line up who volume-up will call before they set off.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableTarget(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    combinedClickable(
        interactionSource = remembered(),
        indication = ripple(color = Electric),
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
private fun remembered(): MutableInteractionSource =
    androidx.compose.runtime.remember { MutableInteractionSource() }
