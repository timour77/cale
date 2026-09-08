#include <Arduino.h>
#include <Wire.h>
#include <U8g2lib.h>
#include <bluefruit.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <nrf_gpio.h>
#include <nrf_soc.h>
#include "SparkFun_Qwiic_Scale_NAU7802_Arduino_Library.h"

using namespace Adafruit_LittleFS_Namespace;

const int TARE_BUTTON_PIN = D0;
float calibrationFactor = -895.9;

U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
NAU7802 myScale;

// Signal quality / calibration tuning
const int CAL_READ_SAMPLES = 32;        // heavy averaging for tare/span calibration

// The ADC free-runs at 40 SPS and the main loop drains whatever is ready without
// ever blocking, so the display, BLE and the auto-off timer all keep their own
// cadence. Noise is set by the filter time constant, not by how many samples we
// stop and wait for.
const float RAW_FILTER_ALPHA = 0.05f;   // tau ~0.5s at 40 SPS
const float WEIGHT_JUMP_THRESHOLD_G = 2.0f;

// Zero tracking corrects slow thermal drift and nothing else. It used to run with
// a 2g band and a ~6s time constant, which is fast enough to chase the reading
// itself rather than the drift.
const float ZERO_TRACK_BAND_G = 0.5f;
const unsigned long ZERO_TRACK_DELAY_MS = 5000;
const unsigned long ZERO_TRACK_PERIOD_MS = 250;
const float ZERO_TRACK_BETA = 0.01f;    // tau ~25s at the cadence above

// The display shows 0.1g steps, so sub-0.05g wobble is invisible noise that only
// makes the last digit flicker. Hold the reported value until it really moves.
const float REPORT_DEADBAND_G = 0.05f;

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
float reportedWeight = 0.0;

// Double, not long. As an integer this accumulator truncated toward zero on every
// update -- about half a count each time, always the same direction, which is a
// steady one-way drift of roughly 6g/hour rather than the drift compensation it
// was meant to be.
double manualZero = 0.0;
double rawFiltered = 0.0;
bool rawFilterPrimed = false;
unsigned long lastZeroTrackMs = 0;
bool timerRunning = false;
unsigned long timerStartMs = 0;
volatile bool tareRequested = false;
volatile bool calZeroRequested = false;
volatile bool calSpanRequested = false;
float calSpanGramsRequested = 0.0f;
unsigned long nearZeroSinceMs = 0;
float inactivityReferenceWeight = 0.0f;
unsigned long lastWeightActivityMs = 0;
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
  } else if (cmd == "STAGE_CLEAR") {
    currentStageName = "";
    currentStageTarget = 0.0f;
  } else if (cmd == "TIMER:START") {
    // The app owns the brew timer; this only mirrors its state onto the display.
    timerRunning = true;
    timerStartMs = millis();
    lastWeightActivityMs = millis();
  } else if (cmd == "TIMER:STOP") {
    timerRunning = false;
  }
}

void onCtrlCharWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  handleControlCommand(data, len);
}

void sendCalibrationValues() {
  String response = String((long)llround(manualZero)) + "," + String(calibrationFactor);
  ctrlChar.notify(response.c_str(), response.length());
}

void loadCalibration() {
  File file(InternalFS);
  if (file.open(CAL_STORE_FILE, FILE_O_READ)) {
    CalibrationStore store;
    file.read(&store, sizeof(store));
    file.close();

    if (store.magic == CAL_STORE_MAGIC) {
      manualZero = (double)store.manualZero;
      calibrationFactor = store.calibrationFactor;
    }
  }
}

void saveCalibration() {
  File file(InternalFS);
  if (file.open(CAL_STORE_FILE, FILE_O_WRITE)) {
    CalibrationStore store = {CAL_STORE_MAGIC, (int32_t)llround(manualZero), calibrationFactor};
    file.write((uint8_t const *)&store, sizeof(store));
    file.close();
  }
}

/** True while the board is on USB bus power rather than the battery. */
bool usbPowered() {
  uint32_t status = 0;
  if (sd_power_usbregstatus_get(&status) == NRF_SUCCESS) {
    return (status & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
  }
  return (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
}

/**
 * Power the scale down properly: SYSTEM OFF, a couple of microamps, woken by the
 * tare button. It comes back as a cold start, which is fine -- the calibration
 * lives in LittleFS and survives.
 *
 * The wake polarity is read off the button rather than assumed. Whether the pin
 * rests high and the button pulls it to ground or the other way round, DETECT is
 * armed for the opposite of whatever the pin is doing right now, so pressing it
 * always wakes the board. Guessing wrong here means a scale that only a reset can
 * revive.
 */
void enterDeepSleep() {
  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_ncenB08_tr);
  u8g2.drawStr(0, 20, "Sleeping...");
  u8g2.drawStr(0, 40, "press TARE to wake");
  u8g2.sendBuffer();
  delay(600);

  Bluefruit.Advertising.stop();
  myScale.powerDown();
  u8g2.setPowerSave(1);
  delay(50);

  // Latch the button pin as a wake source before the core stops clocking, armed
  // against whichever way it currently rests.
  bool restsHigh = (digitalRead(TARE_BUTTON_PIN) == HIGH);
  nrf_gpio_cfg_sense_input(
      g_ADigitalPinMap[TARE_BUTTON_PIN],
      restsHigh ? NRF_GPIO_PIN_PULLUP : NRF_GPIO_PIN_PULLDOWN,
      restsHigh ? NRF_GPIO_PIN_SENSE_LOW : NRF_GPIO_PIN_SENSE_HIGH);

  sd_power_system_off();
  // Only reached if the SoftDevice declined (e.g. it is not enabled yet).
  NRF_POWER->SYSTEMOFF = 1;
  while (1) delay(100);
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

  ctrlChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
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
  snprintf(wStr, sizeof(wStr), "%.1f", reportedWeight);
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

  myScale.setSampleRate(NAU7802_SPS_40);
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
  unsigned long now = millis();

  // Drain every conversion the ADC has ready. Never wait for one: the old code
  // blocked ~300ms per iteration waiting on three samples, which is why the loop
  // ran at 3Hz and everything else was starved.
  while (myScale.available()) {
    long raw = myScale.getReading();
    if (!rawFilterPrimed) {
      rawFiltered = raw;
      rawFilterPrimed = true;
    } else if (fabs((raw - rawFiltered) / calibrationFactor) > WEIGHT_JUMP_THRESHOLD_G) {
      // Something was placed or removed: snap rather than sliding over a second.
      rawFiltered = raw;
    } else {
      rawFiltered = rawFiltered * (1.0 - RAW_FILTER_ALPHA) + (double)raw * RAW_FILTER_ALPHA;
    }
  }

  // Nothing meaningful to show until the ADC has produced its first conversion.
  if (!rawFilterPrimed) {
    delay(5);
    return;
  }

  displayWeight = (float)((rawFiltered - manualZero) / calibrationFactor);

  // Zero tracking, on its own slow cadence and fed by the *filtered* reading.
  // Feeding it the instantaneous sample, as before, walked the zero around with
  // the noise instead of following the drift.
  if (fabs(displayWeight) < ZERO_TRACK_BAND_G) {
    if (nearZeroSinceMs == 0) {
      nearZeroSinceMs = now;
    } else if (now - nearZeroSinceMs > ZERO_TRACK_DELAY_MS
               && now - lastZeroTrackMs >= ZERO_TRACK_PERIOD_MS) {
      manualZero += (rawFiltered - manualZero) * ZERO_TRACK_BETA;
      lastZeroTrackMs = now;
    }
  } else {
    nearZeroSinceMs = 0;
  }

  // Hold the reported value still until it actually moves, so the last digit
  // stops flickering on a scale that is simply sitting there.
  if (fabs(displayWeight - reportedWeight) >= REPORT_DEADBAND_G) {
    reportedWeight = displayWeight;
  }

  if (fabs(reportedWeight - inactivityReferenceWeight) > INACTIVITY_DELTA_G) {
    lastWeightActivityMs = now;
    inactivityReferenceWeight = reportedWeight;
  }

  // readRawAverage blocks for the better part of a second, so `now` is stale
  // afterwards. Anything comparing against it must be given a fresh reading, or
  // the timestamps end up in the future and unsigned subtraction wraps.
  if (tareRequested) {
    manualZero = (double)readRawAverage(CAL_READ_SAMPLES);
    rawFiltered = manualZero;
    reportedWeight = 0.0f;
    tareRequested = false;
    now = millis();
    lastWeightActivityMs = now;
    inactivityReferenceWeight = 0.0f;
  }

  if (calZeroRequested) {
    manualZero = (double)readRawAverage(CAL_READ_SAMPLES);
    rawFiltered = manualZero;
    calZeroRequested = false;
    now = millis();
    lastWeightActivityMs = now;
  }

  if (calSpanRequested) {
    long spanRaw = readRawAverage(CAL_READ_SAMPLES);
    calibrationFactor = (spanRaw - manualZero) / calSpanGramsRequested;
    saveCalibration();
    calSpanRequested = false;
    now = millis();
    lastWeightActivityMs = now;
  }

  // Auto-off. A running brew counts as activity; sending a BLE notification does
  // not, which is what used to reset this timer several times a second and made
  // the timeout unreachable.
  // Signed difference on purpose: if a timestamp ever ends up ahead of `now`,
  // this reads as negative rather than as four billion milliseconds. Powering the
  // scale off is not something to do on an arithmetic accident.
  // Never power down while on USB. That is exactly when the board is being
  // flashed or watched, it is the situation where a failed wake is most annoying,
  // and there is no battery to save. On USB the timeout simply does not apply.
  if (timerRunning) {
    lastWeightActivityMs = now;
  } else if (!usbPowered()
             && (int32_t)(now - lastWeightActivityMs) > (int32_t)AUTO_OFF_TIMEOUT_MS) {
    enterDeepSleep();
  }

  // Periodically recalibrate the NAU7802 analog frontend to compensate for its
  // thermal offset drift. This shifts the raw scale underneath us, so the zero
  // has to be re-established afterwards or the correction shows up as a step.
  static unsigned long lastAfeCalMs = 0;
  if (now - lastAfeCalMs > AFE_RECAL_INTERVAL_MS
      && fabs(displayWeight) < ZERO_TRACK_BAND_G
      && nearZeroSinceMs != 0
      && now - nearZeroSinceMs > ZERO_TRACK_DELAY_MS) {
    myScale.calibrateAFE();
    manualZero = (double)readRawAverage(CAL_READ_SAMPLES);
    rawFiltered = manualZero;
    now = millis();
    lastAfeCalMs = now;
  }

  static unsigned long lastWeightNotifyMs = 0;
  if (now - lastWeightNotifyMs > 100) {
    char weightStr[12];
    snprintf(weightStr, sizeof(weightStr), "%.1f", round(reportedWeight * 10.0) / 10.0);
    weightChar.notify(weightStr, strlen(weightStr));
    lastWeightNotifyMs = now;
  }

  if (now - lastBatteryNotifyMs > 30000) {
    char battStr[8];
    snprintf(battStr, sizeof(battStr), "%d", readBatteryPercent());
    battChar.notify(battStr, strlen(battStr));
    lastBatteryNotifyMs = now;
  }

  // The OLED is a 1KB frame over I2C; redrawing it on every 5ms pass would spend
  // most of the loop talking to it for no visible gain.
  static unsigned long lastDisplayMs = 0;
  if (now - lastDisplayMs > 100) {
    updateDisplay();
    lastDisplayMs = now;
  }

  delay(5);
}
