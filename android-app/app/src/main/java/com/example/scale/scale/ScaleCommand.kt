package com.example.scale.scale

/**
 * A message the app sends to the scale.
 *
 * Commands are values, not calls: a caller decides *what* to say without knowing how it is
 * spelled on the wire, and something else decides when to say it. [ScaleProtocol.encode] is the
 * only place that turns one of these into bytes.
 */
sealed interface ScaleCommand {

    /** Zero the reading at the current load, for this brew only. Not persisted on the device. */
    data object Tare : ScaleCommand

    /** Capture the current raw ADC reading as the persisted zero point. */
    data object CalZero : ScaleCommand

    /**
     * Calibrate the span against a known reference mass. The device derives and persists a new
     * calibration factor from the raw reading at this weight.
     */
    data class CalSpan(val grams: Float) : ScaleCommand

    /** Ask the device to report its stored calibration; answered with a [ScaleEvent.Calibration]. */
    data object CalGet : ScaleCommand

    /**
     * Tell the device which recipe stage is active, so it can show it on the on-device display.
     *
     * [name] is truncated to fit the frame when encoded; see [ScaleProtocol.MAX_STAGE_NAME].
     */
    data class SetStage(val name: String, val targetGrams: Int) : ScaleCommand

    /** Clear the active stage. Distinct from `SetStage("", 0)`, which is not a thing you can say. */
    data object ClearStage : ScaleCommand

    /**
     * Mirror the app's brew timer onto the device display. The app is the authority: the device
     * only reflects the run/stop state it is told about and keeps its own elapsed count.
     */
    data class Timer(val running: Boolean) : ScaleCommand
}
