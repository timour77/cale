package com.example.scale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun RecipeCard(
    recipeTitle: String?,
    stageCount: Int,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val borderColor = if (enabled) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(ScaleColors.BG_SURFACE, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (enabled) "RECIPE" else "RECIPE · OFF",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    color = if (enabled) accent else ScaleColors.TEXT_SECONDARY_45,
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = recipeTitle?.takeIf { it.isNotBlank() } ?: "No recipe",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ScaleColors.TEXT_PRIMARY,
                ),
            )
        }
        Text(
            text = if (stageCount > 0) "$stageCount stages ›" else "tap ›",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ScaleColors.TEXT_SECONDARY_45,
                letterSpacing = 1.sp,
            ),
        )
    }
}
