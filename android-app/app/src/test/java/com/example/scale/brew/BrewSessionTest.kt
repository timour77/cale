package com.example.scale.brew

import com.example.scale.scale.ScaleCommand
import com.example.scale.ui.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brew rules, as behaviour rather than as fields.
 *
 * Every test here is a sequence of inputs at explicit timestamps followed by an assertion — no
 * clock, no Android, no scale. That is the point of the reducer: these rules used to be reachable
 * only by pouring water on a physical device.
 */
class BrewSessionTest {

    private val stages = listOf(
        Stage("Bloom", startSec = 0, endSec = 40, targetWeight = 50f, note = ""),
        Stage("Pour 1", startSec = 40, endSec = 75, targetWeight = 180f, note = ""),
        Stage("Pour 2", startSec = 75, endSec = 105, targetWeight = 320f, note = ""),
    )

    /** A session mid-brew: recipe mode on, stages loaded, timer started at t=0. */
    private fun brewing(autoStage: Boolean = true): BrewState {
        var state = BrewState(recipeMode = true, autoStage = autoStage)
        state = BrewSession.reduce(state, BrewInput.SelectStages(stages)).state
        return BrewSession.reduce(state, BrewInput.StartTimer(atMs = 0L)).state
    }

    private fun BrewState.sample(grams: Float, atMs: Long) =
        BrewSession.reduce(this, BrewInput.Sample(grams, atMs))

    // ------------------------------------------------------------- auto start

    @Test
    fun `starts the timer once weight crosses the start threshold`() {
        val outcome = BrewState().sample(grams = 0.6f, atMs = 1_000L)

        assertTrue(outcome.state.timerRunning)
        assertEquals(listOf(ScaleCommand.Timer(running = true)), outcome.commands)
    }

    @Test
    fun `does not start below the threshold`() {
        val outcome = BrewState().sample(grams = 0.4f, atMs = 1_000L)

        assertFalse(outcome.state.timerRunning)
        assertTrue(outcome.commands.isEmpty())
    }

    @Test
    fun `elapsed time is measured from the start`() {
        var state = BrewState().sample(grams = 5f, atMs = 1_000L).state
        state = state.sample(grams = 20f, atMs = 4_500L).state

        assertEquals(3.5f, state.elapsedSeconds, 0.001f)
    }

    // -------------------------------------------------------------- auto stop

    @Test
    fun `stops after a sustained period below the stop threshold`() {
        var state = brewing()
        state = state.sample(grams = 0.1f, atMs = 10_000L).state
        val outcome = state.sample(grams = 0.1f, atMs = 11_600L)

        assertFalse(outcome.state.timerRunning)
        assertEquals(listOf(ScaleCommand.Timer(running = false)), outcome.commands)
    }

    @Test
    fun `does not stop before the dwell has elapsed`() {
        var state = brewing()
        state = state.sample(grams = 0.1f, atMs = 10_000L).state
        state = state.sample(grams = 0.1f, atMs = 11_400L).state

        assertTrue(state.timerRunning)
    }

    @Test
    fun `weight rising back above the threshold resets the dwell`() {
        var state = brewing()
        state = state.sample(grams = 0.1f, atMs = 10_000L).state
        state = state.sample(grams = 5f, atMs = 11_000L).state      // back on the pan
        state = state.sample(grams = 0.1f, atMs = 11_400L).state    // dwell restarts here
        state = state.sample(grams = 0.1f, atMs = 12_500L).state    // only 1.1s in

        assertTrue(state.timerRunning)
    }

    // ------------------------------------------------------------- flow rate

    @Test
    fun `flow rate is zero for the first sample`() {
        val outcome = BrewState().sample(grams = 5f, atMs = 1_000L)

        assertEquals(0f, outcome.state.flowRate, 0.001f)
    }

    @Test
    fun `flow rate smooths towards the instantaneous rate`() {
        var state = BrewState().sample(grams = 0f, atMs = 0L).state
        state = state.sample(grams = 10f, atMs = 1_000L).state

        // 10g in 1s = 10 g/s instantaneous, smoothed as 0.7*0 + 0.3*10.
        assertEquals(3f, state.flowRate, 0.001f)
    }

    @Test
    fun `flow rate never goes negative when weight is removed`() {
        var state = BrewState().sample(grams = 100f, atMs = 0L).state
        state = state.sample(grams = 0f, atMs = 1_000L).state

        assertEquals(0f, state.flowRate, 0.001f)
    }

    @Test
    fun `samples arriving in the same millisecond do not divide by zero`() {
        var state = BrewState().sample(grams = 0f, atMs = 1_000L).state
        state = state.sample(grams = 5f, atMs = 1_000L).state

        assertTrue("flow was ${state.flowRate}", state.flowRate.isFinite())
    }

    // ----------------------------------------------------------- stage advance

    @Test
    fun `advances stage by time and announces it`() {
        val state = brewing()
        val outcome = state.sample(grams = 60f, atMs = 41_000L)

        assertEquals(1, outcome.state.stageIndex)
        assertEquals(listOf(ScaleCommand.SetStage("Pour 1", 180)), outcome.commands)
    }

    @Test
    fun `clears the stage once the last window has passed`() {
        val state = brewing()
        val outcome = state.sample(grams = 320f, atMs = 106_000L)

        assertEquals(stages.size, outcome.state.stageIndex)
        assertEquals(listOf(ScaleCommand.ClearStage), outcome.commands)
    }

    @Test
    fun `does not re-announce a stage it is already on`() {
        var state = brewing()
        state = state.sample(grams = 60f, atMs = 41_000L).state
        val outcome = state.sample(grams = 70f, atMs = 42_000L)

        assertTrue(outcome.commands.isEmpty())
    }

    @Test
    fun `does not advance stages when recipe mode is off`() {
        var state = BrewState(recipeMode = false, autoStage = true)
        state = BrewSession.reduce(state, BrewInput.SelectStages(stages)).state
        state = BrewSession.reduce(state, BrewInput.StartTimer(0L)).state
        val outcome = state.sample(grams = 60f, atMs = 41_000L)

        assertEquals(0, outcome.state.stageIndex)
    }

    @Test
    fun `does not advance stages by time when auto stage is off`() {
        val outcome = brewing(autoStage = false).sample(grams = 60f, atMs = 41_000L)

        assertEquals(0, outcome.state.stageIndex)
    }

    @Test
    fun `manual advance moves one stage on`() {
        val outcome = BrewSession.reduce(brewing(), BrewInput.AdvanceStage(atMs = 5_000L))

        assertEquals(1, outcome.state.stageIndex)
        assertEquals(listOf(ScaleCommand.SetStage("Pour 1", 180)), outcome.commands)
    }

    @Test
    fun `manual advance past the last stage clears it`() {
        var state = brewing()
        repeat(stages.size) {
            state = BrewSession.reduce(state, BrewInput.AdvanceStage(atMs = 5_000L)).state
        }

        assertEquals(stages.size, state.stageIndex)
        val outcome = BrewSession.reduce(state, BrewInput.AdvanceStage(atMs = 6_000L))
        assertTrue("should not advance past the end", outcome.commands.isEmpty())
        assertEquals(stages.size, outcome.state.stageIndex)
    }

    @Test
    fun `manual advance starts the timer if it is not running`() {
        var state = BrewState(recipeMode = true)
        state = BrewSession.reduce(state, BrewInput.SelectStages(stages)).state
        val outcome = BrewSession.reduce(state, BrewInput.AdvanceStage(atMs = 2_000L))

        assertTrue(outcome.state.timerRunning)
        assertEquals(
            listOf(ScaleCommand.Timer(running = true), ScaleCommand.SetStage("Pour 1", 180)),
            outcome.commands,
        )
    }

    // ------------------------------------------------------------ stage progress

    @Test
    fun `stage progress fills against the previous stage's cumulative target`() {
        var state = brewing()
        state = state.sample(grams = 115f, atMs = 41_000L).state // stage 1: 50g..180g

        val progress = state.stageProgress
        assertEquals(1f, progress[0].fill, 0.001f)         // past stages are full
        assertEquals(0.5f, progress[1].fill, 0.001f)       // halfway from 50 to 180
        assertEquals(0f, progress[2].fill, 0.001f)         // untouched
        assertTrue(progress[1].isActive)
        assertTrue(progress[0].isPast)
    }

    @Test
    fun `stage progress clamps rather than overflowing`() {
        val state = brewing().sample(grams = 500f, atMs = 1_000L).state

        assertEquals(1f, state.stageProgress[0].fill, 0.001f)
    }

    // -------------------------------------------------------------------- chart

    @Test
    fun `chart records the brew from the moment it starts`() {
        var state = BrewState()
        state = state.sample(grams = 0.1f, atMs = 1_000L).state   // idle, not recorded
        state = state.sample(grams = 5f, atMs = 2_000L).state     // starts the brew
        state = state.sample(grams = 20f, atMs = 3_000L).state

        assertEquals(2, state.chart.size)
        assertEquals(0f, state.chart.first().seconds, 0.001f)
        assertEquals(5f, state.chart.first().grams, 0.001f)
        assertEquals(1f, state.chart.last().seconds, 0.001f)
    }

    @Test
    fun `chart is retained after the brew stops`() {
        var state = brewing()
        state = state.sample(grams = 20f, atMs = 1_000L).state
        state = BrewSession.reduce(state, BrewInput.StopTimer).state
        val afterStop = state.chart.size

        state = state.sample(grams = 0f, atMs = 5_000L).state

        assertTrue("chart should survive the stop", afterStop > 0)
        assertEquals("idle samples must not extend it", afterStop, state.chart.size)
    }

    @Test
    fun `chart is cleared when the next brew starts`() {
        var state = brewing()
        state = state.sample(grams = 20f, atMs = 1_000L).state
        state = BrewSession.reduce(state, BrewInput.StopTimer).state
        state = BrewSession.reduce(state, BrewInput.StartTimer(atMs = 60_000L)).state

        assertTrue(state.chart.isEmpty())
    }

    @Test
    fun `chart is bounded`() {
        var state = brewing()
        repeat(BrewSession.MAX_CHART_POINTS + 50) { i ->
            state = state.sample(grams = i.toFloat(), atMs = 1_000L + i * 100L).state
        }

        assertEquals(BrewSession.MAX_CHART_POINTS, state.chart.size)
    }

    // ------------------------------------------------------------------ resets

    @Test
    fun `selecting a recipe resets the stage and stops the brew`() {
        val outcome = BrewSession.reduce(brewing(), BrewInput.SelectStages(stages.take(2)))

        assertEquals(0, outcome.state.stageIndex)
        assertFalse(outcome.state.timerRunning)
        assertEquals(2, outcome.state.stages.size)
        assertEquals(
            listOf(ScaleCommand.Timer(running = false), ScaleCommand.ClearStage),
            outcome.commands,
        )
    }

    @Test
    fun `resetting clears the stage without discarding the recipe`() {
        var state = brewing()
        state = state.sample(grams = 60f, atMs = 41_000L).state
        val outcome = BrewSession.reduce(state, BrewInput.ResetBrew)

        assertEquals(0, outcome.state.stageIndex)
        assertEquals(stages, outcome.state.stages)
        assertTrue(outcome.commands.contains(ScaleCommand.ClearStage))
    }

    @Test
    fun `disconnecting clears the brew and sends nothing`() {
        var state = brewing()
        state = state.sample(grams = 60f, atMs = 41_000L).state
        val outcome = BrewSession.reduce(state, BrewInput.Disconnected)

        assertFalse(outcome.state.timerRunning)
        assertEquals(0, outcome.state.stageIndex)
        assertEquals(0f, outcome.state.weightGrams, 0.001f)
        assertTrue(outcome.state.chart.isEmpty())
        assertTrue("the link is gone; there is nobody to tell", outcome.commands.isEmpty())
    }

    @Test
    fun `disconnecting keeps the loaded recipe and mode settings`() {
        val outcome = BrewSession.reduce(brewing(), BrewInput.Disconnected)

        assertEquals(stages, outcome.state.stages)
        assertTrue(outcome.state.recipeMode)
    }
}
