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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.model.Stage
import com.example.scale.ui.theme.ScaleColors

private data class StageDraft(
    val name: String = "",
    val startSec: String = "0",
    val endSec: String = "30",
    val target: String = "50",
)

private fun StageDraft.toStage(): Stage? {
    val n = name.trim()
    val start = startSec.trim().toIntOrNull()
    val end = endSec.trim().toIntOrNull()
    val tg = target.trim().replace(',', '.').toFloatOrNull()
    if (n.isEmpty() || start == null || end == null || tg == null) return null
    if (end < start) return null
    return Stage(name = n, startSec = start, endSec = end, targetWeight = tg, note = "")
}

@Composable
fun RecipeEditorDialog(
    initial: Recipe?,
    isEdit: Boolean,
    accent: Color,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (Recipe) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    val drafts = remember {
        mutableStateListOf<StageDraft>().apply {
            if (initial != null) {
                addAll(
                    initial.stages.map {
                        StageDraft(
                            name = it.name,
                            startSec = it.startSec.toString(),
                            endSec = it.endSec.toString(),
                            target = if (it.targetWeight % 1f == 0f) it.targetWeight.toInt().toString() else it.targetWeight.toString(),
                        )
                    },
                )
            } else {
                add(StageDraft(name = "Step 1"))
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScaleColors.BG_SURFACE,
        title = {
            Text(
                text = if (isEdit) "Edit recipe" else "New recipe",
                color = ScaleColors.TEXT_PRIMARY,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Recipe name", color = ScaleColors.TEXT_SECONDARY_55) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Spacer(Modifier.padding(top = 8.dp))

                drafts.forEachIndexed { i, draft ->
                    StageDraftRow(
                        index = i,
                        draft = draft,
                        accent = accent,
                        onChange = { drafts[i] = it },
                        onRemove = { drafts.removeAt(i) },
                        canRemove = drafts.size > 1,
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = "+ ADD STEP",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = accent,
                    ),
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable {
                            val last = drafts.lastOrNull()
                            val prevEnd = last?.endSec?.toIntOrNull() ?: 0
                            val prevTg = last?.target?.replace(',', '.')?.toFloatOrNull() ?: 0f
                            drafts.add(
                                StageDraft(
                                    name = "Step ${drafts.size + 1}",
                                    startSec = prevEnd.toString(),
                                    endSec = (prevEnd + 30).toString(),
                                    target = (prevTg + 50f).let {
                                        if (it % 1f == 0f) it.toInt().toString() else it.toString()
                                    },
                                ),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                error?.let {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(it, color = ScaleColors.ACCENT_AMBER)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = title.trim()
                if (name.isEmpty()) {
                    error = "Recipe name is required"
                    return@TextButton
                }
                if (drafts.isEmpty()) {
                    error = "At least one step is required"
                    return@TextButton
                }
                val stages = drafts.map { it.toStage() }
                if (stages.any { it == null }) {
                    error = "Check step fields (end ≥ start, numbers required)"
                    return@TextButton
                }
                onSave(Recipe(title = name, stages = stages.filterNotNull()))
            }) { Text("Save", color = accent) }
        },
        dismissButton = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = ScaleColors.ACCENT_AMBER)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = ScaleColors.TEXT_SECONDARY_55)
                }
            }
        },
    )
}

@Composable
private fun StageDraftRow(
    index: Int,
    draft: StageDraft,
    accent: Color,
    canRemove: Boolean,
    onChange: (StageDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "STEP ${index + 1}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        color = accent,
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.padding(end = 8.dp))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove step",
                            tint = ScaleColors.TEXT_SECONDARY_55,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name", color = ScaleColors.TEXT_SECONDARY_55) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = draft.startSec,
                    onValueChange = { onChange(draft.copy(startSec = it.filter { c -> c.isDigit() })) },
                    label = { Text("Start", color = ScaleColors.TEXT_SECONDARY_55) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors(),
                )
                OutlinedTextField(
                    value = draft.endSec,
                    onValueChange = { onChange(draft.copy(endSec = it.filter { c -> c.isDigit() })) },
                    label = { Text("End", color = ScaleColors.TEXT_SECONDARY_55) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors(),
                )
                OutlinedTextField(
                    value = draft.target,
                    onValueChange = { onChange(draft.copy(target = it.filter { c -> c.isDigit() || c == '.' || c == ',' })) },
                    label = { Text("Target g", color = ScaleColors.TEXT_SECONDARY_55) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f),
                    colors = textFieldColors(),
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = ScaleColors.TEXT_PRIMARY,
    unfocusedTextColor = ScaleColors.TEXT_PRIMARY,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = ScaleColors.TEXT_PRIMARY,
    focusedIndicatorColor = ScaleColors.TEXT_SECONDARY_45,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.12f),
)
