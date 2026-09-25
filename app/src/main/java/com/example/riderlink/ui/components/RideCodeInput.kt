package com.example.riderlink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.Hairline
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextFaint
import com.example.riderlink.theme.TextPrimary

/**
 * Four large digit cells backed by one invisible text field.
 *
 * Separate fields per digit are a common pattern and a bad one here: they steal
 * focus from each other and break backspace. A single field keeps entry and
 * deletion predictable while the cells stay big enough to read through a visor.
 */
@Composable
fun RideCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = code,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(length)
                onCodeChange(digits)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .alpha(0f),
            decorationBox = { },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableTarget { runCatching { focusRequester.requestFocus() } },
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            repeat(length) { index ->
                DigitCell(
                    digit = code.getOrNull(index)?.toString(),
                    focused = index == code.length,
                )
            }
        }
    }
}

@Composable
private fun DigitCell(digit: String?, focused: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) Electric else Hairline,
        animationSpec = tween(180),
        label = "cellBorder",
    )

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface2)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit ?: "·",
            color = if (digit != null) TextPrimary else TextFaint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
            ),
        )
    }
}
