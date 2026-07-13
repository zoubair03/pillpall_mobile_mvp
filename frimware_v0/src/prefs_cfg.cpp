// ============================================================
//  PillPal — prefs_cfg.cpp
//  Preferences (NVS) based persistent config.
// ============================================================

#include "prefs_cfg.h"

// NVS namespace — all keys live under "pillpal"
static const char* NVS_NS = "pillpal";

// Singleton definition
PrefsConfig prefsCfg;

// ── Constructor ──────────────────────────────────────────────
PrefsConfig::PrefsConfig() {
    memset(&_cfg, 0, sizeof(_cfg));
}

// ── begin() ──────────────────────────────────────────────────
void PrefsConfig::begin() {
    // Open namespace read-only first to check if it exists
    _prefs.begin(NVS_NS, true);  // true = read-only
    bool firstRun = !_prefs.isKey("morningHH");
    _prefs.end();

    if (firstRun) {
        Serial.println(F("[Prefs] First run — writing factory defaults to NVS."));
        writeDefaults();
        save();
    } else {
        Serial.println(F("[Prefs] NVS config found. Loading..."));
        loadFromNvs();
    }

    printConfig();
}

// ── loadFromNvs() ────────────────────────────────────────────
void PrefsConfig::loadFromNvs() {
    _prefs.begin(NVS_NS, true);  // read-only

    _cfg.morningHH        = _prefs.getUChar("morningHH",  DEFAULT_MORNING_HH);
    _cfg.morningMM        = _prefs.getUChar("morningMM",  DEFAULT_MORNING_MM);
    _cfg.middayHH         = _prefs.getUChar("middayHH",   DEFAULT_MIDDAY_HH);
    _cfg.middayMM         = _prefs.getUChar("middayMM",   DEFAULT_MIDDAY_MM);
    _cfg.nightHH          = _prefs.getUChar("nightHH",    DEFAULT_NIGHT_HH);
    _cfg.nightMM          = _prefs.getUChar("nightMM",    DEFAULT_NIGHT_MM);
    _cfg.missedTimeoutMin = _prefs.getUChar("missedTout", DEFAULT_MISSED_TIMEOUT_MIN);
    _cfg.flags            = _prefs.getUChar("flags",      (1 << FLAG_BUZZER_ENABLED));

    // String keys — getStr(key, default) handles missing gracefully
    String ssid = _prefs.getString("wifiSsid", "");
    String pass = _prefs.getString("wifiPass",  "");
    strncpy(_cfg.wifiSsid, ssid.c_str(), sizeof(_cfg.wifiSsid) - 1);
    strncpy(_cfg.wifiPass, pass.c_str(), sizeof(_cfg.wifiPass) - 1);

    String deviceUid    = _prefs.getString("deviceUid", "");
    String pairingCode  = _prefs.getString("pairingCode", "");
    strncpy(_cfg.deviceUid, deviceUid.c_str(), sizeof(_cfg.deviceUid) - 1);
    strncpy(_cfg.pairingCode, pairingCode.c_str(), sizeof(_cfg.pairingCode) - 1);

    _prefs.end();
}

// ── save() ───────────────────────────────────────────────────
// Preferences only writes a key if the value has actually changed
// (NVS does value comparison before erasing flash). This means
// calling save() frequently is safe — flash is only erased when
// something genuinely changed.
void PrefsConfig::save() {
    _prefs.begin(NVS_NS, false);  // false = read-write

    _prefs.putUChar("morningHH",  _cfg.morningHH);
    _prefs.putUChar("morningMM",  _cfg.morningMM);
    _prefs.putUChar("middayHH",   _cfg.middayHH);
    _prefs.putUChar("middayMM",   _cfg.middayMM);
    _prefs.putUChar("nightHH",    _cfg.nightHH);
    _prefs.putUChar("nightMM",    _cfg.nightMM);
    _prefs.putUChar("missedTout", _cfg.missedTimeoutMin);
    _prefs.putUChar("flags",      _cfg.flags);
    _prefs.putString("wifiSsid",  _cfg.wifiSsid);
    _prefs.putString("wifiPass",  _cfg.wifiPass);
    _prefs.putString("deviceUid",   _cfg.deviceUid);
    _prefs.putString("pairingCode", _cfg.pairingCode);

    _prefs.end();
    Serial.println(F("[Prefs] Config saved to NVS."));
}

// ── factoryReset() ───────────────────────────────────────────
void PrefsConfig::factoryReset() {
    Serial.println(F("[Prefs] Factory reset — clearing NVS namespace."));

    // Clear the entire namespace, then rewrite defaults
    _prefs.begin(NVS_NS, false);
    _prefs.clear();
    _prefs.end();

    writeDefaults();
    save();
    Serial.println(F("[Prefs] Factory reset complete."));
}

// ── writeDefaults() ──────────────────────────────────────────
void PrefsConfig::writeDefaults() {
    _cfg.morningHH        = DEFAULT_MORNING_HH;
    _cfg.morningMM        = DEFAULT_MORNING_MM;
    _cfg.middayHH         = DEFAULT_MIDDAY_HH;
    _cfg.middayMM         = DEFAULT_MIDDAY_MM;
    _cfg.nightHH          = DEFAULT_NIGHT_HH;
    _cfg.nightMM          = DEFAULT_NIGHT_MM;
    _cfg.missedTimeoutMin = DEFAULT_MISSED_TIMEOUT_MIN;
    _cfg.flags            = (1 << FLAG_BUZZER_ENABLED); // Buzzer on, WiFi off by default
    memset(_cfg.wifiSsid, 0, sizeof(_cfg.wifiSsid));
    memset(_cfg.wifiPass, 0, sizeof(_cfg.wifiPass));

    // deviceUid/pairingCode deliberately NOT reset here: on a genuine
    // first boot _cfg is already zeroed (constructor), so they start
    // empty regardless; on a factoryReset() (serial-console only, see
    // serial_cmd.cpp), leaving them untouched means the in-RAM values
    // loaded before the reset survive being re-saved afterward —
    // identity should only ever change via a deliberate re-provision,
    // not an accidental config wipe.
}

// ── Flag helpers ─────────────────────────────────────────────
bool PrefsConfig::isBuzzerEnabled() const {
    return (_cfg.flags >> FLAG_BUZZER_ENABLED) & 1;
}
bool PrefsConfig::isWifiEnabled() const {
    return (_cfg.flags >> FLAG_WIFI_ENABLED) & 1;
}
void PrefsConfig::setBuzzerEnabled(bool en) {
    if (en) _cfg.flags |=  (1 << FLAG_BUZZER_ENABLED);
    else    _cfg.flags &= ~(1 << FLAG_BUZZER_ENABLED);
}
void PrefsConfig::setWifiEnabled(bool en) {
    if (en) _cfg.flags |=  (1 << FLAG_WIFI_ENABLED);
    else    _cfg.flags &= ~(1 << FLAG_WIFI_ENABLED);
}

// ── printConfig() ────────────────────────────────────────────
void PrefsConfig::printConfig() const {
    Serial.println(F("┌──────────────────────────────────────┐"));
    Serial.println(F("│         PillPal Current Config        │"));
    Serial.println(F("├──────────────────────────────────────┤"));
    Serial.printf( "│  Morning dose  : %02d:%02d                │\n",
                   _cfg.morningHH, _cfg.morningMM);
    Serial.printf( "│  Midday dose   : %02d:%02d                │\n",
                   _cfg.middayHH,  _cfg.middayMM);
    Serial.printf( "│  Night dose    : %02d:%02d                │\n",
                   _cfg.nightHH,   _cfg.nightMM);
    Serial.printf( "│  Missed timeout: %d min               │\n",
                   _cfg.missedTimeoutMin);
    Serial.printf( "│  Buzzer        : %-3s                  │\n",
                   isBuzzerEnabled() ? "ON" : "OFF");
    Serial.printf( "│  WiFi SSID     : %-20s  │\n",
                   strlen(_cfg.wifiSsid) ? _cfg.wifiSsid : "(not set)");
    Serial.printf( "│  Device UID    : %-20s  │\n",
                   strlen(_cfg.deviceUid) ? _cfg.deviceUid : "(unprovisioned)");
    Serial.println(F("└──────────────────────────────────────┘"));
}
