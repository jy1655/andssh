package com.opencode.sshterminal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = TerminalGreen,
        onPrimary = SurfaceBlack,
        primaryContainer = TerminalGreenDark,
        onPrimaryContainer = TextPrimary,
        secondary = TerminalGreen,
        onSecondary = SurfaceBlack,
        background = SurfaceBlack,
        onBackground = TextPrimary,
        surface = SurfaceDim,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceCard,
        onSurfaceVariant = TextSecondary,
        surfaceContainerHighest = SurfaceContainer,
        error = ErrorRed,
        onError = SurfaceBlack,
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
