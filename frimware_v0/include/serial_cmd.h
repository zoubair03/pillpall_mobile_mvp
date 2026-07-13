#pragma once
// ============================================================
//  PillPal — serial_cmd.h
//  Serial monitor command parser.
//
//  All commands uppercase, newline terminated.
//  Reads character-by-character non-blocking into a buffer.
//  On newline: parse and dispatch.
//
//  Command list:
//    HELP                        — list all commands
//    STATUS                      — full system status
//    CONFIG                      — dump EEPROM config
//    SET MORNING HH:MM           — set morning dose time
//    SET MIDDAY  HH:MM
//    SET NIGHT   HH:MM
//    SET TIME HH:MM:SS           — set RTC time
//    SET DATE DD/MM/YYYY         — set RTC date
//    SET TIMEOUT <minutes>       — missed dose timeout
//    DISPENSE MORNING            — manual dispense
//    DISPENSE MIDDAY
//    DISPENSE NIGHT
//    HOME ALL                    — re-home all motors
//    HOME MORNING/MIDDAY/NIGHT   — re-home one motor
//    ACK                         — acknowledge all missed doses
//    ACK MORNING/MIDDAY/NIGHT    — acknowledge one
//    WIFI SSID PASSWORD          — connect to WiFi (saves to EEPROM)
//    BUZZER ON/OFF               — enable/disable buzzer
//    RESET                       — factory reset EEPROM
// ============================================================

#include <Arduino.h>
#include "config.h"
#include "scheduler.h" 

class Motor;


// ============================================================
class SerialCmd {
public:
    SerialCmd(Motor& mMorning, Motor& mMidday, Motor& mNight, Scheduler& sched);

    void begin();
    void tick();   // Call every loop() — reads and dispatches commands

private:
    Motor&      _mMorning;
    Motor&      _mMidday;
    Motor&      _mNight;
    Scheduler&  _sched;

    char        _buf[128];
    uint8_t     _bufLen;

    void dispatch(char* cmd);

    // ── Command handlers ──────────────────────────────────────
    void cmdHelp();
    void cmdStatus();
    void cmdConfig();
    void cmdSet(char* args);
    void cmdDispense(char* args);
    void cmdHome(char* args);
    void cmdAck(char* args);
    void cmdWifi(char* args);
    void cmdBuzzer(char* args);
    void cmdReset();

    // ── Utilities ─────────────────────────────────────────────
    bool parseHHMM(const char* str, uint8_t& hh, uint8_t& mm);
    bool parseHHMMSS(const char* str, uint8_t& hh, uint8_t& mm, uint8_t& ss);
    bool parseDDMMYYYY(const char* str, uint8_t& dd, uint8_t& mo, uint16_t& yr);
    Motor* motorByName(const char* name);
    DoseType doseByName(const char* name, bool& found);

    void printSeparator();
};
