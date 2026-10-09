-- Apply only after the live schema/function baseline is reconciled and pg_cron,
-- pg_net, and Vault are available. This file intentionally does not enable extensions.
do $$
begin
  if to_regnamespace('cron') is null
    or to_regprocedure('cron.schedule(text,text,text)') is null then
    raise exception 'Enable and review pg_cron before scheduling billing reconciliation';
  end if;
  if to_regnamespace('net') is null then
    raise exception 'Enable and review pg_net before scheduling billing reconciliation';
  end if;
  if to_regclass('vault.decrypted_secrets') is null then
    raise exception 'Create the required billing reconciliation credentials in Vault first';
  end if;
  if (select count(*) from vault.decrypted_secrets
      where name in ('project_url','publishable_key','reconcile_billing_secret')) <> 3 then
    raise exception 'Vault must contain project_url, publishable_key, and reconcile_billing_secret';
  end if;
end;
$$;

select cron.unschedule(jobid)
from cron.job
where jobname = 'kipu-billing-reconciliation-s5';

select cron.schedule(
  'kipu-billing-reconciliation-s5',
  '*/15 * * * *',
  $cron$
    select net.http_post(
      url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url')
        || '/functions/v1/reconcile-billing',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'publishable_key'),
        'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'reconcile_billing_secret')
      ),
      body := '{}'::jsonb,
      timeout_milliseconds := 5000
    );
  $cron$
);
