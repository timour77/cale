package com.example.scale.brew

import com.example.scale.scale.ScaleCommand
import com.example.scale.ui.model.Stage

/**
 * The brew rules, as a pure function.
 *
 * [reduce] takes the current [BrewState] and one [BrewInput] and returns the next state plus any
 * [ScaleCommand]s the transition implies. It reads no clock, touches no Android, and sends
 * nothing: whoever calls it decides what to do with the commands. That is what makes the rules
 * testable — the whole of `BrewSessionTest` is calls to this function.
 *
 * A single input can imply more than one command. A weight sample can start the timer *and* cross
 * a stage boundary in the same instant, which is why the result carries a list.
 */
object BrewSession {

    /** Weight at which a pour is considered to have begun. */
    const val START_THRESHOLD_G = 0.5f

    /** Weight below which the scale is considered empty again. */
    const val STOP_THRESHOLD_G = 0.2f

    /** How long the scale must stay empty before the brew is called finished. */
    const val STOP_DWELL_MS = 1_500L

    /** Weight of the newest reading in the smoothed flow rate; the rest is history. */
    const val FLOW_SMOOTHING = 0.3f

    /**
     * Shortest interval used for a flow-rate calculation. Two samples in the same millisecond
     * would otherwise divide by zero.
     */
    const val MIN_FLOW_INTERVAL_MS = 50L

    /** Chart length cap, so a long brew cannot grow without bound. */
    const val MAX_CHART_POINTS = 600

    data class Outcome(
        val state: BrewState,
        val commands: List<ScaleCommand> = emptyList(),
    )

    fun reduce(state: BrewState, input: BrewInput): Outcome = when (input) {
        is BrewInput.Sample -> onSample(state, input.grams, input.atMs)
        is BrewInput.StartTimer -> startTimer(state, input.atMs)
        BrewInput.StopTimer -> stopTimer(state)
        is BrewInput.ToggleTimer ->
            if (state.timerRunning) stopTimer(state) else startTimer(state, input.atMs)
        is BrewInput.AdvanceStage -> advanceStage(state, input.atMs)
        is BrewInput.SelectStages -> selectStages(state, input.stages)
        BrewInput.ResetBrew -> resetBrew(state)
        BrewInput.Disconnected -> disconnected(state)
        is BrewInput.SetRecipeMode -> Outcome(state.copy(recipeMode = input.enabled))
        is BrewInput.SetAutoStage -> Outcome(state.copy(autoStage = input.enabled))
    }

    /**
     * A weight reading, and everything that follows from it: flow rate, the auto start/stop rules,
     * elapsed time, the chart trace, and time-driven stage advance — in that order, because the
     * later steps depend on whether the earlier ones started or stopped the brew.
     */
    private fun onSample(state: BrewState, grams: Float, atMs: Long): Outcome {
        val commands = mutableListOf<ScaleCommand>()

        var next = withFlowRate(state, grams, atMs).copy(weightGrams = grams)

        val timing = autoStartStop(next, grams, atMs)
        next = timing.state
        commands += timing.commands

        if (next.timerRunning) {
            next = next.copy(
                elapsedSeconds = (atMs - next.bookkeeping.timerStartedAtMs) / 1000f,
            )
            next = withChartPoint(next, grams, atMs)

            if (next.recipeMode && next.autoStage) {
                val advanced = advanceStageByTime(next, next.elapsedSeconds.toInt())
                next = advanced.state
                commands += advanced.commands
            }
        }

        return Outcome(next, commands)
    }

    /**
     * Exponentially smoothed grams per second. The first sample of a session has nothing to
     * compare against and reports zero; a falling weight reports zero rather than a negative rate,
     * since lifting the cup off is not a negative pour.
     */
    private fun withFlowRate(state: BrewState, grams: Float, atMs: Long): BrewState {
        val book = state.bookkeeping
        val previousAtMs = book.lastSampleAtMs
        if (previousAtMs == null) {
            return state.copy(
                flowRate = 0f,
                bookkeeping = book.copy(lastSampleAtMs = atMs, lastSampleGrams = grams),
            )
        }

        val intervalMs = (atMs - previousAtMs).coerceAtLeast(MIN_FLOW_INTERVAL_MS)
        val instant = ((grams - book.lastSampleGrams) / intervalMs) * 1000f
        val smoothed = (state.flowRate * (1f - FLOW_SMOOTHING)) + (instant * FLOW_SMOOTHING)

        return state.copy(
            flowRate = smoothed.coerceAtLeast(0f),
            bookkeeping = book.copy(lastSampleAtMs = atMs, lastSampleGrams = grams),
        )
    }

    /**
     * Start the brew when something is placed on the scale, and end it once the scale has been
     * empty for [STOP_DWELL_MS] — the dwell being what stops a wobble mid-pour from ending things.
     */
    private fun autoStartStop(state: BrewState, grams: Float, atMs: Long): Outcome {
        if (!state.timerRunning) {
            return if (grams >= START_THRESHOLD_G) startTimer(state, atMs) else Outcome(state)
        }

        if (grams > STOP_THRESHOLD_G) {
            return Outcome(
                state.copy(bookkeeping = state.bookkeeping.copy(belowStopThresholdSinceMs = null)),
            )
        }

        val since = state.bookkeeping.belowStopThresholdSinceMs
        if (since == null) {
            return Outcome(
                state.copy(bookkeeping = state.bookkeeping.copy(belowStopThresholdSinceMs = atMs)),
            )
        }
        return if (atMs - since > STOP_DWELL_MS) stopTimer(state) else Outcome(state)
    }

    private fun withChartPoint(state: BrewState, grams: Float, atMs: Long): BrewState {
        val seconds = (atMs - state.bookkeeping.chartStartedAtMs) / 1000f
        val points = state.chart + ChartPoint(seconds, grams)
        return state.copy(
            chart = if (points.size > MAX_CHART_POINTS) {
                points.subList(points.size - MAX_CHART_POINTS, points.size)
            } else {
                points
            },
        )
    }

    /** Beginning a brew starts the clock and the trace together, discarding the previous one. */
    private fun startTimer(state: BrewState, atMs: Long): Outcome = Outcome(
        state.copy(
            timerRunning = true,
            elapsedSeconds = 0f,
            chart = emptyList(),
            bookkeeping = state.bookkeeping.copy(
                timerStartedAtMs = atMs,
                chartStartedAtMs = atMs,
                belowStopThresholdSinceMs = null,
            ),
        ),
        listOf(ScaleCommand.Timer(running = true)),
    )

    /** Ending a brew keeps the elapsed time and the trace on screen to be read. */
    private fun stopTimer(state: BrewState): Outcome = Outcome(
        state.copy(
            timerRunning = false,
            bookkeeping = state.bookkeeping.copy(belowStopThresholdSinceMs = null),
        ),
        listOf(ScaleCommand.Timer(running = false)),
    )

    private fun advanceStage(state: BrewState, atMs: Long): Outcome {
        if (!state.recipeMode || state.stages.isEmpty()) return Outcome(state)

        val commands = mutableListOf<ScaleCommand>()
        var next = state
        if (!next.timerRunning) {
            val started = startTimer(next, atMs)
            next = started.state
            commands += started.commands
        }

        // The index is allowed to reach stages.size, which means "past the end" and clears the
        // device display. It must not go beyond that.
        if (next.stageIndex > next.stages.lastIndex) return Outcome(next, commands)

        next = next.copy(stageIndex = next.stageIndex + 1)
        commands += stageCommand(next)
        return Outcome(next, commands)
    }

    private fun advanceStageByTime(state: BrewState, elapsedSec: Int): Outcome {
        if (state.stages.isEmpty()) return Outcome(state)

        val firstUnfinished = state.stages.indexOfFirst { elapsedSec < it.endSec }
        val index = if (firstUnfinished == -1) state.stages.size else firstUnfinished
        if (index == state.stageIndex) return Outcome(state)

        val next = state.copy(stageIndex = index)
        return Outcome(next, listOf(stageCommand(next)))
    }

    private fun selectStages(state: BrewState, stages: List<Stage>): Outcome {
        val commands = mutableListOf<ScaleCommand>()
        var next = state
        if (next.timerRunning) {
            val stopped = stopTimer(next)
            next = stopped.state
            commands += stopped.commands
        }
        next = next.copy(stages = stages, stageIndex = 0)
        commands += ScaleCommand.ClearStage
        return Outcome(next, commands)
    }

    private fun resetBrew(state: BrewState): Outcome {
        val commands = mutableListOf<ScaleCommand>()
        var next = state
        if (next.timerRunning) {
            val stopped = stopTimer(next)
            next = stopped.state
            commands += stopped.commands
        }
        next = next.copy(stageIndex = 0)
        commands += ScaleCommand.ClearStage
        return Outcome(next, commands)
    }

    /**
     * Everything about the brew goes; the recipe and the mode settings stay, because they are what
     * the user chose rather than what the scale reported. No commands: there is nothing listening.
     */
    private fun disconnected(state: BrewState): Outcome = Outcome(
        BrewState(
            stages = state.stages,
            recipeMode = state.recipeMode,
            autoStage = state.autoStage,
        ),
    )

    private fun stageCommand(state: BrewState): ScaleCommand {
        val stage = state.activeStage ?: return ScaleCommand.ClearStage
        return ScaleCommand.SetStage(stage.name, stage.targetWeight.toInt())
    }
}
