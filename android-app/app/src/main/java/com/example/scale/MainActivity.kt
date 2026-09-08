package com.example.scale

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.scale.brew.BrewInput
import com.example.scale.scale.ScaleCommand
import com.example.scale.scale.ScaleEvent
import com.example.scale.scale.ScaleProtocol
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.model.Stage
import com.example.scale.ui.screens.BrewScreen
import com.example.scale.ui.theme.ScaleTheme
import com.example.scale.ui.viewmodel.BrewViewModel
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: BrewViewModel
    private lateinit var prefs: SharedPreferences

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var weightChar: BluetoothGattCharacteristic? = null
    private var ctrlChar: BluetoothGattCharacteristic? = null
    private var battChar: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Characteristics still waiting to have notifications enabled. Android permits one outstanding
     * GATT operation at a time, so descriptor writes are drained one per [onDescriptorWrite].
     */
    private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()

    private val scanTimeoutMs = 10_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("scale_prefs", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[BrewViewModel::class.java]

        loadRecipesFromPrefs()
        if (viewModel.recipes.value.isNullOrEmpty()) {
            viewModel.recipes.value = defaultRecipes()
            saveRecipesToPrefs()
        }

        wireViewModelCallbacks()

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        scanner = bluetoothAdapter?.bluetoothLeScanner

        setContent {
            ScaleTheme {
                BrewScreen(viewModel)
            }
        }
    }

    private fun wireViewModelCallbacks() {
        viewModel.onConnectToggle = {
            if (gatt != null) disconnect() else ensurePermissionsAndScan()
        }
        viewModel.onSendCommands = { commands -> commands.forEach(::send) }
        viewModel.onTare = { send(ScaleCommand.Tare) }
        viewModel.onCalZero = {
            send(ScaleCommand.CalZero)
            showToast("CAL:ZERO sent")
        }
        viewModel.onCalSpan = { grams ->
            send(ScaleCommand.CalSpan(grams))
            showToast("CAL:SPAN sent")
        }
        viewModel.onCalGet = { send(ScaleCommand.CalGet) }
        viewModel.onSelectRecipe = { idx ->
            val list = viewModel.recipes.value
            if (list != null && idx in list.indices) {
                viewModel.currentRecipeIndex.value = idx
                loadStagesIntoBrew()
            }
        }
        viewModel.onSaveRecipe = { idx, recipe ->
            val current = viewModel.recipes.value.orEmpty().toMutableList()
            if (idx != null && idx in current.indices) {
                current[idx] = recipe
            } else {
                current.add(recipe)
                viewModel.currentRecipeIndex.value = current.lastIndex
            }
            viewModel.recipes.value = current
            saveRecipesToPrefs()
            loadStagesIntoBrew()
        }
        viewModel.onDeleteRecipe = { idx ->
            val current = viewModel.recipes.value.orEmpty().toMutableList()
            if (idx in current.indices) {
                current.removeAt(idx)
                viewModel.recipes.value = current
                viewModel.currentRecipeIndex.value =
                    (viewModel.currentRecipeIndex.value ?: 0).coerceAtMost(current.lastIndex.coerceAtLeast(0))
                saveRecipesToPrefs()
                loadStagesIntoBrew()
            }
        }
    }

    /** Push the selected recipe's stages into the brew, which resets stage progress. */
    private fun loadStagesIntoBrew() {
        val recipes = viewModel.recipes.value.orEmpty()
        val index = viewModel.currentRecipeIndex.value ?: 0
        viewModel.dispatch(BrewInput.SelectStages(recipes.getOrNull(index)?.stages.orEmpty()))
    }

    private fun ensurePermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            return
        }
        startScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) startScan() else updateStatus("Permission denied")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanner == null) {
            updateStatus("Bluetooth not available")
            return
        }
        updateStatus("Scanning…")
        viewModel.dispatch(BrewInput.Disconnected)
        viewModel.battery.value = null

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(ScaleProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        handler.postDelayed({
            stopScan()
            if (gatt == null) updateStatus("Not found")
        }, scanTimeoutMs)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            updateStatus("Connecting…")
            connect(result.device.address)
        }

        override fun onScanFailed(errorCode: Int) {
            updateStatus("Scan failed: $errorCode")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun connect(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(this, false, gattCallback)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread { updateStatus("Discovering…") }
                gatt.discoverServices()
            } else {
                runOnUiThread {
                    setConnected(false)
                    updateStatus("Disconnected")
                }
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.getService(ScaleProtocol.SERVICE_UUID)
            weightChar = service?.getCharacteristic(ScaleProtocol.WEIGHT_CHAR_UUID)
            ctrlChar = service?.getCharacteristic(ScaleProtocol.CTRL_CHAR_UUID)
            battChar = service?.getCharacteristic(ScaleProtocol.BATTERY_CHAR_UUID)
            if (weightChar == null || ctrlChar == null) {
                runOnUiThread { updateStatus("Characteristic not found") }
                return
            }

            // Weight and battery push samples; the control characteristic notifies calibration
            // replies back. Queue them: only one descriptor write may be in flight at a time.
            pendingSubscriptions.clear()
            listOfNotNull(weightChar, battChar, ctrlChar).forEach { pendingSubscriptions.addLast(it) }
            subscribeNext(gatt)

            runOnUiThread {
                setConnected(true)
                updateStatus("Scale 02")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (pendingSubscriptions.isNotEmpty()) {
                subscribeNext(gatt)
            } else {
                // Everything is subscribed; the firmware only notifies battery every 30s, so read
                // it once to avoid an empty indicator until the first push arrives.
                readBatteryOnce()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleCharacteristicRead(characteristic, characteristic.value ?: return, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(characteristic, value, status)
        }

        private fun handleCharacteristicRead(
            characteristic: BluetoothGattCharacteristic,
            raw: ByteArray,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            handleCharacteristicChanged(characteristic, raw)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleCharacteristicChanged(characteristic, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        private fun handleCharacteristicChanged(
            characteristic: BluetoothGattCharacteristic,
            raw: ByteArray,
        ) {
            val event = ScaleProtocol.decode(characteristic.uuid, raw) ?: return
            runOnUiThread { onScaleEvent(event) }
        }
    }

    private fun onScaleEvent(event: ScaleEvent) {
        when (event) {
            is ScaleEvent.Weight ->
                viewModel.dispatch(BrewInput.Sample(event.grams, System.currentTimeMillis()))
            is ScaleEvent.Battery -> viewModel.battery.value = event.percent
            is ScaleEvent.Calibration ->
                showToast("Zero ${event.zero}, factor ${event.factor}")
        }
    }

    private fun setConnected(value: Boolean) {
        viewModel.connected.value = value
    }

    private fun updateStatus(text: String) {
        viewModel.connectionStatus.value = text
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        weightChar = null
        ctrlChar = null
        battChar = null
        pendingSubscriptions.clear()
        viewModel.dispatch(BrewInput.Disconnected)
        viewModel.battery.value = null
        setConnected(false)
        updateStatus("Disconnected")
    }

    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun send(command: ScaleCommand) {
        val characteristic = ctrlChar ?: return
        characteristic.value = ScaleProtocol.encode(command)
        gatt?.writeCharacteristic(characteristic)
    }

    @android.annotation.SuppressLint("MissingPermission")
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

    @android.annotation.SuppressLint("MissingPermission")
    private fun readBatteryOnce() {
        val connectedGatt = gatt ?: return
        val characteristic = battChar ?: return
        connectedGatt.readCharacteristic(characteristic)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun defaultRecipes(): List<Recipe> = listOf(
        Recipe(
            "Decaf V60",
            listOf(
                Stage("Bloom", 0, 40, 50f, "Wet all grounds, wait 40s"),
                Stage("Pour 1", 40, 75, 180f, "Slow circular pour to 180g"),
                Stage("Pour 2", 75, 105, 320f, "Finish to 320g, thin stream"),
            ),
        ),
        Recipe(
            "4:6 (3 pours)",
            listOf(
                Stage("Pour 1", 0, 30, 60f, "Center pour to 60g"),
                Stage("Pour 2", 30, 60, 150f, "Circle to 150g"),
                Stage("Pour 3", 60, 120, 300f, "Finish to 300g"),
            ),
        ),
        Recipe(
            "Hoffmann V60",
            listOf(
                Stage("Bloom", 0, 45, 60f, "Bloom 2x dose"),
                Stage("Main Pour", 45, 120, 300f, "Continuous pour to 300g"),
            ),
        ),
        Recipe(
            "Tetsu 4:6 (5 pours)",
            listOf(
                Stage("Pour 1", 0, 30, 50f, "Start sweet"),
                Stage("Pour 2", 30, 60, 100f, "Balance"),
                Stage("Pour 3", 60, 90, 160f, "Strength"),
                Stage("Pour 4", 90, 120, 220f, "Body"),
                Stage("Pour 5", 120, 150, 300f, "Finish"),
            ),
        ),
        Recipe(
            "Kalita 155",
            listOf(
                Stage("Bloom", 0, 30, 40f, "Short bloom"),
                Stage("Pour 1", 30, 70, 120f, "Steady pour"),
                Stage("Pour 2", 70, 110, 200f, "Finish"),
            ),
        ),
        Recipe(
            "Bypass Iced",
            listOf(
                Stage("Bloom", 0, 30, 40f, "Bloom"),
                Stage("Pour", 30, 90, 180f, "Brew concentrate"),
                Stage("Bypass", 90, 90, 300f, "Add ice/water to 300g"),
            ),
        ),
    )

    private fun saveRecipesToPrefs() {
        val root = JSONArray()
        for (recipe in viewModel.recipes.value.orEmpty()) {
            val recipeObj = JSONObject()
            recipeObj.put("title", recipe.title)
            val stagesArray = JSONArray()
            for (stage in recipe.stages) {
                val stageObj = JSONObject()
                stageObj.put("name", stage.name)
                stageObj.put("startSec", stage.startSec)
                stageObj.put("endSec", stage.endSec)
                stageObj.put("targetWeight", stage.targetWeight.toDouble())
                stageObj.put("note", stage.note)
                stagesArray.put(stageObj)
            }
            recipeObj.put("stages", stagesArray)
            root.put(recipeObj)
        }
        prefs.edit().putString("recipes_json", root.toString()).apply()
    }

    private fun loadRecipesFromPrefs() {
        val raw = prefs.getString("recipes_json", null) ?: return
        try {
            val root = JSONArray(raw)
            val list = mutableListOf<Recipe>()
            for (i in 0 until root.length()) {
                val recipeObj = root.optJSONObject(i) ?: continue
                val title = recipeObj.optString("title", "").trim()
                if (title.isEmpty()) continue
                val stagesJson = recipeObj.optJSONArray("stages") ?: JSONArray()
                val stages = mutableListOf<Stage>()
                for (j in 0 until stagesJson.length()) {
                    val stageObj = stagesJson.optJSONObject(j) ?: continue
                    val name = stageObj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    stages.add(
                        Stage(
                            name = name,
                            startSec = stageObj.optInt("startSec", 0),
                            endSec = stageObj.optInt("endSec", 0),
                            targetWeight = stageObj.optDouble("targetWeight", 0.0).toFloat(),
                            note = stageObj.optString("note", ""),
                        ),
                    )
                }
                if (stages.isNotEmpty()) list.add(Recipe(title, stages))
            }
            viewModel.recipes.value = list
        } catch (_: Exception) {
            viewModel.recipes.value = emptyList()
        }
    }

}
