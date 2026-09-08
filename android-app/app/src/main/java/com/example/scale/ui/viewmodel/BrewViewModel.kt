package com.example.scale.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.scale.brew.BrewInput
import com.example.scale.brew.BrewSession
import com.example.scale.brew.BrewState
import com.example.scale.scale.ScaleCommand
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.theme.ScaleColors

class BrewViewModel : ViewModel() {

    /**
     * The brew, in one value. Everything about weight, time, flow, stages and the chart trace
     * lives here rather than as loose fields, and survives configuration change along with it.
     */
    val brew = mutableStateOf(BrewState())

    // Connection state, owned by whatever is holding the radio.
    val connected = MutableLiveData(false)
    val connectionStatus = MutableLiveData("Disconnected")
    val battery = MutableLiveData<Int?>(null)

    // Recipe library. Storage still belongs to MainActivity for now.
    val recipes = MutableLiveData<List<Recipe>>(emptyList())
    val currentRecipeIndex = MutableLiveData(0)

    // Purely presentational: which sheet is open, what it looks like.
    val pickerSheetOpen = mutableStateOf(false)
    val settingsSheetOpen = mutableStateOf(false)
    val recipeEditorOpen = mutableStateOf<RecipeEditorTarget?>(null)
    val calSpanDialogOpen = mutableStateOf(false)
    val showChart = mutableStateOf(true)
    val showStages = mutableStateOf(true)
    val units = mutableStateOf("g")
    val accentColor = mutableStateOf(ScaleColors.ACCENT_MINT)

    /** Sends a command to the scale. Wired by MainActivity, which owns the connection. */
    var onSendCommands: (List<ScaleCommand>) -> Unit = {}

    // Action callbacks wired by MainActivity. Defaults are no-ops so previews work.
    var onConnectToggle: () -> Unit = {}
    var onTare: () -> Unit = {}
    var onCalZero: () -> Unit = {}
    var onCalSpan: (Float) -> Unit = {}
    var onCalGet: () -> Unit = {}
    var onSelectRecipe: (Int) -> Unit = {}
    var onSaveRecipe: (Int?, Recipe) -> Unit = { _, _ -> }
    var onDeleteRecipe: (Int) -> Unit = {}

    /**
     * Advance the brew by one input, and send whatever the rules say should be sent.
     *
     * The single path by which the brew changes: [BrewSession] decides, this applies.
     */
    fun dispatch(input: BrewInput) {
        val outcome = BrewSession.reduce(brew.value, input)
        brew.value = outcome.state
        if (outcome.commands.isNotEmpty()) onSendCommands(outcome.commands)
    }

    fun tareNow() {
        onTare()
    }

    fun toggleTimer() {
        dispatch(BrewInput.ToggleTimer(System.currentTimeMillis()))
    }

    fun advanceStage() {
        dispatch(BrewInput.AdvanceStage(System.currentTimeMillis()))
    }

    fun resetBrew() {
        dispatch(BrewInput.ResetBrew)
    }

    fun toggleRecipeMode() {
        dispatch(BrewInput.SetRecipeMode(!brew.value.recipeMode))
    }

    fun toggleAutoStage() {
        dispatch(BrewInput.SetAutoStage(!brew.value.autoStage))
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

    fun setUnits(unit: String) {
        units.value = unit
    }

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    sealed class RecipeEditorTarget {
        data object New : RecipeEditorTarget()
        data class Existing(val index: Int) : RecipeEditorTarget()
    }
}
