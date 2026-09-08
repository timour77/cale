package com.example.scale.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.scale.ui.components.CalSpanDialog
import com.example.scale.ui.components.ControlBar
import com.example.scale.ui.components.DisconnectedView
import com.example.scale.ui.components.FlowChart
import com.example.scale.ui.components.HeroWeight
import com.example.scale.ui.components.MetaStrip
import com.example.scale.ui.components.RecipeCard
import com.example.scale.ui.components.RecipeEditorDialog
import com.example.scale.ui.components.RecipePickerSheet
import com.example.scale.ui.components.SettingsSheet
import com.example.scale.ui.components.StageStrip
import com.example.scale.ui.components.TopBar
import com.example.scale.ui.theme.ScaleColors
import com.example.scale.ui.theme.ScaleTheme
import com.example.scale.ui.viewmodel.BrewViewModel

@Composable
fun BrewScreen(viewModel: BrewViewModel) {
    val connected by viewModel.connected.observeAsState(false)
    val statusText by viewModel.connectionStatus.observeAsState("Disconnected")
    val battery by viewModel.battery.observeAsState(null)
    val recipes by viewModel.recipes.observeAsState(emptyList())
    val recipeIndex by viewModel.currentRecipeIndex.observeAsState(0)

    val brew = viewModel.brew.value
    val accent = viewModel.accentColor.value
    val units = viewModel.units.value
    val recipeMode = brew.recipeMode

    val currentRecipe = recipes.getOrNull(recipeIndex)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ScaleColors.BG_PRIMARY,
    ) {
        if (!connected) {
            DisconnectedView(
                statusText = statusText,
                accent = accent,
                onConnect = { viewModel.onConnectToggle() },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                TopBar(
                    connected = connected,
                    statusText = statusText,
                    battery = battery,
                    accent = accent,
                    onSettingsClick = { viewModel.openSettings() },
                )

                RecipeCard(
                    recipeTitle = currentRecipe?.title,
                    stageCount = currentRecipe?.stages?.size ?: 0,
                    enabled = recipeMode,
                    accent = accent,
                    onClick = { viewModel.openRecipePicker() },
                )

                HeroWeight(
                    weight = brew.weightGrams,
                    units = units,
                    accent = accent,
                )

                MetaStrip(
                    elapsedSeconds = brew.elapsedSeconds,
                    flow = brew.flowRate,
                    stageName = if (recipeMode) brew.activeStage?.name else null,
                    units = units,
                    accent = accent,
                    secondary = ScaleColors.ACCENT_SECONDARY,
                )

                if (viewModel.showChart.value) {
                    FlowChart(
                        points = brew.chart,
                        accent = accent,
                    )
                }

                if (viewModel.showStages.value && recipeMode) {
                    StageStrip(
                        progress = brew.stageProgress,
                        units = units,
                        accent = accent,
                    )
                }

                ControlBar(
                    enabled = connected,
                    timerRunning = brew.timerRunning,
                    accent = accent,
                    onTare = { viewModel.tareNow() },
                    onStartStop = { viewModel.toggleTimer() },
                    onRecipes = { viewModel.openRecipePicker() },
                )
            }
        }
    }

    if (viewModel.pickerSheetOpen.value) {
        RecipePickerSheet(
            recipes = recipes,
            currentIndex = recipeIndex,
            accent = accent,
            recipeModeEnabled = recipeMode,
            autoStage = brew.autoStage,
            canControl = connected,
            onSelect = { viewModel.onSelectRecipe(it) },
            onAddNew = { viewModel.startNewRecipe() },
            onEditCurrent = { viewModel.editCurrentRecipe() },
            onToggleRecipeMode = { viewModel.toggleRecipeMode() },
            onToggleAutoStage = { viewModel.toggleAutoStage() },
            onAdvanceStage = { viewModel.advanceStage() },
            onResetRecipe = { viewModel.resetBrew() },
            onDismiss = { viewModel.closeRecipePicker() },
        )
    }

    if (viewModel.settingsSheetOpen.value) {
        SettingsSheet(
            connected = connected,
            statusText = statusText,
            accent = accent,
            accentPalette = ScaleColors.ACCENT_PALETTE,
            units = units,
            showChart = viewModel.showChart.value,
            showStages = viewModel.showStages.value,
            onDismiss = { viewModel.closeSettings() },
            onConnectToggle = { viewModel.onConnectToggle() },
            onCalZero = { viewModel.onCalZero() },
            onCalSpan = { viewModel.openCalSpanDialog() },
            onCalGet = { viewModel.onCalGet() },
            onUnitsChange = { viewModel.setUnits(it) },
            onAccentChange = { viewModel.setAccentColor(it) },
            onToggleChart = { viewModel.toggleChart() },
            onToggleStages = { viewModel.toggleStages() },
        )
    }

    if (viewModel.calSpanDialogOpen.value) {
        CalSpanDialog(
            accent = accent,
            onDismiss = { viewModel.closeCalSpanDialog() },
            onConfirm = { grams ->
                viewModel.onCalSpan(grams)
                viewModel.closeCalSpanDialog()
            },
        )
    }

    val editorTarget = viewModel.recipeEditorOpen.value
    if (editorTarget != null) {
        val isEdit = editorTarget is BrewViewModel.RecipeEditorTarget.Existing
        val initial = (editorTarget as? BrewViewModel.RecipeEditorTarget.Existing)?.let { recipes.getOrNull(it.index) }
        RecipeEditorDialog(
            initial = initial,
            isEdit = isEdit,
            accent = accent,
            onDelete = if (isEdit) {
                {
                    val idx = (editorTarget as BrewViewModel.RecipeEditorTarget.Existing).index
                    viewModel.onDeleteRecipe(idx)
                    viewModel.closeRecipeEditor()
                }
            } else null,
            onDismiss = { viewModel.closeRecipeEditor() },
            onSave = { recipe ->
                val targetIdx = (editorTarget as? BrewViewModel.RecipeEditorTarget.Existing)?.index
                viewModel.onSaveRecipe(targetIdx, recipe)
                viewModel.closeRecipeEditor()
            },
        )
    }
}

@Preview
@Composable
private fun BrewScreenPreview() {
    ScaleTheme {
        BrewScreen(BrewViewModel())
    }
}
