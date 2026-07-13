-- ============================================================
--  Data API grants.
--  As of the current Postgres image, new public-schema tables are no
--  longer auto-exposed to the Data API roles (anon/authenticated/
--  service_role) — see api.auto_expose_new_tables in config.toml.
--  RLS policies alone are not enough: Postgres checks the base table
--  GRANT before it ever evaluates RLS, so without this, every role
--  (service_role included, despite bypassing RLS) gets a flat
--  "permission denied" on every table.
-- ============================================================

-- service_role is the trusted device/broker and scheduled-job boundary
-- (emqx-webhook, check-missed-doses) — it bypasses RLS entirely, so it
-- gets full CRUD on every table rather than a per-table allowlist.
grant all on all tables in schema public to service_role;

-- authenticated (the app, via the caller's own JWT) relies on RLS to
-- restrict rows — these grants just allow the operations that the
-- existing policies then filter down to "your own" data.
grant select, update on patients to authenticated;
grant select, delete on caregiver_patients to authenticated;
grant select on devices to authenticated;
grant select, update on schedules to authenticated;
grant select on dose_events to authenticated;
grant select on device_status to authenticated;
grant select, update on alerts to authenticated;
grant select, insert, update, delete on caregiver_push_tokens to authenticated;

-- No grants for anon: nothing in this app is meant to be reachable
-- without signing in, and every RLS policy here keys off auth.uid()
-- anyway, so an anon grant would be a no-op at best.

-- The grants above only cover tables that exist right now — "on all
-- tables in schema public" is a one-time snapshot, not a standing rule.
-- Without this, any table a *future* migration adds would hit the exact
-- same silent "permission denied" wall. service_role gets full access on
-- new tables by default (matches its trusted role); authenticated gets a
-- default of read-only, so a future migration adding a table still has
-- to explicitly opt in to write access — same posture as RLS policies
-- already being explicit per table.
alter default privileges in schema public grant all on tables to service_role;
alter default privileges in schema public grant select on tables to authenticated;
