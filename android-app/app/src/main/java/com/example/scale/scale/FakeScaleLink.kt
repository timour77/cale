package com.example.scale.scale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One scripted reading: how long after the previous one, and what the scale then read. */
data class ScriptedSample(val afterMs: Long, val grams: Float)

/**
 * A [ScaleLink] that replays a scripted pour instead of talking to hardware.
 *
 * The second adapter at this seam, and the reason the seam is real rather than hypothetical. It
 * makes the brew exercisable without a device — in unit tests, and in Compose previews, which
 * otherwise can only ever render the disconnected screen.
 *
 * Commands sent to it are recorded in [sent] rather than transmitted, so a test can assert what
 * the app would have told the scale.
 */
class FakeScaleLink(
    private val scope: CoroutineScope,
    private val script: List<ScriptedSample> = defaultPour(),
    private val batteryPercent: Int = 82,
) : ScaleLink {

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScaleEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val events: Flow<ScaleEvent> = _events.asSharedFlow()

    /** Every command the app has sent, in order. */
    val sent = mutableListOf<ScaleCommand>()

    private var playback: Job? = null

    override fun connect() {
        _state.value = LinkState.Connecting
        playback?.cancel()
        playback = scope.launch {
            _state.value = LinkState.Ready
            _events.emit(ScaleEvent.Battery(batteryPercent))
            for (sample in script) {
                delay(sample.afterMs)
                _events.emit(ScaleEvent.Weight(sample.grams))
            }
        }
    }

    override fun disconnect() {
        playback?.cancel()
        playback = null
        _state.value = LinkState.Idle
    }

    override fun send(command: ScaleCommand) {
        sent += command
        if (command is ScaleCommand.CalGet) {
            _events.tryEmit(ScaleEvent.Calibration(zero = 8123L, factor = -895.9f))
        }
    }

    override fun close() {
        disconnect()
    }

    /** Put the link into a state directly, for a preview that wants to look connected. */
    fun forceState(state: LinkState) {
        _state.value = state
    }

    companion object {
        /**
         * A plausible V60: an empty pan, a bloom, a pause, two pours, then the cup lifted off.
         * Roughly 100 ms between samples, matching what the firmware actually sends.
         */
        fun defaultPour(): List<ScriptedSample> = buildList {
            repeat(10) { add(ScriptedSample(100, 0f)) }
            // Bloom to 50g over ~4s
            repeat(40) { i -> add(ScriptedSample(100, 50f * (i + 1) / 40f)) }
            // Rest
            repeat(30) { add(ScriptedSample(100, 50f)) }
            // Pour to 180g
            repeat(50) { i -> add(ScriptedSample(100, 50f + 130f * (i + 1) / 50f)) }
            // Pour to 320g
            repeat(50) { i -> add(ScriptedSample(100, 180f + 140f * (i + 1) / 50f)) }
            // Settle, then lifted off
            repeat(20) { add(ScriptedSample(100, 320f)) }
            repeat(20) { add(ScriptedSample(100, 0f)) }
        }
    }
}
