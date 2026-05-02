package com.example.scale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ScaleColors.ACCENT_MINT,
    secondary = ScaleColors.ACCENT_SECONDARY,
    background = ScaleColors.BG_PRIMARY,
    surface = ScaleColors.BG_SURFACE,
    onBackground = ScaleColors.TEXT_PRIMARY,
    onSurface = ScaleColors.TEXT_PRIMARY,
)

@Composable
fun ScaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ScaleTypography,
        content = content,
    )
}
