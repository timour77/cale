// M5StickC Plus2 variant with optimized display handling
#include <Arduino.h>
#include <Wire.h>
#include <bluefruit.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include "SparkFun_Qwiic_Scale_NAU7802_Arduino_Library.h"

using namespace Adafruit_LittleFS_Namespace;

NAU7802 myScale;
const int TARE_BUTTON_PIN = D0;
const float FILTER_ALPHA = 0.10f;
const float ZERO_TRACK_BETA = 0.02f;
const unsigned long ZERO_TRACK_DELAY_MS = 5000;

float calibrationFactor = -895.9;
long manualZero = 0;
float displayWeight = 0.0;
bool timerRunning = false;
unsigned long timerStartMs = 0;

// BLE UUIDs
const uint8_t SERVICE_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x01,0x00,0x40,0x6e};
const uint8_t WEIGHT_CHAR_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x03,0x00,0x40,0x6e};
const uint8_t CTRL_CHAR_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x02,0x00,0x40,0x6e};
const uint8_t BATT_CHAR_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x04,0x00,0x40,0x6e};

BLEService scaleService(SERVICE_UUID);
BLECharacteristic weightChar(WEIGHT_CHAR_UUID);
BLECharacteristic ctrlChar(CTRL_CHAR_UUID);
BLECharacteristic battChar(BATT_CHAR_UUID);

const uint32_t CAL_STORE_MAGIC = 0x5343414Cu;
const char* CAL_STORE_FILE = "/scale_cal.bin";

struct CalibrationStore {
  uint32_t magic;
  int32_t manualZero;
  float calibrationFactor;
};

volatile bool tareRequested = false;
volatile bool calZeroRequested = false;
volatile bool calSpanRequested = false;
float calSpanGramsRequested = 0.0f;
unsigned long nearZeroSinceMs = 0;
unsigned long lastWeightActivityMs = 0;

long readRawAverage(int samples) {
  long sum = 0;
  for (int i = 0; i < samples; i++) {
    while (!myScale.available()) delay(1);
    sum += myScale.getReading();
  }
  return sum / samples;
}

void onCtrlCharWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  if (len == 0) return;
  String cmd;
  for (uint16_t i = 0; i < len; i++) cmd += (char)data[i];
  cmd.trim();

  if (cmd == "TARE") {
    tareRequested = true;
  } else if (cmd == "CAL:ZERO") {
    calZeroRequested = true;
  } else if (cmd.startsWith("CAL:SPAN:")) {
    calSpanGramsRequested = cmd.substring(9).toFloat();
    calSpanRequested = true;
  } else if (cmd == "START_TIMER") {
    timerRunning = true;
    timerStartMs = millis();
  } else if (cmd == "STOP_TIMER") {
    timerRunning = false;
  }
}

void loadCalibration() {
  File file(InternalFileSystem);
  if (file.open(CAL_STORE_FILE, FILE_O_READ)) {
    CalibrationStore store;
    file.read(&store, sizeof(store));
    file.close();
    if (store.magic == CAL_STORE_MAGIC) {
      manualZero = store.manualZero;
      calibrationFactor = store.calibrationFactor;
    }
  }
}

void saveCalibration() {
  File file(InternalFileSystem);
  if (file.open(CAL_STORE_FILE, FILE_O_WRITE)) {
    CalibrationStore store = {CAL_STORE_MAGIC, manualZero, calibrationFactor};
    file.write(&store, sizeof(store));
    file.close();
  }
}

void setupBLE() {
  Bluefruit.begin();
  Bluefruit.setTxPower(4);
  Bluefruit.setName("M5Scale");

  scaleService.begin();

  weightChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  weightChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  weightChar.setMaxLen(20);
  weightChar.begin();

  ctrlChar.setProperties(CHR_PROPS_WRITE);
  ctrlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  ctrlChar.setMaxLen(50);
  ctrlChar.setWriteCallback(onCtrlCharWrite);
  ctrlChar.begin();

  battChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  battChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  battChar.setMaxLen(20);
  battChar.begin();

  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISCOVERABLE_MODE);
  Bluefruit.Advertising.addTxPowerLevel();
  Bluefruit.Advertising.addService(scaleService);
  Bluefruit.Advertising.addName();
  Bluefruit.Advertising.start(0);
}

void setup() {
  Serial.begin(115200);
  delay(500);

  InternalFileSystem.begin();
  loadCalibration();

  Wire.begin();
  if (!myScale.begin()) {
    Serial.println("Scale init failed!");
    while(1);
  }

  myScale.setSampleRate(NAU7802_SPS_10);
  myScale.calibrateAFE();

  pinMode(TARE_BUTTON_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(TARE_BUTTON_PIN), []() { tareRequested = true; }, FALLING);

  setupBLE();

  Serial.println("M5StickC Scale Ready");
  lastWeightActivityMs = millis();
}

void loop() {
  long raw = readRawAverage(3);
  float calibrated = (raw - manualZero) / calibrationFactor;

  if (abs(calibrated - displayWeight) <= 2.0f) {
    displayWeight = displayWeight * (1.0 - FILTER_ALPHA) + calibrated * FILTER_ALPHA;
  }

  if (abs(displayWeight) < 0.6f) {
    if (nearZeroSinceMs == 0) {
      nearZeroSinceMs = millis();
    } else if (millis() - nearZeroSinceMs > ZERO_TRACK_DELAY_MS) {
      manualZero = manualZero * (1.0 - ZERO_TRACK_BETA) + raw * ZERO_TRACK_BETA;
    }
  } else {
    nearZeroSinceMs = 0;
  }

  if (tareRequested) {
    manualZero = readRawAverage(32);
    tareRequested = false;
    lastWeightActivityMs = millis();
  }

  if (calZeroRequested) {
    manualZero = readRawAverage(32);
    calZeroRequested = false;
  }

  if (calSpanRequested) {
    long spanRaw = readRawAverage(32);
    calibrationFactor = (spanRaw - manualZero) / calSpanGramsRequested;
    saveCalibration();
    calSpanRequested = false;
  }

  static unsigned long lastNotifyMs = 0;
  if (millis() - lastNotifyMs > 100) {
    float toSend = round(displayWeight * 10.0) / 10.0;
    uint8_t bytes[4];
    memcpy(bytes, &toSend, 4);
    weightChar.write(bytes, 4);
    lastNotifyMs = millis();
  }

  if (timerRunning) {
    unsigned long elapsed = (millis() - timerStartMs) / 1000;
    Serial.printf("Weight: %.1f g | Timer: %lu s\n", displayWeight, elapsed);
  } else {
    Serial.printf("Weight: %.1f g\n", displayWeight);
  }

  delay(10);
}
