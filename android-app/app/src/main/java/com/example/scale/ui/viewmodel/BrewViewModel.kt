package com.example.scale.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.theme.ScaleColors

class BrewViewModel : ViewModel() {

    // Observable state from BLE/Activity layer
    val weight = MutableLiveData(0f)
    val elapsedSeconds = MutableLiveData(0f)
    val flowRate = MutableLiveData(0f)
    val stageIndex = MutableLiveData(0)
    val connected = MutableLiveData(false)
    val connectionStatus = MutableLiveData("Disconnected")
    val battery = MutableLiveData<Int?>(null)
    val timerRunning = MutableLiveData(false)
    val recipes = MutableLiveData<List<Recipe>>(emptyList())
    val currentRecipeIndex = MutableLiveData(0)

    // Compose-driven UI state
    val pickerSheetOpen = mutableStateOf(false)
    val settingsSheetOpen = mutableStateOf(false)
    val recipeEditorOpen = mutableStateOf<RecipeEditorTarget?>(null)
    val calSpanDialogOpen = mutableStateOf(false)
    val showChart = mutableStateOf(true)
    val showStages = mutableStateOf(true)
    val recipeModeEnabled = mutableStateOf(false)
    val autoStageMode = mutableStateOf(true)
    val units = mutableStateOf("g")
    val accentColor = mutableStateOf(ScaleColors.ACCENT_MINT)

    // Rolling chart data. Pairs of (elapsed seconds since chart start, weight).
    val chartPoints = mutableStateListOf<Pair<Float, Float>>()

    // Action callbacks wired by MainActivity. Defaults are no-ops so previews work.
    var onConnectToggle: () -> Unit = {}
    var onTare: () -> Unit = {}
    var onToggleTimer: () -> Unit = {}
    var onCalZero: () -> Unit = {}
    var onCalSpan: (Float) -> Unit = {}
    var onCalGet: () -> Unit = {}
    var onSelectRecipe: (Int) -> Unit = {}
    var onSaveRecipe: (Int?, Recipe) -> Unit = { _, _ -> }
    var onDeleteRecipe: (Int) -> Unit = {}
    var onAdvanceStage: () -> Unit = {}
    var onResetRecipe: () -> Unit = {}

    fun tareNow() {
        onTare()
    }

    fun openRecipePicker() {
        pickerSheetOpen.value = true
    }

    fun closeRecipePicker() {
        pickerSheetOpen.value = false
    }

    fun openSettings() {
        settingsSheetOpen.value = true
    }

    fun closeSettings() {
        settingsSheetOpen.value = false
    }

    fun openCalSpanDialog() {
        calSpanDialogOpen.value = true
    }

    fun closeCalSpanDialog() {
        calSpanDialogOpen.value = false
    }

    fun startNewRecipe() {
        recipeEditorOpen.value = RecipeEditorTarget.New
    }

    fun editCurrentRecipe() {
        val idx = currentRecipeIndex.value ?: return
        recipeEditorOpen.value = RecipeEditorTarget.Existing(idx)
    }

    fun closeRecipeEditor() {
        recipeEditorOpen.value = null
    }

    fun toggleChart() {
        showChart.value = !showChart.value
    }

    fun toggleStages() {
        showStages.value = !showStages.value
    }

    fun toggleRecipeMode() {
        recipeModeEnabled.value = !recipeModeEnabled.value
    }

    fun setUnits(unit: String) {
        units.value = unit
    }

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun pushChartPoint(secondsSinceStart: Float, weightValue: Float) {
        chartPoints.add(secondsSinceStart to weightValue)
        // Keep memory bounded; chart only ever shows recent samples.
        if (chartPoints.size > 600) {
            chartPoints.removeAt(0)
        }
    }

    fun clearChart() {
        chartPoints.clear()
    }

    sealed class RecipeEditorTarget {
        data object New : RecipeEditorTarget()
        data class Existing(val index: Int) : RecipeEditorTarget()
    }
}
