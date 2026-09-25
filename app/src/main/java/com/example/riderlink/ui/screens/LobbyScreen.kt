package com.example.riderlink.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.riderlink.theme.DangerRed
import com.example.riderlink.theme.Electric
import com.example.riderlink.theme.Hairline
import com.example.riderlink.theme.PageGradient
import com.example.riderlink.theme.Surface2
import com.example.riderlink.theme.TextFaint
import com.example.riderlink.theme.TextMuted
import com.example.riderlink.theme.TextPrimary
import com.example.riderlink.theme.TextSecondary
import com.example.riderlink.ui.components.PrimaryAction
import com.example.riderlink.ui.components.RideCodeInput
import com.example.riderlink.ui.components.SecondaryAction
import com.example.riderlink.ui.components.SectionLabel
import com.example.riderlink.ui.components.clickableTarget
import com.example.riderlink.ui.components.panel

/**
 * Where a ride begins.
 *
 * Two choices, nothing else on screen. Joining expands in place rather than
 * pushing a second screen, so the code can be entered without losing sight of
 * the button that submits it.
 */
@Composable
fun LobbyScreen(
    riderName: String,
    onRiderNameChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onCreateRide: () -> Unit,
    onJoinRide: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinExpanded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GearButton(onClick = onOpenSettings)
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary)) { append("RIDER") }
                    withStyle(SpanStyle(color = Electric)) { append("LINK") }
                },
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Group intercom for standard helmet headsets",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            RiderNameField(value = riderName, onValueChange = onRiderNameChange)

            Spacer(Modifier.height(28.dp))

            PrimaryAction(
                label = "Create ride",
                onClick = onCreateRide,
                enabled = !isLoading,
                loading = isLoading && !joinExpanded,
            )

            Spacer(Modifier.height(14.dp))

            SecondaryAction(
                label = if (joinExpanded) "Cancel" else "Join ride",
                onClick = {
                    joinExpanded = !joinExpanded
                    code = ""
                    onDismissError()
                },
                enabled = !isLoading,
            )

            AnimatedVisibility(
                visible = joinExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionLabel("Enter ride code")
                    Spacer(Modifier.height(16.dp))
                    RideCodeInput(code = code, onCodeChange = { code = it })
                    Spacer(Modifier.height(24.dp))
                    PrimaryAction(
                        label = "Join",
                        onClick = { onJoinRide(code) },
                        enabled = code.length == 4 && !isLoading,
                        loading = isLoading,
                    )
                }
            }

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ErrorBanner(
                    message = error.orEmpty(),
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RiderNameField(value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Your call sign")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .panel(corner = 18.dp, fill = Surface2)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(24)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = TextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Electric),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Rider",
                            color = TextFaint,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

/** Errors are shown inline and dismissible, never as a raw stack trace. */
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DangerRed.copy(alpha = 0.10f))
            .clickableTarget(onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(DangerRed))
        Text(
            text = message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = "DISMISS", color = DangerRed, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Surface2)
            .clickableTarget(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Three stacked bars read as "settings" without needing an icon font.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(width = if (index == 1) 12.dp else 18.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(if (index == 1) Electric else Hairline.copy(alpha = 0.9f)),
                )
            }
        }
    }
}
