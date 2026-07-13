#pragma once
// ============================================================
//  PillPal — motor.h
//  Non-blocking stepper motor driver for 28BYJ-48 via ULN2003.
//
//  Design:
//    - Half-step sequence (8 phases) for max torque + smooth motion
//    - All timing via micros() — zero delay(), zero blocking
//    - State machine: IDLE → HOMING → HOMED → DISPENSING → IDLE
//    - Tracks current slot (0=Home, 1–7=Mon–Sun)
//    - Fault detection: limit switch not found within HOMING_MAX_STEPS
// ============================================================

#include <Arduino.h>
#include "config.h" 

// ── Motor states ─────────────────────────────────────────────
enum class MotorState {
    IDLE,           // Coils de-energized, waiting
    HOMING,         // Spinning to find limit switch (Home position)
    HOMED,          // Home found, slot=0, ready to dispense
    DISPENSING,     // Moving to next slot
    FAULT           // Homing failed or step overrun
};

// ── Motor identifiers ────────────────────────────────────────
enum class MotorID {
    MORNING,
    MIDDAY,
    NIGHT
};

// ============================================================
class Motor {
public:
    // ---------------------------------------------------------
    // Constructor — pass the 4 ULN2003 control pins + limit switch pin
    Motor(MotorID id,
          uint8_t in1, uint8_t in2, uint8_t in3, uint8_t in4,
          uint8_t limitPin);

    // ---------------------------------------------------------
    // Call once in setup()
    void begin();

    // ---------------------------------------------------------
    // Call every loop() — executes one step if interval elapsed
    // Returns true if a step was taken this tick
    bool tick();

    // ---------------------------------------------------------
    // Start homing sequence (non-blocking)
    // Safe to call even if already homed — will re-home
    void startHoming();

    // ---------------------------------------------------------
    // Advance one slot forward (dispense one compartment)
    // Only valid when state == HOMED or IDLE after homing
    // Returns false if motor is busy or in FAULT
    bool dispense();

    // ---------------------------------------------------------
    // Getters
    MotorState  getState()       const { return _state; }
    uint8_t     getCurrentSlot() const { return _currentSlot; }
    MotorID     getID()          const { return _id; }
    bool        isBusy()         const { return _state == MotorState::HOMING ||
                                                _state == MotorState::DISPENSING; }
    bool        isHomed()        const { return _state == MotorState::HOMED ||
                                                _state == MotorState::IDLE; }
    bool        isFault()        const { return _state == MotorState::FAULT; }
    const char* getStateName()   const;
    const char* getIDName()      const;

    // ---------------------------------------------------------
    // Force de-energize coils (saves power when idle)
    void deenergize();

private:
    // Identity
    MotorID _id;

    // GPIO pins
    uint8_t _pins[4];       // IN1..IN4 on ULN2003
    uint8_t _limitPin;      // Limit switch (LOW = triggered)

    // State machine
    MotorState  _state;
    uint8_t     _currentSlot;       // 0=Home, 1–7=Mon–Sun
    int32_t     _stepsRemaining;    // Steps left in current move
    uint32_t    _homingStepCount;   // Steps taken during homing (fault guard)

    // Timing
    uint32_t    _lastStepMicros;    // micros() at last step
    uint32_t    _stepInterval;      // Current interval (dispensing vs homing)

    // Half-step phase (0–7 cycles through 8-phase sequence)
    uint8_t     _phase;

    // ── Half-step sequence for 28BYJ-48 ─────────────────────
    // 8 phases, each activates a combination of coils A B C D
    // Produces smooth micro-stepping with good torque
    static const uint8_t HALF_STEP_SEQ[8][4];

    // ── Internal helpers ─────────────────────────────────────
    void     applyPhase();          // Write current _phase to pins
    void     advancePhase();        // Increment _phase (0→1→...→7→0)
    bool     limitTriggered() const;// Read limit switch (active LOW)
    uint32_t currentInterval() const;
};
