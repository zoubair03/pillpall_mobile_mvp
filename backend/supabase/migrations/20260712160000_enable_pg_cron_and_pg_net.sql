-- ============================================================
--  pg_cron + pg_net — needed to schedule check-missed-doses to
--  run on its own, instead of requiring a manual invocation.
--  pg_cron runs the schedule; pg_net makes the outbound HTTP
--  call to the Edge Function from inside Postgres.
-- ============================================================

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

grant usage on schema cron to postgres;
grant all on all tables in schema cron to postgres;
