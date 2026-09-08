package com.example.scale.scale

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A scale you can talk to.
 *
 * The seam between the brew and the radio. Everything about Bluetooth — scanning, GATT, descriptor
 * writes, Android version differences, thread hopping — lives behind this in an adapter, so
 * nothing above it needs to know that BLE has API levels.
 *
 * Two adapters satisfy it: [GattScaleLink] over a real radio, and `FakeScaleLink` replaying a
 * scripted pour for tests and previews.
 *
 * [connect] and [disconnect] are side effects on real hardware and take effect whether or not
 * anyone is collecting; [state] and [events] report what happened rather than causing it.
 */
interface ScaleLink {

    /** Where the connection currently stands. Always has a value. */
    val state: StateFlow<LinkState>

    /** Readings from the scale, in order. Hot: emitted whether or not anyone is listening. */
    val events: Flow<ScaleEvent>

    /** Begin scanning for a scale and connect to the first one found. */
    fun connect()

    /** Drop the connection and release the radio. Safe to call when not connected. */
    fun disconnect()

    /** Send a command. Silently does nothing if the scale is not [LinkState.Ready]. */
    fun send(command: ScaleCommand)

    /** Release everything permanently. The link is not usable afterwards. */
    fun close()
}

/**
 * How the connection is doing.
 *
 * Deliberately free of display strings: this is a state machine, not a status bar. The UI decides
 * what these are called, which is how "Scale 02" — a device label — stopped being a connection
 * state.
 */
sealed interface LinkState {

    /** Nothing is happening, and nothing has gone wrong. */
    data object Idle : LinkState

    /** Looking for a scale advertising the service. */
    data object Scanning : LinkState

    /** Found one; connecting and discovering its characteristics. */
    data object Connecting : LinkState

    /** Connected, subscribed, and able to send commands. */
    data object Ready : LinkState

    /** Gave up. [reason] says why, so the UI can decide what to tell the user. */
    data class Failed(val reason: Reason) : LinkState

    sealed interface Reason {
        /** No Bluetooth adapter, or it is switched off. */
        data object BluetoothUnavailable : Reason

        /** The scan ran to its timeout without seeing a scale. */
        data object NotFound : Reason

        /** The Android scan API refused to start. */
        data class ScanFailed(val errorCode: Int) : Reason

        /** Connected, but the device does not expose the characteristics we need. */
        data object ServiceMissing : Reason

        /** The connection dropped after having been established. */
        data object ConnectionLost : Reason
    }
}
