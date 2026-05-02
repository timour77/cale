package com.example.scale.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scale.ui.theme.ScaleColors

@Composable
fun ControlBar(
    enabled: Boolean,
    timerRunning: Boolean,
    accent: Color,
    onTare: () -> Unit,
    onStartStop: () -> Unit,
    onRecipes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControlButton(
            label = "TARE",
            enabled = enabled,
            color = ScaleColors.TEXT_PRIMARY,
            borderColor = Color.White.copy(alpha = 0.18f),
            onClick = onTare,
        )
        ControlButton(
            label = if (timerRunning) "STOP" else "START",
            enabled = enabled,
            color = if (timerRunning) ScaleColors.ACCENT_AMBER else accent,
            borderColor = (if (timerRunning) ScaleColors.ACCENT_AMBER else accent).copy(alpha = 0.6f),
            onClick = onStartStop,
        )
        ControlButton(
            label = "RECIPES",
            enabled = true,
            color = ScaleColors.TEXT_PRIMARY,
            borderColor = Color.White.copy(alpha = 0.18f),
            onClick = onRecipes,
        )
    }
}

@Composable
private fun RowScope.ControlButton(
    label: String,
    enabled: Boolean,
    color: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) borderColor else Color.White.copy(alpha = 0.08f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = ScaleColors.TEXT_SECONDARY_35,
        ),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            ),
        )
    }
}
