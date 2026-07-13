// ============================================================
//  PillPal — display.cpp
// ============================================================

#include "display.h"
#include "rtc_manager.h"
#include "scheduler.h"
#include "motor.h"
#include "web_server.h"   // for wifiMgr.isConnected()

Display displayMgr;

// ── Constructor ──────────────────────────────────────────────
Display::Display()
    : _oled(OLED_WIDTH, OLED_HEIGHT, &Wire, OLED_RESET_PIN),
      _oledOk(false),
      _currentScreen(Screen::CLOCK),
      _lastRefreshMs(0),
      _lastRotateMs(0),
      _screenCount(3)
{}

// ── begin() ──────────────────────────────────────────────────
bool Display::begin() {
    // OLED uses custom Wire pins defined in config.h
    Wire.begin(PIN_I2C_SDA_OLED, PIN_I2C_SCL_OLED);

    delay(250); // Wait for the SH1106 controller to boot

    // Initialize SH1106 display
    if (!_oled.begin(OLED_I2C_ADDR, true)) {
        Serial.println(F("[OLED] ERROR: SH1106 not found! Running in serial-only mode."));
        _oledOk = false;
        return false;
    }

    _oledOk = true;
    _oled.clearDisplay();
    _oled.setTextColor(SH110X_WHITE);
    _oled.cp437(true);

    showBoot("PillPal v" PILLPAL_VERSION);
    Serial.println(F("[OLED] SH1106 initialized."));
    return true;
}

// ── tick() ───────────────────────────────────────────────────
void Display::tick(Motor& mMorning, Motor& mMidday, Motor& mNight, Scheduler& sched) {
    if (!_oledOk) return;

    uint32_t now = millis();

    // Refresh rate limit
    if (now - _lastRefreshMs < DISPLAY_REFRESH_MS) return;
    _lastRefreshMs = now;

    // ── Priority: FAULT overrides everything ─────────────────
    bool anyFault = mMorning.isFault() || mMidday.isFault() || mNight.isFault();
    if (anyFault) {
        drawFault(mMorning, mMidday, mNight);
        _oled.display();
        return;
    }

    // ── Priority: Missed dose alert ───────────────────────────
    if (sched.anyMissedDose()) {
        drawAlert(sched);
        _oled.display();
        return;
    }

    // ── Normal rotation ───────────────────────────────────────
    if (now - _lastRotateMs >= DISPLAY_ROTATE_MS) {
        _lastRotateMs = now;
        uint8_t idx = ((uint8_t)_currentScreen + 1) % _screenCount;
        _currentScreen = (Screen)idx;
    }

    _oled.clearDisplay();

    switch (_currentScreen) {
        case Screen::CLOCK:    drawClock();                         break;
        case Screen::NEXTDOSE: drawNextDose(sched);                 break;
        //case Screen::MOTORS:   drawMotors(mMorning, mMidday, mNight); break;
        default:               drawClock(); break;
    }

    _oled.display();
}

// ── showBoot() ───────────────────────────────────────────────
void Display::showBoot(const char* msg) {
    if (!_oledOk) return;
    _oled.clearDisplay();
    drawCenteredText("PillPal", 10, 2);
    drawCenteredText(msg, 35, 1);
    drawCenteredText("Demarrage...", 50, 1);
    _oled.display();
}

// ── showError() ──────────────────────────────────────────────
void Display::showError(const char* line1, const char* line2) {
    if (!_oledOk) return;
    _oled.clearDisplay();
    drawCenteredText("! ERREUR !", 0, 2);
    _oled.drawLine(0, 16, 127, 16, SH110X_WHITE);
    drawCenteredText(line1, 24, 1);
    if (line2) drawCenteredText(line2, 40, 1);
    _oled.display();
}

// ── drawClock() ──────────────────────────────────────────────
void Display::drawClock() {
    TimeStruct t = rtcMgr.getTime();
    char tbuf[6];
    // Affichage HH:MM uniquement pour pouvoir l'écrire en GÉANT
    snprintf(tbuf, sizeof(tbuf), "%02d:%02d", t.hour, t.minute);

    drawStatusBar();

    // Heure géante (Taille 3 = très lisible de loin)
    drawCenteredText(tbuf, 18, 3);

    // Jour de la semaine traduit en français
    const char* daysFR[] = {"Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
    uint8_t dow = t.dayOfWeek > 6 ? 0 : t.dayOfWeek;
    
    // Affichage de la date "Lundi 24/05"
    char dateBuf[20];
    snprintf(dateBuf, sizeof(dateBuf), "%s %02d/%02d", daysFR[dow], t.day, t.month);
    drawCenteredText(dateBuf, 48, 1);

    // Indicateur de perte d'heure RTC
    if (rtcMgr.isTimeLost()) {
        drawCenteredText("! Reglez l'heure !", 56, 1);
    }
}

// ── drawNextDose() ───────────────────────────────────────────
void Display::drawNextDose(Scheduler& sched) {
    drawStatusBar();

    const DoseEvent* ev = sched.nextEvent();
    if (!ev) {
        drawCenteredText("Termine", 20, 2);
        drawCenteredText("pour aujourd'hui!", 42, 1);
        return;
    }

    // Nom de la dose traduit en français pour l'affichage
    const char* nomDose = ev->name;
    if (strcmp(ev->name, "Morning") == 0) nomDose = "Matin";
    if (strcmp(ev->name, "Midday") == 0)  nomDose = "Midi";
    if (strcmp(ev->name, "Night") == 0)   nomDose = "Soir";

    char buf[24];
    
    // Nom et Heure en GROS (Taille 2)
    snprintf(buf, sizeof(buf), "%s %02d:%02d", nomDose, ev->hour, ev->minute);
    drawCenteredText(buf, 18, 2);

    // Compte à rebours
    int32_t mins = sched.minutesUntilNext();
    if (mins >= 0) {
        snprintf(buf, sizeof(buf), "dans %dh %dm", mins / 60, mins % 60);
        drawCenteredText(buf, 42, 1);
    }
}

// ── drawMotors() ─────────────────────────────────────────────
void Display::drawMotors(Motor& mMorning, Motor& mMidday, Motor& mNight) {
    drawCenteredText("ETAT MOTEURS", 0, 1);
    _oled.drawLine(0, 9, 127, 9, SH110X_WHITE);

    Motor* motors[] = { &mMorning, &mMidday, &mNight };
    const char* labels[] = { "MATIN", "MIDI ", "SOIR " };

    for (uint8_t i = 0; i < 3; i++) {
        int16_t y = 14 + i * 16;
        _oled.setCursor(0, y);
        
        // Simplification de l'état pour que ça rentre mieux
        const char* etat = motors[i]->getStateName();
        if (strcmp(etat, "DISPENSING") == 0) etat = "TOURNE";
        if (strcmp(etat, "HOMING") == 0) etat = "INIT";
        
        _oled.printf("%s: S%d %s", labels[i], motors[i]->getCurrentSlot(), etat);
    }
}

// ── drawAlert() ──────────────────────────────────────────────
void Display::drawAlert(Scheduler& sched) {
    _oled.clearDisplay();

    // Effet de bordure clignotante
    static bool flash = false;
    flash = !flash;
    if (flash) _oled.drawRect(0, 0, 128, 64, SH110X_WHITE);

    // Alerte en très gros
    drawCenteredText("OUBLI !", 6, 2);
    _oled.drawLine(0, 24, 127, 24, SH110X_WHITE);

    uint8_t y = 28;
    for (int i = 0; i < 3; i++) {
        const DoseEvent& ev = sched.getEvent((DoseType)i);
        if (ev.missedAlerted) {
            const char* nomDose = ev.name;
            if (strcmp(ev.name, "Morning") == 0) nomDose = "Matin";
            if (strcmp(ev.name, "Midday") == 0)  nomDose = "Midi";
            if (strcmp(ev.name, "Night") == 0)   nomDose = "Soir";

            char buf[24];
            snprintf(buf, sizeof(buf), "> %s %02d:%02d", nomDose, ev.hour, ev.minute);
            drawCenteredText(buf, y, 1);
            y += 12;
        }
    }

    drawCenteredText("Bouton pour OK", 52, 1);
}

// ── drawFault() ──────────────────────────────────────────────
void Display::drawFault(Motor& mMorning, Motor& mMidday, Motor& mNight) {
    _oled.clearDisplay();
    drawCenteredText("PANNE MOTEUR", 0, 1);
    _oled.drawLine(0, 10, 127, 10, SH110X_WHITE);

    uint8_t y = 16;
    auto check = [&](Motor& m, const char* nom) {
        if (m.isFault()) {
            char buf[24];
            snprintf(buf, sizeof(buf), "%s: BLOQUE", nom);
            drawCenteredText(buf, y, 1);
            y += 12;
        }
    };
    check(mMorning, "Matin");
    check(mMidday, "Midi");
    check(mNight, "Soir");

    drawCenteredText("Clic Home All via WiFi", 52, 1);
}

// ── drawStatusBar() ──────────────────────────────────────────
void Display::drawStatusBar() {
    // Battery percentage
    uint8_t pct = getBatteryPercent();
    char buf[8];
    snprintf(buf, sizeof(buf), "%3d%%", pct);
    _oled.setTextSize(1);
    _oled.setCursor(0, 0);
    _oled.print(buf);

    // WiFi indicator (center-right)
    // wifiMgr checked via extern — defined in web_server.h
    extern bool wifiConnected;
    
    int16_t x1, y1;
    uint16_t w, h;
    const char* wifiStr = wifiConnected ? "WiFi" : " AP ";
    _oled.getTextBounds(wifiStr, 0, 0, &x1, &y1, &w, &h);
    _oled.setCursor(128 - w, 0); // Align right exactly
    _oled.print(wifiStr);

    _oled.drawLine(0, 9, 127, 9, SH110X_WHITE);
}

// ── drawCenteredText() ───────────────────────────────────────
void Display::drawCenteredText(const char* text, int16_t y, uint8_t size) {
    _oled.setTextSize(size);
    int16_t x1, y1;
    uint16_t w, h;
    _oled.getTextBounds(text, 0, 0, &x1, &y1, &w, &h);
    _oled.setCursor((OLED_WIDTH - w) / 2, y);
    _oled.print(text);
}

// ── getBatteryPercent() ──────────────────────────────────────
uint8_t Display::getBatteryPercent() {
    int raw = analogRead(PIN_BATTERY);
    float v = (raw / (float)BATTERY_ADC_MAX) * BATTERY_VREF / BATTERY_DIV_RATIO;
    float pct = (v - BATTERY_V_EMPTY) / (BATTERY_V_FULL - BATTERY_V_EMPTY) * 100.0f;
    if (pct > 100.0f) pct = 100.0f;
    if (pct < 0.0f)   pct = 0.0f;
    return (uint8_t)pct;
}

