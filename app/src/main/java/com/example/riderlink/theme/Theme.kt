package com.example.riderlink.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * RiderLink is dark-only and does not use dynamic colour.
 *
 * A rider needs the same instrument panel on every phone, and a wallpaper-derived
 * pastel accent would undermine the one thing the accent colour means here: live.
 */
private val RiderLinkColorScheme = darkColorScheme(
  primary = Electric,
  onPrimary = Obsidian,
  secondary = ElectricDim,
  onSecondary = Obsidian,
  tertiary = LiveGreen,
  onTertiary = Obsidian,
  background = Obsidian,
  onBackground = TextPrimary,
  surface = Surface1,
  onSurface = TextPrimary,
  surfaceVariant = Surface2,
  onSurfaceVariant = TextSecondary,
  outline = Hairline,
  outlineVariant = HairlineSubtle,
  error = DangerRed,
  onError = TextPrimary,
)

@Composable
fun RiderLinkTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = RiderLinkColorScheme,
    typography = Typography,
    content = content,
  )
}
