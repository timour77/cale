package com.example.scale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun DisconnectedView(
    statusText: String,
    accent: Color,
    onConnect: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(ScaleColors.DISABLED_DOT, CircleShape),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = statusText.uppercase(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = ScaleColors.TEXT_SECONDARY_55,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pair to your scale",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Light,
                    fontSize = 28.sp,
                    color = ScaleColors.TEXT_PRIMARY,
                ),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "CONNECT",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.5.sp,
                    color = accent,
                ),
                modifier = Modifier
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onConnect)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
