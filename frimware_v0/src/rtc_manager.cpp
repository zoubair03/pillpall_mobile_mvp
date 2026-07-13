// ============================================================
//  PillPal — rtc_manager.cpp
// ============================================================

#include "rtc_manager.h"

// Singleton definition
RtcManager rtcMgr;

// ── Constructor ──────────────────────────────────────────────
RtcManager::RtcManager()
    : _wire(1),          // Wire1 = second I2C peripheral on ESP32
      _rtcOk(false),
      _timeLost(false),
      _bootMillis(0),
      _fallbackEpoch(0)
{}

// ── begin() ──────────────────────────────────────────────────
bool RtcManager::begin() {
    // Initialize dedicated Wire bus for RTC (pins 32, 33)
    _wire.begin(PIN_I2C_SDA_RTC, PIN_I2C_SCL_RTC);

    if (!_rtc.begin(&_wire)) {
        Serial.println(F("[RTC] ERROR: DS3231 not found on I2C bus (SDA=32, SCL=33)"));
        Serial.println(F("[RTC] Falling back to millis() timekeeping (time starts at 00:00:00)"));
        _rtcOk       = false;
        _bootMillis  = millis();
        _fallbackEpoch = 0;  // Start at midnight 00:00:00
        return false;
    }

    // Check if the RTC lost power (battery died, first run, etc.)
    if (_rtc.lostPower()) {
        Serial.println(F("[RTC] WARNING: RTC lost power — time is not set!"));
        Serial.println(F("[RTC] Use: SET TIME HH:MM:SS and SET DATE DD/MM/YYYY"));
        _timeLost = true;
        // Set a sane default so the system doesn't fire doses at garbage times
        _rtc.adjust(DateTime(2024, 1, 1, 0, 0, 0));
    }

    _rtcOk = true;
    TimeStruct t = getTime();
    char tbuf[9], dbuf[11];
    formatTime(t, tbuf);
    formatDate(t, dbuf);
    Serial.printf("[RTC] DS3231 OK. Current time: %s %s (%s)\n",
                  dbuf, tbuf, dowName(t.dayOfWeek));
    return true;
}

// ── getTime() ────────────────────────────────────────────────
TimeStruct RtcManager::getTime() {
    TimeStruct ts;

    if (_rtcOk) {
        DateTime now = _rtc.now();
        ts.hour      = now.hour();
        ts.minute    = now.minute();
        ts.second    = now.second();
        ts.day       = now.day();
        ts.month     = now.month();
        ts.year      = now.year();
        ts.dayOfWeek = now.dayOfTheWeek();  // 0=Sun … 6=Sat
    } else {
        // millis() fallback — derive HH:MM:SS from elapsed time
        uint32_t elapsed = (millis() - _bootMillis) / 1000UL;
        uint32_t total   = _fallbackEpoch + elapsed;

        ts.second    = total % 60;
        ts.minute    = (total / 60) % 60;
        ts.hour      = (total / 3600) % 24;
        ts.day       = 1;
        ts.month     = 1;
        ts.year      = 2024;
        ts.dayOfWeek = 1;  // Assume Monday in fallback mode
    }

    return ts;
}

// ── setTime() ────────────────────────────────────────────────
void RtcManager::setTime(uint8_t h, uint8_t m, uint8_t s,
                          uint8_t day, uint8_t month, uint16_t year) {
    if (_rtcOk) {
        _rtc.adjust(DateTime(year, month, day, h, m, s));
        _timeLost = false;
        Serial.printf("[RTC] Time updated: %04d/%02d/%02d %02d:%02d:%02d\n",
                      year, month, day, h, m, s);
    } else {
        // Update fallback offset
        _bootMillis    = millis();
        _fallbackEpoch = (uint32_t)h * 3600UL + (uint32_t)m * 60UL + s;
        Serial.printf("[RTC] Fallback time updated: %02d:%02d:%02d\n", h, m, s);
    }
}

// ── formatTime() ─────────────────────────────────────────────
void RtcManager::formatTime(const TimeStruct& t, char* buf) const {
    snprintf(buf, 9, "%02d:%02d:%02d", t.hour, t.minute, t.second);
}

// ── formatDate() ─────────────────────────────────────────────
void RtcManager::formatDate(const TimeStruct& t, char* buf) const {
    snprintf(buf, 11, "%02d/%02d/%04d", t.day, t.month, t.year);
}

// ── dowName() ────────────────────────────────────────────────
const char* RtcManager::dowName(uint8_t dow) {
    static const char* names[] = {
        "Sunday", "Monday", "Tuesday", "Wednesday",
        "Thursday", "Friday", "Saturday"
    };
    if (dow > 6) return "???";
    return names[dow];
}

// ── currentFallbackSeconds() ────────────────────────────────
uint32_t RtcManager::currentFallbackSeconds() const {
    return _fallbackEpoch + (millis() - _bootMillis) / 1000UL;
}
