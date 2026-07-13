// ============================================================
//  PillPal — serial_cmd.cpp
// ============================================================

#include "serial_cmd.h"
#include "motor.h"
#include "scheduler.h"
#include "rtc_manager.h"
#include "prefs_cfg.h"
#include "buzzer.h"
#include "web_server.h"

// ── Constructor ──────────────────────────────────────────────
SerialCmd::SerialCmd(Motor& mMorning, Motor& mMidday, Motor& mNight, Scheduler& sched)
    : _mMorning(mMorning), _mMidday(mMidday), _mNight(mNight), _sched(sched),
      _bufLen(0)
{
    memset(_buf, 0, sizeof(_buf));
}

// ── begin() ──────────────────────────────────────────────────
void SerialCmd::begin() {
    Serial.println(F("\n========================================"));
    Serial.println(F("  PillPal Serial Console v" PILLPAL_VERSION));
    Serial.println(F("  Type HELP for command list"));
    Serial.println(F("========================================\n"));
}

// ── tick() ───────────────────────────────────────────────────
void SerialCmd::tick() {
    while (Serial.available()) {
        char c = (char)Serial.read();

        if (c == '\r') continue;  // Ignore carriage return

        if (c == '\n') {
            _buf[_bufLen] = '\0';
            if (_bufLen > 0) {
                Serial.printf("> %s\n", _buf);
                dispatch(_buf);
            }
            _bufLen = 0;
            memset(_buf, 0, sizeof(_buf));
            return;
        }

        if (_bufLen < sizeof(_buf) - 1) {
            _buf[_bufLen++] = toupper(c);
        }
    }
}

// ── dispatch() ───────────────────────────────────────────────
void SerialCmd::dispatch(char* cmd) {
    // Skip leading spaces
    while (*cmd == ' ') cmd++;

    if      (strncmp(cmd, "HELP",     4)  == 0) cmdHelp();
    else if (strncmp(cmd, "STATUS",   6)  == 0) cmdStatus();
    else if (strncmp(cmd, "CONFIG",   6)  == 0) cmdConfig();
    else if (strncmp(cmd, "SET ",     4)  == 0) cmdSet(cmd + 4);
    else if (strncmp(cmd, "DISPENSE", 8)  == 0) cmdDispense(cmd + 8);
    else if (strncmp(cmd, "HOME",     4)  == 0) cmdHome(cmd + 4);
    else if (strncmp(cmd, "ACK",      3)  == 0) cmdAck(cmd + 3);
    else if (strncmp(cmd, "WIFI",     4)  == 0) cmdWifi(cmd + 4);
    else if (strncmp(cmd, "BUZZER",   6)  == 0) cmdBuzzer(cmd + 6);
    else if (strncmp(cmd, "RESET",    5)  == 0) cmdReset();
    else {
        Serial.printf("[CMD] Unknown command: '%s'. Type HELP.\n", cmd);
    }
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdHelp() {
    Serial.println(F("\n── PillPal Commands ──────────────────────────────────"));
    Serial.println(F("  HELP                        This list"));
    Serial.println(F("  STATUS                      Full system status"));
    Serial.println(F("  CONFIG                      Show saved config"));
    Serial.println(F("  SET MORNING HH:MM           Change morning dose time"));
    Serial.println(F("  SET MIDDAY  HH:MM           Change midday dose time"));
    Serial.println(F("  SET NIGHT   HH:MM           Change night dose time"));
    Serial.println(F("  SET TIME    HH:MM:SS        Set RTC time"));
    Serial.println(F("  SET DATE    DD/MM/YYYY      Set RTC date"));
    Serial.println(F("  SET TIMEOUT <minutes>       Missed dose timeout (5-120)"));
    Serial.println(F("  DISPENSE MORNING|MIDDAY|NIGHT   Manual dispense"));
    Serial.println(F("  HOME ALL                    Re-home all motors"));
    Serial.println(F("  HOME MORNING|MIDDAY|NIGHT   Re-home one motor"));
    Serial.println(F("  ACK                         Acknowledge all missed doses"));
    Serial.println(F("  ACK MORNING|MIDDAY|NIGHT    Acknowledge one"));
    Serial.println(F("  WIFI <SSID> <PASSWORD>      Connect to WiFi"));
    Serial.println(F("  BUZZER ON|OFF               Enable/disable buzzer"));
    Serial.println(F("  RESET                       Factory reset EEPROM"));
    Serial.println(F("──────────────────────────────────────────────────────\n"));
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdStatus() {
    TimeStruct t = rtcMgr.getTime();
    char tbuf[9], dbuf[11];
    rtcMgr.formatTime(t, tbuf);
    rtcMgr.formatDate(t, dbuf);

    printSeparator();
    Serial.println(F("  PillPal System Status"));
    printSeparator();
    Serial.printf("  Time     : %s %s (%s)\n", dbuf, tbuf, RtcManager::dowName(t.dayOfWeek));
    Serial.printf("  RTC      : %s\n", rtcMgr.isRtcOk() ? "DS3231 OK" : "FALLBACK (millis)");

    // WiFi
    extern bool wifiConnected;
    extern String wifiIP;
    Serial.printf("  WiFi     : %s  IP: %s\n",
                  wifiConnected ? "Connected" : "AP Mode",
                  wifiIP.c_str());

    // Motors
    Motor* motors[] = { &_mMorning, &_mMidday, &_mNight };
    const char* mNames[] = { "Morning", "Midday ", "Night  " };
    Serial.println(F("  Motors:"));
    for (int i = 0; i < 3; i++) {
        Serial.printf("    %-8s  State=%-12s  Slot=%d\n",
                      mNames[i], motors[i]->getStateName(), motors[i]->getCurrentSlot());
    }

    // Scheduler
    Serial.println(F("  Schedule:"));
    _sched.printStatus();

    // Battery
    int raw = analogRead(PIN_BATTERY);
    float v = (raw / (float)BATTERY_ADC_MAX) * BATTERY_VREF / BATTERY_DIV_RATIO;
    Serial.printf("  Battery  : %.2fV  (raw ADC=%d)\n", v, raw);

    printSeparator();
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdConfig() {
    prefsCfg.printConfig();
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdSet(char* args) {
    while (*args == ' ') args++;

    if (strncmp(args, "MORNING ", 8) == 0) {
        uint8_t hh, mm;
        if (parseHHMM(args + 8, hh, mm)) {
            _sched.setDoseTime(DoseType::MORNING, hh, mm);
        }
    }
    else if (strncmp(args, "MIDDAY ", 7) == 0) {
        uint8_t hh, mm;
        if (parseHHMM(args + 7, hh, mm)) {
            _sched.setDoseTime(DoseType::MIDDAY, hh, mm);
        }
    }
    else if (strncmp(args, "NIGHT ", 6) == 0) {
        uint8_t hh, mm;
        if (parseHHMM(args + 6, hh, mm)) {
            _sched.setDoseTime(DoseType::NIGHT, hh, mm);
        }
    }
    else if (strncmp(args, "TIME ", 5) == 0) {
        uint8_t hh, mm, ss;
        if (parseHHMMSS(args + 5, hh, mm, ss)) {
            TimeStruct t = rtcMgr.getTime();
            rtcMgr.setTime(hh, mm, ss, t.day, t.month, t.year);
            Serial.printf("[CMD] Time set to %02d:%02d:%02d\n", hh, mm, ss);
        }
    }
    else if (strncmp(args, "DATE ", 5) == 0) {
        uint8_t dd, mo; uint16_t yr;
        if (parseDDMMYYYY(args + 5, dd, mo, yr)) {
            TimeStruct t = rtcMgr.getTime();
            rtcMgr.setTime(t.hour, t.minute, t.second, dd, mo, yr);
            Serial.printf("[CMD] Date set to %02d/%02d/%04d\n", dd, mo, yr);
        }
    }
    else if (strncmp(args, "TIMEOUT ", 8) == 0) {
        int mins = atoi(args + 8);
        if (mins >= 5 && mins <= 120) {
            prefsCfg.get().missedTimeoutMin = (uint8_t)mins;
            prefsCfg.save();
            Serial.printf("[CMD] Missed dose timeout set to %d min\n", mins);
        } else {
            Serial.println(F("[CMD] ERROR: timeout must be 5–120 minutes"));
        }
    }
    else {
        Serial.println(F("[CMD] Unknown SET target. Use: MORNING, MIDDAY, NIGHT, TIME, DATE, TIMEOUT"));
    }
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdDispense(char* args) {
    while (*args == ' ') args++;
    bool found;
    DoseType dt = doseByName(args, found);
    if (!found) {
        Serial.println(F("[CMD] Usage: DISPENSE MORNING|MIDDAY|NIGHT"));
        return;
    }
    bool ok = _sched.manualDispense(dt);
    if (!ok) Serial.println(F("[CMD] Dispense failed — motor busy or in FAULT."));
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdHome(char* args) {
    while (*args == ' ') args++;

    if (strcmp(args, "ALL") == 0) {
        _mMorning.startHoming();
        _mMidday.startHoming();
        _mNight.startHoming();
        Serial.println(F("[CMD] Homing all 3 motors..."));
    } else {
        Motor* m = motorByName(args);
        if (!m) {
            Serial.println(F("[CMD] Usage: HOME ALL|MORNING|MIDDAY|NIGHT"));
            return;
        }
        m->startHoming();
    }
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdAck(char* args) {
    while (*args == ' ') args++;

    if (strlen(args) == 0 || strcmp(args, "ALL") == 0) {
        _sched.acknowledgeAll();
        buzzerMgr.playAck();
    } else {
        bool found;
        DoseType dt = doseByName(args, found);
        if (found) {
            _sched.acknowledge(dt);
            buzzerMgr.playAck();
        } else {
            Serial.println(F("[CMD] Usage: ACK [MORNING|MIDDAY|NIGHT|ALL]"));
        }
    }
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdWifi(char* args) {
    while (*args == ' ') args++;

    // Parse: SSID PASSWORD (space separated; SSID cannot contain spaces)
    char* ssid = args;
    char* pass = strchr(args, ' ');

    if (!pass) {
        Serial.println(F("[CMD] Usage: WIFI <SSID> <PASSWORD>"));
        return;
    }

    *pass = '\0';  // Null-terminate SSID
    pass++;
    while (*pass == ' ') pass++;

    strncpy(prefsCfg.get().wifiSsid, ssid, 31);
    strncpy(prefsCfg.get().wifiPass, pass, 31);
    prefsCfg.get().wifiSsid[31] = '\0';
    prefsCfg.get().wifiPass[31] = '\0';
    prefsCfg.setWifiEnabled(true);
    prefsCfg.save();

    Serial.printf("[CMD] WiFi credentials saved. SSID='%s'\n", ssid);
    Serial.println(F("[CMD] Reboot to connect."));
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdBuzzer(char* args) {
    while (*args == ' ') args++;
    if (strcmp(args, "ON") == 0) {
        prefsCfg.setBuzzerEnabled(true);
        prefsCfg.save();
        Serial.println(F("[CMD] Buzzer enabled."));
        buzzerMgr.playAck();
    } else if (strcmp(args, "OFF") == 0) {
        prefsCfg.setBuzzerEnabled(false);
        prefsCfg.save();
        Serial.println(F("[CMD] Buzzer disabled."));
    } else {
        Serial.println(F("[CMD] Usage: BUZZER ON|OFF"));
    }
}

// ─────────────────────────────────────────────────────────────
void SerialCmd::cmdReset() {
    Serial.println(F("[CMD] Factory reset in 3 seconds... send CANCEL to abort."));
    uint32_t t = millis();
    while (millis() - t < 3000) {
        if (Serial.available()) {
            // Drain buffer
            String s = Serial.readStringUntil('\n');
            s.trim();
            s.toUpperCase();
            if (s == "CANCEL") {
                Serial.println(F("[CMD] Reset cancelled."));
                return;
            }
        }
    }
    prefsCfg.factoryReset();
    Serial.println(F("[CMD] Done. Rebooting..."));
    delay(500);
    ESP.restart();
}

// ── Utilities ────────────────────────────────────────────────
bool SerialCmd::parseHHMM(const char* str, uint8_t& hh, uint8_t& mm) {
    // Expect "HH:MM"
    int h, m;
    if (sscanf(str, "%d:%d", &h, &m) == 2 &&
        h >= 0 && h <= 23 && m >= 0 && m <= 59) {
        hh = h; mm = m;
        return true;
    }
    Serial.printf("[CMD] Invalid time format '%s' — expected HH:MM\n", str);
    return false;
}

bool SerialCmd::parseHHMMSS(const char* str, uint8_t& hh, uint8_t& mm, uint8_t& ss) {
    int h, m, s;
    if (sscanf(str, "%d:%d:%d", &h, &m, &s) == 3 &&
        h >= 0 && h <= 23 && m >= 0 && m <= 59 && s >= 0 && s <= 59) {
        hh = h; mm = m; ss = s;
        return true;
    }
    Serial.printf("[CMD] Invalid time format '%s' — expected HH:MM:SS\n", str);
    return false;
}

bool SerialCmd::parseDDMMYYYY(const char* str, uint8_t& dd, uint8_t& mo, uint16_t& yr) {
    int d, m, y;
    if (sscanf(str, "%d/%d/%d", &d, &m, &y) == 3 &&
        d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 2024) {
        dd = d; mo = m; yr = y;
        return true;
    }
    Serial.printf("[CMD] Invalid date format '%s' — expected DD/MM/YYYY\n", str);
    return false;
}

Motor* SerialCmd::motorByName(const char* name) {
    if (strcmp(name, "MORNING") == 0) return &_mMorning;
    if (strcmp(name, "MIDDAY")  == 0) return &_mMidday;
    if (strcmp(name, "NIGHT")   == 0) return &_mNight;
    return nullptr;
}

DoseType SerialCmd::doseByName(const char* name, bool& found) {
    found = true;
    if (strcmp(name, "MORNING") == 0) return DoseType::MORNING;
    if (strcmp(name, "MIDDAY")  == 0) return DoseType::MIDDAY;
    if (strcmp(name, "NIGHT")   == 0) return DoseType::NIGHT;
    found = false;
    return DoseType::MORNING;
}

void SerialCmd::printSeparator() {
    Serial.println(F("────────────────────────────────────────"));
}
