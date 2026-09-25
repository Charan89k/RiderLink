package com.example.riderlink.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =============================================================================
// RiderLink palette
//
// A cockpit at night: near-black surfaces so the screen never dazzles a rider,
// one electric accent for anything live, and colour reserved for state changes
// rather than decoration. Every value here is used somewhere; there is no
// speculative palette.
// =============================================================================

/** Page background. Almost pure black so OLED pixels stay off. */
val Obsidian = Color(0xFF040405)
val ObsidianDeep = Color(0xFF09090C)

/** Raised surfaces, in order of elevation. */
val Surface1 = Color(0xFF0C0C10)
val Surface2 = Color(0xFF131318)
val Surface3 = Color(0xFF1B1B21)

/** Hairline borders. Alpha, so they sit correctly on any surface. */
val HairlineSubtle = Color(0x0AFFFFFF)
val Hairline = Color(0x14FFFFFF)
val HairlineStrong = Color(0x24FFFFFF)

/** The accent. Used only for live, active, or interactive things. */
val Electric = Color(0xFF00F2FE)
val ElectricDim = Color(0xFF4FACFE)

/** State colours. */
val LiveGreen = Color(0xFF00FFB0)
val WarnAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFFF2A54)
val DangerRedSoft = Color(0xFFFF5252)
val PrivateViolet = Color(0xFFB388FF)

/** Text ramp. */
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)
val TextFaint = Color(0xFF52525B)

// -----------------------------------------------------------------------------
// Gradients
// -----------------------------------------------------------------------------

val ElectricGradient = Brush.horizontalGradient(listOf(Electric, ElectricDim))
val LiveGradient = Brush.horizontalGradient(listOf(LiveGreen, Electric))
val DangerGradient = Brush.horizontalGradient(listOf(DangerRed, DangerRedSoft))
val PageGradient = Brush.verticalGradient(listOf(Obsidian, ObsidianDeep))
