package com.example.scale.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [ScaleLink] over the Android Bluetooth stack.
 *
 * The only place in the app that knows BLE exists. Callers are assumed to hold the scan/connect
 * permissions already — asking for them needs an Activity, and this has no business being one.
 *
 * GATT callbacks arrive on a Binder thread and are published straight onto the flows without
 * hopping to the main thread; collectors decide where they want to be. The event flow drops its
 * oldest value under back pressure, which at ten weight samples a second is the right thing to
 * lose.
 */
@SuppressLint("MissingPermission")
class GattScaleLink(
    context: Context,
    private val scanTimeoutMs: Long = 10_000L,
) : ScaleLink {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScaleEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<ScaleEvent> = _events.asSharedFlow()

    private val adapter: BluetoothAdapter?
        get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var ctrlChar: BluetoothGattCharacteristic? = null
    private var battChar: BluetoothGattCharacteristic? = null
    private var scanTimeoutJob: Job? = null

    /**
     * Characteristics still waiting to have notifications enabled. Android permits one outstanding
     * GATT operation at a time, so descriptor writes are drained one per [onDescriptorWrite].
     */
    private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()

    override fun connect() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = LinkState.Failed(LinkState.Reason.BluetoothUnavailable)
            return
        }

        _state.value = LinkState.Scanning
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(ScaleProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(scanTimeoutMs)
            stopScan()
            if (gatt == null) _state.value = LinkState.Failed(LinkState.Reason.NotFound)
        }
    }

    override fun disconnect() {
        teardown()
        _state.value = LinkState.Idle
    }

    override fun send(command: ScaleCommand) {
        val characteristic = ctrlChar ?: return
        val connected = gatt ?: return
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connected.writeCharacteristic(
                characteristic,
                ScaleProtocol.encode(command),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
        } else {
            characteristic.value = ScaleProtocol.encode(command)
            connected.writeCharacteristic(characteristic)
        }
    }

    override fun close() {
        teardown()
        scope.cancel()
    }

    private fun teardown() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        ctrlChar = null
        battChar = null
        pendingSubscriptions.clear()
    }

    private fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scanTimeoutJob?.cancel()
            stopScan()
            _state.value = LinkState.Connecting
            openGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanTimeoutJob?.cancel()
            _state.value = LinkState.Failed(LinkState.Reason.ScanFailed(errorCode))
        }
    }

    private fun openGatt(device: BluetoothDevice) {
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else {
                val wasReady = _state.value == LinkState.Ready
                teardown()
                _state.value = if (wasReady) {
                    LinkState.Failed(LinkState.Reason.ConnectionLost)
                } else {
                    LinkState.Idle
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(ScaleProtocol.SERVICE_UUID)
            val weight = service?.getCharacteristic(ScaleProtocol.WEIGHT_CHAR_UUID)
            ctrlChar = service?.getCharacteristic(ScaleProtocol.CTRL_CHAR_UUID)
            battChar = service?.getCharacteristic(ScaleProtocol.BATTERY_CHAR_UUID)

            if (weight == null || ctrlChar == null) {
                _state.value = LinkState.Failed(LinkState.Reason.ServiceMissing)
                return
            }

            // Weight and battery push samples; the control characteristic notifies calibration
            // replies back.
            pendingSubscriptions.clear()
            listOfNotNull(weight, battChar, ctrlChar).forEach { pendingSubscriptions.addLast(it) }
            subscribeNext(gatt)

            _state.value = LinkState.Ready
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (pendingSubscriptions.isNotEmpty()) {
                subscribeNext(gatt)
            } else {
                // The firmware only notifies battery every 30s, so read it once rather than
                // showing an empty indicator until the first push arrives.
                readBatteryOnce()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                publish(characteristic, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) publish(characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                publish(characteristic, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            publish(characteristic, value)
        }
    }

    private fun publish(characteristic: BluetoothGattCharacteristic, raw: ByteArray) {
        val event = ScaleProtocol.decode(characteristic.uuid, raw) ?: return
        _events.tryEmit(event)
    }

    @Suppress("DEPRECATION")
    private fun subscribeNext(gatt: BluetoothGatt) {
        val characteristic = pendingSubscriptions.removeFirstOrNull() ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(ScaleProtocol.CCCD_UUID)
        if (cccd == null) {
            // No descriptor means no notifications from this one; move on rather than stalling
            // the rest of the queue.
            subscribeNext(gatt)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }
    }

    private fun readBatteryOnce() {
        val connected = gatt ?: return
        val characteristic = battChar ?: return
        connected.readCharacteristic(characteristic)
    }
}
