package com.example.scale.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.scale.recipe.InMemoryRecipeStore
import com.example.scale.scale.FakeScaleLink
import com.example.scale.scale.LinkState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** What a [LinkState] is called on screen. The link itself has no opinion about words. */
private fun LinkState.label(): String = when (this) {
    LinkState.Idle -> "Disconnected"
    LinkState.Scanning -> "Scanning…"
    LinkState.Connecting -> "Connecting…"
    LinkState.Ready -> "Connected"
    is LinkState.Failed -> when (val r = reason) {
        LinkState.Reason.BluetoothUnavailable -> "Bluetooth off"
        LinkState.Reason.NotFound -> "Scale not found"
        is LinkState.Reason.ScanFailed -> "Scan failed (${r.errorCode})"
        LinkState.Reason.ServiceMissing -> "Unrecognised device"
        LinkState.Reason.ConnectionLost -> "Connection lost"
    }
}

@Composable
fun BrewScreen(
    viewModel: BrewViewModel,
    onConnectToggle: () -> Unit,
) {
    val linkState by viewModel.linkState.collectAsState()
    val connected = linkState is LinkState.Ready
    val statusText = linkState.label()

    val brew = viewModel.brew.value
    val battery = viewModel.battery.value
    val recipes = viewModel.recipes.value
    val recipeIndex = viewModel.currentRecipeIndex.value
    val accent = viewModel.accentColor.value
    val units = viewModel.units.value
    val recipeMode = brew.recipeMode

    val currentRecipe = recipes.getOrNull(recipeIndex)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ScaleColors.BG_PRIMARY,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!connected) {
                DisconnectedView(
                    statusText = statusText,
                    accent = accent,
                    onConnect = onConnectToggle,
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
            onSelect = { viewModel.selectRecipe(it) },
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
            onConnectToggle = onConnectToggle,
            onCalZero = { viewModel.calibrateZero() },
            onCalSpan = { viewModel.openCalSpanDialog() },
            onCalGet = { viewModel.requestCalibration() },
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
                viewModel.calibrateSpan(grams)
                viewModel.closeCalSpanDialog()
            },
        )
    }

    val editorTarget = viewModel.recipeEditorOpen.value
    if (editorTarget != null) {
        val isEdit = editorTarget is BrewViewModel.RecipeEditorTarget.Existing
        val initial = (editorTarget as? BrewViewModel.RecipeEditorTarget.Existing)
            ?.let { recipes.getOrNull(it.index) }
        RecipeEditorDialog(
            initial = initial,
            isEdit = isEdit,
            accent = accent,
            onDelete = if (isEdit) {
                {
                    viewModel.deleteRecipe(
                        (editorTarget as BrewViewModel.RecipeEditorTarget.Existing).index,
                    )
                    viewModel.closeRecipeEditor()
                }
            } else null,
            onDismiss = { viewModel.closeRecipeEditor() },
            onSave = { recipe ->
                val targetIdx = (editorTarget as? BrewViewModel.RecipeEditorTarget.Existing)?.index
                viewModel.saveRecipe(targetIdx, recipe)
                viewModel.closeRecipeEditor()
            },
        )
    }
}

@Preview
@Composable
private fun BrewScreenPreview() {
    // The fake link is what lets this preview show a connected scale at all; a real link could
    // only ever render the disconnected screen here.
    val link = remember {
        FakeScaleLink(CoroutineScope(Dispatchers.Main)).also { it.forceState(LinkState.Ready) }
    }
    val viewModel = remember { BrewViewModel(link, InMemoryRecipeStore()) }
    ScaleTheme {
        BrewScreen(viewModel = viewModel, onConnectToggle = {})
    }
}
