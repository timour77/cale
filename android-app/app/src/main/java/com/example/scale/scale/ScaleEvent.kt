package com.example.scale.scale

/**
 * Something the scale told us, decoded from a notification or a characteristic read.
 *
 * Produced only by [ScaleProtocol.decode]. A frame that cannot be understood yields no event at
 * all rather than a malformed one — see that function for the rationale.
 */
sealed interface ScaleEvent {

    /** A filtered weight sample. The device applies its own IIR filter before sending. */
    data class Weight(val grams: Float) : ScaleEvent

    /** Battery charge, always within 0..100. */
    data class Battery(val percent: Int) : ScaleEvent

    /**
     * The device's stored calibration, in reply to [ScaleCommand.CalGet].
     *
     * [zero] is a raw ADC count; [factor] is ADC counts per gram and is normally negative,
     * because the load cell bridge is wired with the polarity that makes it so.
     */
    data class Calibration(val zero: Long, val factor: Float) : ScaleEvent
}
