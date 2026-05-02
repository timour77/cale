package com.example.scale.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scale.ui.theme.ScaleColors

@Composable
fun CalSpanDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScaleColors.BG_SURFACE,
        title = { Text("Calibration Span", color = ScaleColors.TEXT_PRIMARY) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "Place a known weight on the scale, then enter its mass in grams.",
                    color = ScaleColors.TEXT_SECONDARY_55,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("grams", color = ScaleColors.TEXT_SECONDARY_55) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val grams = text.replace(',', '.').toFloatOrNull()
                    if (grams != null && grams > 0f) {
                        onConfirm(grams)
                    }
                },
            ) { Text("Send", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ScaleColors.TEXT_SECONDARY_55) }
        },
    )
}
