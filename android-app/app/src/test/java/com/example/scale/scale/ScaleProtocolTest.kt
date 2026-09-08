package com.example.scale.scale

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-bytes tests for the wire format.
 *
 * The literals below are the contract with `scales_bt/scales_bt.ino`. If you change one, change
 * the firmware's `handleControlCommand` in the same commit — that is the entire point of these
 * tests existing.
 */
class ScaleProtocolTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun encoded(command: ScaleCommand) =
        String(ScaleProtocol.encode(command), StandardCharsets.US_ASCII)

    private fun frame(text: String) = text.toByteArray(StandardCharsets.US_ASCII)

    // ---------------------------------------------------------------- encode

    @Test
    fun `encodes the simple commands`() {
        assertEquals("TARE", encoded(ScaleCommand.Tare))
        assertEquals("CAL:ZERO", encoded(ScaleCommand.CalZero))
        assertEquals("CAL:GET", encoded(ScaleCommand.CalGet))
    }

    @Test
    fun `encodes a stage with a colon separator, matching the firmware`() {
        assertEquals("STAGE_SET:Bloom:50", encoded(ScaleCommand.SetStage("Bloom", 50)))
    }

    @Test
    fun `clearing a stage is its own command, not an empty name`() {
        assertEquals("STAGE_CLEAR", encoded(ScaleCommand.ClearStage))
    }

    @Test
    fun `encodes timer state`() {
        assertEquals("TIMER:START", encoded(ScaleCommand.Timer(running = true)))
        assertEquals("TIMER:STOP", encoded(ScaleCommand.Timer(running = false)))
    }

    @Test
    fun `encodes span calibration with one decimal place`() {
        assertEquals("CAL:SPAN:1000.0", encoded(ScaleCommand.CalSpan(1000f)))
        assertEquals("CAL:SPAN:500.5", encoded(ScaleCommand.CalSpan(500.5f)))
    }

    @Test
    fun `span calibration uses a dot decimal separator in every locale`() {
        // A comma here would make the firmware's String::toFloat read 1000,0 as 1000 and then
        // stop, or worse. This is the bug this test exists to prevent.
        Locale.setDefault(Locale.GERMANY)
        assertEquals("CAL:SPAN:1000.0", encoded(ScaleCommand.CalSpan(1000f)))
    }

    @Test
    fun `truncates a long stage name rather than dropping the command`() {
        val longName = "P".repeat(80)
        val text = encoded(ScaleCommand.SetStage(longName, 300))

        assertTrue(text.startsWith("STAGE_SET:PPP"))
        assertTrue(text.endsWith(":300"))
        assertTrue(
            "frame was ${text.length} bytes, over the firmware's limit",
            text.length <= ScaleProtocol.MAX_FRAME_BYTES,
        )
    }

    @Test
    fun `a name at the documented limit survives intact`() {
        val name = "P".repeat(ScaleProtocol.MAX_STAGE_NAME)
        assertEquals("STAGE_SET:$name:300", encoded(ScaleCommand.SetStage(name, 300)))
    }

    @Test
    fun `every command fits the firmware frame budget`() {
        val commands = listOf(
            ScaleCommand.Tare,
            ScaleCommand.CalZero,
            ScaleCommand.CalGet,
            ScaleCommand.CalSpan(99999f),
            ScaleCommand.SetStage("P".repeat(120), 99999),
            ScaleCommand.ClearStage,
            ScaleCommand.Timer(running = true),
        )
        for (command in commands) {
            val size = ScaleProtocol.encode(command).size
            assertTrue("$command encoded to $size bytes", size <= ScaleProtocol.MAX_FRAME_BYTES)
        }
    }

    // ---------------------------------------------------------------- decode

    @Test
    fun `decodes a weight sample`() {
        val event = ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("123.4"))
        assertEquals(ScaleEvent.Weight(123.4f), event)
    }

    @Test
    fun `decodes a negative weight`() {
        val event = ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("-0.3"))
        assertEquals(ScaleEvent.Weight(-0.3f), event)
    }

    @Test
    fun `tolerates a comma decimal separator in a weight sample`() {
        val event = ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("123,4"))
        assertEquals(ScaleEvent.Weight(123.4f), event)
    }

    @Test
    fun `decodes battery percentage and clamps it`() {
        assertEquals(
            ScaleEvent.Battery(87),
            ScaleProtocol.decode(ScaleProtocol.BATTERY_CHAR_UUID, frame("87")),
        )
        assertEquals(
            ScaleEvent.Battery(100),
            ScaleProtocol.decode(ScaleProtocol.BATTERY_CHAR_UUID, frame("142")),
        )
        assertEquals(
            ScaleEvent.Battery(0),
            ScaleProtocol.decode(ScaleProtocol.BATTERY_CHAR_UUID, frame("-5")),
        )
    }

    @Test
    fun `decodes a calibration reply`() {
        val event = ScaleProtocol.decode(ScaleProtocol.CTRL_CHAR_UUID, frame("8123,-895.90"))
        assertEquals(ScaleEvent.Calibration(zero = 8123L, factor = -895.90f), event)
    }

    @Test
    fun `returns null for frames it cannot understand`() {
        assertNull(ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("   ")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("hello")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.BATTERY_CHAR_UUID, frame("87%")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.CTRL_CHAR_UUID, frame("8123")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.CTRL_CHAR_UUID, frame("a,b")))
    }

    @Test
    fun `rejects a non-finite weight rather than poisoning the chart`() {
        // "NaN" and "Infinity" both parse happily via String::toFloatOrNull.
        assertNull(ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("NaN")))
        assertNull(ScaleProtocol.decode(ScaleProtocol.WEIGHT_CHAR_UUID, frame("Infinity")))
    }

    @Test
    fun `ignores characteristics it does not know`() {
        val unknown = UUID.fromString("0000dead-0000-1000-8000-00805f9b34fb")
        assertNull(ScaleProtocol.decode(unknown, frame("123.4")))
    }

    @Test
    fun `weight frames are not read as calibration replies`() {
        // Guards the reason calibration replies do not share the weight characteristic: a comma
        // in a reply would otherwise decode as a plausible-looking weight.
        assertNull(ScaleProtocol.decode(ScaleProtocol.CTRL_CHAR_UUID, frame("123.4")))
    }
}
