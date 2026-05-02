package com.example.scale.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

@Composable
fun HeroWeight(
    weight: Float,
    units: String,
    accent: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "● LIVE WEIGHT",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = accent,
                letterSpacing = 2.sp,
            ),
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.1f".format(weight),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 88.sp,
                    letterSpacing = (-2).sp,
                    color = ScaleColors.TEXT_PRIMARY,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = units,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    color = ScaleColors.TEXT_SECONDARY_45,
                ),
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}
