-- completeOnboarding() (Schedule Setup's final step) needs to flip
-- devices.onboarding_completed, but devices has deliberately never had a
-- client-side UPDATE policy at all (see the comment above "read linked
-- devices" in this file) — patient_id/claimed_at/pairing_code must only
-- ever change via create_patient_and_claim(). That meant the app's update
-- was silently matching zero rows (RLS blocks it, Postgrest still returns
-- 200 with no error) — every sign-in re-routed back to Schedule Setup
-- forever, since onboarding_completed never actually became true.
--
-- Fix: grant UPDATE on exactly the one column that's safe for the app to
-- touch, so patient_id/claimed_at/pairing_code/device_uid/firmware_version
-- stay protected even though a row-level policy now exists.
grant update (onboarding_completed) on devices to authenticated;

create policy "update onboarding_completed for linked devices"
  on devices for update
  using (
    patient_id in (
      select patient_id from caregiver_patients
      where caregiver_id = auth.uid() and role = 'owner'
    )
  )
  with check (
    patient_id in (
      select patient_id from caregiver_patients
      where caregiver_id = auth.uid() and role = 'owner'
    )
  );
