package com.fariddroid.gtbikecompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Status colors Material 3 does not name. Always paired with icon/text, never color alone. */
@Immutable
data class StatusColors(val success: Color, val warning: Color, val error: Color, val muted: Color, val zones: List<Color>)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(DuskSuccess, DuskWarning, DuskError, DuskTextMuted, ZoneColors)
}

private val DarkColors = darkColorScheme(
    primary = DuskPrimary, onPrimary = DuskOnPrimary,
    secondary = DuskSecondary, onSecondary = DuskBackground,
    tertiary = DuskTertiary, onTertiary = DuskBackground,
    background = DuskBackground, onBackground = DuskText,
    surface = DuskSurface, onSurface = DuskText,
    surfaceVariant = DuskSurfaceHigh, onSurfaceVariant = DuskTextMuted,
    surfaceContainer = DuskSurface, surfaceContainerHigh = DuskSurfaceHigh, surfaceContainerLow = DuskSurface,
    error = DuskError, onError = DuskBackground,
    outline = DuskTextMuted,
)

private val LightColors = lightColorScheme(
    primary = DayPrimary, onPrimary = DayOnPrimary,
    secondary = DaySecondary, onSecondary = DayOnPrimary,
    tertiary = DayTertiary, onTertiary = DayOnPrimary,
    background = DayBackground, onBackground = DayText,
    surface = DaySurface, onSurface = DayText,
    surfaceVariant = DaySurfaceHigh, onSurfaceVariant = DayTextMuted,
    surfaceContainer = DaySurface, surfaceContainerHigh = DaySurfaceHigh, surfaceContainerLow = DayBackground,
    error = DayError, onError = DayOnPrimary,
    outline = DayTextMuted,
)

/** No dynamic color: the training identity must stay consistent and contrast-checked. */
@Composable
fun CompanionTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val status = if (dark) {
        StatusColors(DuskSuccess, DuskWarning, DuskError, DuskTextMuted, ZoneColors)
    } else {
        StatusColors(DaySuccess, DayWarning, DayError, DayTextMuted, ZoneColors)
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography, content = content)
    }
}
