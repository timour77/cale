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
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.model.Stage
import com.example.scale.ui.screens.BrewScreen
import com.example.scale.ui.theme.ScaleTheme
import com.example.scale.ui.viewmodel.BrewViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

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

    private val serviceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val weightCharUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val ctrlCharUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val battCharUuid = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanTimeoutMs = 10_000L
    private var timerRunning = false
    private var timerStartMs = 0L
    private var belowThresholdSinceMs = 0L
    private val startThreshold = 0.5f
    private val stopThreshold = 0.2f
    private var lastWeight = 0f
    private var lastWeightTime = 0L
    private var flowRate = 0f
    private var chartStartMs = 0L
    private var currentStageIndex = 0

    private val timerTick = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsedSec = (System.currentTimeMillis() - timerStartMs) / 1000f
                viewModel.elapsedSeconds.value = elapsedSec
                if (viewModel.recipeModeEnabled.value && viewModel.autoStageMode.value) {
                    updateStageByTime(elapsedSec.toInt())
                }
                handler.postDelayed(this, 250)
            }
        }
    }
    private val batteryPollTick = object : Runnable {
        override fun run() {
            readBatteryOnce()
            handler.postDelayed(this, 15000)
        }
    }

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
        viewModel.onTare = { sendCommand("TARE") }
        viewModel.onToggleTimer = { toggleTimer() }
        viewModel.onCalZero = {
            sendCommand("CAL:ZERO")
            showToast("CAL:ZERO sent")
        }
        viewModel.onCalSpan = { grams ->
            sendCommand("CAL:SPAN:$grams")
            showToast("CAL:SPAN sent")
        }
        viewModel.onCalGet = {
            sendCommand("CAL:GET")
            showToast("CAL:GET sent")
        }
        viewModel.onSelectRecipe = { idx ->
            val list = viewModel.recipes.value
            if (list != null && idx in list.indices) {
                viewModel.currentRecipeIndex.value = idx
                resetRecipe(stopTimerToo = true)
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
            resetRecipe(stopTimerToo = true)
        }
        viewModel.onDeleteRecipe = { idx ->
            val current = viewModel.recipes.value.orEmpty().toMutableList()
            if (idx in current.indices) {
                current.removeAt(idx)
                viewModel.recipes.value = current
                viewModel.currentRecipeIndex.value =
                    (viewModel.currentRecipeIndex.value ?: 0).coerceAtMost(current.lastIndex.coerceAtLeast(0))
                saveRecipesToPrefs()
                resetRecipe(stopTimerToo = true)
            }
        }
        viewModel.onAdvanceStage = { advanceStageManual() }
        viewModel.onResetRecipe = { resetRecipe(stopTimerToo = true) }
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
        viewModel.weight.value = 0f
        viewModel.battery.value = null

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
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
            val service: BluetoothGattService? = gatt.getService(serviceUuid)
            weightChar = service?.getCharacteristic(weightCharUuid)
            ctrlChar = service?.getCharacteristic(ctrlCharUuid)
            battChar = service?.getCharacteristic(battCharUuid)
            if (weightChar == null || ctrlChar == null) {
                runOnUiThread { updateStatus("Characteristic not found") }
                return
            }

            gatt.setCharacteristicNotification(weightChar, true)
            val cccd = weightChar?.getDescriptor(cccdUuid)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            } else {
                runOnUiThread { updateStatus("Notify descriptor missing") }
            }
            runOnUiThread {
                setConnected(true)
                updateStatus("Scale 02")
                resetChart()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            runOnUiThread {
                handler.removeCallbacks(batteryPollTick)
                readBatteryOnce()
                handler.postDelayed(batteryPollTick, 15000)
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
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == battCharUuid) {
                val pct = String(raw, StandardCharsets.US_ASCII).trim().toIntOrNull()
                runOnUiThread {
                    if (pct != null) viewModel.battery.value = pct.coerceIn(0, 100)
                }
            }
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
            if (characteristic.uuid == battCharUuid) {
                val pct = String(raw, StandardCharsets.US_ASCII).trim().toIntOrNull()
                runOnUiThread {
                    if (pct != null) viewModel.battery.value = pct.coerceIn(0, 100)
                }
            } else if (characteristic.uuid == weightCharUuid) {
                val text = String(raw, StandardCharsets.US_ASCII).trim()
                val parsed = text.replace(',', '.').toFloatOrNull() ?: return
                runOnUiThread { onWeightSample(parsed) }
            }
        }
    }

    private fun onWeightSample(weight: Float) {
        viewModel.weight.value = weight
        updateFlow(weight)
        addChartPoint(weight)
        autoSyncTimer(weight)
        if (viewModel.recipeModeEnabled.value) {
            if (viewModel.autoStageMode.value && timerRunning) {
                val elapsed = ((System.currentTimeMillis() - timerStartMs) / 1000).toInt()
                updateStageByTime(elapsed)
            }
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
        handler.removeCallbacks(batteryPollTick)
        stopTimer()
        resetRecipe(false)
        resetChart()
        viewModel.battery.value = null
        setConnected(false)
        updateStatus("Disconnected")
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun sendCommand(cmd: String) {
        val characteristic = ctrlChar ?: return
        characteristic.value = cmd.toByteArray(StandardCharsets.US_ASCII)
        gatt?.writeCharacteristic(characteristic)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun readBatteryOnce() {
        val connectedGatt = gatt ?: return
        val characteristic = battChar ?: return
        connectedGatt.readCharacteristic(characteristic)
    }

    private fun toggleTimer() {
        if (!timerRunning) startTimer() else stopTimer()
    }

    private fun stopTimer() {
        timerRunning = false
        viewModel.timerRunning.value = false
        handler.removeCallbacks(timerTick)
        sendCommand("TIMER:STOP")
        belowThresholdSinceMs = 0L
    }

    private fun startTimer() {
        timerRunning = true
        viewModel.timerRunning.value = true
        timerStartMs = System.currentTimeMillis()
        viewModel.elapsedSeconds.value = 0f
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
        sendCommand("TIMER:START")
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

    private fun updateStageByTime(elapsedSec: Int) {
        val recipes = viewModel.recipes.value.orEmpty()
        if (recipes.isEmpty()) return
        val idx = viewModel.currentRecipeIndex.value ?: 0
        val stages = recipes.getOrNull(idx)?.stages ?: return
        if (stages.isEmpty()) return
        val first = stages.indexOfFirst { elapsedSec < it.endSec }
        val newIndex = if (first == -1) stages.lastIndex + 1 else first
        if (newIndex != currentStageIndex) {
            currentStageIndex = newIndex
            viewModel.stageIndex.value = newIndex
            broadcastStageCommand()
        }
    }

    private fun advanceStageManual() {
        if (!viewModel.recipeModeEnabled.value) return
        val recipes = viewModel.recipes.value.orEmpty()
        if (recipes.isEmpty()) return
        if (!timerRunning) startTimer()
        val stages = recipes.getOrNull(viewModel.currentRecipeIndex.value ?: 0)?.stages ?: return
        if (stages.isEmpty()) return
        if (currentStageIndex <= stages.lastIndex) {
            currentStageIndex += 1
            viewModel.stageIndex.value = currentStageIndex
            broadcastStageCommand()
        }
    }

    private fun broadcastStageCommand() {
        val recipes = viewModel.recipes.value.orEmpty()
        val recipe = recipes.getOrNull(viewModel.currentRecipeIndex.value ?: 0) ?: return
        val stage = recipe.stages.getOrNull(currentStageIndex)
        if (stage != null) {
            sendCommand("STAGE:${stage.name}|${stage.targetWeight.toInt()}")
        } else {
            sendCommand("STAGE:|0")
        }
    }

    private fun resetRecipe(stopTimerToo: Boolean) {
        if (stopTimerToo && timerRunning) stopTimer()
        currentStageIndex = 0
        viewModel.stageIndex.value = 0
        sendCommand("STAGE:|0")
    }

    private fun autoSyncTimer(weight: Float) {
        val now = System.currentTimeMillis()
        if (!timerRunning && weight >= startThreshold) {
            startTimer()
            return
        }

        if (timerRunning) {
            if (weight <= stopThreshold) {
                if (belowThresholdSinceMs == 0L) belowThresholdSinceMs = now
                if (now - belowThresholdSinceMs > 1500) stopTimer()
            } else {
                belowThresholdSinceMs = 0L
            }
        }
    }

    private fun updateFlow(weight: Float) {
        val now = System.currentTimeMillis()
        if (lastWeightTime == 0L) {
            lastWeightTime = now
            lastWeight = weight
            flowRate = 0f
            viewModel.flowRate.value = 0f
            return
        }
        val dt = (now - lastWeightTime).coerceAtLeast(50L)
        val dw = weight - lastWeight
        val inst = (dw / dt) * 1000f
        flowRate = (flowRate * 0.7f) + (inst * 0.3f)
        if (flowRate < 0f) flowRate = 0f
        viewModel.flowRate.value = flowRate
        lastWeightTime = now
        lastWeight = weight
    }

    private fun addChartPoint(weight: Float) {
        if (chartStartMs == 0L) chartStartMs = System.currentTimeMillis()
        val x = (System.currentTimeMillis() - chartStartMs) / 1000f
        viewModel.pushChartPoint(x, weight)
    }

    private fun resetChart() {
        viewModel.clearChart()
        chartStartMs = System.currentTimeMillis()
        lastWeightTime = 0L
        lastWeight = 0f
        flowRate = 0f
        viewModel.flowRate.value = 0f
    }
}
