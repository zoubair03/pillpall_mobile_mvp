#pragma once
// ============================================================
//  PillPal — scheduler.h
//  Dose scheduling logic.
//
//  Design:
//    - Polls RTC every SCHEDULER_CHECK_MS milliseconds
//    - Each dose event (Morning / Midday / Night) has a 'fired'
//      flag that prevents repeat dispense within same minute
//    - Flags reset at midnight (00:00)
//    - Missed dose: if no ACK within missedTimeoutMin → alert
//    - Re-alert every MISSED_REALERT_MIN minutes
//    - ACK clears the missed dose state for that event
// ============================================================

#include <Arduino.h>
#include "config.h"
#include "rtc_manager.h"
#include "motor.h"

// ── Dose types ───────────────────────────────────────────────
enum class DoseType {
    MORNING = 0,
    MIDDAY  = 1,
    NIGHT   = 2
};

// ── Dose event state ─────────────────────────────────────────
struct DoseEvent {
    DoseType    type;
    uint8_t     hour;
    uint8_t     minute;
    bool        fired;          // True if dispensed today already
    bool        pending;        // True if dose time passed while motor was HOMING
                                // → fire as soon as motor reaches HOMED
    bool        acknowledged;   // True if patient confirmed dose taken
    uint32_t    dispenseMillis; // millis() when last dispensed (for timeout)
                                // NOTE: set to actual dispense time, not scheduled time
                                // (device boot delay = device's fault, not patient's)
    bool        missedAlerted;  // True if missed-dose alert is active
    uint32_t    lastAlertMillis;// millis() of last re-alert
    Motor*      motor;          // Pointer to associated motor
    const char* name;           // "Morning" / "Midday" / "Night"
};

// ============================================================
class Scheduler {
public:
    Scheduler(Motor& motorMorning, Motor& motorMidday, Motor& motorNight);

    // Call in setup() — loads dose times from prefsCfg
    void begin();

    // Call every loop() — checks time and fires doses
    void tick();

    // Update dose time (also saves to EEPROM)
    void setDoseTime(DoseType type, uint8_t hh, uint8_t mm);

    // Acknowledge a dose (clears missed-dose alert)
    void acknowledge(DoseType type);
    void acknowledgeAll();

    // Manual dispense trigger
    bool manualDispense(DoseType type);

    // Getters
    const DoseEvent& getEvent(DoseType type) const;
    bool anyMissedDose() const;

    // Minutes until next dose (returns -1 if RTC unavailable)
    int32_t minutesUntilNext() const;
    const DoseEvent* nextEvent() const;

    void printStatus() const;

private:
    DoseEvent _events[3];
    uint32_t  _lastCheckMillis;
    uint8_t   _lastCheckedDay;   // Detects midnight rollover

    void checkAndFire();
    void checkMissedDoses();
    void fireEvent(DoseEvent& ev);
    void loadFromEeprom();
};

extern Scheduler* schedulerPtr;  // Set in main.cpp after construction
