// ============================================================
//  PillPal — motor.cpp
// ============================================================

#include "motor.h"

// ── Half-step sequence (IN1 IN2 IN3 IN4) ────────────────────
// Each row = one phase. Cycle through 0→7→0 for forward rotation.
// Reversing would cycle 7→0→7 — not needed but trivial to add.
const uint8_t Motor::HALF_STEP_SEQ[8][4] = {
    {1, 0, 0, 0},   // Phase 0
    {1, 1, 0, 0},   // Phase 1
    {0, 1, 0, 0},   // Phase 2
    {0, 1, 1, 0},   // Phase 3
    {0, 0, 1, 0},   // Phase 4
    {0, 0, 1, 1},   // Phase 5
    {0, 0, 0, 1},   // Phase 6
    {1, 0, 0, 1},   // Phase 7
};

// ── Constructor ──────────────────────────────────────────────
Motor::Motor(MotorID id,
             uint8_t in1, uint8_t in2, uint8_t in3, uint8_t in4,
             uint8_t limitPin)
    : _id(id),
      _limitPin(limitPin),
      _state(MotorState::IDLE),
      _currentSlot(0),
      _stepsRemaining(0),
      _homingStepCount(0),
      _lastStepMicros(0),
      _stepInterval(STEP_INTERVAL_US),
      _phase(0)
{
    _pins[0] = in1;
    _pins[1] = in2;
    _pins[2] = in3;
    _pins[3] = in4;
}

// ── begin() ──────────────────────────────────────────────────
void Motor::begin() {
    // Configure motor output pins
    for (uint8_t i = 0; i < 4; i++) {
        pinMode(_pins[i], OUTPUT);
        digitalWrite(_pins[i], LOW);
    }

    // Homing / limit switch removed — motor is assumed homed on boot.
    _state = MotorState::HOMED;

    DBG_F("[Motor:%s] Initialized. Assumed homed.\n", getIDName());
}

// ── tick() — called every loop() ────────────────────────────
bool Motor::tick() {
    // Nothing to do
    if (_state == MotorState::IDLE ||
        _state == MotorState::FAULT) {
        return false;
    }

    // Check if enough time has elapsed since last step
    uint32_t now = micros();
    if ((now - _lastStepMicros) < currentInterval()) {
        return false;  // Not time yet — yield back to loop()
    }
    _lastStepMicros = now;

    // ── DISPENSING state ─────────────────────────────────────
    if (_state == MotorState::DISPENSING) {
        if (_stepsRemaining <= 0) {
            // Move complete
            _state = MotorState::HOMED;
            deenergize();
            Serial.printf("[Motor:%s] Dispense complete. Now at slot %d.\n",
                          getIDName(), _currentSlot);
            return false;
        }

        advancePhase();
        applyPhase();
        _stepsRemaining--;
        return true;
    }

    return false;
}

// ── startHoming() ────────────────────────────────────────────
// Homing/limit switch removed — motor is always assumed homed.
void Motor::startHoming() {
    if (isBusy()) {
        Serial.printf("[Motor:%s] Cannot home — motor is busy.\n", getIDName());
        return;
    }
    _currentSlot = 0;
    _state       = MotorState::HOMED;
    Serial.printf("[Motor:%s] Homing skipped — assumed homed.\n", getIDName());
}

// ── dispense() ───────────────────────────────────────────────
bool Motor::dispense() {
    if (isBusy()) {
        Serial.printf("[Motor:%s] Cannot dispense — motor is busy (%s).\n",
                      getIDName(), getStateName());
        return false;
    }
    if (_state == MotorState::FAULT) {
        Serial.printf("[Motor:%s] Cannot dispense — motor is in FAULT state. Re-home first.\n",
                      getIDName());
        return false;
    }

    // Advance slot counter (wrap 7→ home needed, but re-homing is manual)
    _currentSlot++;
    if (_currentSlot > 7) _currentSlot = 1;  // Safety: clamp to valid range

    _stepsRemaining = STEPS_PER_SLOT;
    _state          = MotorState::DISPENSING;
    _stepInterval   = STEP_INTERVAL_US;

    Serial.printf("[Motor:%s] Dispensing → moving to slot %d (%d steps).\n",
                  getIDName(), _currentSlot, STEPS_PER_SLOT);
    return true;
}

// ── deenergize() ─────────────────────────────────────────────
void Motor::deenergize() {
    for (uint8_t i = 0; i < 4; i++) {
        digitalWrite(_pins[i], LOW);
    }
}

// ── applyPhase() ─────────────────────────────────────────────
void Motor::applyPhase() {
    for (uint8_t i = 0; i < 4; i++) {
        digitalWrite(_pins[i], HALF_STEP_SEQ[_phase][i]);
    }
}

// ── advancePhase() ───────────────────────────────────────────
void Motor::advancePhase() {
    _phase = (_phase + 1) % 8;
}

// ── currentInterval() ────────────────────────────────────────
uint32_t Motor::currentInterval() const {
    return STEP_INTERVAL_US;
}

// ── getStateName() ───────────────────────────────────────────
const char* Motor::getStateName() const {
    switch (_state) {
        case MotorState::IDLE:       return "IDLE";
        case MotorState::HOMING:     return "HOMING";
        case MotorState::HOMED:      return "HOMED";
        case MotorState::DISPENSING: return "DISPENSING";
        case MotorState::FAULT:      return "FAULT";
        default:                     return "UNKNOWN";
    }
}

// ── getIDName() ──────────────────────────────────────────────
const char* Motor::getIDName() const {
    switch (_id) {
        case MotorID::MORNING: return "MORNING";
        case MotorID::MIDDAY:  return "MIDDAY";
        case MotorID::NIGHT:   return "NIGHT";
        default:               return "?";
    }
}
