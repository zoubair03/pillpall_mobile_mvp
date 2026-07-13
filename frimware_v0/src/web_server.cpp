// ============================================================
//  PillPal — web_server.cpp
// ============================================================

#include "web_server.h"
#include "web_page.h"
#include "motor.h"
#include "scheduler.h"
#include "rtc_manager.h"
#include "prefs_cfg.h"
#include "buzzer.h"

// ── Globals ──────────────────────────────────────────────────
bool   wifiConnected = false;   // DEFINED here, extern'd in web_server.h
String wifiIP        = "0.0.0.0";

WebServerManager* webMgrPtr = nullptr;

// ── Constructor ──────────────────────────────────────────────
WebServerManager::WebServerManager(Motor& mMorning, Motor& mMidday,
                                   Motor& mNight, Scheduler& sched)
    : _mMorning(mMorning), _mMidday(mMidday), _mNight(mNight), _sched(sched),
      _server(WIFI_WEB_PORT),
      _state(WifiState::INIT),
      _stateEnterMs(0),
      _lastCheckMs(0),
      _lastApRetryMs(0)
{}

// ── begin() — called once in setup() ────────────────────────
void WebServerManager::begin() {
    const auto& cfg = prefsCfg.get();
    bool hasCredentials = strlen(cfg.wifiSsid) > 0;

    if (!hasCredentials) {
        // ── No credentials: go straight to AP ────────────────
        Serial.println(F("[WiFi] No credentials saved — starting AP mode."));
        enterAP();
    } else {
        // ── Try Station connect (blocking, max 15s) ───────────
        Serial.printf("[WiFi] Connecting to '%s'", cfg.wifiSsid);
        bool connected = tryStationConnect(WIFI_CONNECT_TIMEOUT_MS);

        if (connected) {
            enterStation();
        } else {
            Serial.println(F("\n[WiFi] Connection timed out — falling back to AP."));
            enterAP();
        }
    }

    // ── Start web server (runs regardless of WiFi mode) ──────
    setupRoutes();
    _server.begin();
    Serial.printf("[WebServer] Listening on http://%s\n", wifiIP.c_str());
}

// ── tick() — called every loop() ────────────────────────────
void WebServerManager::tick() {
    uint32_t now = millis();

    switch (_state) {

        // ── STATION: monitor connection every 10s ────────────
        case WifiState::STATION: {
            if (now - _lastCheckMs < 10000UL) break;
            _lastCheckMs = now;

            if (WiFi.status() != WL_CONNECTED) {
                Serial.println(F("[WiFi] Connection lost — entering reconnect mode."));
                enterReconnecting();
            }
            break;
        }

        // ── RECONNECTING: retry for 60s then fall to AP ──────
        case WifiState::RECONNECTING: {
            if (WiFi.status() == WL_CONNECTED) {
                Serial.println(F("[WiFi] Reconnected!"));
                enterStation();
                break;
            }

            // 60s elapsed without reconnecting → AP fallback
            if (now - _stateEnterMs >= 60000UL) {
                Serial.println(F("[WiFi] Reconnect timeout (60s) — falling back to AP."));
                enterAP();
            }
            break;
        }

        // ── AP_MODE: retry Station every 30s if credentials exist
        case WifiState::AP_MODE: {
            if (now - _lastApRetryMs < 30000UL) break;
            _lastApRetryMs = now;

            const auto& cfg = prefsCfg.get();
            if (strlen(cfg.wifiSsid) == 0) break;  // No credentials to try

            Serial.printf("[WiFi] AP mode: retrying Station connect to '%s'...\n",
                          cfg.wifiSsid);

            // Brief non-interruptive attempt (10s)
            WiFi.softAPdisconnect(false);  // Keep AP running during attempt
            bool ok = tryStationConnect(10000UL);

            if (ok) {
                // Close AP, enter full Station mode
                WiFi.softAPdisconnect(true);
                enterStation();
                Serial.println(F("[WiFi] Successfully reconnected to Station — AP closed."));
            } else {
                // Stay in AP, try again in 30s
                Serial.println(F("[WiFi] Retry failed — staying in AP mode."));
                // Re-ensure AP is running (it may have been disrupted)
                WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASSWORD);
                updateGlobals();
            }
            break;
        }

        default:
            break;
    }
}

// ── enterStation() ───────────────────────────────────────────
void WebServerManager::enterStation() {
    _state        = WifiState::STATION;
    _stateEnterMs = millis();
    _lastCheckMs  = millis();
    updateGlobals();
    Serial.printf("[WiFi] Station mode. IP: %s\n", wifiIP.c_str());
}

// ── enterAP() ────────────────────────────────────────────────
void WebServerManager::enterAP() {
    WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASSWORD);
    delay(100);  // AP needs brief settle time before IP is valid
    _state           = WifiState::AP_MODE;
    _stateEnterMs    = millis();
    _lastApRetryMs   = millis();
    updateGlobals();
    Serial.printf("[WiFi] AP mode. SSID='%s'  IP: %s\n",
                  WIFI_AP_SSID, wifiIP.c_str());
}

// ── enterReconnecting() ──────────────────────────────────────
void WebServerManager::enterReconnecting() {
    _state        = WifiState::RECONNECTING;
    _stateEnterMs = millis();
    wifiConnected = false;
    WiFi.reconnect();
    Serial.println(F("[WiFi] Reconnecting... (60s window)"));
}

// ── tryStationConnect() — blocking with Serial dots ──────────
bool WebServerManager::tryStationConnect(uint32_t timeoutMs) {
    const auto& cfg = prefsCfg.get();
    WiFi.mode(WIFI_STA);
    WiFi.begin(cfg.wifiSsid, cfg.wifiPass);

    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED) {
        if (millis() - start >= timeoutMs) return false;
        Serial.print('.');
        delay(500);  // Acceptable: only runs in setup() or AP retry (not in main loop)
    }
    Serial.println();
    return true;
}

// ── updateGlobals() ──────────────────────────────────────────
void WebServerManager::updateGlobals() {
    if (_state == WifiState::STATION) {
        wifiConnected = true;
        wifiIP        = WiFi.localIP().toString();
    } else {
        wifiConnected = false;
        wifiIP        = WiFi.softAPIP().toString();
    }
}

// ── getStateName() ───────────────────────────────────────────
const char* WebServerManager::getStateName() const {
    switch (_state) {
        case WifiState::INIT:         return "Init";
        case WifiState::CONNECTING:   return "Connecting";
        case WifiState::STATION:      return "Station";
        case WifiState::RECONNECTING: return "Reconnecting";
        case WifiState::AP_MODE:      return "AP";
        default:                      return "Unknown";
    }
}

// ── readBatteryPercent() ─────────────────────────────────────
uint8_t WebServerManager::readBatteryPercent() {
    int raw = analogRead(PIN_BATTERY);
    float v = (raw / (float)BATTERY_ADC_MAX) * BATTERY_VREF / BATTERY_DIV_RATIO;
    float pct = (v - BATTERY_V_EMPTY) / (BATTERY_V_FULL - BATTERY_V_EMPTY) * 100.0f;
    if (pct > 100.0f) pct = 100.0f;
    if (pct < 0.0f)   pct = 0.0f;
    return (uint8_t)pct;
}

// ════════════════════════════════════════════════════════════
//  ROUTE SETUP
// ════════════════════════════════════════════════════════════

void WebServerManager::setupRoutes() {

    // ── Serve SPA ─────────────────────────────────────────────
    _server.on("/", HTTP_GET, [](AsyncWebServerRequest* req) {
        req->send(200, "text/html", PILLPAL_HTML); // Fixed send_P warning
    });

    // ── GET /api/status ───────────────────────────────────────
    _server.on("/api/status", HTTP_GET,
        [this](AsyncWebServerRequest* req) {
            handleStatus(req);
        }
    );

    // ── POST handlers via body parser ─────────────────────────
    registerPostHandler("/api/time",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleSetTime(req, doc);
        });

    registerPostHandler("/api/schedule",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleSetSchedule(req, doc);
        });

    registerPostHandler("/api/dispense",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleDispense(req, doc);
        });

    registerPostHandler("/api/ack",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleAck(req, doc);
        });

    registerPostHandler("/api/home",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleHome(req, doc);
        });

    registerPostHandler("/api/wifi",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleWifi(req, doc);
        });

    registerPostHandler("/api/provision",
        [this](AsyncWebServerRequest* req, JsonDocument& doc) {
            handleProvision(req, doc);
        });

    // ── 404 ───────────────────────────────────────────────────
    _server.onNotFound([](AsyncWebServerRequest* req) {
        req->send(404, "application/json", "{\"error\":\"Not found\"}");
    });
}

// ── registerPostHandler() ────────────────────────────────────
// ESPAsyncWebServer handles POST body via an onBody callback.
// We pair it with an onRequest callback that sends the response.
// The body is accumulated into a heap buffer then parsed as JSON.
void WebServerManager::registerPostHandler(
    const char* path,
    std::function<void(AsyncWebServerRequest*, JsonDocument&)> handler)
{
    // We use a shared_ptr body buffer approach via lambda capture
    _server.addHandler(
        new AsyncCallbackJsonWebHandler(path,
            [handler](AsyncWebServerRequest* req, JsonVariant& json) {
                JsonDocument doc;
                // JsonVariant is already parsed by AsyncCallbackJsonWebHandler
                doc.set(json);
                handler(req, doc);
            }
        )
    );
}

// ════════════════════════════════════════════════════════════
//  REST HANDLERS
// ════════════════════════════════════════════════════════════

// ── GET /api/status ──────────────────────────────────────────
void WebServerManager::handleStatus(AsyncWebServerRequest* req) {
    JsonDocument doc;  // Fixed StaticJsonDocument warning

    // Time
    TimeStruct t = rtcMgr.getTime();
    JsonObject time = doc["time"].to<JsonObject>(); // Fixed createNestedObject warning
    time["hh"]  = t.hour;
    time["mm"]  = t.minute;
    time["ss"]  = t.second;
    time["dd"]  = t.day;
    time["mo"]  = t.month;
    time["yr"]  = t.year;
    time["dow"] = RtcManager::dowName(t.dayOfWeek);

    doc["rtcOk"] = rtcMgr.isRtcOk();

    // Schedule
    JsonObject sched = doc["schedule"].to<JsonObject>();
    const auto& cfg = prefsCfg.get();
    char tbuf[6];
    snprintf(tbuf, 6, "%02d:%02d", cfg.morningHH, cfg.morningMM);
    sched["morning"] = tbuf;
    snprintf(tbuf, 6, "%02d:%02d", cfg.middayHH, cfg.middayMM);
    sched["midday"]  = tbuf;
    snprintf(tbuf, 6, "%02d:%02d", cfg.nightHH, cfg.nightMM);
    sched["night"]   = tbuf;
    sched["timeout"] = cfg.missedTimeoutMin;

    // Motors
    JsonObject motors = doc["motors"].to<JsonObject>();
    auto addMotor = [&](const char* key, Motor& m) {
        JsonObject mo = motors[key].to<JsonObject>();
        mo["state"] = m.getStateName();
        mo["slot"]  = m.getCurrentSlot();
        mo["fault"] = m.isFault();
    };
    addMotor("morning", _mMorning);
    addMotor("midday",  _mMidday);
    addMotor("night",   _mNight);

    // Battery
    doc["battery"] = readBatteryPercent();

    // WiFi
    doc["wifiMode"] = wifiConnected ? "Station" : "AP";
    doc["ip"]       = wifiIP;

    // Identity — set once via POST /api/provision, empty on an
    // unprovisioned unit. Read by the app while still connected to this
    // device's AP, before it sends /api/wifi (which reboots the device
    // and ends the AP connection).
    const auto& idCfg = prefsCfg.get();
    doc["deviceUid"]   = idCfg.deviceUid;
    doc["pairingCode"] = idCfg.pairingCode;

    // Next dose
    if (schedulerPtr) {
        const DoseEvent* next = schedulerPtr->nextEvent();
        if (next) {
            JsonObject nd = doc["nextDose"].to<JsonObject>();
            nd["name"] = next->name;
            snprintf(tbuf, 6, "%02d:%02d", next->hour, next->minute);
            nd["time"] = tbuf;
            nd["in"]   = schedulerPtr->minutesUntilNext();
        }

        // Missed doses
        JsonArray missed = doc["missed"].to<JsonArray>(); // Fixed createNestedArray warning
        for (int i = 0; i < 3; i++) {
            const DoseEvent& ev = schedulerPtr->getEvent((DoseType)i);
            if (ev.missedAlerted) missed.add(ev.name);
        }
    }

    // Uptime
    doc["uptimeMs"] = millis();

    // Serialize and send
    String out;
    serializeJson(doc, out);
    req->send(200, "application/json", out);
}

// ── POST /api/time ────────────────────────────────────────────
void WebServerManager::handleSetTime(AsyncWebServerRequest* req,
                                      JsonDocument& doc) {
    TimeStruct cur = rtcMgr.getTime();

    // Fixed containsKey warnings using new .is<T>() check
    uint8_t  hh  = doc["hh"].is<int>() ? (uint8_t)doc["hh"].as<int>() : cur.hour;
    uint8_t  mm  = doc["mm"].is<int>() ? (uint8_t)doc["mm"].as<int>() : cur.minute;
    uint8_t  ss  = doc["ss"].is<int>() ? (uint8_t)doc["ss"].as<int>() : cur.second;
    uint8_t  dd  = doc["dd"].is<int>() ? (uint8_t)doc["dd"].as<int>() : cur.day;
    uint8_t  mo  = doc["mo"].is<int>() ? (uint8_t)doc["mo"].as<int>() : cur.month;
    uint16_t yr  = doc["yr"].is<int>() ? (uint16_t)doc["yr"].as<int>() : cur.year;

    // Validate ranges
    if (hh > 23 || mm > 59 || ss > 59 || dd < 1 || dd > 31 ||
        mo < 1  || mo > 12 || yr < 2024) {
        sendError(req, "Invalid time/date values");
        return;
    }

    rtcMgr.setTime(hh, mm, ss, dd, mo, yr);
    sendOk(req);
}

// ── POST /api/schedule ────────────────────────────────────────
void WebServerManager::handleSetSchedule(AsyncWebServerRequest* req,
                                          JsonDocument& doc) {
    if (!schedulerPtr) { sendError(req, "Scheduler not ready"); return; }

    auto parseTime = [](const char* s, uint8_t& hh, uint8_t& mm) -> bool {
        if (!s || strlen(s) < 5) return false;
        int h, m;
        if (sscanf(s, "%d:%d", &h, &m) != 2) return false;
        if (h < 0 || h > 23 || m < 0 || m > 59) return false;
        hh = h; mm = m;
        return true;
    };

    uint8_t hh, mm;

    // Fixed containsKey warnings
    if (doc["morning"].is<const char*>()) {
        if (parseTime(doc["morning"].as<const char*>(), hh, mm))
            schedulerPtr->setDoseTime(DoseType::MORNING, hh, mm);
    }
    if (doc["midday"].is<const char*>()) {
        if (parseTime(doc["midday"].as<const char*>(), hh, mm))
            schedulerPtr->setDoseTime(DoseType::MIDDAY, hh, mm);
    }
    if (doc["night"].is<const char*>()) {
        if (parseTime(doc["night"].as<const char*>(), hh, mm))
            schedulerPtr->setDoseTime(DoseType::NIGHT, hh, mm);
    }
    if (doc["timeout"].is<int>()) {
        int t = doc["timeout"].as<int>();
        if (t >= 5 && t <= 120) {
            prefsCfg.get().missedTimeoutMin = (uint8_t)t;
            prefsCfg.save();
        }
    }

    sendOk(req);
}

// ── POST /api/dispense ────────────────────────────────────────
void WebServerManager::handleDispense(AsyncWebServerRequest* req,
                                       JsonDocument& doc) {
    if (!schedulerPtr) { sendError(req, "Scheduler not ready"); return; }

    const char* dose = doc["dose"].as<const char*>();
    if (!dose) { sendError(req, "Missing 'dose' field"); return; }

    DoseType dt;
    if      (strcasecmp(dose, "morning") == 0) dt = DoseType::MORNING;
    else if (strcasecmp(dose, "midday")  == 0) dt = DoseType::MIDDAY;
    else if (strcasecmp(dose, "night")   == 0) dt = DoseType::NIGHT;
    else { sendError(req, "Invalid dose name"); return; }

    // Check motor state for grayed-out tooltip info
    Motor* m = nullptr;
    if (dt == DoseType::MORNING) m = &_mMorning;
    if (dt == DoseType::MIDDAY)  m = &_mMidday;
    if (dt == DoseType::NIGHT)   m = &_mNight;

    if (m && m->isFault()) {
        sendError(req, "Motor in FAULT state — run HOME first");
        return;
    }
    if (m && m->isBusy()) {
        sendError(req, "Motor is busy — wait for current operation to finish");
        return;
    }

    bool ok = schedulerPtr->manualDispense(dt);
    if (ok) sendOk(req);
    else    sendError(req, "Dispense failed — check motor state");
}

// ── POST /api/ack ─────────────────────────────────────────────
void WebServerManager::handleAck(AsyncWebServerRequest* req,
                                  JsonDocument& doc) {
    if (!schedulerPtr) { sendError(req, "Scheduler not ready"); return; }

    const char* dose = doc["dose"].as<const char*>();
    if (!dose) { sendError(req, "Missing 'dose' field"); return; }

    if (strcasecmp(dose, "all") == 0) {
        schedulerPtr->acknowledgeAll();
        if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playAck();
        sendOk(req);
        return;
    }

    bool found;
    DoseType dt;
    if      (strcasecmp(dose, "morning") == 0) { dt = DoseType::MORNING; found = true; }
    else if (strcasecmp(dose, "midday")  == 0) { dt = DoseType::MIDDAY;  found = true; }
    else if (strcasecmp(dose, "night")   == 0) { dt = DoseType::NIGHT;   found = true; }
    else found = false;

    if (!found) { sendError(req, "Invalid dose name"); return; }

    schedulerPtr->acknowledge(dt);
    if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playAck();
    sendOk(req);
}

// ── POST /api/home ────────────────────────────────────────────
void WebServerManager::handleHome(AsyncWebServerRequest* req,
                                   JsonDocument& doc) {
    const char* motor = doc["motor"].as<const char*>();
    if (!motor) { sendError(req, "Missing 'motor' field"); return; }

    if (strcasecmp(motor, "all") == 0) {
        _mMorning.startHoming();
        _mMidday.startHoming();
        _mNight.startHoming();
        sendOk(req);
        return;
    }

    Motor* m = nullptr;
    if      (strcasecmp(motor, "morning") == 0) m = &_mMorning;
    else if (strcasecmp(motor, "midday")  == 0) m = &_mMidday;
    else if (strcasecmp(motor, "night")   == 0) m = &_mNight;

    if (!m) { sendError(req, "Invalid motor name"); return; }

    m->startHoming();
    sendOk(req);
}

// ── POST /api/wifi ────────────────────────────────────────────
void WebServerManager::handleWifi(AsyncWebServerRequest* req,
                                   JsonDocument& doc) {
    const char* ssid = doc["ssid"].as<const char*>();
    const char* pass = doc["pass"].as<const char*>();

    if (!ssid || strlen(ssid) == 0) {
        sendError(req, "Missing SSID");
        return;
    }

    strncpy(prefsCfg.get().wifiSsid, ssid, 31);
    prefsCfg.get().wifiSsid[31] = '\0';

    if (pass) {
        strncpy(prefsCfg.get().wifiPass, pass, 31);
        prefsCfg.get().wifiPass[31] = '\0';
    }

    prefsCfg.setWifiEnabled(true);
    prefsCfg.save();

    sendOk(req);

    // Reboot after response is flushed (small delay)
    delay(500);
    ESP.restart();
}

// ── POST /api/provision ───────────────────────────────────────
// One-time write of device_uid/pairing_code — the manufacturing/
// provisioning step calls this while the unit is still in AP mode,
// before it ever ships. Rejects if already provisioned: identity
// should only ever be set once, not silently overwritten by whatever
// happens to be on the local network later.
void WebServerManager::handleProvision(AsyncWebServerRequest* req,
                                        JsonDocument& doc) {
    if (strlen(prefsCfg.get().deviceUid) > 0) {
        sendError(req, "Device already provisioned", 409);
        return;
    }

    const char* deviceUid   = doc["device_uid"].as<const char*>();
    const char* pairingCode = doc["pairing_code"].as<const char*>();

    if (!deviceUid || strlen(deviceUid) == 0) {
        sendError(req, "Missing device_uid");
        return;
    }
    if (!pairingCode || strlen(pairingCode) == 0) {
        sendError(req, "Missing pairing_code");
        return;
    }

    strncpy(prefsCfg.get().deviceUid, deviceUid, sizeof(prefsCfg.get().deviceUid) - 1);
    prefsCfg.get().deviceUid[sizeof(prefsCfg.get().deviceUid) - 1] = '\0';
    strncpy(prefsCfg.get().pairingCode, pairingCode, sizeof(prefsCfg.get().pairingCode) - 1);
    prefsCfg.get().pairingCode[sizeof(prefsCfg.get().pairingCode) - 1] = '\0';

    prefsCfg.save();
    sendOk(req);
}

// ── sendOk() ─────────────────────────────────────────────────
void WebServerManager::sendOk(AsyncWebServerRequest* req) {
    req->send(200, "application/json", "{\"ok\":true}");
}

// ── sendError() ──────────────────────────────────────────────
void WebServerManager::sendError(AsyncWebServerRequest* req,
                                  const char* msg, int code) {
    JsonDocument doc; // Fixed StaticJsonDocument warning
    doc["ok"]    = false;
    doc["error"] = msg;
    String out;
    serializeJson(doc, out);
    req->send(code, "application/json", out);
}

