-- ============================================================
--  Ensure device_status, dose_events, and alerts are published for
--  Realtime — the Android app subscribes to Postgres Changes on these
--  so the dashboard/hub screens update live without polling.
--
--  Whether a table is in the supabase_realtime publication is
--  dashboard/DB state, not something any prior migration recorded, so
--  this can't just assume a starting point. ADD TABLE has no
--  IF NOT EXISTS form and errors on a table that's already a member,
--  so this checks pg_publication_tables first and only adds what's
--  actually missing — safe to run against a project in any prior state.
-- ============================================================

do $$
declare
  t text;
begin
  foreach t in array array['device_status', 'dose_events', 'alerts'] loop
    if not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;
