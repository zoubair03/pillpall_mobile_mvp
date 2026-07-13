#pragma once
// ============================================================
//  PillPal — buzzer.h
//  Non-blocking buzzer melody sequencer using LEDC PWM.
//
//  Design:
//    - Melodies defined as arrays of {frequency, duration_ms}
//    - tick() advances the sequence without any delay()
//    - Supports: dose alert, missed dose alert, ack confirmation,
//      boot jingle, error beep
// ============================================================

#include <Arduino.h>
#include "config.h"

struct Note {
    uint16_t freq;      // Hz (0 = silence/rest)
    uint16_t duration;  // ms
};

// ── Melody IDs ───────────────────────────────────────────────
enum class Melody {
    NONE,
    DOSE_ALERT,     // Pleasant 3-note chime — it's time to take meds
    MISSED_ALERT,   // Urgent repeated beep — missed dose
    ACK_CONFIRM,    // Short positive confirm
    BOOT_JINGLE,    // Startup tune
    ERROR_BEEP      // Single low beep — something is wrong
};

// ============================================================
class Buzzer {
public:
    Buzzer();

    void begin();
    void tick();            // Call every loop()

    void play(Melody m);
    void playDoseTone()   { play(Melody::DOSE_ALERT);   }
    void playMissedTone() { play(Melody::MISSED_ALERT);  }
    void playAck()        { play(Melody::ACK_CONFIRM);   }
    void playBoot()       { play(Melody::BOOT_JINGLE);   }
    void playError()      { play(Melody::ERROR_BEEP);    }
    void stop();

    bool isPlaying() const { return _playing; }

private:
    bool        _playing;
    const Note* _sequence;
    uint8_t     _seqLen;
    uint8_t     _noteIdx;
    uint32_t    _noteStartMs;

    void startNote(uint16_t freq);
    void stopNote();

    // ── Melody definitions (defined in .cpp) ────────────────
    static const Note DOSE_NOTES[];
    static const Note MISSED_NOTES[];
    static const Note ACK_NOTES[];
    static const Note BOOT_NOTES[];
    static const Note ERROR_NOTES[];

    static const uint8_t DOSE_LEN;
    static const uint8_t MISSED_LEN;
    static const uint8_t ACK_LEN;
    static const uint8_t BOOT_LEN;
    static const uint8_t ERROR_LEN;
};

extern Buzzer buzzerMgr;
