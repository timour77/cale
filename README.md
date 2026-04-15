# Scale - Smart Coffee Scale

A Bluetooth-enabled smart scale for precise coffee brewing with recipe support, real-time weight tracking, and flow rate analysis.

## Components

### Android App (`android-app/`)
Companion mobile application written in Kotlin that connects to the scale via BLE and displays:
- Real-time weight (grams)
- Elapsed brew time
- Flow rate (g/s)
- Battery percentage
- Multi-stage recipe progress tracking
- Historical weight graph
- Calibration controls

**Requirements:**
- Android 6.0+ (API 23)
- Gradle 8.2.2
- Kotlin 1.9.22
- Java 17

### Arduino Firmware (`*.ino`)
Embedded firmware running on ESP32/nRF52 based devices:

- **scales_bt.ino** - Main variant with full BLE and recipe support
- **scales.ino** - Simplified variant without BLE (local display only)
- **m5stickc_plus2_ble_display.ino** - Optimized variant for M5StickC Plus2

**Features:**
- NAU7802 load cell ADC integration
- BLE Bluetooth Low Energy communication
- Signal filtering and jump detection
- Zero-tracking and auto-tare
- Calibration storage (zero and span)
- Multi-stage recipe support
- Battery monitoring
- 10-minute auto-off timeout

## Hardware Requirements

- **MCU:** nRF52840 (Adafruit or similar) or ESP32 with compatible pin layout
- **Load Cell ADC:** NAU7802 on Qwiic connector
- **Display:** SSD1306 OLED 128x64 (optional for scales_bt.ino)
- **Battery:** Lithium with voltage monitoring via ADC

## Quick Start

### Building Android App

```bash
cd android-app
./gradlew :app:assembleDebug        # Build APK
./gradlew :app:installDebug         # Install to device
./gradlew test                      # Run unit tests
```

### Uploading Arduino Firmware

1. Install Arduino IDE and required board support packages
2. Install dependencies via Arduino Library Manager:
   - Adafruit Bluefruit nRF52 Libraries
   - SparkFun Qwiic Scale NAU7802
   - U8G2
3. Open desired `.ino` file and upload to connected device

## BLE Protocol

**Service UUID:** `9ecadc24-0ee5-a9e0-93f3-a3b501006e40`

**Characteristics:**
- Weight (0x03): Float weight in grams (notify)
- Control (0x02): Command strings (write)
  - `TARE` - Set tare point
  - `CAL:ZERO` - Calibrate zero
  - `CAL:SPAN:GRAMS` - Calibrate span with known weight
  - `CAL:GET` - Retrieve calibration values
- Battery (0x04): Battery percentage (notify)

## Troubleshooting

- **Scale not found:** Check I2C address (default 0x2A), verify Qwiic wiring
- **Erratic readings:** Ensure stable power supply, check load cell wiring polarity
- **BLE connection issues:** Verify Bluetooth permissions on Android 6.0+
- **Display blank:** Check U8G2 initialization and I2C clock speed

## Documentation

See `CLAUDE.md` for detailed architecture and development guidance.

## License

Proprietary - Timur's Project
