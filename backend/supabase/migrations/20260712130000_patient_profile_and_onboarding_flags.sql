-- Supports the redesigned onboarding flow: patient profile (name/age/DOB/
-- conditions) and schedule setup now happen as dedicated screens *after*
-- pairing, instead of collecting a patient name at signup and leaving
-- schedules empty until the caregiver happens to edit "Horaires" later.
--
-- age/date_of_birth are both independently nullable rather than deriving
-- one from the other: a caregiver may know an elderly patient's
-- approximate age without knowing their exact birth date, or vice versa.
--
-- profile_completed/onboarding_completed let the app resume onboarding at
-- the right screen after an interrupted session (see
-- routeAfterAuthentication() in PillPalViewModel.kt) without relying on
-- fragile heuristics like "is full_name still the placeholder value".
alter table patients add column if not exists age int;
alter table patients add column if not exists date_of_birth date;
alter table patients add column if not exists conditions text;
alter table patients add column if not exists profile_completed boolean not null default false;

alter table devices add column if not exists onboarding_completed boolean not null default false;

-- No RLS changes: "update linked patients" / the devices update policy
-- are table-level and already cover these new columns.

-- create_patient_and_claim now also seeds 3 default schedule rows
-- (matching the app's old mock defaults) so the new Schedule Setup
-- onboarding screen has real rows to UPDATE via the existing
-- ScheduleRepository.updateTime() — schedules still has no client-side
-- insert policy, this keeps that invariant intact.
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

  insert into schedules (device_id, dose, time_of_day)
  values
    (v_device_id, 'morning', '08:00:00'),
    (v_device_id, 'midday',  '13:00:00'),
    (v_device_id, 'night',   '20:00:00');

  return query select v_patient_id, v_device_id;
end;
$$;
