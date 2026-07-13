// ============================================================
//  PillPal — main.cpp
//  Entry point. Owns all object instances.
//  setup() initializes in dependency order.
//  loop() is a pure non-blocking dispatcher — no logic here.
//
//  Object init order (each depends on those above it):
//    1. Serial
//    2. GPIO (LED, button)
//    3. EEPROM config       ← everything reads this
//    4. Buzzer              ← early so boot jingle can play
//    5. RTC                 ← before display (shows time)
//    6. Display             ← shows boot progress
//    7. Motors              ← configure GPIO pins
//    8. WiFi + WebServer    ← blocking connect attempt, then async
//    9. Scheduler           ← needs motors + RTC both ready
//   10. SerialCmd           ← print welcome banner
//   11. WDT                 ← AFTER WiFi (avoids false trigger)
//   12. Home all motors     ← final calibration
// ============================================================

#include <Arduino.h>
#include <esp_task_wdt.h>

#include "config.h"
#include "prefs_cfg.h"
#include "buzzer.h"
#include "rtc_manager.h"
#include "display.h"
#include "motor.h"
#include "scheduler.h"
#include "serial_cmd.h"
#include "web_server.h"

// ── Global object instances ──────────────────────────────────
// All constructors are safe to call here — no hardware access
// in any constructor. Hardware init happens in begin() methods.

Motor motorMorning(MotorID::MORNING,
    MOTOR_MORNING_IN1, MOTOR_MORNING_IN2,
    MOTOR_MORNING_IN3, MOTOR_MORNING_IN4,
    PIN_LIMIT_MORNING);

Motor motorMidday(MotorID::MIDDAY,
    MOTOR_MIDDAY_IN1, MOTOR_MIDDAY_IN2,
    MOTOR_MIDDAY_IN3, MOTOR_MIDDAY_IN4,
    PIN_LIMIT_MIDDAY);

Motor motorNight(MotorID::NIGHT,
    MOTOR_NIGHT_IN1, MOTOR_NIGHT_IN2,
    MOTOR_NIGHT_IN3, MOTOR_NIGHT_IN4,
    PIN_LIMIT_NIGHT);

Scheduler     scheduler(motorMorning, motorMidday, motorNight);
SerialCmd     serialCmd(motorMorning, motorMidday, motorNight, scheduler);
WebServerManager webMgr(motorMorning, motorMidday, motorNight, scheduler);

// ── setup() ──────────────────────────────────────────────────
void setup() {
    // ── 1. Serial ────────────────────────────────────────────
    Serial.begin(SERIAL_BAUD);
    delay(200);  // Brief settle for USB serial on some boards
    Serial.println(F("\n\n"));
    Serial.println(F("╔══════════════════════════════════════╗"));
    Serial.println(F("║          PillPal Firmware            ║"));
    Serial.printf( "║          Version: %-18s║\n", PILLPAL_VERSION);
    Serial.println(F("║          Booting...                  ║"));
    Serial.println(F("╚══════════════════════════════════════╝\n"));

    // ── 2. GPIO ──────────────────────────────────────────────
    pinMode(PIN_LED_RED,  OUTPUT);
    pinMode(PIN_BUTTON,   INPUT_PULLUP);
    digitalWrite(PIN_LED_RED, LOW);

    // ADC setup for battery monitor
    // Core 3.x: analogSetAttenuation() global form removed.
    // Use analogSetPinAttenuation(pin, atten) per-pin instead.
    analogReadResolution(12);                               // 12-bit = 0–4095
    analogSetPinAttenuation(PIN_BATTERY, ADC_11db);        // Full 0–3.3V range

    // ── 3. Preferences (NVS) config ──────────────────────────
    prefsCfg.begin();  // Loads from NVS or writes factory defaults on first run

    // ── 4. Buzzer ─────────────────────────────────────────────
    buzzerMgr.begin();
    if (prefsCfg.isBuzzerEnabled()) {
        buzzerMgr.playBoot();
        // Let boot jingle play during rest of setup (non-blocking)
    }

    // ── 5. RTC ───────────────────────────────────────────────
    bool rtcOk = rtcMgr.begin();
    if (!rtcOk) {
        Serial.println(F("[MAIN] WARNING: RTC failed — millis() fallback active."));
    }

    // ── 6. Display ───────────────────────────────────────────
    bool oledOk = displayMgr.begin();
    if (!oledOk) {
        Serial.println(F("[MAIN] WARNING: OLED failed — serial-only mode."));
    } else {
        // Show boot screen long enough to read (only blocking delay in firmware)
        displayMgr.showBoot("Booting...");
        delay(1500);
    }

    if (!rtcOk) {
        displayMgr.showError("RTC Failed", "Using millis()");
        delay(1000);
    }

    // ── 7. Motors ────────────────────────────────────────────
    Serial.println(F("[MAIN] Initializing motors..."));
    displayMgr.showBoot("Init motors...");

    motorMorning.begin();
    motorMidday.begin();
    motorNight.begin();

    // ── 8. WiFi + Web Server ─────────────────────────────────
    Serial.println(F("[MAIN] Starting WiFi..."));
    displayMgr.showBoot("Connecting WiFi...");

    webMgrPtr       = &webMgr;    // Set global pointer before begin()
    schedulerPtr    = &scheduler; // Set global pointer before web server starts
    webMgr.begin();               // Blocking up to 15s for Station connect

    // Update display with WiFi result
    if (wifiConnected) {
        char ipMsg[24];
        snprintf(ipMsg, sizeof(ipMsg), "IP: %s", wifiIP.c_str());
        displayMgr.showBoot(ipMsg);
    } else {
        displayMgr.showBoot("AP: PillPal-Setup");
    }
    delay(1000);

    // ── 9. Scheduler ─────────────────────────────────────────
    scheduler.begin();  // Loads dose times from NVS via prefsCfg

    // ── 10. Serial command interface ─────────────────────────
    serialCmd.begin();  // Prints welcome banner + command list

    // ── 11. Watchdog Timer ───────────────────────────────────
    // ESP-IDF 5.1 (Core 3.x) changed esp_task_wdt_init() to take a
    // config struct instead of (timeout, panic) raw arguments.
    // Initialized AFTER WiFi so the 15s connect doesn't false-trigger it.
    {
        esp_task_wdt_config_t wdt_cfg = {
            .timeout_ms     = WDT_TIMEOUT_S * 1000U,
            .idle_core_mask = 0,    // Don't watch idle tasks
            .trigger_panic  = true  // Reboot on timeout
        };
        esp_task_wdt_reconfigure(&wdt_cfg);  // reconfigure if already init'd by IDF
        esp_task_wdt_add(NULL);              // Watch the Arduino loop task
    }
    Serial.printf("[MAIN] Watchdog timer enabled (%ds timeout).\n", WDT_TIMEOUT_S);

 
}

// ── loop() ───────────────────────────────────────────────────
// Pure dispatcher — zero logic, zero delay(), zero blocking.
// Each tick() method checks its own timer and returns immediately
// if it's not time to do anything.
void loop() {
    // ── Always first: feed watchdog ───────────────────────────
    // If this line is not reached within WDT_TIMEOUT_S seconds,
    // the ESP32 reboots automatically.
    esp_task_wdt_reset();

    // ── Motor steps (most time-sensitive) ────────────────────
    // Each call takes ~1µs if idle, or executes one half-step
    // if the step interval (1200µs) has elapsed.
    // All three run every iteration → parallel homing + dispensing.
    motorMorning.tick();
    motorMidday.tick();
    motorNight.tick();

    // ── Buzzer melody advancement ─────────────────────────────
    // Checks if current note duration expired; if so, plays next.
    // Takes ~1µs when nothing is playing.
    buzzerMgr.tick();

    // ── OLED display refresh ──────────────────────────────────
    // Only redraws if DISPLAY_REFRESH_MS (1000ms) has elapsed.
    // Full redraw takes ~3ms (I2C transfer). Skipped otherwise (~1µs).
    displayMgr.tick(motorMorning, motorMidday, motorNight, scheduler);

    // ── Dose scheduler ────────────────────────────────────────
    // Only polls RTC if SCHEDULER_CHECK_MS (5000ms) has elapsed.
    // Checks time, fires doses, checks missed-dose timeouts,
    // and fires any pending doses (queued while motor was homing).
    scheduler.tick();

    // ── Serial command parser ─────────────────────────────────
    // Reads one character per loop if available. Dispatches command
    // on newline. Takes ~1µs when no serial data is pending.
    serialCmd.tick();

    // ── WiFi state machine ────────────────────────────────────
    // Monitors WiFi.status() every 10s.
    // Handles reconnect (60s), AP fallback, AP→Station retry (30s).
    // ESPAsyncWebServer handles HTTP in background — no tick needed.
    webMgr.tick();

    // ── Red LED — missed dose indicator ──────────────────────
    // Simple logic: LED on if any missed dose is unacknowledged.
    // No timer needed — this is a pure state read.
    digitalWrite(PIN_LED_RED, scheduler.anyMissedDose() ? HIGH : LOW);

    // --- Blinking Logic ---
    static uint32_t blinkStartMs = 0;
    // Boot mode: Blinking fast for the first 5 seconds
    bool isBooting = (millis() < 5000);
    // Dispense mode: Check if ANY motor is dispensing
    bool anyDispensing = (motorMorning.getState() == MotorState::DISPENSING || 
                      motorMidday.getState()  == MotorState::DISPENSING || 
                      motorNight.getState()   == MotorState::DISPENSING);

    if (isBooting || anyDispensing) {
    // 200ms interval = fast blink
    if (millis() - blinkStartMs > 2000) {
        digitalWrite(PIN_LED_RED, !digitalRead(PIN_LED_RED));
        blinkStartMs = millis();
    }
    } else {
    digitalWrite(PIN_LED_RED, LOW); // LED off when idle
    }

    // ── Button handling ───────────────────────────────────────
    // Active LOW (INPUT_PULLUP). Debounced via static millis() check.
    // Press = acknowledge all missed doses (most useful single action).
    static bool     lastButtonState = HIGH;
    static uint32_t lastDebounceMs  = 0;
    bool currentButton = digitalRead(PIN_BUTTON);


    if (currentButton != lastButtonState) {
        lastDebounceMs = millis();
    }
    if ((millis() - lastDebounceMs) > 50) {  // 50ms debounce
        if (currentButton == LOW && lastButtonState == HIGH) {
            // Button just pressed (falling edge after debounce)
            if (scheduler.anyMissedDose()) {
                scheduler.acknowledgeAll();
                if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playAck();
                Serial.println(F("[BUTTON] Missed doses acknowledged."));
            } else {
                // No missed dose: brief confirmation beep
                if (prefsCfg.isBuzzerEnabled()) buzzerMgr.playAck();
                Serial.println(F("[BUTTON] Button pressed (no missed doses)."));
            }
        }
    }
    lastButtonState = currentButton;
}
