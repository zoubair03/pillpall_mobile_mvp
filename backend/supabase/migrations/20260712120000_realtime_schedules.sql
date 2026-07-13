-- Phase 3's realtime-publication migration added device_status/dose_events/
-- alerts but missed schedules, even though ScheduleRepository.observeSchedules()
-- (Phase 5) subscribes to it via selectAsFlow. The missing table caused the
-- channel subscription to error out repeatedly ("Unable to subscribe to
-- changes... Realtime is enabled for the given connect parameters"), which
-- was tearing down the shared Realtime socket connection for the app's
-- other channels too. Idempotent, same pattern as the earlier migration.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'schedules'
  ) then
    alter publication supabase_realtime add table schedules;
  end if;
end $$;
