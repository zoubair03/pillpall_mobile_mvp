// ============================================================
//  PillPal — buzzer.cpp
// ============================================================

#include "buzzer.h"

Buzzer buzzerMgr;

// ── Melody definitions ───────────────────────────────────────
// Format: {frequency_hz, duration_ms}  — 0 Hz = rest

const Note Buzzer::DOSE_NOTES[]   = {
    {1047, 200}, {0, 50}, {1319, 200}, {0, 50}, {1568, 400}
};
const Note Buzzer::MISSED_NOTES[] = {
    {880, 300}, {0, 100}, {880, 300}, {0, 100}, {880, 600}
};
const Note Buzzer::ACK_NOTES[]    = {
    {1319, 100}, {1568, 200}
};
const Note Buzzer::BOOT_NOTES[]   = {
    {523, 100}, {659, 100}, {784, 100}, {1047, 200}
};
const Note Buzzer::ERROR_NOTES[]  = {
    {300, 600}
};

const uint8_t Buzzer::DOSE_LEN   = sizeof(DOSE_NOTES)   / sizeof(Note);
const uint8_t Buzzer::MISSED_LEN = sizeof(MISSED_NOTES) / sizeof(Note);
const uint8_t Buzzer::ACK_LEN    = sizeof(ACK_NOTES)    / sizeof(Note);
const uint8_t Buzzer::BOOT_LEN   = sizeof(BOOT_NOTES)   / sizeof(Note);
const uint8_t Buzzer::ERROR_LEN  = sizeof(ERROR_NOTES)  / sizeof(Note);

// ── Constructor ──────────────────────────────────────────────
Buzzer::Buzzer()
    : _playing(false), _sequence(nullptr),
      _seqLen(0), _noteIdx(0), _noteStartMs(0)
{}

// ── begin() ──────────────────────────────────────────────────
void Buzzer::begin() {
    // Core 3.x LEDC API: ledcAttach(pin, freq, resolution_bits)
    // Replaces the old ledcSetup(channel,...) + ledcAttachPin(pin, channel)
    // Channel is now assigned automatically by the peripheral manager.
    ledcAttach(PIN_BUZZER, BUZZER_FREQ_HZ, 10);
    stopNote();
    Serial.println(F("[Buzzer] PWM initialized (Core 3.x API)."));
}

// ── tick() ───────────────────────────────────────────────────
void Buzzer::tick() {
    if (!_playing) return;

    uint32_t now = millis();
    uint32_t elapsed = now - _noteStartMs;

    // Wait for current note duration to expire
    if (elapsed < _sequence[_noteIdx].duration) return;

    // Advance to next note
    _noteIdx++;
    if (_noteIdx >= _seqLen) {
        // Melody finished
        stop();
        return;
    }

    startNote(_sequence[_noteIdx].freq);
    _noteStartMs = now;
}

// ── play() ───────────────────────────────────────────────────
void Buzzer::play(Melody m) {
    switch (m) {
        case Melody::DOSE_ALERT:
            _sequence = DOSE_NOTES;   _seqLen = DOSE_LEN;   break;
        case Melody::MISSED_ALERT:
            _sequence = MISSED_NOTES; _seqLen = MISSED_LEN; break;
        case Melody::ACK_CONFIRM:
            _sequence = ACK_NOTES;    _seqLen = ACK_LEN;    break;
        case Melody::BOOT_JINGLE:
            _sequence = BOOT_NOTES;   _seqLen = BOOT_LEN;   break;
        case Melody::ERROR_BEEP:
            _sequence = ERROR_NOTES;  _seqLen = ERROR_LEN;  break;
        default:
            return;
    }

    _noteIdx     = 0;
    _playing     = true;
    _noteStartMs = millis();
    startNote(_sequence[0].freq);
}

// ── stop() ───────────────────────────────────────────────────
void Buzzer::stop() {
    _playing = false;
    stopNote();
}

// ── startNote() ──────────────────────────────────────────────
void Buzzer::startNote(uint16_t freq) {
    if (freq == 0) {
        stopNote();
    } else {
        // Core 3.x: ledcWriteTone(pin, freq) — no channel argument
        ledcWriteTone(PIN_BUZZER, freq);
        ledcWrite(PIN_BUZZER, 512);  // 50% duty cycle
    }
}

// ── stopNote() ───────────────────────────────────────────────
void Buzzer::stopNote() {
    // Core 3.x: ledcWrite(pin, duty) — no channel argument
    ledcWrite(PIN_BUZZER, 0);
}
