package com.example.scale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scale.ui.model.Stage
import com.example.scale.ui.theme.ScaleColors

@Composable
fun StageStrip(
    stages: List<Stage>,
    currentStageIndex: Int,
    weight: Float,
    units: String,
    accent: Color,
) {
    if (stages.isEmpty()) return

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "STAGES",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ScaleColors.TEXT_SECONDARY_45,
                letterSpacing = 2.sp,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stages.forEachIndexed { i, stage ->
                val isActive = i == currentStageIndex
                val isPast = i < currentStageIndex
                val prevTarget = if (i > 0) stages[i - 1].targetWeight else 0f
                val span = (stage.targetWeight - prevTarget).coerceAtLeast(0.001f)
                val fill = when {
                    isPast -> 1f
                    isActive -> ((weight - prevTarget) / span).coerceIn(0f, 1f)
                    else -> 0f
                }

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = fill)
                                .background(
                                    color = when {
                                        isActive -> accent
                                        isPast -> Color.White.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stage.name,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) ScaleColors.TEXT_PRIMARY else ScaleColors.TEXT_SECONDARY_45,
                        ),
                    )
                    Text(
                        text = "${stage.targetWeight.toInt()}$units",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (isActive) accent else ScaleColors.TEXT_SECONDARY_35,
                        ),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}
