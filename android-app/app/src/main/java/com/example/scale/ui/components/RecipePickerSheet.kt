package com.example.scale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.theme.ScaleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerSheet(
    recipes: List<Recipe>,
    currentIndex: Int,
    accent: Color,
    recipeModeEnabled: Boolean,
    autoStage: Boolean,
    canControl: Boolean,
    onSelect: (Int) -> Unit,
    onAddNew: () -> Unit,
    onEditCurrent: () -> Unit,
    onToggleRecipeMode: () -> Unit,
    onToggleAutoStage: () -> Unit,
    onAdvanceStage: () -> Unit,
    onResetRecipe: () -> Unit,
    onDismiss: () -> Unit,
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
            SectionLabel(text = "RECIPES", accent = accent)

            Spacer(Modifier.height(12.dp))

            ToggleRow(
                label = "Recipe mode",
                value = recipeModeEnabled,
                accent = accent,
                onChange = { onToggleRecipeMode() },
            )
            ToggleRow(
                label = "Auto-advance",
                value = autoStage,
                accent = accent,
                onChange = { onToggleAutoStage() },
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(recipes) { recipe ->
                    val idx = recipes.indexOf(recipe)
                    val isSelected = idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(idx) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isSelected) accent else ScaleColors.DISABLED_DOT,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.title,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = ScaleColors.TEXT_PRIMARY,
                                ),
                            )
                            Text(
                                text = "${recipe.stages.size} stages · target ${recipe.stages.lastOrNull()?.targetWeight?.toInt() ?: 0}g",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = ScaleColors.TEXT_SECONDARY_45,
                                    letterSpacing = 1.sp,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetActionButton(label = "+ New", accent = accent, onClick = onAddNew, modifier = Modifier.weight(1f))
                SheetActionButton(
                    label = "Edit",
                    accent = accent,
                    onClick = onEditCurrent,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetActionButton(
                    label = "Next stage",
                    accent = accent,
                    enabled = canControl && recipeModeEnabled && !autoStage,
                    onClick = onAdvanceStage,
                    modifier = Modifier.weight(1f),
                )
                SheetActionButton(
                    label = "Reset",
                    accent = accent,
                    enabled = canControl && recipeModeEnabled,
                    onClick = onResetRecipe,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun SectionLabel(text: String, accent: Color) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = accent,
        ),
    )
}

@Composable
internal fun ToggleRow(label: String, value: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 14.sp,
                color = ScaleColors.TEXT_PRIMARY,
            ),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.45f),
                uncheckedThumbColor = ScaleColors.TEXT_SECONDARY_45,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
            ),
        )
    }
}

@Composable
internal fun SheetActionButton(
    label: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (enabled) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = if (enabled) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
            ),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = if (enabled) accent else ScaleColors.TEXT_SECONDARY_45,
            ),
        )
    }
}
