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
- Custom service UUID: `9ecadc24-0ee5-a9e0-93f3-a3b501006e` (16 bytes)
- **Weight characteristic** (0x03): Float weight in grams (readable, notify)
- **Control characteristic** (0x02): Command strings (writable) — `TARE`, `CAL:ZERO`, `CAL:SPAN:1000.0`, etc.
- **Battery characteristic** (0x04): Percentage as integer (readable, notify)

### Arduino Firmware

**Main Files:**
- `scales_bt.ino` — Primary firmware with BLE service, weight measurement, and recipe/timer logic
- `scales.ino` — Legacy variant
- `m5stickc_plus2_ble_display.ino` — Display-focused variant for M5StickC Plus2

**Key Components:**
- **NAU7802 Driver:** ADC-based weight sensor with gain-of-128 load cell interface
- **Calibration:** Manual zero/span calibration stored in flash (`/scale_cal.bin`)
- **Filtering:** IIR low-pass filter (alpha=0.10) with jump detection (2.0g threshold) and zero-tracking
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
4. **Subscribe:** `setNotificationEnabled()` on weight and battery for auto-updates
5. **Communicate:** `characteristic.setValue()` + `writeCharacteristic()` for commands; notifications trigger UI updates

### Recipe System

Recipes are stored as JSON in SharedPreferences under key `"recipes"`. Each recipe is a JSON object:
```json
{
  "name": "V60",
  "stages": [
    {"name": "Bloom", "target": 50.0, "type": "weight"},
    {"name": "Pour 1", "target": 150.0, "type": "weight"}
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
- **Arduino Board:** Firmware targets nRF52840 (Adafruit/M5StickC Plus2); different pins required for other boards
- **NAU7802 I2C:** Scale operates at I2C address 0x2A on default Qwiic connector; verify wiring if not detecting scale on boot
