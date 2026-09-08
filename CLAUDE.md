# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Scale** is a smart scale companion app project with two main components:

1. **Android App** (`android-app/`): Kotlin-based companion mobile application that connects to a Bluetooth-enabled scale device
2. **Arduino Firmware** (`*.ino` files): Embedded firmware running on an M5StickC Plus2 or similar ESP32-based device with a NAU7802 weight sensor

The Android app communicates with the scale via **BLE (Bluetooth Low Energy)**, displaying real-time weight, supporting multi-stage brewing recipes, timers, and historical graphing. The scale firmware handles weight measurement, calibration, battery management, and BLE communication.

## Architecture

### Android App (`android-app/`)

**Structure:**
- `app/src/main/java/com/example/scale/MainActivity.kt` — Single activity app handling all UI logic and BLE communication
- `app/src/main/res/layout/activity_main.xml` — UI layout: status displays, control buttons, recipe panel, weight chart
- `app/build.gradle` — App module configuration (SDK level 34, Kotlin 1.9.22, Java 17)

**Key Features:**
- **BLE Connection:** Scans for and connects to scale device using Android BLE APIs
- **Real-time Display:** Shows weight, elapsed time, flow rate (g/s), battery percentage
- **Recipes:** Multi-stage brewing instructions (up to 5 stages) with progress tracking via progress bars
- **Graphing:** LineChart (MPAndroidChart library) for weight history visualization
- **Calibration:** UI for zero and span calibration commands sent to scale
- **Tare/Timer:** Per-brew tare, start/stop timer functionality
- **SharedPreferences:** Local storage for recipes and app state
- **Permissions:** Requires Bluetooth and location permissions (Android 6.0+)

**BLE Protocol:**

`app/src/main/java/com/example/scale/scale/ScaleProtocol.kt` is the specification — read it rather
than a restatement here, which is how the two sides drifted apart in the first place. The firmware's
`handleControlCommand` is the second implementation of the same spec, and
`ScaleProtocolTest` is the contract between them: **change a byte in one, change it in both, in the
same commit.**

In outline: Nordic UART UUIDs (`6e40000X-b5a3-f393-e0a9-e50e24dcca9e`) carrying **ASCII text**, not
binary. Weight (0x03) and battery (0x04) notify; control (0x02) takes command writes and notifies
calibration replies back. Commands are values of `ScaleCommand`, events are values of `ScaleEvent`,
and nothing outside `ScaleProtocol` should build or parse a frame.

### Arduino Firmware

**Main Files:**
- `scales_bt.ino` — Primary firmware with BLE service, weight measurement, and recipe/timer logic
- `scales.ino` — Legacy variant
- `m5stickc_plus2_ble_display.ino` — Display-focused variant for M5StickC Plus2

**Key Components:**
- **NAU7802 Driver:** ADC-based weight sensor with gain-of-128 load cell interface
- **Calibration:** Manual zero/span calibration stored in flash (`/scale_cal.bin`)
- **Filtering:** IIR low-pass filter (alpha=0.30) with jump detection (2.0g threshold) and zero-tracking
- **BLE Notifications:** Sends weight and battery updates at ~10 SPS (samples per second)
- **Control Handler:** Parses incoming commands (TARE, CAL:ZERO, CAL:SPAN, STAGE_SET, etc.)
- **Auto-off:** 10-minute inactivity timeout
- **Battery Monitoring:** Analog read of battery voltage via nRF52 ADC

## Common Development Tasks

### Building the Android App

```bash
cd android-app

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease

# Build and install to connected device/emulator
./gradlew :app:installDebug

# Full build (compiles and packages)
./gradlew build
```

### Running Tests

```bash
cd android-app

# Run all unit tests
./gradlew test

# Run tests for a single test class
./gradlew test --tests com.example.scale.SomeTestClass
```

### Building the Firmware

```bash
# Compile (does not flash)
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840 scales_bt

# Compile and flash to a connected board
arduino-cli upload --fqbn Seeeduino:nrf52:xiaonRF52840 -p /dev/cu.usbmodem* scales_bt
```

Requires the `Seeeduino:nrf52` core plus the `U8g2` and
`SparkFun Qwiic Scale NAU7802 Arduino Library` libraries.

### Cleaning Build Artifacts

```bash
cd android-app
./gradlew clean
```

### Debugging

- Use Android Studio (IDE) for full debugging experience with breakpoints and logcat viewing
- Connect device via USB or use Android Emulator
- Logcat output: `adb logcat | grep scale` (filter for app logs)

## Key Implementation Details

### MainActivity Initialization

The app uses a single `onCreate` which:
1. Initializes all UI views from `activity_main.xml`
2. Sets up BLE adapter and scanner with proper permission checks
3. Registers connect button listener to start BLE scan
4. Loads recipes from SharedPreferences
5. Sets up recipe/graph toggle buttons to show/hide panels

### BLE Connection Flow

1. **Scan:** `BluetoothLeScanner.startScan()` with callback
2. **Connect:** On device found, call `device.connectGatt()`
3. **Discover:** `BluetoothGattCallback.onServicesDiscovered()` finds weight/control/battery characteristics
4. **Subscribe:** enable notifications on weight, battery, and control (for calibration replies). Android allows one outstanding GATT operation, so descriptor writes are queued and drained one per `onDescriptorWrite`
5. **Communicate:** `characteristic.setValue()` + `writeCharacteristic()` for commands; notifications trigger UI updates

### Recipe System

Recipes are stored as JSON in SharedPreferences under key `"recipes_json"`. Each recipe is a JSON
object. Stage targets are **cumulative**, not per-stage:
```json
{
  "title": "V60",
  "stages": [
    {"name": "Bloom", "startSec": 0, "endSec": 40, "targetWeight": 50.0, "note": "Wet all grounds"},
    {"name": "Pour 1", "startSec": 40, "endSec": 75, "targetWeight": 180.0, "note": "Slow circular pour"}
  ]
}
```

During brewing, the app tracks current stage index and updates progress bars and target weight display.

### Weight Filtering & Calibration

**Arduino Side:**
- Raw ADC reading → calibration factor applied → IIR filter → jump detection → display
- Calibration factor stored as float in flash after CAL:SPAN command
- Zero offset (manual tare point) also stored and applied at ADC read time

**Android Side:**
- Receives filtered weight via BLE notification
- Further smooths via exponential moving average if needed
- Displays with 1 decimal place precision

### Load Cell & NAU7802

- 16-bit ADC, internal PGA (up to gain 128), differential input
- Requires load cell amplification circuit (typically 350Ω or 400Ω bridge)
- Calibration: zero point established via TARE, then span calibration with known weight
- Sensitivity calibration factor calculated as: `ADC_counts / grams` (typically negative due to differential bridge polarity)

## Important Notes

- **Permissions:** BLE and location permissions must be requested at runtime (Android 6.0+) and declared in AndroidManifest.xml
- **ANR Timeout:** Long BLE operations must run on background thread to avoid "Application Not Responding" dialog
- **SharedPreferences:** Recipe persistence uses default SharedPreferences; consider migration if adding a database layer
- **Emulator BLE:** Android Emulator has limited BLE support; use real device for reliable testing
- **Gradle Version:** Project uses Gradle 8.2.2 with Kotlin 1.9.22; ensure gradle wrapper is up-to-date
- **Arduino Board:** Firmware targets the Seeed XIAO nRF52840 (`Seeeduino:nrf52:xiaonRF52840`); different pins required for other boards
- **NAU7802 I2C:** Scale operates at I2C address 0x2A on default Qwiic connector; verify wiring if not detecting scale on boot
