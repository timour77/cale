// Legacy scales.ino - simplified weight measurement without BLE
#include <Arduino.h>
#include <Wire.h>
#include <U8g2lib.h>
#include "SparkFun_Qwiic_Scale_NAU7802_Arduino_Library.h"

U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);
NAU7802 myScale;

const int TARE_BUTTON_PIN = D0;
const float FILTER_ALPHA = 0.10f;
float calibrationFactor = -895.9;
long manualZero = 0;
float displayWeight = 0.0;

void setup() {
  Serial.begin(115200);
  delay(500);

  u8g2.begin();
  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_ncenB08_tr);
  u8g2.drawStr(0, 20, "Initializing...");
  u8g2.sendBuffer();

  Wire.begin();
  if (!myScale.begin()) {
    Serial.println("NAU7802 not found!");
    u8g2.clearBuffer();
    u8g2.drawStr(0, 20, "Scale Failed");
    u8g2.sendBuffer();
    while(1);
  }

  myScale.setSampleRate(NAU7802_SPS_10);
  myScale.calibrateAFE();

  pinMode(TARE_BUTTON_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(TARE_BUTTON_PIN), onTare, FALLING);

  Serial.println("Scale initialized");
}

void onTare() {
  manualZero = readRawAverage(10);
}

long readRawAverage(int samples) {
  long sum = 0;
  for (int i = 0; i < samples; i++) {
    while (!myScale.available()) delay(1);
    sum += myScale.getReading();
  }
  return sum / samples;
}

void loop() {
  long raw = readRawAverage(3);
  float calibrated = (raw - manualZero) / calibrationFactor;

  displayWeight = displayWeight * (1.0 - FILTER_ALPHA) + calibrated * FILTER_ALPHA;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_ncenB10_tr);
  u8g2.setCursor(20, 40);
  u8g2.printf("%.1f g", displayWeight);
  u8g2.sendBuffer();

  Serial.printf("Weight: %.2f g\n", displayWeight);

  delay(100);
}
