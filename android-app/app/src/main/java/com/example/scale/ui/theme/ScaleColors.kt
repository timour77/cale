package com.example.scale.ui.theme

import androidx.compose.ui.graphics.Color

object ScaleColors {
    val BG_PRIMARY = Color(0xFF0A1410)
    val BG_SURFACE = Color(0xFF0E1612)
    val TEXT_PRIMARY = Color.White
    val TEXT_SECONDARY_55 = Color.White.copy(alpha = 0.55f)
    val TEXT_SECONDARY_45 = Color.White.copy(alpha = 0.45f)
    val TEXT_SECONDARY_35 = Color.White.copy(alpha = 0.35f)
    val ACCENT_MINT = Color(0xFF86EFAC)
    val ACCENT_AMBER = Color(0xFFFBBF24)
    val ACCENT_SKY = Color(0xFF7DD3FC)
    val ACCENT_ROSE = Color(0xFFEDA4AF)
    val ACCENT_VIOLET = Color(0xFFC4B5FD)
    val ACCENT_SECONDARY = Color(0xFFFB923C)
    val DISABLED_DOT = Color(0xFF888888)

    val ACCENT_PALETTE = listOf(
        ACCENT_MINT,
        ACCENT_AMBER,
        ACCENT_SKY,
        ACCENT_ROSE,
        ACCENT_VIOLET,
    )
}
