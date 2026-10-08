begin;
select plan(14);

select ok(
  exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'sweep_billing_reconciliation_jobs'
      and has_function_privilege('service_role', p.oid, 'EXECUTE')
      and not has_function_privilege('anon', p.oid, 'EXECUTE')
      and not has_function_privilege('authenticated', p.oid, 'EXECUTE')),
  'only the server role can run the reconciliation sweep'
);

insert into auth.users (id, email)
values ('73000000-0000-4000-8000-000000000004', 'billing-sweep@example.test');
insert into public.billing_products (id, store_product_id, base_plan_id, name, plan_type)
values ('kipu_sweep_monthly', 'kipu_sweep_monthly', 'monthly', 'Kipu Pro mensual', 'PRO_MONTHLY');
insert into public.billing_purchases (
  id, user_id, purchase_token_hash, product_id, purchase_state, entitlement_state,
  acknowledgement_state, starts_at, expires_at, verified_at
) values (
  '74000000-0000-4000-8000-000000000004',
  '73000000-0000-4000-8000-000000000004', repeat('8', 64), 'kipu_sweep_monthly',
  'PURCHASED', 'ACTIVE', 'ACKNOWLEDGED', now(), now() + interval '30 days', now()
);
create temporary table sweep_active_job as
select * from public.begin_billing_rtdn_event(
  repeat('7', 64), 'pgtap-sweep-expired-lease', repeat('8', 64), 'SUBSCRIPTION'
);
update internal.billing_reconciliation_jobs
set lease_expires_at = now() - interval '1 minute'
where id = (select job_id from sweep_active_job);
select set_config('test.sweep_purchase_count', (select count(*)::text from public.billing_purchases), true);
select set_config('test.sweep_billing_event_count', (select count(*)::text from internal.billing_events), true);

create temporary table sweep_expired_lease as
select * from public.sweep_billing_reconciliation_jobs(100);
select is((select reclaimed_count from sweep_expired_lease), 1, 'expired lease is reclaimed without provider access');
select is((select job_status from internal.billing_reconciliation_jobs where id = (select job_id from sweep_active_job)), 'WAITING_FOR_TOKEN', 'hash-only job returns to token waiting');
select is((select lease_owner from internal.billing_reconciliation_jobs where id = (select job_id from sweep_active_job)), null::uuid, 'expired lease owner is cleared');
select is((select attempt_count from internal.billing_reconciliation_jobs where id = (select job_id from sweep_active_job)), 1, 'lease-only sweep does not count a provider attempt');
select is((select count(*)::text from public.billing_purchases), current_setting('test.sweep_purchase_count'), 'hash-only sweep does not mutate purchases or entitlements');
select is((select count(*)::text from internal.billing_events), current_setting('test.sweep_billing_event_count'), 'hash-only sweep does not append a billing event');
select is((select entitlement_state from public.billing_purchases where purchase_token_hash = repeat('8', 64)), 'ACTIVE', 'previously verified entitlement remains unchanged until its known expiry');

update internal.billing_reconciliation_jobs
set job_status = 'RETRYABLE', lease_owner = null, lease_expires_at = null,
    next_attempt_at = now() + interval '10 minutes'
where purchase_token_hash = repeat('8', 64);
create temporary table sweep_before_backoff as
select * from public.sweep_billing_reconciliation_jobs(100);
select is((select reclaimed_count from sweep_before_backoff), 0, 'retry backoff is respected');
select is((select job_status from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('8', 64)), 'RETRYABLE', 'future retry remains scheduled');

update internal.billing_reconciliation_jobs
set next_attempt_at = now() - interval '1 second'
where purchase_token_hash = repeat('8', 64);
create temporary table sweep_due_retry as
select * from public.sweep_billing_reconciliation_jobs(100);
select is((select reclaimed_count from sweep_due_retry), 1, 'due retry is classified without a provider query');
select is((select job_status from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('8', 64)), 'WAITING_FOR_TOKEN', 'due hash-only retry waits for a fresh token');

create temporary table sweep_fresh_token as
select * from public.begin_billing_rtdn_event(
  repeat('6', 64), 'pgtap-sweep-resume-with-token', repeat('8', 64), 'SUBSCRIPTION'
);
select is((select receipt_status from sweep_fresh_token), 'PROCESSING', 'fresh RTDN token resumes the shared verifier path');
select ok((select lease_owner is not null from sweep_fresh_token), 'resumed work owns an exclusive per-purchase lease');

select * from finish();
rollback;
