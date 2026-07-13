#pragma once
// ============================================================
//  PillPal — rtc_manager.h
//  DS3231 RTC wrapper with graceful millis() fallback.
//
//  Design:
//    - Uses RTClib for DS3231 on I2C bus (SDA=32, SCL=33)
//    - If DS3231 fails to init → rtcOK = false
//      → time derived from millis() offset from boot (00:00:00)
//    - Provides getTime() returning a simple TimeStruct
//    - setTime() writes to DS3231 if available, else updates offset
//    - isRtcOk() allows callers to know which mode is active
// ============================================================

#include <Arduino.h>
#include <Wire.h>
#include <RTClib.h>
#include "config.h"

// ── Simple time container ────────────────────────────────────
struct TimeStruct {
    uint8_t  hour;
    uint8_t  minute;
    uint8_t  second;
    uint8_t  day;       // 1–31
    uint8_t  month;     // 1–12
    uint16_t year;
    uint8_t  dayOfWeek; // 0=Sunday … 6=Saturday (matches RTClib)

    // Convenience: day-of-week as 1-indexed (1=Mon … 7=Sun)
    // Used for motor slot mapping
    uint8_t slotIndex() const {
        // RTClib: 0=Sun,1=Mon,...,6=Sat
        // We want: 1=Mon,...,7=Sun
        if (dayOfWeek == 0) return 7;  // Sunday → slot 7
        return dayOfWeek;              // Mon=1 … Sat=6
    }
};

// ============================================================
class RtcManager {
public:
    RtcManager();

    // Call in setup() — uses dedicated Wire instance on pins 32,33
    // Returns true if DS3231 found and running
    bool begin();

    // Returns current time (from RTC or millis() fallback)
    TimeStruct getTime();

    // Set the RTC time (also updates fallback offset)
    void setTime(uint8_t h, uint8_t m, uint8_t s,
                 uint8_t day, uint8_t month, uint16_t year);

    // Status
    bool isRtcOk()    const { return _rtcOk; }
    bool isTimeLost() const { return _timeLost; } // RTC lost power, time unreliable

    // Format HH:MM:SS as string (into provided buffer, min 9 chars)
    void formatTime(const TimeStruct& t, char* buf) const;

    // Format DD/MM/YYYY (into provided buffer, min 11 chars)
    void formatDate(const TimeStruct& t, char* buf) const;

    // Day-of-week name (short)
    static const char* dowName(uint8_t dow);

private:
    RTC_DS3231  _rtc;
    TwoWire     _wire;      // Dedicated Wire instance for RTC bus
    bool        _rtcOk;
    bool        _timeLost;

    // millis() fallback state
    uint32_t    _bootMillis;        // millis() at last setTime() call
    uint32_t    _fallbackEpoch;     // Seconds from midnight at last setTime()

    uint32_t currentFallbackSeconds() const;
};

// Singleton — accessible everywhere via rtcMgr
extern RtcManager rtcMgr;
