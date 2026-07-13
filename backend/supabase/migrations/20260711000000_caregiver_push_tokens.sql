-- ============================================================
--  caregiver_push_tokens — FCM registration per caregiver.
--  One row per caregiver (upserted on (re)registration), mirroring
--  the device_status "latest known state" pattern. Needed by the
--  check-missed-doses function to know where to send push alerts.
-- ============================================================

-- caregiver_id defaults to auth.uid() so the app can upsert its own row
-- with just { fcm_token } and rely on RLS/the default to scope it —
-- without a default, an insert that omits caregiver_id leaves it null,
-- and "null = auth.uid()" in the RLS check below fails closed (403)
-- rather than doing anything unsafe, but it also means a "normal" insert
-- from the app would never work at all.
create table caregiver_push_tokens (
  caregiver_id  uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  fcm_token     text not null,
  updated_at    timestamptz not null default now()
);

alter table caregiver_push_tokens enable row level security;

-- Unlike patients/caregiver_patients, there's no attack surface in
-- letting a caregiver manage their own token row directly — it's
-- keyed off auth.uid(), not a guessable foreign id.
create policy "read own push token"
  on caregiver_push_tokens for select
  using (caregiver_id = auth.uid());

create policy "insert own push token"
  on caregiver_push_tokens for insert
  with check (caregiver_id = auth.uid());

create policy "update own push token"
  on caregiver_push_tokens for update
  using (caregiver_id = auth.uid())
  with check (caregiver_id = auth.uid());

create policy "delete own push token"
  on caregiver_push_tokens for delete
  using (caregiver_id = auth.uid());
