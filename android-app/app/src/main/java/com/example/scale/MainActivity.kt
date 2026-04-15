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
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.text.InputType
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var timeText: TextView
    private lateinit var weightText: TextView
    private lateinit var flowText: TextView
    private lateinit var batteryText: TextView
    private lateinit var connectButton: Button
    private lateinit var tareButton: Button
    private lateinit var timerButton: Button
    private lateinit var recipeToggleButton: Button
    private lateinit var graphToggleButton: Button
    private lateinit var calZeroButton: Button
    private lateinit var calSpanButton: Button
    private lateinit var calGetButton: Button
    private lateinit var recipeTitle: TextView
    private lateinit var stageRow1Title: TextView
    private lateinit var stageRow1Name: TextView
    private lateinit var stageRow1Bar: ProgressBar
    private lateinit var stageRow1Value: TextView
    private lateinit var stageRow2Title: TextView
    private lateinit var stageRow2Name: TextView
    private lateinit var stageRow2Bar: ProgressBar
    private lateinit var stageRow2Value: TextView
    private lateinit var stageRow3Title: TextView
    private lateinit var stageRow3Name: TextView
    private lateinit var stageRow3Bar: ProgressBar
    private lateinit var stageRow3Value: TextView
    private lateinit var stageRow4Title: TextView
    private lateinit var stageRow4Name: TextView
    private lateinit var stageRow4Bar: ProgressBar
    private lateinit var stageRow4Value: TextView
    private lateinit var stageRow5Title: TextView
    private lateinit var stageRow5Name: TextView
    private lateinit var stageRow5Bar: ProgressBar
    private lateinit var stageRow5Value: TextView
    private lateinit var stageRow1Container: android.view.View
    private lateinit var stageRow2Container: android.view.View
    private lateinit var stageRow3Container: android.view.View
    private lateinit var stageRow4Container: android.view.View
    private lateinit var stageRow5Container: android.view.View
    private lateinit var autoSwitch: Switch
    private lateinit var nextStageButton: Button
    private lateinit var resetRecipeButton: Button
    private lateinit var recipeSpinner: Spinner
    private lateinit var addRecipeButton: Button
    private lateinit var editRecipeButton: Button
    private lateinit var recipePanel: android.view.View
    private lateinit var weightChart: LineChart

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
    private var recipeModeEnabled = false
    private var graphVisible = false
    private var sampleIndex = 0f
    private var lineDataSet: LineDataSet? = null
    private var chartStartMs = 0L
    private val chartMinWindowSec = 20f

    private data class Stage(
        val name: String,
        val startSec: Int,
        val endSec: Int,
        val targetWeight: Float,
        val note: String
    )

    private data class Recipe(
        val title: String,
        val stages: MutableList<Stage>
    )

    private lateinit var prefs: SharedPreferences
    private val recipes = mutableListOf<Recipe>()
    private lateinit var recipeAdapter: ArrayAdapter<String>
    private var currentRecipeIndex = 0
    private var currentStageIndex = 0
    private val timerTick = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = (System.currentTimeMillis() - timerStartMs) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                timeText.text = "TIME: " + String.format("%02d:%02d", minutes, seconds)
                if (autoSwitch.isChecked) {
                    updateStageByTime(elapsed.toInt())
                }
                handler.postDelayed(this, 1000)
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
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        modeText = findViewById(R.id.modeText)
        timeText = findViewById(R.id.timeText)
        weightText = findViewById(R.id.weightText)
        flowText = findViewById(R.id.flowText)
        batteryText = findViewById(R.id.batteryText)
        connectButton = findViewById(R.id.connectButton)
        tareButton = findViewById(R.id.tareButton)
        timerButton = findViewById(R.id.timerButton)
        recipeToggleButton = findViewById(R.id.recipeToggleButton)
        graphToggleButton = findViewById(R.id.graphToggleButton)
        calZeroButton = findViewById(R.id.calZeroButton)
        calSpanButton = findViewById(R.id.calSpanButton)
        calGetButton = findViewById(R.id.calGetButton)
        recipeTitle = findViewById(R.id.recipeTitle)
        stageRow1Title = findViewById(R.id.stageRow1Title)
        stageRow1Name = findViewById(R.id.stageRow1Name)
        stageRow1Bar = findViewById(R.id.stageRow1Bar)
        stageRow1Value = findViewById(R.id.stageRow1Value)
        stageRow2Title = findViewById(R.id.stageRow2Title)
        stageRow2Name = findViewById(R.id.stageRow2Name)
        stageRow2Bar = findViewById(R.id.stageRow2Bar)
        stageRow2Value = findViewById(R.id.stageRow2Value)
        stageRow3Title = findViewById(R.id.stageRow3Title)
        stageRow3Name = findViewById(R.id.stageRow3Name)
        stageRow3Bar = findViewById(R.id.stageRow3Bar)
        stageRow3Value = findViewById(R.id.stageRow3Value)
        stageRow4Title = findViewById(R.id.stageRow4Title)
        stageRow4Name = findViewById(R.id.stageRow4Name)
        stageRow4Bar = findViewById(R.id.stageRow4Bar)
        stageRow4Value = findViewById(R.id.stageRow4Value)
        stageRow5Title = findViewById(R.id.stageRow5Title)
        stageRow5Name = findViewById(R.id.stageRow5Name)
        stageRow5Bar = findViewById(R.id.stageRow5Bar)
        stageRow5Value = findViewById(R.id.stageRow5Value)
        stageRow1Container = findViewById(R.id.stageRow1Container)
        stageRow2Container = findViewById(R.id.stageRow2Container)
        stageRow3Container = findViewById(R.id.stageRow3Container)
        stageRow4Container = findViewById(R.id.stageRow4Container)
        stageRow5Container = findViewById(R.id.stageRow5Container)
        autoSwitch = findViewById(R.id.autoSwitch)
        nextStageButton = findViewById(R.id.nextStageButton)
        resetRecipeButton = findViewById(R.id.resetRecipeButton)
        recipeSpinner = findViewById(R.id.recipeSpinner)
        addRecipeButton = findViewById(R.id.addRecipeButton)
        editRecipeButton = findViewById(R.id.editRecipeButton)
        recipePanel = findViewById(R.id.recipePanel)
        weightChart = findViewById(R.id.weightChart)
        prefs = getSharedPreferences("scale_prefs", Context.MODE_PRIVATE)

        loadRecipesFromPrefs()
        if (recipes.isEmpty()) {
            recipes.addAll(defaultRecipes())
            saveRecipesToPrefs()
        }

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        scanner = bluetoothAdapter?.bluetoothLeScanner

        connectButton.setOnClickListener {
            if (gatt != null) {
                disconnect()
            } else {
                ensurePermissionsAndScan()
            }
        }
        tareButton.setOnClickListener { sendCommand("TARE") }
        timerButton.setOnClickListener { toggleTimer() }
        recipeToggleButton.setOnClickListener { toggleRecipeMode() }
        graphToggleButton.setOnClickListener { toggleGraph() }
        calZeroButton.setOnClickListener {
            sendCommand("CAL:ZERO")
            showToast("CAL:ZERO sent")
        }
        calSpanButton.setOnClickListener { showCalSpanDialog() }
        calGetButton.setOnClickListener {
            sendCommand("CAL:GET")
            showToast("CAL:GET sent (check Serial)")
        }
        nextStageButton.setOnClickListener { advanceStageManual() }
        resetRecipeButton.setOnClickListener { resetRecipe(true) }
        addRecipeButton.setOnClickListener { showRecipeEditorDialog(isEdit = false) }
        editRecipeButton.setOnClickListener { showRecipeEditorDialog(isEdit = true) }
        autoSwitch.setOnCheckedChangeListener { _, isChecked ->
            nextStageButton.isEnabled = !isChecked && gatt != null
            modeText.text = "MODE: " + if (isChecked) "Auto" else "Manual"
        }

        modeText.text = "MODE: Auto"
        recipePanel.visibility = android.view.View.GONE
        weightChart.visibility = android.view.View.GONE
        setupChart()
        setupRecipeSpinner()
        updateStageUI()

        updateUi("Disconnected")
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) startScan() else updateUi("Permission denied")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanner == null) {
            updateUi("Bluetooth not available")
            return
        }
        updateUi("Scanning...")
        weightText.text = "—"
        batteryText.text = "BAT: --%"

        scanner?.startScan(scanCallback)
        handler.postDelayed({
            stopScan()
            if (gatt == null) updateUi("Not found")
        }, scanTimeoutMs)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: ""
            if (name.equals("Scale", ignoreCase = true)) {
                stopScan()
                updateUi("Connecting...")
                connect(result.device.address)
            }
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
                runOnUiThread { updateUi("Discovering services...") }
                gatt.discoverServices()
            } else {
                runOnUiThread { updateUi("Disconnected") }
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.getService(serviceUuid)
            weightChar = service?.getCharacteristic(weightCharUuid)
            ctrlChar = service?.getCharacteristic(ctrlCharUuid)
            battChar = service?.getCharacteristic(battCharUuid)
            if (weightChar == null || ctrlChar == null) {
                runOnUiThread { updateUi("Characteristic not found") }
                return
            }

            gatt.setCharacteristicNotification(weightChar, true)
            val cccd = weightChar?.getDescriptor(cccdUuid)
            cccd?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)

            runOnUiThread {
                updateUi("Connected")
                handler.removeCallbacks(batteryPollTick)
                readBatteryOnce()
                handler.postDelayed(batteryPollTick, 15000)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == battCharUuid) {
                val raw = characteristic.value ?: return
                val value = String(raw, StandardCharsets.US_ASCII).trim()
                val pct = value.toIntOrNull()
                runOnUiThread {
                    if (pct != null) {
                        batteryText.text = "BAT: ${pct.coerceIn(0, 100)}%"
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == battCharUuid) {
                val raw = characteristic.value ?: return
                val value = String(raw, StandardCharsets.US_ASCII).trim()
                val pct = value.toIntOrNull()
                runOnUiThread {
                    if (pct != null) {
                        batteryText.text = "BAT: ${pct.coerceIn(0, 100)}%"
                    }
                }
            } else if (characteristic.uuid == weightCharUuid) {
                val raw = characteristic.value ?: return
                val filtered = raw.filter { b ->
                    val v = b.toInt() and 0xFF
                    v in 32..126
                }.toByteArray()
                val value = String(filtered, StandardCharsets.US_ASCII).trim()
                if (value.isNotEmpty()) {
                    val parsed = value.replace(',', '.').toFloatOrNull()
                    runOnUiThread {
                        weightText.text = "WEIGHT: $value g"
                        if (parsed != null) {
                            updateFlow(parsed)
                            addChartPoint(parsed)
                            autoSyncTimer(parsed)
                            if (recipeModeEnabled) {
                                if (autoSwitch.isChecked && timerRunning) {
                                    val elapsed = ((System.currentTimeMillis() - timerStartMs) / 1000).toInt()
                                    updateStageByTime(elapsed)
                                }
                                updateStageProgress(parsed)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateUi(status: String) {
        statusText.text = status
        connectButton.text = if (gatt == null) "Connect" else "Disconnect"
        val connected = gatt != null
        tareButton.isEnabled = connected
        timerButton.isEnabled = connected
        recipeToggleButton.isEnabled = connected
        graphToggleButton.isEnabled = connected
        calZeroButton.isEnabled = connected
        calSpanButton.isEnabled = connected
        calGetButton.isEnabled = connected
        nextStageButton.isEnabled = connected && !autoSwitch.isChecked
        resetRecipeButton.isEnabled = connected
        modeText.text = "MODE: " + if (autoSwitch.isChecked) "Auto" else "Manual"
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
        batteryText.text = "BAT: --%"
        updateUi("Disconnected")
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
        if (!timerRunning) startTimer()
        else stopTimer()
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerTick)
        timerButton.text = "Start/Stop"
        sendCommand("TIMER:STOP")
        belowThresholdSinceMs = 0L
    }

    private fun startTimer() {
        timerRunning = true
        timerStartMs = System.currentTimeMillis()
        timerButton.text = "Start/Stop"
        timeText.text = "TIME: 00:00"
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
        sendCommand("TIMER:START")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun defaultRecipes(): List<Recipe> {
        return listOf(
            Recipe(
                "Decaf V60",
                mutableListOf(
                    Stage("Bloom", 0, 40, 50f, "Wet all grounds, wait 40s"),
                    Stage("Pour 1", 40, 75, 180f, "Slow circular pour to 180g"),
                    Stage("Pour 2", 75, 105, 320f, "Finish to 320g, thin stream")
                )
            ),
            Recipe(
                "4:6 (3 pours)",
                mutableListOf(
                    Stage("Pour 1", 0, 30, 60f, "Center pour to 60g"),
                    Stage("Pour 2", 30, 60, 150f, "Circle to 150g"),
                    Stage("Pour 3", 60, 120, 300f, "Finish to 300g")
                )
            ),
            Recipe(
                "Hoffmann V60",
                mutableListOf(
                    Stage("Bloom", 0, 45, 60f, "Bloom 2x dose"),
                    Stage("Main Pour", 45, 120, 300f, "Continuous pour to 300g")
                )
            ),
            Recipe(
                "Tetsu 4:6 (5 pours)",
                mutableListOf(
                    Stage("Pour 1", 0, 30, 50f, "Start sweet"),
                    Stage("Pour 2", 30, 60, 100f, "Balance"),
                    Stage("Pour 3", 60, 90, 160f, "Strength"),
                    Stage("Pour 4", 90, 120, 220f, "Body"),
                    Stage("Pour 5", 120, 150, 300f, "Finish")
                )
            ),
            Recipe(
                "Kalita 155",
                mutableListOf(
                    Stage("Bloom", 0, 30, 40f, "Short bloom"),
                    Stage("Pour 1", 30, 70, 120f, "Steady pour"),
                    Stage("Pour 2", 70, 110, 200f, "Finish")
                )
            ),
            Recipe(
                "Bypass Iced",
                mutableListOf(
                    Stage("Bloom", 0, 30, 40f, "Bloom"),
                    Stage("Pour", 30, 90, 180f, "Brew concentrate"),
                    Stage("Bypass", 90, 90, 300f, "Add ice/water to 300g")
                )
            )
        )
    }

    private fun saveRecipesToPrefs() {
        val root = JSONArray()
        for (recipe in recipes) {
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
        recipes.clear()
        val raw = prefs.getString("recipes_json", null) ?: return
        try {
            val root = JSONArray(raw)
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
                            note = stageObj.optString("note", "")
                        )
                    )
                }
                if (stages.isNotEmpty()) {
                    recipes.add(Recipe(title, stages))
                }
            }
        } catch (_: Exception) {
            recipes.clear()
        }
    }

    private fun showCalSpanDialog() {
        val input = EditText(this).apply {
            hint = "Known weight in grams (e.g. 200)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("Calibration Span")
            .setMessage("Place known weight on scale, then send command.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                val grams = input.text.toString().trim().replace(',', '.').toFloatOrNull()
                if (grams == null || grams <= 0f) {
                    showToast("Invalid weight")
                    return@setPositiveButton
                }
                sendCommand("CAL:SPAN:$grams")
                showToast("CAL:SPAN sent")
            }
            .show()
    }

    private data class StageRowInputs(
        val root: LinearLayout,
        val nameInput: EditText,
        val startInput: EditText,
        val endInput: EditText,
        val targetInput: EditText
    )

    private fun showRecipeEditorDialog(isEdit: Boolean) {
        if (isEdit && recipes.isEmpty()) {
            showToast("No recipe to edit")
            return
        }

        val recipeToEdit = if (isEdit) recipes[currentRecipeIndex] else null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 8)
        }

        val titleInput = EditText(this).apply {
            hint = "Recipe name"
            setText(recipeToEdit?.title ?: "")
        }
        container.addView(titleInput)

        val stageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(stageList)

        val rows = mutableListOf<StageRowInputs>()

        fun addStageRow(defaultName: String, defaultStart: Int, defaultEnd: Int, defaultTarget: Float) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 18, 0, 0)
            }
            val nameInput = EditText(this).apply {
                hint = "Step name"
                setText(defaultName)
            }
            val startInput = EditText(this).apply {
                hint = "Start sec"
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(defaultStart.toString())
            }
            val endInput = EditText(this).apply {
                hint = "End sec"
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(defaultEnd.toString())
            }
            val targetInput = EditText(this).apply {
                hint = "Target g"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(if (defaultTarget % 1f == 0f) defaultTarget.toInt().toString() else defaultTarget.toString())
            }
            val removeButton = Button(this).apply {
                text = "Remove step"
                setOnClickListener {
                    stageList.removeView(row)
                    rows.removeAll { it.root == row }
                }
            }

            row.addView(nameInput)
            row.addView(startInput)
            row.addView(endInput)
            row.addView(targetInput)
            row.addView(removeButton)
            stageList.addView(row)
            rows.add(StageRowInputs(row, nameInput, startInput, endInput, targetInput))
        }

        if (recipeToEdit != null) {
            for (stage in recipeToEdit.stages) {
                addStageRow(stage.name, stage.startSec, stage.endSec, stage.targetWeight)
            }
        } else {
            addStageRow("Step 1", 0, 30, 50f)
        }

        val addStepButton = Button(this).apply {
            text = "Add step"
            setOnClickListener {
                val last = rows.lastOrNull()
                if (last == null) {
                    addStageRow("Step 1", 0, 30, 50f)
                } else {
                    val index = rows.size + 1
                    val prevEnd = last.endInput.text.toString().trim().toIntOrNull() ?: 0
                    val prevTarget = last.targetInput.text.toString().trim().replace(',', '.').toFloatOrNull() ?: 0f
                    addStageRow("Step $index", prevEnd, prevEnd + 30, prevTarget + 50f)
                }
            }
        }
        container.addView(addStepButton)

        val dialogTitle = if (isEdit) "Edit Recipe" else "Add Recipe"
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    showToast("Recipe name is required")
                    return@setPositiveButton
                }
                if (rows.isEmpty()) {
                    showToast("At least one step is required")
                    return@setPositiveButton
                }

                val stages = mutableListOf<Stage>()
                for (row in rows) {
                    val name = row.nameInput.text.toString().trim()
                    val start = row.startInput.text.toString().trim().toIntOrNull()
                    val end = row.endInput.text.toString().trim().toIntOrNull()
                    val target = row.targetInput.text.toString().trim().replace(',', '.').toFloatOrNull()
                    if (name.isEmpty() || start == null || end == null || target == null) {
                        showToast("Invalid step fields")
                        return@setPositiveButton
                    }
                    if (end < start) {
                        showToast("Step end time must be >= start time")
                        return@setPositiveButton
                    }
                    stages.add(Stage(name, start, end, target, ""))
                }

                if (isEdit) {
                    recipes[currentRecipeIndex] = Recipe(title, stages)
                } else {
                    recipes.add(Recipe(title, stages))
                    currentRecipeIndex = recipes.lastIndex
                }
                saveRecipesToPrefs()
                refreshRecipeSpinner()
                resetRecipe(true)
            }
            .show()
    }

    private fun updateStageByTime(elapsedSec: Int) {
        if (!recipeModeEnabled || recipes.isEmpty()) return
        val stages = recipes[currentRecipeIndex].stages
        if (stages.isEmpty()) return
        val idx = stages.indexOfFirst { elapsedSec < it.endSec }
        val newIndex = if (idx == -1) stages.lastIndex + 1 else idx
        if (newIndex != currentStageIndex) {
            currentStageIndex = newIndex
            updateStageUI()
        }
    }

    private fun advanceStageManual() {
        if (!recipeModeEnabled || recipes.isEmpty()) return
        if (!timerRunning) startTimer()
        val stages = recipes[currentRecipeIndex].stages
        if (stages.isEmpty()) return
        if (currentStageIndex <= stages.lastIndex) {
            currentStageIndex += 1
            updateStageUI()
        }
    }

    private fun updateStageUI() {
        if (!recipeModeEnabled || recipes.isEmpty()) {
            sendCommand("STAGE:|0")
            return
        }
        val recipe = recipes[currentRecipeIndex]
        recipeTitle.text = "RECIPE: ${recipe.title}"
        if (currentStageIndex > recipe.stages.lastIndex) {
            setStageRow(stageRow1Container, stageRow1Title, stageRow1Name, stageRow1Bar, stageRow1Value, null, true)
            setStageRow(stageRow2Container, stageRow2Title, stageRow2Name, stageRow2Bar, stageRow2Value, null, false)
            setStageRow(stageRow3Container, stageRow3Title, stageRow3Name, stageRow3Bar, stageRow3Value, null, false)
            setStageRow(stageRow4Container, stageRow4Title, stageRow4Name, stageRow4Bar, stageRow4Value, null, false)
            setStageRow(stageRow5Container, stageRow5Title, stageRow5Name, stageRow5Bar, stageRow5Value, null, false)
            sendCommand("STAGE:|0")
            return
        }
        val current = recipe.stages.getOrNull(currentStageIndex)
        val next1 = recipe.stages.getOrNull(currentStageIndex + 1)
        val next2 = recipe.stages.getOrNull(currentStageIndex + 2)
        val next3 = recipe.stages.getOrNull(currentStageIndex + 3)
        val next4 = recipe.stages.getOrNull(currentStageIndex + 4)
        setStageRow(stageRow1Container, stageRow1Title, stageRow1Name, stageRow1Bar, stageRow1Value, current, true)
        setStageRow(stageRow2Container, stageRow2Title, stageRow2Name, stageRow2Bar, stageRow2Value, next1, false)
        setStageRow(stageRow3Container, stageRow3Title, stageRow3Name, stageRow3Bar, stageRow3Value, next2, false)
        setStageRow(stageRow4Container, stageRow4Title, stageRow4Name, stageRow4Bar, stageRow4Value, next3, false)
        setStageRow(stageRow5Container, stageRow5Title, stageRow5Name, stageRow5Bar, stageRow5Value, next4, false)
        if (current != null) {
            sendCommand("STAGE:${current.name}|${current.targetWeight.toInt()}")
        }
    }

    private fun resetRecipe(stopTimerToo: Boolean) {
        if (stopTimerToo && timerRunning) stopTimer()
        currentStageIndex = 0
        sendCommand("STAGE:|0")
        updateStageUI()
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%d:%02d", m, s)
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
                if (now - belowThresholdSinceMs > 1500) {
                    stopTimer()
                }
            } else {
                belowThresholdSinceMs = 0L
            }
        }
    }

    private fun updateStageProgress(weight: Float) {
        if (!recipeModeEnabled || recipes.isEmpty()) return
        val stages = recipes[currentRecipeIndex].stages
        if (currentStageIndex > stages.lastIndex) return
        val stage = stages[currentStageIndex]
        val weightFrac = if (stage.targetWeight > 0f) {
            (weight / stage.targetWeight).coerceIn(0f, 1f)
        } else 0f
        val progress = (weightFrac * 100f).toInt().coerceIn(0, 100)
        stageRow1Bar.progress = progress
        stageRow1Value.text = "${weight.toInt()}/${stage.targetWeight.toInt()} g"
    }

    private fun setupRecipeSpinner() {
        recipeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, recipes.map { it.title }.toMutableList())
        recipeSpinner.adapter = recipeAdapter
        recipeSpinner.setSelection(currentRecipeIndex)
        recipeSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (position != currentRecipeIndex) {
                    currentRecipeIndex = position
                    resetRecipe(true)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun refreshRecipeSpinner() {
        val titles = recipes.map { it.title }
        recipeAdapter.clear()
        recipeAdapter.addAll(titles)
        recipeAdapter.notifyDataSetChanged()
        if (recipes.isEmpty()) {
            currentRecipeIndex = 0
            return
        }
        currentRecipeIndex = currentRecipeIndex.coerceIn(0, recipes.lastIndex)
        recipeSpinner.setSelection(currentRecipeIndex)
    }

    private fun toggleRecipeMode() {
        recipeModeEnabled = !recipeModeEnabled
        recipePanel.visibility = if (recipeModeEnabled) android.view.View.VISIBLE else android.view.View.GONE
        if (!recipeModeEnabled) {
            resetRecipe(false)
        }
        updateUi(statusText.text.toString())
    }

    private fun updateFlow(weight: Float) {
        val now = System.currentTimeMillis()
        if (lastWeightTime == 0L) {
            lastWeightTime = now
            lastWeight = weight
            flowRate = 0f
            flowText.text = "FLOW: 0.0 g/s"
            return
        }
        val dt = (now - lastWeightTime).coerceAtLeast(50L)
        val dw = weight - lastWeight
        val inst = (dw / dt) * 1000f
        flowRate = (flowRate * 0.7f) + (inst * 0.3f)
        if (flowRate < 0f) flowRate = 0f
        flowText.text = "FLOW: " + String.format("%.1f g/s", flowRate)
        lastWeightTime = now
        lastWeight = weight
    }

    private fun toggleGraph() {
        graphVisible = !graphVisible
        weightChart.visibility = if (graphVisible) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupChart() {
        val dataSet = LineDataSet(mutableListOf(), "Weight (g)")
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 2f
        dataSet.color = 0xFF18D1AE.toInt()
        lineDataSet = dataSet
        weightChart.data = LineData(dataSet)
        weightChart.description.isEnabled = false
        weightChart.legend.isEnabled = false
        weightChart.axisRight.isEnabled = false
        weightChart.axisLeft.axisMinimum = -5f
        weightChart.axisLeft.textColor = 0xFFD6D6D6.toInt()
        weightChart.axisLeft.gridColor = 0x44FFFFFF
        weightChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        weightChart.xAxis.granularity = 1f
        weightChart.xAxis.textColor = 0xFFD6D6D6.toInt()
        weightChart.xAxis.gridColor = 0x33FFFFFF
        weightChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val total = value.toInt().coerceAtLeast(0)
                val min = total / 60
                val sec = total % 60
                return String.format("%d:%02d", min, sec)
            }
        }
        weightChart.xAxis.axisMinimum = 0f
        weightChart.xAxis.axisMaximum = chartMinWindowSec
        weightChart.isDragEnabled = false
        weightChart.setScaleEnabled(false)
        weightChart.isAutoScaleMinMaxEnabled = false
        chartStartMs = System.currentTimeMillis()
        weightChart.invalidate()
    }

    private fun addChartPoint(weight: Float) {
        if (!graphVisible) return
        if (chartStartMs == 0L) chartStartMs = System.currentTimeMillis()
        val data = weightChart.data ?: return
        val set = lineDataSet ?: return
        val x = (System.currentTimeMillis() - chartStartMs) / 1000f
        set.addEntry(Entry(x, weight))
        weightChart.xAxis.axisMaximum = maxOf(chartMinWindowSec, x + 1f)
        data.notifyDataChanged()
        weightChart.notifyDataSetChanged()
        weightChart.invalidate()
    }

    private fun resetChart() {
        sampleIndex = 0f
        lineDataSet?.clear()
        weightChart.data?.notifyDataChanged()
        weightChart.notifyDataSetChanged()
        chartStartMs = System.currentTimeMillis()
        weightChart.invalidate()
    }

    private fun setStageRow(
        container: android.view.View,
        titleView: TextView,
        nameView: TextView,
        barView: ProgressBar,
        valueView: TextView,
        stage: Stage?,
        active: Boolean
    ) {
        if (stage == null) {
            container.visibility = android.view.View.GONE
            return
        }
        container.visibility = android.view.View.VISIBLE
        val label = if (active) "● " else "○ "
        titleView.text = "[STAGE] ${stage.name} (${formatTime(stage.startSec)}–${formatTime(stage.endSec)})  Target: ${stage.targetWeight.toInt()} g"
        nameView.text = label + stage.name
        if (!active) barView.progress = 0
        valueView.text = "0/${stage.targetWeight.toInt()} g"
    }
}
