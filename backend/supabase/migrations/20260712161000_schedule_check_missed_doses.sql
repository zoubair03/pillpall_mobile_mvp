-- ============================================================
--  Schedules check-missed-doses to run every 5 minutes via
--  pg_cron + pg_net. The project URL and service role key are
--  pulled from Vault at run time (stored there manually via the
--  Dashboard as 'project_url' and 'service_role_key') rather
--  than appearing in this migration, consistent with never
--  hardcoding secrets or tying the migration to one project.
-- ============================================================

select cron.schedule(
  'check-missed-doses',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url') || '/functions/v1/check-missed-doses',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
    ),
    body := '{}'::jsonb
  ) as request_id;
  $$
);
