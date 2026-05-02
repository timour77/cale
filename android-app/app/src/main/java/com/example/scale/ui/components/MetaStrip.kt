package com.example.scale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scale.ui.theme.ScaleColors
import kotlin.math.roundToInt

@Composable
fun MetaStrip(
    elapsedSeconds: Float,
    flow: Float,
    stageName: String?,
    units: String,
    accent: Color,
    secondary: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        MetaCell(label = "TIME", value = formatTime(elapsedSeconds), valueColor = ScaleColors.TEXT_PRIMARY)
        MetaCell(label = "FLOW", value = "%.1f $units/s".format(flow), valueColor = secondary)
        MetaCell(
            label = "STAGE",
            value = stageName?.takeIf { it.isNotBlank() } ?: "—",
            valueColor = if (stageName.isNullOrBlank()) ScaleColors.TEXT_SECONDARY_45 else accent,
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun MetaCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = ScaleColors.TEXT_SECONDARY_45,
            ),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = valueColor,
            ),
        )
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
