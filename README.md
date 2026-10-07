# PillPal — Caregiver App & Backend

PillPal is an automatic medication dispenser: an ESP32 device dispenses morning, midday and night doses, and caregivers follow and control it from an Android app.

This repo contains:

| Folder | What |
|---|---|
| `app/` | Android caregiver app (Kotlin, Jetpack Compose, Hilt, French UI) |
| `backend/` | Supabase project: Postgres schema + RLS, Edge Functions, cron, provisioning script |
| `frimware_v0/` | Older snapshot of the ESP32 firmware. The maintained firmware lives in the separate **`Pillpal_firmware`** repo. |

See [CLAUDE.md](CLAUDE.md) for architecture decisions and [backend/plan.md](backend/plan.md) for backend status and deployment notes.

## Architecture

```
                     ┌──────────── Supabase ────────────┐
 Android app ──JWT──▶│ Auth · Postgres (RLS) · Realtime │
      ▲              │ Edge Functions:                  │
      │ Realtime     │  publish-command ──▶ EMQX HTTP API ──MQTT──▶ device
      └──────────────│  emqx-webhook    ◀── EMQX rule    ◀──MQTT── device
                     │  check-missed-doses (cron, FCM push)
                     └──────────────────────────────────┘
```

- **Telemetry:** device → EMQX (`pillpal/{uid}/status`, `.../event/dose`) → rule engine → `emqx-webhook` → `device_status` / `dose_events` → Realtime → app.
- **Commands:** app → `publish-command` → EMQX → `pillpal/{uid}/cmd`. Commands: `update_schedule`, `ack_dose`, `force_dispense`, `calibrate`, `restart`.
- **Pairing (local, no cloud):** the app joins the device's `PillPal-Setup` hotspot, reads `deviceUid`/`pairingCode` from `http://192.168.4.1/api/status`, sends the home WiFi with `/api/wifi`, then claims the device with `create_patient_and_claim()`.
- **Push notifications:** Firebase Cloud Messaging, sent by the scheduled `check-missed-doses` function.

## The app

Screens:

- **Onboarding:** sign in / sign up (email), connect to the device hotspot, send home WiFi, patient profile, dose schedule.
- **Accueil:** next-dose countdown and today's doses. Tap a dose to send `ack_dose`.
- **Horaires:** edit dose times. The change is written to `schedules`, then `update_schedule` is sent to the device.
- **Historique:** dose events and alerts.
- **Contrôles:** device status, manual dispense, calibration, restart.

### Run it

Prerequisites: Android Studio, JDK 17+, an Android 10+ device for pairing (hotspot pairing uses `WifiNetworkSpecifier`). A physical phone is needed to test pairing against real hardware.

1. Create `.env` in the repo root (gitignored; see `.env.example`):
   ```
   SUPABASE_URL=https://<project>.supabase.co
   SUPABASE_ANON_KEY=<anon key>
   ```
   Values come from the Supabase dashboard → Project Settings → API. They are injected with the Secrets Gradle Plugin.
2. Make sure `app/google-services.json` matches the Firebase project (`pillpal-app-7548d`, package `com.example`).
3. Open the project in Android Studio and run the `app` configuration, or:
   ```sh
   ./gradlew :app:installDebug
   ```

Release builds are signed with the `upload` key: set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.

### Tests

```sh
./gradlew :app:testDebugUnitTest
```

There is a ViewModel unit test for each screen in `app/src/test/java/com/example/ui/viewmodel/`.

## The backend

Production project: `bnubxkkwflrteponobwl` (eu-west-1). The Supabase CLI is installed locally via `npm`:

```sh
cd backend
npm install
npx supabase link --project-ref bnubxkkwflrteponobwl
npx supabase db push                      # apply migrations
npx supabase functions deploy <name>      # emqx-webhook | publish-command | check-missed-doses
npx supabase secrets set --env-file supabase/.env.local
```

| Edge Function | Called by | Auth |
|---|---|---|
| `emqx-webhook` | EMQX rule engine | `X-Webhook-Secret` header (`verify_jwt = false`) |
| `publish-command` | App | Caller's JWT; RLS checks device access |
| `check-missed-doses` | `pg_cron` every 5 min | Service role key |

Function secrets: `EMQX_WEBHOOK_SECRET`, `EMQX_API_URL`, `EMQX_API_KEY`, `EMQX_API_SECRET`, `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`. Keep them in `backend/supabase/.env.local`, never in git.

### Provisioning a device

Each physical unit needs a matching `devices` row before it can be paired:

1. Create the row:
   ```sh
   cd backend
   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... node scripts/provision-device.mjs
   ```
   This prints `device_uid` and `pairing_code`.
2. In EMQX Cloud, create MQTT credentials for that device, restricted to `pillpal/{device_uid}/#`.
3. Flash the unit with the firmware from the `Pillpal_firmware` repo, then over USB serial run:
   ```
   PROVISION <device_uid> <pairing_code> <mqtt_password>
   ```
4. Pair it from the app.

## Status

- [x] Schema, RLS, `create_patient_and_claim()`
- [x] Edge Functions deployed, EMQX rule → webhook verified, missed-dose cron running
- [x] App on real Supabase Auth / RPC / Realtime / Edge Functions
- [x] Firmware: MQTT/TLS cloud link, commands, dose events, NTP, provisioning (in `Pillpal_firmware`; compiles, not yet hardware-tested)
- [ ] Live hardware test of the full pairing + cloud flow
- [ ] `check-missed-doses` doesn't push when the device itself reports a `missed` dose (a dispensed dose that was never confirmed)
- [ ] Before launch: custom SMTP + OTP email template, re-enable email confirmation (see `backend/plan.md`)
azertymahdi18*
