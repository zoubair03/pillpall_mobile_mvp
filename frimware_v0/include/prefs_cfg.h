#pragma once
// ============================================================
//  PillPal — prefs_cfg.h
//  Persistent configuration via ESP32 Preferences (NVS).
//
//  Why Preferences over EEPROM emulation:
//    - Power-loss safe: transactional writes, CRC per entry
//    - Wear leveling: built into NVS partition
//    - No magic bytes needed: isKey() detects first run
//    - Typed API: getUChar, getBool, getString — no raw casting
//    - Native string support: WiFi SSID/pass without fixed buffers
//
//  Namespace layout (all under "pillpal"):
//    morningHH / morningMM   — morning dose time
//    middayHH  / middayMM    — midday dose time
//    nightHH   / nightMM     — night dose time
//    missedTout               — missed dose timeout (minutes)
//    flags                    — bitmask (buzzer, wifi enabled)
//    wifiSsid / wifiPass      — WiFi credentials
//    deviceUid / pairingCode — set once via POST /api/provision at
//                              manufacturing time; empty on an
//                              unprovisioned unit. Never generated
//                              on-device — must match a real backend
//                              devices row, so they only ever arrive
//                              from the provisioning step.
// ============================================================

#include <Arduino.h>
#include <Preferences.h>
#include "config.h"

// ── Config structure (in-RAM mirror of NVS) ─────────────────
struct PillPalConfig {
    uint8_t  morningHH;
    uint8_t  morningMM;
    uint8_t  middayHH;
    uint8_t  middayMM;
    uint8_t  nightHH;
    uint8_t  nightMM;
    uint8_t  missedTimeoutMin;
    uint8_t  flags;             // Bitmask: see FLAG_* in config.h
    char     wifiSsid[64];      // Longer than EEPROM version — NVS handles it
    char     wifiPass[64];
    char     deviceUid[24];     // Set once via POST /api/provision
    char     pairingCode[8];    // Set once via POST /api/provision
};

// ============================================================
class PrefsConfig {
public:
    PrefsConfig();

    // Call in setup() — loads NVS or writes defaults on first run
    void begin();

    // Persist current _cfg to NVS (only changed keys are written)
    void save();

    // Reset to factory defaults and save
    void factoryReset();

    // In-RAM config access
    PillPalConfig&       get()       { return _cfg; }
    const PillPalConfig& get() const { return _cfg; }

    // Flag helpers
    bool isBuzzerEnabled() const;
    bool isWifiEnabled()   const;
    void setBuzzerEnabled(bool en);
    void setWifiEnabled(bool en);

    void printConfig() const;

private:
    PillPalConfig _cfg;
    Preferences   _prefs;

    void writeDefaults();
    void loadFromNvs();
};

// Singleton — used everywhere via prefsCfg
extern PrefsConfig prefsCfg;
