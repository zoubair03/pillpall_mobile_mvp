// ============================================================
//  PillPal — scheduler.cpp
// ============================================================

#include "scheduler.h"
#include "prefs_cfg.h"
#include "buzzer.h"

Scheduler* schedulerPtr = nullptr;

// ── Constructor ──────────────────────────────────────────────
Scheduler::Scheduler(Motor& motorMorning, Motor& motorMidday, Motor& motorNight)
    : _lastCheckMillis(0), _lastCheckedDay(255)
{
    //                type             HH  MM  fired  pending  acked  dispMs  missed lastAlMs  motor          name
    _events[0] = { DoseType::MORNING, 8,  0,  false, false,   false, 0,      false, 0,        &motorMorning, "Morning" };
    _events[1] = { DoseType::MIDDAY,  13, 0,  false, false,   false, 0,      false, 0,        &motorMidday,  "Midday"  };
    _events[2] = { DoseType::NIGHT,   21, 0,  false, false,   false, 0,      false, 0,        &motorNight,   "Night"   };
}

// ── begin() ──────────────────────────────────────────────────
void Scheduler::begin() {
    loadFromEeprom();
    Serial.println(F("[Scheduler] Dose schedule loaded:"));
    printStatus();
}

// ── tick() ───────────────────────────────────────────────────
void Scheduler::tick() {
    uint32_t now = millis();
    if (now - _lastCheckMillis < SCHEDULER_CHECK_MS) return;
    _lastCheckMillis = now;

    checkAndFire();
    checkMissedDoses();
}

// ── checkAndFire() ───────────────────────────────────────────
void Scheduler::checkAndFire() {
    TimeStruct t = rtcMgr.getTime();

    // Midnight rollover: reset all fired + pending flags
    if (t.day != _lastCheckedDay) {
        _lastCheckedDay = t.day;
        for (auto& ev : _events) {
            ev.fired        = false;
            ev.pending      = false;
            ev.acknowledged = false;
        }
        Serial.println(F("[Scheduler] New day — dose flags reset."));
    }

    // ── Check scheduled dose times ────────────────────────────
    for (auto& ev : _events) {
        if (!ev.fired &&
            t.hour   == ev.hour &&
            t.minute == ev.minute) {
            fireEvent(ev);
        }
    }

    // ── Fire any pending doses (motor was homing at dose time) ─
    // Once the motor reaches HOMED, fire the queued dose immediately.
    // Timeout counts from actual dispense time (device's fault, not patient's).
    for (auto& ev : _events) {
        if (ev.pending && ev.motor->isHomed()) {
            Serial.printf("[Scheduler] Firing PENDING %s dose (motor now homed).\n", ev.name);
            ev.pending = false;
            bool ok = ev.motor->dispense();
            if (ok) {
                ev.fired          = true;
                ev.acknowledged   = false;
                ev.dispenseMillis = millis();  // ← actual dispense time, not scheduled
                ev.missedAlerted  = false;
                ev.lastAlertMillis = millis();
                if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playDoseTone();
            }
        }
    }
}

// ── fireEvent() ──────────────────────────────────────────────
void Scheduler::fireEvent(DoseEvent& ev) {
    Serial.printf("[Scheduler] >>> DISPENSING %s dose <<<\n", ev.name);

    // Trigger motor
    bool ok = ev.motor->dispense();

    if (ok) {
        ev.fired           = true;
        ev.pending         = false;
        ev.acknowledged    = false;
        ev.dispenseMillis  = millis();
        ev.missedAlerted   = false;
        ev.lastAlertMillis = millis();

        if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playDoseTone();

        Serial.printf("[Scheduler] %s dose fired. ACK within %d min or missed alert activates.\n",
                      ev.name, prefsCfg.get().missedTimeoutMin);
    } else if (ev.motor->isBusy()) {
        // Motor is homing — queue the dose instead of dropping it
        ev.pending = true;
        Serial.printf("[Scheduler] %s motor is HOMING — dose QUEUED, will fire when homed.\n",
                      ev.name);
    } else {
        // Motor in FAULT — cannot queue
        Serial.printf("[Scheduler] ERROR: %s motor in FAULT — dose LOST. Re-home motor!\n",
                      ev.name);
        if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playError();
    }
}

// ── checkMissedDoses() ───────────────────────────────────────
void Scheduler::checkMissedDoses() {
    uint32_t now = millis();
    uint32_t timeoutMs = (uint32_t)prefsCfg.get().missedTimeoutMin * 60000UL;
    uint32_t realertMs = (uint32_t)MISSED_REALERT_MIN * 60000UL;

    for (auto& ev : _events) {
        if (!ev.fired || ev.acknowledged) continue;

        uint32_t elapsed = now - ev.dispenseMillis;

        if (elapsed >= timeoutMs) {
            // Trigger re-alert on interval
            if (!ev.missedAlerted || (now - ev.lastAlertMillis >= realertMs)) {
                ev.missedAlerted   = true;
                ev.lastAlertMillis = now;

                Serial.printf("[Scheduler] ⚠ MISSED DOSE: %s! Please acknowledge.\n", ev.name);

                if (prefsCfg.isBuzzerEnabled()) {
                    buzzerMgr.playMissedTone();
                }
            }
        }
    }
}

// ── setDoseTime() ────────────────────────────────────────────
void Scheduler::setDoseTime(DoseType type, uint8_t hh, uint8_t mm) {
    auto& ev = _events[(int)type];
    ev.hour   = hh;
    ev.minute = mm;

    // Persist
    auto& cfg = prefsCfg.get();
    switch (type) {
        case DoseType::MORNING:
            cfg.morningHH = hh; cfg.morningMM = mm; break;
        case DoseType::MIDDAY:
            cfg.middayHH  = hh; cfg.middayMM  = mm; break;
        case DoseType::NIGHT:
            cfg.nightHH   = hh; cfg.nightMM   = mm; break;
    }
    prefsCfg.save();

    Serial.printf("[Scheduler] %s dose time updated to %02d:%02d\n", ev.name, hh, mm);
}

// ── acknowledge() ────────────────────────────────────────────
void Scheduler::acknowledge(DoseType type) {
    auto& ev = _events[(int)type];
    ev.acknowledged  = true;
    ev.missedAlerted = false;
    Serial.printf("[Scheduler] %s dose acknowledged. ✓\n", ev.name);
}

void Scheduler::acknowledgeAll() {
    for (auto& ev : _events) {
        ev.acknowledged  = true;
        ev.missedAlerted = false;
    }
    Serial.println(F("[Scheduler] All doses acknowledged."));
}

// ── manualDispense() ─────────────────────────────────────────
bool Scheduler::manualDispense(DoseType type) {
    auto& ev = _events[(int)type];
    Serial.printf("[Scheduler] Manual dispense: %s\n", ev.name);

    bool ok = ev.motor->dispense();
    if (ok) {
        ev.fired          = true;
        ev.acknowledged   = false;
        ev.dispenseMillis = millis();
        ev.missedAlerted  = false;
        if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playDoseTone();
    }
    return ok;
}

// ── getEvent() ───────────────────────────────────────────────
const DoseEvent& Scheduler::getEvent(DoseType type) const {
    return _events[(int)type];
}

// ── anyMissedDose() ──────────────────────────────────────────
bool Scheduler::anyMissedDose() const {
    for (const auto& ev : _events) {
        if (ev.missedAlerted) return true;
    }
    return false;
}

// ── nextEvent() ──────────────────────────────────────────────
const DoseEvent* Scheduler::nextEvent() const {
    TimeStruct t = rtcMgr.getTime();
    int32_t nowMin = (int32_t)t.hour * 60 + t.minute;

    const DoseEvent* best = nullptr;
    int32_t bestDelta = INT32_MAX;

    for (const auto& ev : _events) {
        if (ev.fired) continue;
        int32_t evMin   = (int32_t)ev.hour * 60 + ev.minute;
        int32_t delta   = evMin - nowMin;
        if (delta < 0) delta += 1440;  // Wrap past midnight
        if (delta < bestDelta) {
            bestDelta = delta;
            best = &ev;
        }
    }
    return best;
}

// ── minutesUntilNext() ───────────────────────────────────────
int32_t Scheduler::minutesUntilNext() const {
    const DoseEvent* ev = nextEvent();
    if (!ev) return -1;

    TimeStruct t = rtcMgr.getTime();
    int32_t nowMin = (int32_t)t.hour * 60 + t.minute;
    int32_t evMin  = (int32_t)ev->hour * 60 + ev->minute;
    int32_t delta  = evMin - nowMin;
    if (delta < 0) delta += 1440;
    return delta;
}

// ── printStatus() ────────────────────────────────────────────
void Scheduler::printStatus() const {
    for (const auto& ev : _events) {
        Serial.printf("  %-8s %02d:%02d  fired=%d  acked=%d  missed=%d\n",
                      ev.name, ev.hour, ev.minute,
                      ev.fired, ev.acknowledged, ev.missedAlerted);
    }
}

// ── loadFromEeprom() ─────────────────────────────────────────
void Scheduler::loadFromEeprom() {
    const auto& cfg = prefsCfg.get();
    _events[0].hour   = cfg.morningHH;
    _events[0].minute = cfg.morningMM;
    _events[1].hour   = cfg.middayHH;
    _events[1].minute = cfg.middayMM;
    _events[2].hour   = cfg.nightHH;
    _events[2].minute = cfg.nightMM;
}
