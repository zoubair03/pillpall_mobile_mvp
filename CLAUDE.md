# PillPal

Medication dispenser: ESP32 hardware + Android caregiver app + Supabase/EMQX cloud backend.

## Repo layout

- `app/` — Android caregiver app (Kotlin). UI is built; network calls are real (Supabase Auth/RPC/Realtime/Edge Functions). Device pairing uses real local WiFi (`WifiNetworkSpecifier`) against the device's hotspot — built but not yet verified against real hardware. Root of the repo doubles as the Gradle project (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`), so keep non-Android stuff out of it.
- `frimware_v0/` — ESP32 firmware (Arduino/PlatformIO). Handles local WiFi provisioning (device broadcasts its own hotspot, `POST /api/wifi` to configure), `GET /api/status` (includes `device_uid`/`pairing_code` once provisioned), `POST /api/provision` (one-time device_uid/pairing_code write), scheduling, dispensing, and (in progress) MQTT connectivity to the cloud broker. No PlatformIO install or physical unit available in this workspace — changes here are compile-pattern-correct by review only, not build- or flash-tested.
- `backend/` — the cloud backend. This is what's actively being built. Everything Supabase/Node-related lives here, separate from the Android tree.
  - `backend/supabase/` — Supabase project: migrations in `supabase/migrations/`, Edge Functions in `supabase/functions/<name>/index.ts`.
  - `backend/pillpal_schema.sql` — reference copy of the schema (also present as a migration in `backend/supabase/migrations/`).
  - `backend/package.json` — just the `supabase` CLI devDependency, for running it via `npx`.

## Architecture — decided, don't re-litigate

- **Broker**: EMQX Cloud Serverless. Devices connect outbound over MQTT/TLS (solves NAT — a home device can't accept inbound connections, so it has to phone out). Each device has its own MQTT credentials, restricted to `pillpal/{device_uid}/#`.
- **Database**: Supabase Postgres, RLS enabled on every table.
- **Data flow (telemetry)**: device → EMQX → EMQX rule engine → webhook → Supabase Edge Function (service role, bypasses RLS) → Postgres → Supabase Realtime → app.
- **Data flow (commands)**: app → Edge Function (caller's own JWT, RLS applies) → EMQX HTTP publish API → device.
- **Push notifications**: Firebase Cloud Messaging only, via a scheduled function. Not related to Supabase Realtime.
- **Device provisioning (local WiFi setup) is a separate, non-cloud pattern** — phone talks directly to the device's local hotspot over HTTP, no broker or Supabase involved at that stage. Don't conflate the two flows.
- **BLE was deliberately dropped** in favor of AP-based provisioning (WifiNetworkSpecifier on Android + the device's own SoftAP). Don't reintroduce it.
- **Caregiver sharing/invites was deliberately dropped** for now — one caregiver, self-registered, immediately creates their patient. Don't add an invite flow unless asked.

## Schema rules that matter

- `patients` and `caregiver_patients` have **no client-side insert policy, on purpose**. The only way rows are created is through `create_patient_and_claim()` (a `security definer` Postgres function), which always creates a brand-new patient rather than linking to an existing one — this closes off any "guess someone else's patient_id" attack surface. Never add a direct insert policy to either table as a shortcut.
- `dose_events` is append-only (history). `device_status` is upserted (current snapshot). Don't merge these.
- Writes from the device/broker path always use the Supabase **service role** client (trusted boundary, bypasses RLS). Writes initiated by the app always use a client built from the **caller's JWT** (RLS applies) — never mix these up.

## Current status / TODO

- [x] Core schema + RLS + `create_patient_and_claim()`
- [x] `caregiver_push_tokens` table (needed before missed-dose push notifications can work)
- [x] `emqx-webhook` Edge Function
- [x] `publish-command` Edge Function
- [x] `check-missed-doses` Edge Function (scheduled) — cron wired via `pg_cron`/`pg_net`, runs every 5 minutes, verified firing successfully
- [x] Firmware: extend `/api/status` to return `pairing_code`/`device_uid` for the app to read during local setup — also added `POST /api/provision` to write them; not flash-tested (no hardware/PlatformIO in this workspace)
- [x] Android: replace stubbed network calls with real Supabase Auth / RPC / Realtime / Edge Function calls
- [x] Android: device pairing flow uses real `WifiNetworkSpecifier` + local HTTP to the device (`/api/status`, `/api/wifi`) instead of mocked data — not verified against a real ESP32
- [ ] Firmware: MQTT client + TLS root CA + per-device credentials in NVS
- [ ] Manufacturing script: `backend/scripts/provision-device.mjs` covers the DB half (generates + inserts `device_uid`/`pairing_code`); pushing those same values onto a physical unit via its `/api/provision` still has to be done by hand once hardware exists — not automated
- [ ] Live hardware test of the full pairing flow (connect to hotspot → read `/api/status` → send WiFi creds → device reboots onto home WiFi → app claims via `create_patient_and_claim`) — blocked on having a real ESP32 to test against

## Conventions

- Edge Functions: TypeScript, Deno runtime, `backend/supabase/functions/<name>/index.ts`, secrets via `Deno.env.get(...)` — never hardcoded.
- These functions run unattended — log errors clearly, don't let a single malformed message throw and wedge a webhook handler (return 200 and log instead).
- When building or changing a function, show the plan before writing code, and stop for review after each function rather than batching multiple at once.
