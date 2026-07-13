# PillPal backend — build plan

Cloud layer connecting the ESP32 firmware to the Android caregiver app. See
the root `CLAUDE.md` for the architecture decisions this plan follows —
this file is the step-by-step build order and current status, not a design
doc.

Data flow this is all in service of:
- **Telemetry**: device → EMQX → EMQX rule engine → webhook → Edge Function (service role) → Postgres → Realtime → app.
- **Commands**: app → Edge Function (caller's JWT, RLS) → EMQX HTTP publish API → device.

## Steps

### 0. Core schema + RLS — done
`supabase/migrations/00000000000000_initial_schema.sql`. `patients`,
`caregiver_patients`, `devices`, `schedules`, `dose_events`,
`device_status`, `alerts`, all RLS-enabled, plus `create_patient_and_claim()`
for self-serve onboarding.

### 1. `caregiver_push_tokens` migration — done
`supabase/migrations/20260711000000_caregiver_push_tokens.sql`. One row per
caregiver, RLS scoped to `caregiver_id = auth.uid()`. Needed before step 4
can send push notifications anywhere.

### 2. `emqx-webhook` Edge Function — done
`supabase/functions/emqx-webhook/index.ts`. Ingests device telemetry
(`pillpal/{device_uid}/status`, `pillpal/{device_uid}/event/dose`) via a
shared-secret-authenticated webhook, using the service-role client. Built
and tested against a seeded local device before EMQX exists — see the
function's test commands from earlier in this project for the curl
payloads that exercise it.

### 3. `publish-command` Edge Function — done
`supabase/functions/publish-command/index.ts`. App calls this (its own
JWT) to send `update_schedule` / `ack_dose` commands to a device. Confirms
the caller has access via `caregiver_patients` (falls out of RLS for free),
then relays to EMQX's HTTP publish API. The actual publish call can't be
fully tested until step 5 exists — only the 400/401/403 paths are testable
against the local DB today.

### 4. `check-missed-doses` Edge Function — done
`supabase/functions/check-missed-doses/index.ts`. Scheduled (cron), not
called by app or device — access-gated by comparing the raw Authorization
header against the service role key directly, since only our own cron
trigger should ever call this. For each `schedules` row, checks whether
`time_of_day + missed_timeout_min` has passed *today in the patient's own
timezone* (via `npm:luxon`, not server/UTC time) with no matching
`dispensed`/`acknowledged`/`missed` `dose_events` row — idempotent by
design, reruns don't re-alert. For each real miss: inserts a `missed`
`dose_events` row (`source: 'system'`), an `alerts` row, then FCM-pushes
every caregiver linked to that device's patient via
`caregiver_push_tokens`, using a hand-rolled service-account OAuth2 flow
(FCM's legacy server-key API was shut down by Google in 2024; v1 requires
signing a JWT and exchanging it, done here with Web Crypto rather than an
npm auth library, to avoid Deno-compat risk). Verified end-to-end locally,
including the idempotency rerun and the graceful-degradation path when FCM
credentials are fake/invalid (the dose_event/alert writes still succeed;
only the push itself fails and logs). **Not yet wired to an actual
schedule** — needs the Supabase Dashboard's Edge Functions → Cron
scheduler (a `pg_cron`/`pg_net` migration was deliberately avoided here
since doing it right would mean committing the project URL and service
role key into a migration file, which conflicts with "never hardcode
credentials"). Suggested cadence: every 5–15 minutes.

Also found and fixed two real bugs while testing this, both project-wide,
not specific to this function:
- `emqx-webhook`'s `config.toml` was missing `verify_jwt = false` — every
  Edge Function requires a platform-level Supabase JWT by default, which
  EMQX will never send (it only has our shared-secret header). Every real
  webhook call would have 401'd before our code even ran.
- No table was reachable by any Data API role at all. Newer Supabase no
  longer auto-exposes new tables to `anon`/`authenticated`/`service_role` —
  RLS alone isn't sufficient, each role also needs an explicit `GRANT`.
  Fixed in `supabase/migrations/20260711010000_data_api_grants.sql`,
  including `ALTER DEFAULT PRIVILEGES` so tables added by future
  migrations don't hit the same silent "permission denied" wall. This
  would have equally blocked the Android app's own queries later, not
  just the Edge Functions.

### 5. EMQX Cloud Serverless setup — not started
Create the instance, configure per-device MQTT credentials restricted to
`pillpal/{device_uid}/#`, and configure the rule engine: subscribe to
`pillpal/+/status` and `pillpal/+/event/dose`, forward matching messages to
step 2's webhook as `{ topic, payload }` with the shared secret header.
Nothing from device→cloud is real until this exists — steps 2 and 3 have
only been exercised with fake local payloads so far.

### 6. Android: replace stubbed network calls — not started
Wire up real Supabase Auth, the `create_patient_and_claim()` RPC, Realtime
subscriptions on `device_status`/`dose_events`/`alerts`, and calls into
`publish-command`. Fully unblocked today (steps 0–3 give it everything it
needs) — doesn't require EMQX or real hardware, since fake device/telemetry
rows inserted straight into Postgres look identical to real ones from the
app's point of view. Can proceed in parallel with step 5.

### 7. Firmware: MQTT client — not started (separate repo)
TLS root CA + per-device credentials in NVS, publish to
`pillpal/{device_uid}/status` and `.../event/dose`, subscribe to
`.../cmd` and act on `update_schedule`/`ack_dose`. Also: extend
`/api/status` to return `pairing_code`/`device_uid` so the app can read
them during local WiFi setup. Depends on step 5 (needs real MQTT
credentials to connect with).

### 8. Manufacturing script — not started
Pre-provision `device_uid` + `pairing_code` + MQTT credentials per physical
unit before flashing. Lowest priority — only matters once real units are
being built, and depends on step 5 (credentials come from EMQX).

## Deployed to production — 2026-07-11

Project `riphkcjgnwfsfrluwnlo` (Supabase Cloud, eu-central-1). All three
migrations applied, all three functions deployed, all secrets set
(EMQX_WEBHOOK_SECRET, EMQX_API_URL/KEY/SECRET, FCM_PROJECT_ID/CLIENT_EMAIL/
PRIVATE_KEY), and every function smoke-tested live against the real
project — including a real publish to the EMQX Cloud Serverless broker via
`publish-command`, not just a local mock. All smoke-test data was cleaned
up afterward.

One thing found during this deploy worth remembering: **the project
already had `patients`/`devices`/etc. created manually (via the SQL
editor) before any migration had been pushed**, so `db push` initially
collided on `CREATE TYPE`. Fixed with `supabase migration repair --status
applied 00000000000000` after verifying the existing schema was identical
to what the migration would have produced — a metadata-only fix, no data
touched. If you ever hand-run SQL against a Supabase project outside of
migrations again, expect this same collision next time you push.

Also: this project uses Supabase's newer `sb_secret_...` key format for
`SUPABASE_SERVICE_ROLE_KEY` inside deployed functions (not the legacy JWT)
— but the GoTrue Admin API (`/auth/v1/admin/*`) still only accepts the
legacy JWT-format service role key, not the new one. Different subsystems,
different expectations — worth knowing if you're scripting against this
project again.

**Still not done**:
- EMQX rule engine isn't configured yet — nothing from a *real* device has
  ever reached `emqx-webhook`, only manual test payloads. Need: a rule
  subscribing to `pillpal/+/status` and `pillpal/+/event/dose`, forwarding
  to `https://riphkcjgnwfsfrluwnlo.supabase.co/functions/v1/emqx-webhook`
  with header `X-Webhook-Secret` matching the deployed secret.
- `check-missed-doses` has no cron trigger yet — wire it up via the
  Supabase Dashboard's Edge Functions → Cron scheduler.
- Android app still has stubbed network calls (step 6) — this is now the
  most valuable next step, since the backend it needs to talk to is live.
- Firmware MQTT client still doesn't exist (step 7) — now unblocked by
  real EMQX credentials whenever it's picked up.
- Manufacturing script (step 8) — still lowest priority.
