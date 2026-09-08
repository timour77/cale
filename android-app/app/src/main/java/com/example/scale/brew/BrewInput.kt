package com.example.scale.brew

import com.example.scale.ui.model.Stage

/**
 * Something that happens to a brew.
 *
 * Anything time-dependent carries the instant it happened, so the session never reads a clock and
 * a test can say exactly when things occurred.
 */
sealed interface BrewInput {

    /** A weight reading from the scale. The engine of everything else. */
    data class Sample(val grams: Float, val atMs: Long) : BrewInput

    data class StartTimer(val atMs: Long) : BrewInput

    data object StopTimer : BrewInput

    data class ToggleTimer(val atMs: Long) : BrewInput

    /** Move to the next stage by hand, starting the brew if it has not begun. */
    data class AdvanceStage(val atMs: Long) : BrewInput

    /** Load a different recipe's stages, which resets the brew. */
    data class SelectStages(val stages: List<Stage>) : BrewInput

    /** Return to the first stage, stopping the brew, without unloading the recipe. */
    data object ResetBrew : BrewInput

    /** The scale went away. Clears the brew but keeps the recipe and mode settings. */
    data object Disconnected : BrewInput

    data class SetRecipeMode(val enabled: Boolean) : BrewInput

    data class SetAutoStage(val enabled: Boolean) : BrewInput
}
