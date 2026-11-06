package com.fariddroid.gtbikecompanion.ui.theme

import androidx.compose.ui.graphics.Color

// Original "Los Santos dusk" palette: night violet surfaces with sunset orange, neon pink and pool cyan.
// No colors, logos or assets are taken from Rockstar or Zwift. WCAG contrast checked 2026-11-04:
// every text/accent token is >= 4.5:1 on background, surface and surfaceHigh in both themes.

// Dark (default for training)
val DuskBackground = Color(0xFF100C1A)
val DuskSurface = Color(0xFF1C1529)
val DuskSurfaceHigh = Color(0xFF2A2040)
val DuskText = Color(0xFFF4EFFA)
val DuskTextMuted = Color(0xFFBDB1D0)
val DuskPrimary = Color(0xFFFF9A4D)      // sunset orange
val DuskOnPrimary = Color(0xFF2A1200)
val DuskSecondary = Color(0xFFFF5FAE)    // neon pink
val DuskTertiary = Color(0xFF47D9E3)     // pool cyan
val DuskSuccess = Color(0xFF5BD68A)
val DuskWarning = Color(0xFFFFD15C)
val DuskError = Color(0xFFFF7A7A)

// Light
val DayBackground = Color(0xFFFBF6F1)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceHigh = Color(0xFFF1E8F5)
val DayText = Color(0xFF1C1426)
val DayTextMuted = Color(0xFF5A4E68)
val DayPrimary = Color(0xFFA84300)
val DayOnPrimary = Color(0xFFFFFFFF)
val DaySecondary = Color(0xFFA3135F)
val DayTertiary = Color(0xFF00666E)
val DaySuccess = Color(0xFF00703F)
val DayWarning = Color(0xFF7A5500)
val DayError = Color(0xFFB3261E)

// Power zones 1..7 (drawn on dark surface, >= 5.7:1). Zones always come with a number/label, never color alone.
val ZoneColors = listOf(
    Color(0xFF9A98B0), Color(0xFF4AA8FF), Color(0xFF5BD68A), Color(0xFFFFD15C),
    Color(0xFFFF9A4D), Color(0xFFFF5A5A), Color(0xFFC77DFF),
)
