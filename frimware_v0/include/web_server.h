#pragma once
// ============================================================
//  PillPal — web_server.h
//  WiFi state machine + ESPAsyncWebServer + REST API.
//
//  WiFi States:
//    INIT        → deciding what to do on boot
//    CONNECTING  → blocking Station connect attempt (setup() only)
//    STATION     → connected to home router, monitoring every 10s
//    RECONNECTING→ lost connection, retrying for 60s
//    AP_MODE     → fallback hotspot, retries Station every 30s
//
//  REST Endpoints:
//    GET  /              → serve embedded SPA (web_page.h)
//    GET  /api/status    → full system JSON snapshot
//    POST /api/time      → set RTC time and/or date
//    POST /api/schedule  → update dose times + timeout
//    POST /api/dispense  → manual motor dispense
//    POST /api/ack       → acknowledge missed dose(s)
//    POST /api/home      → trigger motor homing
//    POST /api/wifi      → save credentials + reboot
//    POST /api/provision → one-time device_uid/pairing_code write (rejects
//                           if already provisioned — see handleProvision)
//
//  Globals (extern'd for display.cpp and serial_cmd.cpp):
//    wifiConnected  — true = Station mode, false = AP mode
//    wifiIP         — current IP as String
// ============================================================

#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <AsyncJson.h>            // AsyncCallbackJsonWebHandler — part of ESP32Async/ESPAsyncWebServer
#include <ArduinoJson.h>
#include "config.h"

// Forward declarations
class Motor;
class Scheduler;

// ── WiFi state machine states ────────────────────────────────
enum class WifiState {
    INIT,
    CONNECTING,
    STATION,
    RECONNECTING,
    AP_MODE
};

// ── Globals — read by display.cpp and serial_cmd.cpp ────────
extern bool   wifiConnected;   // true = Station, false = AP
extern String wifiIP;          // Current IP address as string

// ============================================================
class WebServerManager {
public:
    WebServerManager(Motor& mMorning, Motor& mMidday, Motor& mNight,
                     Scheduler& sched);

    // Call in setup() — handles initial WiFi connect (blocking up to 15s)
    // then starts the AsyncWebServer regardless of WiFi outcome
    void begin();

    // Call every loop() — manages WiFi state machine
    // (reconnect logic, AP fallback, Station retry from AP)
    void tick();

    // Getters
    WifiState   getState()    const { return _state; }
    const char* getStateName() const;
    bool        isConnected() const { return _state == WifiState::STATION; }

private:
    Motor&      _mMorning;
    Motor&      _mMidday;
    Motor&      _mNight;
    Scheduler&  _sched;

    AsyncWebServer _server;

    WifiState   _state;
    uint32_t    _stateEnterMs;     // millis() when we entered current state
    uint32_t    _lastCheckMs;      // Last WiFi.status() check
    uint32_t    _lastApRetryMs;    // Last Station retry from AP mode

    // ── WiFi state machine ────────────────────────────────────
    void enterStation();
    void enterAP();
    void enterReconnecting();

    bool tryStationConnect(uint32_t timeoutMs);   // Blocking connect attempt
    void updateGlobals();

    // ── Web server setup ──────────────────────────────────────
    void setupRoutes();

    // ── Route handlers ────────────────────────────────────────
    void handleStatus(AsyncWebServerRequest* req);
    void handleSetTime(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleSetSchedule(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleDispense(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleAck(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleHome(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleWifi(AsyncWebServerRequest* req, JsonDocument& doc);
    void handleProvision(AsyncWebServerRequest* req, JsonDocument& doc);

    // ── JSON helpers ──────────────────────────────────────────
    void sendOk(AsyncWebServerRequest* req);
    void sendError(AsyncWebServerRequest* req, const char* msg, int code = 400);

    // ── Body parser helper ────────────────────────────────────
    // ESPAsyncWebServer needs a body handler to receive POST JSON
    void registerPostHandler(const char* path,
        std::function<void(AsyncWebServerRequest*, JsonDocument&)> handler);

    // ── Battery reading ───────────────────────────────────────
    uint8_t readBatteryPercent();
};

// Singleton
extern WebServerManager* webMgrPtr;  // Set in main.cpp after construction
