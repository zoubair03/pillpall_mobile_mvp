-- ============================================================
--  Schedules check-missed-doses to run every 5 minutes via
--  pg_cron + pg_net. The service role key is pulled from Vault
--  at run time (stored there manually via the Dashboard) rather
--  than appearing in this migration, consistent with never
--  hardcoding secrets.
-- ============================================================

select cron.schedule(
  'check-missed-doses',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := 'https://riphkcjgnwfsfrluwnlo.supabase.co/functions/v1/check-missed-doses',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
    ),
    body := '{}'::jsonb
  ) as request_id;
  $$
);
