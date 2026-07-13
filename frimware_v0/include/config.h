#pragma once
// ============================================================
//  PillPal — config.h
//  Single source of truth for ALL hardware pins, timing
//  constants, and tuneable defaults.
//
//  Change things HERE, not scattered across source files.
// ============================================================

// ── Firmware identity ────────────────────────────────────────
#ifndef PILLPAL_VERSION
  #define PILLPAL_VERSION "1.0.0-dev"
#endif

// ── Serial ───────────────────────────────────────────────────
#define SERIAL_BAUD         115200

// ── I2C Bus (shared: OLED + RTC) ────────────────────────────
// NOTE: We use non-default I2C pins to free up default 21/22
//       for the OLED per the BOM wiring table.
#define PIN_I2C_SDA_OLED    21      // OLED SDA
#define PIN_I2C_SCL_OLED    22      // OLED SCL
#define PIN_I2C_SDA_RTC     32      // RTC SDA  (separate I2C bus)
#define PIN_I2C_SCL_RTC     33      // RTC SCL

// ── OLED Display ─────────────────────────────────────────────
#define OLED_I2C_ADDR       0x3C    // Most SSD1306 1.3" modules
#define OLED_WIDTH          128
#define OLED_HEIGHT         64
#define OLED_RESET_PIN      -1      // No reset pin wired; -1 = use RST line

// ── Motors (28BYJ-48 via ULN2003) ───────────────────────────
// Each motor uses 4 GPIO pins: IN1, IN2, IN3, IN4 on the ULN2003

// Motor 1 — MIDDAY/EVENING carousel
#define MOTOR_MIDDAY_IN1    13
#define MOTOR_MIDDAY_IN2    12
#define MOTOR_MIDDAY_IN3    14
#define MOTOR_MIDDAY_IN4    27

// Motor 2 — NIGHT carousel
#define MOTOR_NIGHT_IN1     26
#define MOTOR_NIGHT_IN2     25
#define MOTOR_NIGHT_IN3     15
#define MOTOR_NIGHT_IN4     5

// Motor 3 — MORNING carousel
#define MOTOR_MORNING_IN1   16
#define MOTOR_MORNING_IN2   17
#define MOTOR_MORNING_IN3   18
#define MOTOR_MORNING_IN4   19

// ── Limit Switches ───────────────────────────────────────────
// INPUT_PULLUP assumed — switch pulls to GND when triggered
// These are input-only GPIOs on ESP32 (34, 36, 39 = no pullup HW)
// → MUST use external 10kΩ pull-up resistors to 3.3V
#define PIN_LIMIT_MORNING   34
#define PIN_LIMIT_MIDDAY    36
#define PIN_LIMIT_NIGHT     39

// ── User Interface ───────────────────────────────────────────
#define PIN_BUTTON          0       // Momentary push button (boot button doubles up)
#define PIN_BUZZER          2       // PWM buzzer
#define PIN_LED_RED         23      // Red LED (with 220Ω series resistor)

// ── Battery Monitor ──────────────────────────────────────────
#define PIN_BATTERY         4       // ADC input via 100kΩ voltage divider
// Voltage divider ratio: if top=100kΩ, bottom=100kΩ → ratio = 0.5
// Full battery (4.2V) → ADC sees 2.1V → ADC reads ~1720/4095
// Empty battery (3.0V) → ADC sees 1.5V → ADC reads ~1228/4095
#define BATTERY_DIV_RATIO   0.5f
#define BATTERY_VREF        3.3f
#define BATTERY_ADC_MAX     4095
#define BATTERY_V_FULL      4.2f
#define BATTERY_V_EMPTY     3.0f

// ── Motor Mechanics ──────────────────────────────────────────
// 28BYJ-48: 2048 half-steps per full revolution
// 8 compartments (1 Home + 7 days) → 2048 / 8 = 256 steps/slot
#define STEPS_PER_REVOLUTION    4096
#define COMPARTMENTS_PER_MOTOR  8
#define STEPS_PER_SLOT          (STEPS_PER_REVOLUTION / COMPARTMENTS_PER_MOTOR)  // = 256

// Step interval in microseconds (controls speed)
// 1200µs = safe dispensing speed, smooth movement
// 3000µs = slow homing speed, less torque risk on limit switch
#define STEP_INTERVAL_US        1500
#define STEP_INTERVAL_HOME_US   3000

// Max steps allowed during homing before declaring fault
// Full revolution + 20% margin = 2048 * 1.2 ≈ 2458
#define HOMING_MAX_STEPS        2500

// ── Scheduler ────────────────────────────────────────────────
// Default dose times (HH, MM) — overridden by EEPROM after first config
#define DEFAULT_MORNING_HH      8
#define DEFAULT_MORNING_MM      0

#define DEFAULT_MIDDAY_HH       13
#define DEFAULT_MIDDAY_MM       0

#define DEFAULT_NIGHT_HH        21
#define DEFAULT_NIGHT_MM        0

// How many minutes after dispense before declaring a missed dose
#define DEFAULT_MISSED_TIMEOUT_MIN  30

// Re-alert interval for missed dose (minutes)
#define MISSED_REALERT_MIN      5

// ── Scheduler polling interval ───────────────────────────────
#define SCHEDULER_CHECK_MS      5000    // Check RTC every 5 seconds

// ── Display refresh ──────────────────────────────────────────
#define DISPLAY_REFRESH_MS      1000    // Redraw OLED every 1 second
#define DISPLAY_ROTATE_MS       4000    // Rotate screen every 4 seconds

// ── Buzzer ───────────────────────────────────────────────────
// Core 3.x LEDC API: channel is auto-assigned, only pin + freq needed.
// BUZZER_CHANNEL is intentionally removed — it no longer exists in Core 3.x.
#define BUZZER_FREQ_HZ          2000    // Base frequency (Hz)

// ── NVS / Preferences ────────────────────────────────────────
// Preferences (NVS) replaces EEPROM emulation.
// No byte addresses needed — keys are human-readable strings.
// NVS namespace: "pillpal"  (see prefs_cfg.h for key names)
// NVS partition: default ESP32 partition table (~24KB, wear-leveled)
//
// Flag byte bit positions (stored under key "flags"):
#define FLAG_BUZZER_ENABLED     0       // bit 0
#define FLAG_WIFI_ENABLED       1       // bit 1

// ── WiFi ─────────────────────────────────────────────────────
#define WIFI_CONNECT_TIMEOUT_MS     15000   // 15s to connect before AP fallback
#define WIFI_AP_SSID                "PillPal-Setup"
#define WIFI_AP_PASSWORD            ""      // Open — no password
#define WIFI_AP_IP                  "192.168.4.1"
#define WIFI_WEB_PORT               80

// ── Watchdog ─────────────────────────────────────────────────
#define WDT_TIMEOUT_S               10      // Reset if loop stalls > 10 seconds

// ── Debug helpers ────────────────────────────────────────────
#ifdef PILLPAL_DEBUG
  #define DBG(msg)        Serial.println(F(msg))
  #define DBG_F(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
  #define DBG(msg)
  #define DBG_F(fmt, ...)
#endif
