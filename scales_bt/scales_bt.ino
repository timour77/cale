#include <Arduino.h>
#include <Wire.h>
#include <U8g2lib.h>
#include <bluefruit.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <nrf_gpio.h>
#include "SparkFun_Qwiic_Scale_NAU7802_Arduino_Library.h"

using namespace Adafruit_LittleFS_Namespace;

const int TARE_BUTTON_PIN = D0;
float calibrationFactor = -895.9;

U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
NAU7802 myScale;

// Signal quality / calibration tuning
const int NORMAL_READ_SAMPLES = 3;      // at 10SPS keeps UI responsive
const int CAL_READ_SAMPLES = 32;        // better averaging for tare/span calibration
const float WEIGHT_JUMP_THRESHOLD_G = 2.0f;
const float FILTER_ALPHA = 0.30f;       // tau ~0.9s at 10SPS/3-sample cycles
const float ZERO_TRACK_BAND_G = 2.0f;   // wide enough to catch thermal warmup drift
const unsigned long ZERO_TRACK_DELAY_MS = 2000;
const float ZERO_TRACK_BETA = 0.05f;    // faster drift compensation
const float INACTIVITY_DELTA_G = 0.2f;
const unsigned long AUTO_OFF_TIMEOUT_MS = 10UL * 60UL * 1000UL;
const unsigned long AFE_RECAL_INTERVAL_MS = 5UL * 60UL * 1000UL;

const uint32_t CAL_STORE_MAGIC = 0x5343414Cu; // "SCAL"
const char* CAL_STORE_FILE = "/scale_cal.bin";

struct CalibrationStore {
  uint32_t magic;
  int32_t manualZero;
  float calibrationFactor;
};

// BLE UUIDs (custom)
const uint8_t SERVICE_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x01,0x00,0x40,0x6e};
const uint8_t WEIGHT_CHAR_UUID[16] = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x03,0x00,0x40,0x6e};
const uint8_t CTRL_CHAR_UUID[16]   = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x02,0x00,0x40,0x6e};
const uint8_t BATT_CHAR_UUID[16]   = {0x9e,0xca,0xdc,0x24,0x0e,0xe5,0xa9,0xe0,0x93,0xf3,0xa3,0xb5,0x04,0x00,0x40,0x6e};

BLEService scaleService(SERVICE_UUID);
BLECharacteristic weightChar(WEIGHT_CHAR_UUID);
BLECharacteristic ctrlChar(CTRL_CHAR_UUID);
BLECharacteristic battChar(BATT_CHAR_UUID);

float displayWeight = 0.0;
long manualZero = 0;
bool timerRunning = false;
unsigned long timerStartMs = 0;
volatile bool tareRequested = false;
volatile bool calZeroRequested = false;
volatile bool calSpanRequested = false;
float calSpanGramsRequested = 0.0f;
unsigned long nearZeroSinceMs = 0;
float inactivityReferenceWeight = 0.0f;
unsigned long lastWeightActivityMs = 0;
bool autoOffArmed = false;
String currentStageName = "";
float currentStageTarget = 0.0f;
unsigned long lastBatteryNotifyMs = 0;

long readRawAverage(int samples) {
  long sum = 0;
  for (int i = 0; i < samples; i++) {
    while (!myScale.available()) delay(1);
    sum += myScale.getReading();
  }
  return sum / samples;
}

long autoTare(int avgSamples, int maxLoops, long stableThreshold) {
  long last = 0;
  int stableCount = 0;

  for (int i = 0; i < maxLoops; i++) {
    long v = readRawAverage(avgSamples);
    if (i > 0 && labs(v - last) <= stableThreshold) stableCount++;
    else stableCount = 0;
    last = v;
    delay(20);
    if (stableCount >= 3) break;
  }
  return last;
}

void handleControlCommand(const uint8_t* data, uint16_t len) {
  if (len == 0) return;
  String cmd;
  cmd.reserve(len);
  for (uint16_t i = 0; i < len; i++) cmd += (char)data[i];
  cmd.trim();

  if (cmd == "TARE") {
    tareRequested = true;
  } else if (cmd == "CAL:ZERO") {
    calZeroRequested = true;
  } else if (cmd.startsWith("CAL:SPAN:")) {
    String gramsStr = cmd.substring(9);
    calSpanGramsRequested = gramsStr.toFloat();
    calSpanRequested = true;
  } else if (cmd == "CAL:GET") {
    sendCalibrationValues();
  } else if (cmd.startsWith("STAGE_SET:")) {
    String stageInfo = cmd.substring(10);
    int colonIdx = stageInfo.indexOf(':');
    if (colonIdx > 0) {
      currentStageName = stageInfo.substring(0, colonIdx);
      currentStageTarget = stageInfo.substring(colonIdx + 1).toFloat();
    }
  }
}

void onCtrlCharWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  handleControlCommand(data, len);
}

void sendCalibrationValues() {
  String response = String(manualZero) + "," + String(calibrationFactor);
  ctrlChar.write(response.c_str(), response.length());
}

void loadCalibration() {
  File file(InternalFS);
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
  File file(InternalFS);
  if (file.open(CAL_STORE_FILE, FILE_O_WRITE)) {
    CalibrationStore store = {CAL_STORE_MAGIC, manualZero, calibrationFactor};
    file.write((uint8_t const *)&store, sizeof(store));
    file.close();
  }
}

void setupBLE() {
  Bluefruit.begin();
  Bluefruit.setTxPower(4);
  Bluefruit.setName("ScaleDevice");

  scaleService.begin();

  weightChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  weightChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  weightChar.setMaxLen(20);
  weightChar.begin();
  weightChar.write32(0);

  ctrlChar.setProperties(CHR_PROPS_WRITE);
  ctrlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  ctrlChar.setMaxLen(50);
  ctrlChar.setWriteCallback(onCtrlCharWrite);
  ctrlChar.begin();

  battChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  battChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  battChar.setMaxLen(20);
  battChar.begin();
  battChar.write32(100);

  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(scaleService);
  Bluefruit.Advertising.addName();
  Bluefruit.Advertising.start(0);
}

void updateDisplay() {
  u8g2.clearBuffer();

  // Large weight number: baseline at y=40 (~2/3 of 64px screen)
  u8g2.setFont(u8g2_font_fub35_tn);
  char wStr[12];
  snprintf(wStr, sizeof(wStr), "%.1f", displayWeight);
  u8g2.setCursor(0, 40);
  u8g2.print(wStr);
  // Small "g" unit right after the number
  u8g2.setFont(u8g2_font_ncenB14_tr);
  u8g2.setCursor(u8g2.getStrWidth(wStr) + 2, 40);
  u8g2.print("g");

  // Bottom strip: timer/stage on the left, battery on the right
  u8g2.setFont(u8g2_font_ncenB08_tr);
  int battPercent = readBatteryPercent();
  u8g2.setCursor(105, 63);
  u8g2.printf("%d%%", battPercent);

  if (timerRunning) {
    unsigned long elapsed = (millis() - timerStartMs) / 1000;
    u8g2.setCursor(0, 63);
    u8g2.printf("%lu:%02lu", elapsed / 60, elapsed % 60);
  } else if (currentStageName.length() > 0) {
    u8g2.setCursor(0, 63);
    u8g2.printf("%.0fg %s", currentStageTarget, currentStageName.c_str());
  }

  u8g2.sendBuffer();
}

int readBatteryPercent() {
  int raw = analogRead(PIN_VBAT);
  float volt = raw * 3.6 / 1024.0;
  int percent = constrain((int)((volt - 3.0) / 1.2 * 100), 0, 100);
  return percent;
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  u8g2.begin();
  u8g2.setFont(u8g2_font_ncenB08_tr);
  u8g2.drawStr(0, 20, "Scale Boot...");
  u8g2.sendBuffer();

  InternalFS.begin();
  loadCalibration();

  Wire.begin();
  if (!myScale.begin()) {
    Serial.println("Scale init failed!");
    u8g2.clearBuffer();
    u8g2.drawStr(0, 20, "Scale FAILED");
    u8g2.sendBuffer();
    while(1);
  }

  myScale.setSampleRate(NAU7802_SPS_10);
  myScale.calibrateAFE();

  pinMode(TARE_BUTTON_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(TARE_BUTTON_PIN), handleTareButton, FALLING);

  setupBLE();

  Serial.println("Scale ready!");
  lastWeightActivityMs = millis();
}

void handleTareButton() {
  tareRequested = true;
}

void loop() {
  long rawReading = readRawAverage(NORMAL_READ_SAMPLES);

  float calibratedWeight = (rawReading - manualZero) / calibrationFactor;

  if (abs(calibratedWeight - displayWeight) > WEIGHT_JUMP_THRESHOLD_G) {
    // Step change (weight placed/removed): snap immediately so zero-tracking
    // doesn't misread a frozen near-zero display as "still empty"
    displayWeight = calibratedWeight;
  } else {
    displayWeight = displayWeight * (1.0f - FILTER_ALPHA) + calibratedWeight * FILTER_ALPHA;
  }

  if (abs(displayWeight) < ZERO_TRACK_BAND_G) {
    if (nearZeroSinceMs == 0) {
      nearZeroSinceMs = millis();
    } else if (millis() - nearZeroSinceMs > ZERO_TRACK_DELAY_MS) {
      manualZero = manualZero * (1.0 - ZERO_TRACK_BETA) + rawReading * ZERO_TRACK_BETA;
    }
  } else {
    nearZeroSinceMs = 0;
  }

  if (abs(displayWeight - inactivityReferenceWeight) > INACTIVITY_DELTA_G) {
    lastWeightActivityMs = millis();
    inactivityReferenceWeight = displayWeight;
  }

  if (tareRequested) {
    manualZero = readRawAverage(CAL_READ_SAMPLES);
    tareRequested = false;
    lastWeightActivityMs = millis();
  }

  if (calZeroRequested) {
    manualZero = readRawAverage(CAL_READ_SAMPLES);
    calZeroRequested = false;
  }

  if (calSpanRequested) {
    long spanRaw = readRawAverage(CAL_READ_SAMPLES);
    calibrationFactor = (spanRaw - manualZero) / calSpanGramsRequested;
    saveCalibration();
    calSpanRequested = false;
  }

  if (timerRunning) {
    // Timer is running, keep display active
  } else if (millis() - lastWeightActivityMs > AUTO_OFF_TIMEOUT_MS) {
    if (!autoOffArmed) {
      autoOffArmed = true;
      // TODO: Power down
    }
  } else {
    autoOffArmed = false;
  }

  // Periodically recalibrate NAU7802 internal analog frontend to compensate
  // for thermal offset drift of the amplifier (~1-3g shift during warmup).
  // Only do it when scale is stable and near zero to avoid disturbing readings.
  static unsigned long lastAfeCalMs = 0;
  if (millis() - lastAfeCalMs > AFE_RECAL_INTERVAL_MS
      && abs(displayWeight) < ZERO_TRACK_BAND_G
      && nearZeroSinceMs != 0
      && millis() - nearZeroSinceMs > ZERO_TRACK_DELAY_MS) {
    myScale.calibrateAFE();
    lastAfeCalMs = millis();
  }

  static unsigned long lastWeightNotifyMs = 0;
  if (millis() - lastWeightNotifyMs > 100) {
    char weightStr[12];
    snprintf(weightStr, sizeof(weightStr), "%.1f", round(displayWeight * 10.0) / 10.0);
    weightChar.notify(weightStr, strlen(weightStr));
    lastWeightNotifyMs = millis();
    lastWeightActivityMs = millis();
  }

  if (millis() - lastBatteryNotifyMs > 30000) {
    char battStr[8];
    snprintf(battStr, sizeof(battStr), "%d", readBatteryPercent());
    battChar.notify(battStr, strlen(battStr));
    lastBatteryNotifyMs = millis();
  }

  updateDisplay();

  delay(10);
}
