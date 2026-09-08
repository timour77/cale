package com.example.scale.scale

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

private const val STAGE_SET_PREFIX_LEN = 10 // "STAGE_SET:"
private const val MAX_TARGET_DIGITS = 5
private const val MAX_TARGET = 99_999

/**
 * The wire format spoken between this app and the scale firmware (`scales_bt/scales_bt.ino`).
 *
 * This object is the specification. The firmware's `handleControlCommand` and its `notify` calls
 * are the other implementation of it; when the two disagree, the bug is on whichever side does not
 * match what is written here.
 *
 * The transport is Nordic UART Service UUIDs carrying ASCII text — not binary. Every frame is a
 * short, human-readable string, which is why the tests for this file can assert on literals.
 *
 * Deliberately free of Android types so the whole format is testable on the JVM.
 */
object ScaleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** App -> scale commands. Also carries calibration replies back (notify). */
    val CTRL_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** Scale -> app weight samples, roughly 10/second. */
    val WEIGHT_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    /** Scale -> app battery percentage, every 30 seconds. */
    val BATTERY_CHAR_UUID: UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")

    /** Standard Client Characteristic Configuration descriptor, used to enable notifications. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * Largest control frame the firmware will accept, from its `ctrlChar.setMaxLen(50)`.
     * Anything longer is truncated by the radio, silently, which is worth avoiding.
     */
    const val MAX_FRAME_BYTES = 50

    /**
     * Longest stage name that always fits in a [ScaleCommand.SetStage] frame. Longer names are
     * truncated by [encode] rather than dropping the command — a clipped name on a 128x64 display
     * beats no stage update at all. Bound the input at the source if you want the full name shown.
     */
    const val MAX_STAGE_NAME = MAX_FRAME_BYTES - STAGE_SET_PREFIX_LEN - 1 - MAX_TARGET_DIGITS

    /**
     * Render a command as the bytes to write to [CTRL_CHAR_UUID].
     *
     * Always produces a frame of at most [MAX_FRAME_BYTES] bytes.
     */
    fun encode(command: ScaleCommand): ByteArray = text(command).toByteArray(StandardCharsets.US_ASCII)

    /**
     * Interpret a notification or read from the scale.
     *
     * Returns null for anything not understood: an unknown characteristic, a blank frame, a number
     * that will not parse, or a weight that is not finite. A garbled frame is an ordinary event on
     * a radio rather than an exceptional one, so this reports absence instead of throwing — and it
     * stays silent about it, leaving logging to the caller that owns the connection.
     */
    fun decode(characteristicUuid: UUID, raw: ByteArray): ScaleEvent? {
        val frame = String(raw, StandardCharsets.US_ASCII).trim()
        if (frame.isEmpty()) return null

        return when (characteristicUuid) {
            WEIGHT_CHAR_UUID -> {
                // Tolerate a comma decimal separator: the firmware always emits '.', but a
                // differently-built device need not.
                val grams = frame.replace(',', '.').toFloatOrNull() ?: return null
                if (!grams.isFinite()) null else ScaleEvent.Weight(grams)
            }

            BATTERY_CHAR_UUID -> {
                val percent = frame.toIntOrNull() ?: return null
                ScaleEvent.Battery(percent.coerceIn(0, 100))
            }

            CTRL_CHAR_UUID -> {
                // The only thing the scale sends back on the control characteristic is its stored
                // calibration, as "<rawZeroCount>,<countsPerGram>".
                val parts = frame.split(',')
                if (parts.size != 2) return null
                val zero = parts[0].trim().toLongOrNull() ?: return null
                val factor = parts[1].trim().toFloatOrNull() ?: return null
                if (!factor.isFinite()) null else ScaleEvent.Calibration(zero, factor)
            }

            else -> null
        }
    }

    private fun text(command: ScaleCommand): String = when (command) {
        ScaleCommand.Tare -> "TARE"
        ScaleCommand.CalZero -> "CAL:ZERO"
        ScaleCommand.CalGet -> "CAL:GET"
        // Locale.ROOT matters: the default locale would render this "1000,0" in much of Europe,
        // and the firmware's String::toFloat would read that as 1000 grams of nothing.
        is ScaleCommand.CalSpan -> "CAL:SPAN:" + String.format(Locale.ROOT, "%.1f", command.grams)
        is ScaleCommand.SetStage -> {
            val target = command.targetGrams.coerceIn(0, MAX_TARGET)
            val name = command.name.trim().take(MAX_STAGE_NAME)
            "STAGE_SET:$name:$target"
        }
        ScaleCommand.ClearStage -> "STAGE_CLEAR"
        is ScaleCommand.Timer -> if (command.running) "TIMER:START" else "TIMER:STOP"
    }
}
