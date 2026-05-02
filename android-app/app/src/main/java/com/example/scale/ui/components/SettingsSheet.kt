package com.example.scale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    connected: Boolean,
    statusText: String,
    accent: Color,
    accentPalette: List<Color>,
    units: String,
    showChart: Boolean,
    showStages: Boolean,
    onDismiss: () -> Unit,
    onConnectToggle: () -> Unit,
    onCalZero: () -> Unit,
    onCalSpan: () -> Unit,
    onCalGet: () -> Unit,
    onUnitsChange: (String) -> Unit,
    onAccentChange: (Color) -> Unit,
    onToggleChart: () -> Unit,
    onToggleStages: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ScaleColors.BG_SURFACE,
        contentColor = ScaleColors.TEXT_PRIMARY,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionLabel(text = "DEVICE", accent = accent)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (connected) accent else ScaleColors.DISABLED_DOT,
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ScaleColors.TEXT_SECONDARY_55,
                        letterSpacing = 1.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                SheetActionButton(
                    label = if (connected) "Disconnect" else "Connect",
                    accent = accent,
                    onClick = onConnectToggle,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "CALIBRATION", accent = accent)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetActionButton(
                    label = "Zero",
                    accent = accent,
                    enabled = connected,
                    onClick = onCalZero,
                    modifier = Modifier.weight(1f),
                )
                SheetActionButton(
                    label = "Span",
                    accent = accent,
                    enabled = connected,
                    onClick = onCalSpan,
                    modifier = Modifier.weight(1f),
                )
                SheetActionButton(
                    label = "Read",
                    accent = accent,
                    enabled = connected,
                    onClick = onCalGet,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "DISPLAY", accent = accent)
            Spacer(Modifier.height(12.dp))
            ToggleRow(label = "Show flow chart", value = showChart, accent = accent, onChange = { onToggleChart() })
            ToggleRow(label = "Show stage strip", value = showStages, accent = accent, onChange = { onToggleStages() })

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "UNITS", accent = accent)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("g", "oz").forEach { u ->
                    val selected = units == u
                    Text(
                        text = u,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            color = if (selected) accent else ScaleColors.TEXT_SECONDARY_45,
                        ),
                        modifier = Modifier
                            .background(
                                color = if (selected) accent.copy(alpha = 0.10f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onUnitsChange(u) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "ACCENT", accent = accent)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accentPalette.forEach { color ->
                    val selected = color.value == accent.value
                    Box(
                        modifier = Modifier
                            .size(if (selected) 32.dp else 26.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                                shape = CircleShape,
                            )
                            .clickable { onAccentChange(color) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
