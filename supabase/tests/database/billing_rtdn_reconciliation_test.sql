begin;
select plan(47);

select ok(
  to_regclass('internal.billing_event_receipts') is not null,
  'RTDN receipts are stored in the internal schema'
);
select ok(
  to_regclass('internal.billing_reconciliation_jobs') is not null,
  'reconciliation jobs are stored in the internal schema'
);
select ok(
  coalesce((select c.relrowsecurity and c.relforcerowsecurity
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_event_receipts'), false),
  'event receipts enable and force row-level security'
);
select ok(
  coalesce((select c.relrowsecurity and c.relforcerowsecurity
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_reconciliation_jobs'), false),
  'reconciliation jobs enable and force row-level security'
);
select ok(
  not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_event_receipts'
      and (has_table_privilege('anon', c.oid, 'SELECT,INSERT,UPDATE,DELETE')
        or has_table_privilege('authenticated', c.oid, 'SELECT,INSERT,UPDATE,DELETE'))),
  'client roles have no receipt table access'
);
select ok(
  not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_reconciliation_jobs'
      and (has_table_privilege('anon', c.oid, 'SELECT,INSERT,UPDATE,DELETE')
        or has_table_privilege('authenticated', c.oid, 'SELECT,INSERT,UPDATE,DELETE'))),
  'client roles have no reconciliation table access'
);
select ok(
  not exists (select 1 from pg_attribute a join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_event_receipts'
      and a.attname = 'purchase_token' and a.attnum > 0 and not a.attisdropped),
  'receipts never store a raw purchase token column'
);
select ok(
  not exists (select 1 from pg_attribute a join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'internal' and c.relname = 'billing_reconciliation_jobs'
      and a.attname = 'purchase_token' and a.attnum > 0 and not a.attisdropped),
  'jobs never store a raw purchase token column'
);
select ok(
  exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'begin_billing_rtdn_event'
      and has_function_privilege('service_role', p.oid, 'EXECUTE')
      and not has_function_privilege('anon', p.oid, 'EXECUTE')
      and not has_function_privilege('authenticated', p.oid, 'EXECUTE')),
  'only the server role can begin RTDN processing'
);
select ok(
  exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'persist_verified_rtdn_billing_purchase'
      and has_function_privilege('service_role', p.oid, 'EXECUTE')
      and not has_function_privilege('anon', p.oid, 'EXECUTE')
      and not has_function_privilege('authenticated', p.oid, 'EXECUTE')),
  'only the server role can atomically persist an RTDN verification'
);
select ok(
  exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'persist_verified_restored_billing_purchase'
      and has_function_privilege('service_role', p.oid, 'EXECUTE')
      and not has_function_privilege('anon', p.oid, 'EXECUTE')
      and not has_function_privilege('authenticated', p.oid, 'EXECUTE')),
  'only the server role can atomically resume a hash-only RTDN during restore'
);
select ok(
  exists (select 1 from pg_index i join pg_class t on t.oid = i.indrelid
    join pg_namespace n on n.oid = t.relnamespace
    join pg_class idx on idx.oid = i.indexrelid
    where n.nspname = 'internal' and t.relname = 'billing_event_receipts'
      and i.indisunique and pg_get_indexdef(i.indexrelid) like '%event_identity_hash%'),
  'stable event identity has a unique constraint'
);
select ok(
  exists (select 1 from pg_index i
    where i.indrelid = 'public.billing_purchases'::regclass
      and i.indisunique and i.indnatts = 1 and i.indnkeyatts = 1
      and pg_get_indexdef(i.indexrelid) like '%(purchase_token_hash)%'),
  'purchase token ownership stays globally unique'
);
select ok(
  exists (select 1 from pg_constraint c join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where n.nspname = 'internal' and t.relname = 'billing_reconciliation_jobs'
      and c.conname = 'billing_reconciliation_jobs_lease_pair_check'),
  'lease owner and expiry are constrained as a pair'
);

select set_config('test.rtdn_purchase_count', (select count(*)::text from public.billing_purchases), true);
select set_config('test.rtdn_event_count', (select count(*)::text from internal.billing_events), true);

create temporary table rtdn_waiting_result as
select * from public.begin_billing_rtdn_event(
  repeat('a', 64), 'pgtap-message-waiting', repeat('b', 64), 'SUBSCRIPTION'
);
select is((select receipt_status from rtdn_waiting_result), 'WAITING_FOR_TOKEN', 'unknown owner stays waiting');
select is((select user_id from rtdn_waiting_result), null::uuid, 'RTDN does not infer a Kipu owner');
select is((select count(*)::text from public.billing_purchases), current_setting('test.rtdn_purchase_count'), 'waiting RTDN does not create a purchase');
select is((select count(*)::text from internal.billing_events), current_setting('test.rtdn_event_count'), 'waiting RTDN does not append a verified billing event');

create temporary table rtdn_first_delivery as
select * from public.begin_billing_rtdn_event(
  repeat('c', 64), 'pgtap-message-duplicate', repeat('d', 64), 'SUBSCRIPTION'
);
create temporary table rtdn_redelivery as
select * from public.begin_billing_rtdn_event(
  repeat('c', 64), 'pgtap-message-duplicate', repeat('d', 64), 'SUBSCRIPTION'
);
select is((select duplicate from rtdn_first_delivery), false, 'first delivery creates a receipt');
select is((select duplicate from rtdn_redelivery), true, 'stable message redelivery returns duplicate');
select is((select receipt_id from rtdn_first_delivery), (select receipt_id from rtdn_redelivery), 'redelivery returns one canonical receipt');

create temporary table rtdn_missing_token as
select * from public.begin_billing_rtdn_event(
  repeat('e', 64), 'pgtap-message-no-token', null, 'TEST'
);
select is((select receipt_status from rtdn_missing_token), 'WAITING_FOR_TOKEN', 'event without a token remains waiting');
select is((select job_id from rtdn_missing_token), null::uuid, 'no-token event does not create an uncorrelated job');

insert into auth.users (id, email) values
  ('73000000-0000-4000-8000-000000000003', 'billing-rtdn@example.test');
insert into public.billing_products (id, store_product_id, base_plan_id, name, plan_type)
values ('kipu_rtdn_monthly', 'kipu_pro_monthly', 'monthly', 'Kipu Pro mensual', 'PRO_MONTHLY');
insert into public.billing_purchases (
  id, user_id, purchase_token_hash, product_id, purchase_state, entitlement_state,
  acknowledgement_state, starts_at, expires_at, verified_at
) values (
  '74000000-0000-4000-8000-000000000003',
  '73000000-0000-4000-8000-000000000003', repeat('9', 64), 'kipu_rtdn_monthly',
  'PURCHASED', 'ACTIVE', 'ACKNOWLEDGED', now(), now() + interval '30 days', now()
);
create temporary table rtdn_known_owner as
select * from public.begin_billing_rtdn_event(
  repeat('f', 64), 'pgtap-known-purchase', repeat('9', 64), 'SUBSCRIPTION'
);
create temporary table rtdn_atomic_result as
select * from public.persist_verified_rtdn_billing_purchase(
  '73000000-0000-4000-8000-000000000003',
  'kipu_pro_monthly', repeat('9', 64), 'GPA.RTDN', 'PURCHASED', 'ACTIVE',
  'ACKNOWLEDGED', now(), now() + interval '45 days',
  '{"provider":"GOOGLE_PLAY","productId":"kipu_pro_monthly","purchaseState":"PURCHASED"}'::jsonb,
  (select receipt_id from rtdn_known_owner),
  (select job_id from rtdn_known_owner),
  (select lease_owner from rtdn_known_owner)
);
select is((select result from rtdn_atomic_result), 'VERIFIED', 'RTDN uses the shared current purchase writer');
select is((select receipt_status from internal.billing_event_receipts where event_identity_hash = repeat('f', 64)), 'COMPLETED', 'purchase receipt commits with verification');
select is((select job_status from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('9', 64)), 'COMPLETED', 'reconciliation job commits with verification');
select is((select lease_owner from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('9', 64)), null::uuid, 'atomic completion releases the per-purchase lease');
select is((select effective_premium from rtdn_atomic_result), true, 'shared writer returns current account entitlement');
select is((select count(*)::integer from internal.billing_events where purchase_token_hash = repeat('9', 64)), 1, 'shared writer appends one sanitized verification event');
select ok(not ((select raw_payload::text from internal.billing_events where purchase_token_hash = repeat('9', 64)) like '%purchaseToken%'), 'billing event contains no raw token');

create temporary table restore_waiting_result as
select * from public.begin_billing_rtdn_event(
  repeat('8', 64), 'pgtap-restore-resume', repeat('7', 64), 'SUBSCRIPTION'
);
create temporary table restored_purchase_result (
  result text,
  purchase_id uuid,
  effective_premium boolean,
  effective_expires_at timestamptz
);
select lives_ok($restore$
  insert into restored_purchase_result
  select * from public.persist_verified_restored_billing_purchase(
    '73000000-0000-4000-8000-000000000003',
    'kipu_pro_monthly', repeat('7', 64), 'GPA.RESTORE-ORDER', 'PURCHASED', 'ACTIVE',
    'ACKNOWLEDGED', now(), now() + interval '30 days',
    '{"provider":"GOOGLE_PLAY","productId":"kipu_pro_monthly","purchaseState":"PURCHASED"}'::jsonb
  )
$restore$, 'restore RPC accepts a current token through the authenticated verifier boundary');
select is((select result from restored_purchase_result limit 1), 'VERIFIED', 'restore returns the verified purchase outcome');
select is((select receipt_status from internal.billing_event_receipts where event_identity_hash = repeat('8', 64)), 'COMPLETED', 'restore completes the hash-only RTDN receipt');
select is((select safe_result_code from internal.billing_event_receipts where event_identity_hash = repeat('8', 64)), 'RESTORED_VERIFIED', 'restore records a sanitized completion reason');
select is((select user_id from internal.billing_event_receipts where event_identity_hash = repeat('8', 64)), '73000000-0000-4000-8000-000000000003'::uuid, 'verified restore binds the previously unknown receipt to the authenticated owner');
select is((select job_status from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('7', 64)), 'COMPLETED', 'restore completes the hash-only reconciliation job');
select is((select user_id from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('7', 64)), '73000000-0000-4000-8000-000000000003'::uuid, 'verified restore binds the job to the authenticated owner');
select is((select lease_owner from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('7', 64)), null::uuid, 'restore leaves no active reconciliation lease');
select is((select count(*)::integer from public.billing_purchases where purchase_token_hash = repeat('7', 64)), 1, 'restore persists exactly one globally owned purchase');
select ok((select effective_premium from restored_purchase_result limit 1), 'restore returns the provider-derived effective entitlement');

create temporary table restore_conflict_waiting as
select * from public.begin_billing_rtdn_event(
  repeat('6', 64), 'pgtap-restore-owner-conflict', repeat('6', 64), 'SUBSCRIPTION'
);
insert into auth.users (id, email) values
  ('73000000-0000-4000-8000-000000000004', 'billing-rtdn-owner@example.test');
insert into public.billing_purchases (
  id, user_id, purchase_token_hash, product_id, purchase_state, entitlement_state,
  acknowledgement_state, starts_at, expires_at, verified_at
) values (
  '74000000-0000-4000-8000-000000000004',
  '73000000-0000-4000-8000-000000000004', repeat('6', 64), 'kipu_rtdn_monthly',
  'PURCHASED', 'ACTIVE', 'ACKNOWLEDGED', now(), now() + interval '30 days', now()
);
create temporary table restore_conflict_result (
  result text,
  purchase_id uuid,
  effective_premium boolean,
  effective_expires_at timestamptz
);
select lives_ok($restore_conflict$
  insert into restore_conflict_result
  select * from public.persist_verified_restored_billing_purchase(
    '73000000-0000-4000-8000-000000000003',
    'kipu_pro_monthly', repeat('6', 64), 'GPA.RESTORE-CONFLICT', 'PURCHASED', 'ACTIVE',
    'ACKNOWLEDGED', now(), now() + interval '30 days',
    '{"provider":"GOOGLE_PLAY","productId":"kipu_pro_monthly","purchaseState":"PURCHASED"}'::jsonb
  )
$restore_conflict$, 'restore owner conflict is returned without changing the waiting work item');
select is((select result from restore_conflict_result limit 1), 'TOKEN_ACCOUNT_CONFLICT', 'restore rejects a purchase owned by another Kipu account');
select is((select user_id from public.billing_purchases where purchase_token_hash = repeat('6', 64)), '73000000-0000-4000-8000-000000000004'::uuid, 'restore never transfers an existing purchase');
select is((select receipt_status from internal.billing_event_receipts where event_identity_hash = repeat('6', 64)), 'WAITING_FOR_TOKEN', 'conflicted restore leaves the receipt waiting');
select is((select user_id from internal.billing_event_receipts where event_identity_hash = repeat('6', 64)), null::uuid, 'conflicted restore does not bind the receipt to the caller');
select is((select job_status from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('6', 64)), 'WAITING_FOR_TOKEN', 'conflicted restore leaves the reconciliation job waiting');
select is((select user_id from internal.billing_reconciliation_jobs where purchase_token_hash = repeat('6', 64)), null::uuid, 'conflicted restore does not bind the job to the caller');

select * from finish();
rollback;
