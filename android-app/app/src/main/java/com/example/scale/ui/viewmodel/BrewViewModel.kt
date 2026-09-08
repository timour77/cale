package com.example.scale.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scale.brew.BrewInput
import com.example.scale.brew.BrewSession
import com.example.scale.brew.BrewState
import com.example.scale.recipe.RecipeStore
import com.example.scale.scale.LinkState
import com.example.scale.scale.ScaleCommand
import com.example.scale.scale.ScaleEvent
import com.example.scale.scale.ScaleLink
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.theme.ScaleColors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Holds the brew and the things it talks to.
 *
 * Owns a [ScaleLink] and a [RecipeStore] rather than being handed lambdas by an Activity, so the
 * connection and the brew both outlive a rotation. The Activity above it does permissions and
 * `setContent`, nothing else.
 */
class BrewViewModel(
    private val link: ScaleLink,
    private val recipeStore: RecipeStore,
) : ViewModel() {

    /** The brew, in one value: weight, time, flow, stages, chart trace. */
    val brew = mutableStateOf(BrewState())

    /** Connection state, straight from the link. */
    val linkState: StateFlow<LinkState> = link.state

    val battery = mutableStateOf<Int?>(null)

    val recipes = mutableStateOf<List<Recipe>>(emptyList())
    val currentRecipeIndex = mutableStateOf(0)

    // Purely presentational.
    val pickerSheetOpen = mutableStateOf(false)
    val settingsSheetOpen = mutableStateOf(false)
    val recipeEditorOpen = mutableStateOf<RecipeEditorTarget?>(null)
    val calSpanDialogOpen = mutableStateOf(false)
    val showChart = mutableStateOf(true)
    val showStages = mutableStateOf(true)
    val units = mutableStateOf("g")
    val accentColor = mutableStateOf(ScaleColors.ACCENT_MINT)

    private val _messages = MutableSharedFlow<String>(
        // replay = 1 so a message emitted during construction — the corrupt-recipes warning, in
        // particular — still reaches the screen, which subscribes strictly afterwards.
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Things worth telling the user in passing. Collected by the screen as a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        val loaded = recipeStore.load()
        recipes.value = loaded.recipes
        if (loaded.recoveredFromCorruption) {
            _messages.tryEmit("Saved recipes could not be read — restored the defaults")
        }
        loadStagesIntoBrew()

        viewModelScope.launch {
            link.events.collect(::onScaleEvent)
        }
        viewModelScope.launch {
            link.state.collect { state ->
                if (state !is LinkState.Ready) dispatch(BrewInput.Disconnected)
                if (state !is LinkState.Ready) battery.value = null
            }
        }
    }

    private fun onScaleEvent(event: ScaleEvent) {
        when (event) {
            is ScaleEvent.Weight ->
                dispatch(BrewInput.Sample(event.grams, System.currentTimeMillis()))
            is ScaleEvent.Battery -> battery.value = event.percent
            is ScaleEvent.Calibration ->
                _messages.tryEmit("Zero ${event.zero}, factor ${event.factor}")
        }
    }

    /**
     * Advance the brew by one input and send whatever the rules say should be sent.
     *
     * The single path by which the brew changes: [BrewSession] decides, this applies.
     */
    fun dispatch(input: BrewInput) {
        val outcome = BrewSession.reduce(brew.value, input)
        brew.value = outcome.state
        outcome.commands.forEach(link::send)
    }

    // ------------------------------------------------------------------ intents

    fun toggleConnection() {
        if (link.state.value is LinkState.Ready) link.disconnect() else link.connect()
    }

    fun tareNow() {
        link.send(ScaleCommand.Tare)
    }

    fun calibrateZero() {
        link.send(ScaleCommand.CalZero)
        _messages.tryEmit("Zero calibration sent")
    }

    fun calibrateSpan(grams: Float) {
        link.send(ScaleCommand.CalSpan(grams))
        _messages.tryEmit("Span calibration sent")
    }

    fun requestCalibration() {
        link.send(ScaleCommand.CalGet)
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

    // ------------------------------------------------------------------ recipes

    fun selectRecipe(index: Int) {
        if (index !in recipes.value.indices) return
        currentRecipeIndex.value = index
        loadStagesIntoBrew()
    }

    fun saveRecipe(index: Int?, recipe: Recipe) {
        val updated = recipes.value.toMutableList()
        if (index != null && index in updated.indices) {
            updated[index] = recipe
        } else {
            updated.add(recipe)
            currentRecipeIndex.value = updated.lastIndex
        }
        recipes.value = updated
        recipeStore.save(updated)
        loadStagesIntoBrew()
    }

    fun deleteRecipe(index: Int) {
        val updated = recipes.value.toMutableList()
        if (index !in updated.indices) return
        updated.removeAt(index)
        recipes.value = updated
        currentRecipeIndex.value =
            currentRecipeIndex.value.coerceAtMost(updated.lastIndex.coerceAtLeast(0))
        recipeStore.save(updated)
        loadStagesIntoBrew()
    }

    /** Push the selected recipe's stages into the brew, which resets stage progress. */
    private fun loadStagesIntoBrew() {
        val stages = recipes.value.getOrNull(currentRecipeIndex.value)?.stages.orEmpty()
        dispatch(BrewInput.SelectStages(stages))
    }

    // ------------------------------------------------------------------- chrome

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
        recipeEditorOpen.value = RecipeEditorTarget.Existing(currentRecipeIndex.value)
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

    override fun onCleared() {
        super.onCleared()
        link.close()
    }

    sealed class RecipeEditorTarget {
        data object New : RecipeEditorTarget()
        data class Existing(val index: Int) : RecipeEditorTarget()
    }

    /** Builds the ViewModel with its collaborators. */
    class Factory(
        private val link: ScaleLink,
        private val recipeStore: RecipeStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BrewViewModel(link, recipeStore) as T
    }
}
