package com.example.findme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider

/** Shortcut for the current Yandex-style palette. */
val yPalette: YPalette
    @Composable get() = LocalYPalette.current

@Composable
fun FindMeTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val p = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            secondary = p.coral, onSecondary = p.onCoral,
            error = YColors.Red, outline = p.divider, outlineVariant = p.divider,
            surface = p.sheet, onSurface = p.textPrimary,
            background = p.sheet, onBackground = p.textPrimary,
            surfaceVariant = p.field, onSurfaceVariant = p.textSecondary,
            surfaceContainerLow = p.sheet, surfaceContainer = p.sheet, surfaceContainerHigh = p.sheet,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            secondary = p.coral, onSecondary = p.onCoral,
            error = YColors.Red, outline = p.divider, outlineVariant = p.divider,
            surface = p.sheet, onSurface = p.textPrimary,
            background = p.sheet, onBackground = p.textPrimary,
            surfaceVariant = p.field, onSurfaceVariant = p.textSecondary,
            surfaceContainerLow = p.sheet, surfaceContainer = p.sheet, surfaceContainerHigh = p.sheet,
        )
    }
    // Default content color = the palette's text. Bottom sheets on a color outside the scheme
    // (e.g. `ground`) fall back to it, and pick their status/navigation bar icons from it.
    CompositionLocalProvider(LocalYPalette provides p, LocalContentColor provides p.textPrimary) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
