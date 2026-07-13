-- ============================================================
--  PillPal — Supabase schema (step 1: data model + RLS)
--  Run this in the Supabase SQL editor, or as a migration.
-- ============================================================

create extension if not exists "pgcrypto";

-- ── Enums ─────────────────────────────────────────────────────
create type dose_type as enum ('morning', 'midday', 'night');
create type dose_status as enum ('pending', 'dispensed', 'acknowledged', 'missed');
create type caregiver_role as enum ('owner', 'viewer');

-- ============================================================
--  CORE ENTITIES
-- ============================================================

-- A patient is the person taking medication. Not tied 1:1 to a
-- login — a patient may have zero, one, or several caregivers.
create table patients (
  id          uuid primary key default gen_random_uuid(),
  full_name   text not null,
  timezone    text not null default 'Africa/Tunis',
  created_at  timestamptz not null default now()
);

-- Many-to-many: which caregivers (auth.users) can see which patients.
-- This is the join table every RLS policy below hinges on.
create table caregiver_patients (
  caregiver_id uuid not null references auth.users(id) on delete cascade,
  patient_id   uuid not null references patients(id) on delete cascade,
  role         caregiver_role not null default 'owner',
  created_at   timestamptz not null default now(),
  primary key (caregiver_id, patient_id)
);

-- One physical ESP32 unit. device_uid is a stable hardware ID
-- burned into firmware (e.g. derived from the ESP32 MAC address).
-- pairing_code is shown on the OLED during setup so the caregiver
-- can claim the device from the app without typing the device_uid.
create table devices (
  id                uuid primary key default gen_random_uuid(),
  device_uid        text not null unique,
  patient_id        uuid references patients(id) on delete set null,
  pairing_code      text unique,
  claimed_at        timestamptz,
  firmware_version  text,
  created_at        timestamptz not null default now()
);

-- Dose schedule, 3 rows per device (morning/midday/night).
-- Mirrors what already lives in ESP32 NVS (prefs_cfg.cpp) — this
-- is the cloud's copy, kept in sync via the command path.
create table schedules (
  id                  uuid primary key default gen_random_uuid(),
  device_id           uuid not null references devices(id) on delete cascade,
  dose                dose_type not null,
  time_of_day         time not null,
  missed_timeout_min  int not null default 30,
  updated_at          timestamptz not null default now(),
  unique (device_id, dose)
);

-- Append-only log of everything that happens to a dose:
-- dispensed by the device, acknowledged by the patient/caregiver,
-- or marked missed after the timeout. This is the adherence history.
create table dose_events (
  id            uuid primary key default gen_random_uuid(),
  device_id     uuid not null references devices(id) on delete cascade,
  dose          dose_type not null,
  status        dose_status not null,
  scheduled_at  timestamptz,
  occurred_at   timestamptz not null default now(),
  source        text not null default 'device'  -- 'device' | 'app'
);
create index dose_events_device_time_idx on dose_events (device_id, occurred_at desc);

-- Latest known state per device — one row, upserted on every
-- telemetry message. This is what the app's "status card" reads;
-- dose_events is history, this is "right now".
create table device_status (
  device_id           uuid primary key references devices(id) on delete cascade,
  battery_pct         int,
  wifi_connected      boolean,
  wifi_ip             text,
  rtc_ok              boolean,
  motor_morning_state text,
  motor_midday_state  text,
  motor_night_state   text,
  updated_at          timestamptz not null default now()
);

-- Alerts surfaced to the caregiver: missed doses, low battery,
-- motor faults, device gone offline. Acknowledging one is a
-- normal app action, not a device action.
create table alerts (
  id            uuid primary key default gen_random_uuid(),
  device_id     uuid not null references devices(id) on delete cascade,
  kind          text not null,  -- 'missed_dose' | 'low_battery' | 'motor_fault' | 'offline'
  message       text,
  acknowledged  boolean not null default false,
  created_at    timestamptz not null default now()
);
create index alerts_device_idx on alerts (device_id, created_at desc);

-- ============================================================
--  ROW LEVEL SECURITY
--  Everything below chains back to: does a row exist in
--  caregiver_patients linking auth.uid() to this device's patient?
-- ============================================================

alter table patients            enable row level security;
alter table caregiver_patients  enable row level security;
alter table devices             enable row level security;
alter table schedules           enable row level security;
alter table dose_events         enable row level security;
alter table device_status       enable row level security;
alter table alerts              enable row level security;

-- ── caregiver_patients: readable by the caregiver themselves.
--    Deliberately NO insert policy: possession of a patient_id is not
--    proof of authorization to attach yourself to it. The only way
--    a row gets created here is the create_patient_and_claim()
--    function below, which always creates a brand-new patient rather
--    than linking to an existing one — so there's no arbitrary
--    patient_id to guess or attach to in the first place. ──
create policy "read own caregiver links"
  on caregiver_patients for select
  using (caregiver_id = auth.uid());

create policy "remove own caregiver link"
  on caregiver_patients for delete
  using (caregiver_id = auth.uid());

-- ── patients: visible if linked via caregiver_patients ──
create policy "read linked patients"
  on patients for select
  using (
    exists (
      select 1 from caregiver_patients cp
      where cp.patient_id = patients.id
        and cp.caregiver_id = auth.uid()
    )
  );

-- Deliberately no insert policy here either: a patients row with no
-- owning caregiver is a dangling record nobody can see or reach. The
-- create_patient_and_claim() function creates the patient row and
-- the owner's caregiver_patients row in one transaction, so there's
-- never a window where one exists without the other.

create policy "update linked patients"
  on patients for update
  using (
    exists (
      select 1 from caregiver_patients cp
      where cp.patient_id = patients.id
        and cp.caregiver_id = auth.uid()
        and cp.role = 'owner'
    )
  );

-- ── devices: visible/manageable if linked to one of the caregiver's patients ──
create policy "read linked devices"
  on devices for select
  using (
    patient_id in (
      select patient_id from caregiver_patients where caregiver_id = auth.uid()
    )
  );

-- Note: there is deliberately no client-side UPDATE policy for
-- claiming a device (setting patient_id / claimed_at). That write
-- only happens inside create_patient_and_claim() below, which
-- validates the pairing code server-side before touching the row.

-- ── schedules: read/update if caregiver owns the parent device ──
create policy "read schedules for linked devices"
  on schedules for select
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid()
    )
  );

create policy "update schedules for linked devices"
  on schedules for update
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid() and cp.role = 'owner'
    )
  );

-- ── dose_events / device_status: read-only from the app's perspective.
--    Writes come from the EMQX webhook via a service-role Edge Function,
--    which bypasses RLS entirely — no insert policy needed here. ──
create policy "read dose events for linked devices"
  on dose_events for select
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid()
    )
  );

create policy "read status for linked devices"
  on device_status for select
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid()
    )
  );

-- ── alerts: caregiver can read and acknowledge (update), not create/delete ──
create policy "read alerts for linked devices"
  on alerts for select
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid()
    )
  );

create policy "acknowledge alerts for linked devices"
  on alerts for update
  using (
    device_id in (
      select d.id from devices d
      join caregiver_patients cp on cp.patient_id = d.patient_id
      where cp.caregiver_id = auth.uid()
    )
  );

-- ============================================================
--  SELF-SERVE ONBOARDING
--  One function, called via supabase.rpc(...) straight from the
--  app right after signup — no Edge Function, no invite step.
--  security definer means it runs with the owning role's privileges
--  (the role that ran this migration), which owns these tables and
--  therefore bypasses RLS for the duration of the function only.
--  It ALWAYS creates a new patient — it never links the caller to
--  an existing one — so there is no "guess someone else's patient_id"
--  attack surface, even without an insert policy on either table.
-- ============================================================

create or replace function create_patient_and_claim(
  p_patient_name  text,
  p_timezone      text,
  p_pairing_code  text
)
returns table (patient_id uuid, device_id uuid)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_patient_id uuid;
  v_device_id  uuid;
begin
  select id into v_device_id
  from devices
  where pairing_code = p_pairing_code
    and claimed_at is null
  for update;  -- lock the row so two callers can't claim it at once

  if v_device_id is null then
    raise exception 'invalid_or_already_claimed_pairing_code';
  end if;

  insert into patients (full_name, timezone)
  values (p_patient_name, p_timezone)
  returning id into v_patient_id;

  insert into caregiver_patients (caregiver_id, patient_id, role)
  values (auth.uid(), v_patient_id, 'owner');

  update devices
  set patient_id = v_patient_id, claimed_at = now()
  where id = v_device_id;

  return query select v_patient_id, v_device_id;
end;
$$;

-- Any signed-in user may call this — the function body is what's
-- trusted, not the caller's own insert/update rights on the tables.
grant execute on function create_patient_and_claim(text, text, text) to authenticated;