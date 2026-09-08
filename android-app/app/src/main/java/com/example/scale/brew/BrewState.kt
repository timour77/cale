package com.example.scale.brew

import com.example.scale.ui.model.Stage

/** One point on the brew trace: seconds since the brew started, and the weight then. */
data class ChartPoint(val seconds: Float, val grams: Float)

/** How far along one Stage is, derived from the current weight. */
data class StageProgress(
    val index: Int,
    val stage: Stage,
    val fill: Float,
    val isActive: Boolean,
    val isPast: Boolean,
)

/**
 * Everything true about a brew at one instant.
 *
 * Held by the caller and threaded through [BrewSession.reduce]; the session itself keeps nothing.
 * Values a view would show are at the top; [bookkeeping] is the reducer's own memory and is of no
 * interest to anyone else.
 */
data class BrewState(
    val weightGrams: Float = 0f,
    val flowRate: Float = 0f,
    val timerRunning: Boolean = false,
    val elapsedSeconds: Float = 0f,
    val stages: List<Stage> = emptyList(),
    val stageIndex: Int = 0,
    val chart: List<ChartPoint> = emptyList(),

    /** Whether stage tracking applies at all. */
    val recipeMode: Boolean = false,

    /** Whether stages advance on their own with the clock, or only when the user says so. */
    val autoStage: Boolean = true,

    val bookkeeping: Bookkeeping = Bookkeeping(),
) {

    /** The Stage being poured, or null once the recipe has run out of them. */
    val activeStage: Stage? get() = stages.getOrNull(stageIndex)

    /**
     * Per-stage fill, derived rather than stored so it cannot go stale.
     *
     * Stage targets are cumulative, so a stage's own span is its target minus the previous one's;
     * fill is how far the current weight has crossed that span.
     */
    val stageProgress: List<StageProgress>
        get() = stages.mapIndexed { i, stage ->
            val previousTarget = if (i > 0) stages[i - 1].targetWeight else 0f
            val span = (stage.targetWeight - previousTarget).coerceAtLeast(0.001f)
            StageProgress(
                index = i,
                stage = stage,
                fill = when {
                    i < stageIndex -> 1f
                    i == stageIndex -> ((weightGrams - previousTarget) / span).coerceIn(0f, 1f)
                    else -> 0f
                },
                isActive = i == stageIndex,
                isPast = i < stageIndex,
            )
        }

    /**
     * The reducer's memory between inputs: when things happened, and what the last sample was.
     * Part of the state because the reducer is pure and keeps nothing of its own.
     */
    data class Bookkeeping(
        val timerStartedAtMs: Long = 0L,
        val chartStartedAtMs: Long = 0L,
        /** When the weight first dropped below the stop threshold, or null if it is above it. */
        val belowStopThresholdSinceMs: Long? = null,
        /** Null until the first sample arrives. Not 0L: that is a legitimate timestamp. */
        val lastSampleAtMs: Long? = null,
        val lastSampleGrams: Float = 0f,
    )
}
