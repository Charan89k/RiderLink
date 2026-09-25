package com.example.riderlink.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography for a screen glanced at through a visor.
 *
 * Two ideas: display text is large and tight, and anything that acts as an
 * instrument label is small, uppercase and widely tracked so it reads as a label
 * rather than as prose. Ride codes use a monospace face so digits never shift
 * position as they are typed.
 */
val Typography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 52.sp,
      lineHeight = 56.sp,
      letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Bold,
      fontSize = 36.sp,
      lineHeight = 40.sp,
      letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 24.sp,
      lineHeight = 30.sp,
      letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 17.sp,
      lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 20.sp,
    ),
    /** Instrument label: uppercase, tracked out. */
    labelLarge = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 1.4.sp,
    ),
    labelMedium = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      lineHeight = 14.sp,
      letterSpacing = 1.2.sp,
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.Default,
      fontWeight = FontWeight.Medium,
      fontSize = 10.sp,
      lineHeight = 13.sp,
      letterSpacing = 1.sp,
    ),
  )

/** Ride codes. Monospace keeps digit positions fixed while typing. */
val RideCodeStyle = TextStyle(
  fontFamily = FontFamily.Monospace,
  fontWeight = FontWeight.Bold,
  fontSize = 40.sp,
  letterSpacing = 8.sp,
)
