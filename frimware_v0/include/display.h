#pragma once
// ============================================================
//  PillPal — display.h
// ============================================================

#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SH110X.h>  // <-- Switched to SH110X library
#include "config.h"

class Motor;
class Scheduler;

enum class Screen { CLOCK, NEXTDOSE, MOTORS };

class Display {
public:
    Display();
    bool begin();
    void tick(Motor& mMorning, Motor& mMidday, Motor& mNight, Scheduler& sched);
    void showBoot(const char* msg);
    void showError(const char* line1, const char* line2 = nullptr);

private:
    Adafruit_SH1106G _oled;   // <-- Using the SH1106 driver
    bool _oledOk;
    Screen _currentScreen;
    uint32_t _lastRefreshMs;
    uint32_t _lastRotateMs;
    uint8_t _screenCount;

    void drawClock();
    void drawNextDose(Scheduler& sched);
    void drawMotors(Motor& mMorning, Motor& mMidday, Motor& mNight);
    void drawAlert(Scheduler& sched);
    void drawFault(Motor& mMorning, Motor& mMidday, Motor& mNight);
    void drawStatusBar();
    void drawCenteredText(const char* text, int16_t y, uint8_t size = 1);
    uint8_t getBatteryPercent();
};

extern Display displayMgr;