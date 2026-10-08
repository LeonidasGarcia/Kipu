do $$
begin
  if to_regnamespace('internal') is null
    or to_regclass('internal.billing_events') is null
    or to_regclass('public.billing_purchases') is null
    or to_regclass('public.billing_products') is null then
    raise exception 'S5 billing RTDN requires the verified S3 billing schema';
  end if;
  if to_regclass('internal.billing_event_receipts') is not null
    or to_regclass('internal.billing_reconciliation_jobs') is not null then
    raise exception 'S5 billing RTDN objects already exist; reconcile schema drift before migration';
  end if;
  if not exists (select 1 from pg_roles where rolname = 'billing_verification_executor') then
    raise exception 'S5 billing RTDN requires billing_verification_executor';
  end if;
  if not exists (select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'billing_purchases'
      and column_name in ('user_id', 'purchase_token_hash', 'product_id')) then
    raise exception 'S5 billing RTDN found an unexpected billing_purchases schema';
  end if;
end;
$$;

do $billing_token_owner_uniqueness$
begin
  if to_regclass('public.billing_purchases_purchase_token_hash_uidx') is not null
    and not exists (
      select 1 from pg_index i
      where i.indexrelid = to_regclass('public.billing_purchases_purchase_token_hash_uidx')
        and i.indisunique and i.indisvalid and i.indpred is null and i.indexprs is null
        and i.indnatts = 1 and i.indnkeyatts = 1
        and pg_get_indexdef(i.indexrelid) like '%(purchase_token_hash)%'
    ) then
    raise exception 'S5 found a conflicting billing token index; reconcile schema drift';
  end if;
  if not exists (
    select 1 from pg_index i
    where i.indrelid = 'public.billing_purchases'::regclass
      and i.indisunique and i.indisvalid and i.indpred is null and i.indexprs is null
      and i.indnatts = 1 and i.indnkeyatts = 1
      and pg_get_indexdef(i.indexrelid) like '%(purchase_token_hash)%'
  ) then
    if exists (
      select 1 from public.billing_purchases
      group by purchase_token_hash having count(*) > 1
    ) then
      raise exception 'S5 cannot restore unique billing token ownership while duplicate hashes exist';
    end if;
    execute 'create unique index billing_purchases_purchase_token_hash_uidx on public.billing_purchases (purchase_token_hash)';
  end if;
end;
$billing_token_owner_uniqueness$;

create table internal.billing_event_receipts (
  id uuid primary key default gen_random_uuid(),
  event_identity_hash text not null,
  provider_message_id text not null,
  purchase_token_hash text,
  user_id uuid references auth.users(id) on delete restrict,
  notification_type text not null,
  receipt_status text not null,
  safe_result_code text,
  received_at timestamptz not null default clock_timestamp(),
  updated_at timestamptz not null default clock_timestamp(),
  completed_at timestamptz,
  constraint billing_event_receipts_identity_hash_check
    check (event_identity_hash ~ '^[0-9a-f]{64}$'),
  constraint billing_event_receipts_message_id_check
    check (length(provider_message_id) between 1 and 256),
  constraint billing_event_receipts_token_hash_check
    check (purchase_token_hash is null or purchase_token_hash ~ '^[0-9a-f]{64}$'),
  constraint billing_event_receipts_type_check
    check (notification_type in ('SUBSCRIPTION','ONE_TIME_PRODUCT','VOIDED_PURCHASE','TEST')),
  constraint billing_event_receipts_status_check
    check (receipt_status in ('WAITING_FOR_TOKEN','PROCESSING','RETRYABLE','COMPLETED','REJECTED')),
  constraint billing_event_receipts_safe_code_check
    check (safe_result_code is null or safe_result_code ~ '^[A-Z0-9_]{1,64}$'),
  constraint billing_event_receipts_completed_at_check
    check ((receipt_status in ('COMPLETED','REJECTED')) = (completed_at is not null)),
  constraint billing_event_receipts_identity_key unique (event_identity_hash)
);

create table internal.billing_reconciliation_jobs (
  id uuid primary key default gen_random_uuid(),
  purchase_token_hash text not null,
  user_id uuid references auth.users(id) on delete restrict,
  latest_event_receipt_id uuid references internal.billing_event_receipts(id) on delete restrict,
  job_status text not null,
  attempt_count integer not null default 0,
  next_attempt_at timestamptz,
  lease_owner uuid,
  lease_expires_at timestamptz,
  last_safe_error_code text,
  created_at timestamptz not null default clock_timestamp(),
  updated_at timestamptz not null default clock_timestamp(),
  constraint billing_reconciliation_jobs_token_hash_check
    check (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  constraint billing_reconciliation_jobs_status_check
    check (job_status in ('WAITING_FOR_TOKEN','PROCESSING','RETRYABLE','COMPLETED','REJECTED')),
  constraint billing_reconciliation_jobs_attempt_count_check
    check (attempt_count >= 0),
  constraint billing_reconciliation_jobs_safe_code_check
    check (last_safe_error_code is null or last_safe_error_code ~ '^[A-Z0-9_]{1,64}$'),
  constraint billing_reconciliation_jobs_lease_pair_check
    check ((lease_owner is null) = (lease_expires_at is null)),
  constraint billing_reconciliation_jobs_processing_lease_check
    check (job_status <> 'PROCESSING' or lease_owner is not null),
  constraint billing_reconciliation_jobs_purchase_hash_key unique (purchase_token_hash)
);

create index billing_event_receipts_status_updated_idx
  on internal.billing_event_receipts (receipt_status, updated_at);
create index billing_reconciliation_jobs_status_retry_idx
  on internal.billing_reconciliation_jobs (job_status, next_attempt_at, lease_expires_at);
create index billing_reconciliation_jobs_owner_idx
  on internal.billing_reconciliation_jobs (user_id, updated_at desc);

alter table internal.billing_event_receipts enable row level security;
alter table internal.billing_event_receipts force row level security;
alter table internal.billing_reconciliation_jobs enable row level security;
alter table internal.billing_reconciliation_jobs force row level security;

create policy billing_event_receipts_executor_all
  on internal.billing_event_receipts to billing_verification_executor
  using (true) with check (true);
create policy billing_reconciliation_jobs_executor_all
  on internal.billing_reconciliation_jobs to billing_verification_executor
  using (true) with check (true);

revoke all on internal.billing_event_receipts, internal.billing_reconciliation_jobs
  from public, anon, authenticated, service_role;
grant usage on schema internal to billing_verification_executor;
grant select, insert, update, delete
  on internal.billing_event_receipts, internal.billing_reconciliation_jobs
  to billing_verification_executor;
grant select on public.billing_purchases, public.billing_products
  to billing_verification_executor;

create function public.begin_billing_rtdn_event(
  p_event_identity_hash text,
  p_provider_message_id text,
  p_purchase_token_hash text,
  p_notification_type text
)
returns table(
  receipt_id uuid,
  duplicate boolean,
  receipt_status text,
  user_id uuid,
  store_product_id text,
  billing_product_id text,
  job_id uuid,
  lease_owner uuid,
  safe_result_code text
)
language plpgsql security definer set search_path = '' as $$
declare
  v_receipt internal.billing_event_receipts%rowtype;
  v_job internal.billing_reconciliation_jobs%rowtype;
  v_user_id uuid;
  v_product_id text;
  v_billing_product_id text;
  v_lease_owner uuid;
  v_now timestamptz := clock_timestamp();
  v_insert_count integer;
  v_duplicate boolean := false;
begin
  if p_event_identity_hash is null or p_event_identity_hash !~ '^[0-9a-f]{64}$'
    or p_provider_message_id is null or length(p_provider_message_id) not between 1 and 256
    or (p_purchase_token_hash is not null and p_purchase_token_hash !~ '^[0-9a-f]{64}$')
    or p_notification_type is null
    or p_notification_type not in ('SUBSCRIPTION','ONE_TIME_PRODUCT','VOIDED_PURCHASE','TEST') then
    raise exception using errcode = '22023', message = 'invalid RTDN receipt identity';
  end if;

  insert into internal.billing_event_receipts as existing
    (event_identity_hash, provider_message_id, purchase_token_hash, notification_type,
     receipt_status, updated_at)
  values
    (p_event_identity_hash, p_provider_message_id, p_purchase_token_hash, p_notification_type,
     'WAITING_FOR_TOKEN', v_now)
  on conflict (event_identity_hash) do nothing;
  get diagnostics v_insert_count = row_count;
  v_duplicate := v_insert_count = 0;

  select r.* into v_receipt
  from internal.billing_event_receipts r
  where r.event_identity_hash = p_event_identity_hash
  for update;

  if v_receipt.provider_message_id <> p_provider_message_id
    or v_receipt.notification_type <> p_notification_type
    or (v_receipt.purchase_token_hash is not null and p_purchase_token_hash is not null
      and v_receipt.purchase_token_hash <> p_purchase_token_hash) then
    return query select v_receipt.id, true, 'REJECTED'::text, v_receipt.user_id,
      null::text, null::text, null::uuid, null::uuid, 'MESSAGE_ID_CONFLICT'::text;
    return;
  end if;

  if v_receipt.receipt_status in ('COMPLETED','REJECTED') then
    return query select v_receipt.id, true, v_receipt.receipt_status, v_receipt.user_id,
      null::text, null::text, null::uuid, null::uuid, v_receipt.safe_result_code;
    return;
  end if;

  if p_purchase_token_hash is null then
    update internal.billing_event_receipts r
    set receipt_status = 'WAITING_FOR_TOKEN', safe_result_code = 'TOKEN_UNAVAILABLE', updated_at = v_now
    where r.id = v_receipt.id
    returning r.* into v_receipt;
    return query select v_receipt.id, v_duplicate, v_receipt.receipt_status, v_receipt.user_id,
      null::text, null::text, null::uuid, null::uuid, v_receipt.safe_result_code;
    return;
  end if;

  select p.user_id, product.store_product_id, product.id
    into v_user_id, v_product_id, v_billing_product_id
  from public.billing_purchases p
  join public.billing_products product on product.id = p.product_id
  where p.purchase_token_hash = p_purchase_token_hash;

  update internal.billing_event_receipts r
  set purchase_token_hash = coalesce(r.purchase_token_hash, p_purchase_token_hash),
      user_id = v_user_id,
      receipt_status = case when v_user_id is null then 'WAITING_FOR_TOKEN' else 'PROCESSING' end,
      safe_result_code = case when v_user_id is null then 'OWNER_UNAVAILABLE' else null end,
      updated_at = v_now
  where r.id = v_receipt.id
  returning r.* into v_receipt;

  if v_user_id is null then
    insert into internal.billing_reconciliation_jobs as existing
      (purchase_token_hash, latest_event_receipt_id, job_status, updated_at)
    values (p_purchase_token_hash, v_receipt.id, 'WAITING_FOR_TOKEN', v_now)
    on conflict (purchase_token_hash) do update set
      latest_event_receipt_id = excluded.latest_event_receipt_id,
      job_status = case when existing.lease_expires_at > v_now then existing.job_status else 'WAITING_FOR_TOKEN' end,
      lease_owner = case when existing.lease_expires_at > v_now then existing.lease_owner else null end,
      lease_expires_at = case when existing.lease_expires_at > v_now then existing.lease_expires_at else null end,
      updated_at = v_now
    returning * into v_job;
    return query select v_receipt.id, v_duplicate, 'WAITING_FOR_TOKEN'::text, null::uuid,
      null::text, null::text, v_job.id, null::uuid, 'OWNER_UNAVAILABLE'::text;
    return;
  end if;

  select j.* into v_job
  from internal.billing_reconciliation_jobs j
  where j.purchase_token_hash = p_purchase_token_hash
  for update;
  if found and v_job.lease_expires_at > v_now then
    update internal.billing_event_receipts r
    set receipt_status = 'RETRYABLE', safe_result_code = 'PURCHASE_PROCESSING', updated_at = v_now
    where r.id = v_receipt.id returning r.* into v_receipt;
    return query select v_receipt.id, v_duplicate, v_receipt.receipt_status, v_user_id,
      v_product_id, v_billing_product_id, v_job.id, null::uuid, v_receipt.safe_result_code;
    return;
  end if;

  v_lease_owner := gen_random_uuid();
  insert into internal.billing_reconciliation_jobs as existing
    (purchase_token_hash, user_id, latest_event_receipt_id, job_status, attempt_count,
     next_attempt_at, lease_owner, lease_expires_at, last_safe_error_code, updated_at)
  values
    (p_purchase_token_hash, v_user_id, v_receipt.id, 'PROCESSING', 1,
     null, v_lease_owner, v_now + interval '2 minutes', null, v_now)
  on conflict (purchase_token_hash) do update set
    user_id = excluded.user_id,
    latest_event_receipt_id = excluded.latest_event_receipt_id,
    job_status = 'PROCESSING',
    attempt_count = existing.attempt_count + 1,
    next_attempt_at = null,
    lease_owner = excluded.lease_owner,
    lease_expires_at = excluded.lease_expires_at,
    last_safe_error_code = null,
    updated_at = v_now
  returning * into v_job;

  return query select v_receipt.id, v_duplicate, 'PROCESSING'::text, v_user_id,
    v_product_id, v_billing_product_id, v_job.id, v_lease_owner, null::text;
end;
$$;

create function public.finish_billing_rtdn_event(
  p_receipt_id uuid,
  p_job_id uuid,
  p_lease_owner uuid,
  p_outcome text,
  p_safe_result_code text
)
returns boolean
language plpgsql security definer set search_path = '' as $$
declare
  v_now timestamptz := clock_timestamp();
  v_updated integer;
begin
  if p_outcome is null or p_outcome not in ('RETRYABLE','COMPLETED','REJECTED')
    or p_safe_result_code is null or p_safe_result_code !~ '^[A-Z0-9_]{1,64}$' then
    raise exception using errcode = '22023', message = 'invalid RTDN result';
  end if;
  update internal.billing_reconciliation_jobs j
  set job_status = p_outcome,
      next_attempt_at = case when p_outcome = 'RETRYABLE' then v_now + interval '1 minute' else null end,
      lease_owner = null,
      lease_expires_at = null,
      last_safe_error_code = case when p_outcome = 'RETRYABLE' then p_safe_result_code else null end,
      updated_at = v_now
  where j.id = p_job_id and j.latest_event_receipt_id = p_receipt_id
    and j.lease_owner = p_lease_owner and j.lease_expires_at > v_now;
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception using errcode = '40001', message = 'RTDN processing lease expired';
  end if;

  update internal.billing_event_receipts r
  set receipt_status = p_outcome,
      safe_result_code = p_safe_result_code,
      completed_at = case when p_outcome in ('COMPLETED','REJECTED') then v_now else null end,
      updated_at = v_now
  where r.id = p_receipt_id and r.receipt_status in ('PROCESSING','RETRYABLE');
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception using errcode = '40001', message = 'RTDN receipt is no longer active';
  end if;
  return true;
end;
$$;

create function public.persist_verified_rtdn_billing_purchase(
  p_user_id uuid,
  p_store_product_id text,
  p_purchase_token_hash text,
  p_order_id text,
  p_purchase_state text,
  p_entitlement_state text,
  p_acknowledgement_state text,
  p_starts_at timestamptz,
  p_expires_at timestamptz,
  p_event_payload jsonb,
  p_receipt_id uuid,
  p_job_id uuid,
  p_lease_owner uuid
)
returns table(result text, purchase_id uuid, effective_premium boolean, effective_expires_at timestamptz)
language plpgsql security definer set search_path = '' as $$
declare
  v_result text;
  v_purchase_id uuid;
  v_effective_premium boolean;
  v_effective_expires_at timestamptz;
  v_now timestamptz := clock_timestamp();
  v_updated integer;
begin
  if p_user_id is null or p_receipt_id is null or p_job_id is null or p_lease_owner is null
    or p_purchase_token_hash is null or p_purchase_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception using errcode = '22023', message = 'invalid RTDN verification context';
  end if;

  perform 1
  from internal.billing_reconciliation_jobs j
  join internal.billing_event_receipts r on r.id = p_receipt_id
  where j.id = p_job_id
    and j.purchase_token_hash = p_purchase_token_hash
    and j.user_id = p_user_id
    and j.latest_event_receipt_id = p_receipt_id
    and j.lease_owner = p_lease_owner
    and j.lease_expires_at > v_now
    and r.purchase_token_hash = p_purchase_token_hash
    and r.user_id = p_user_id
    and r.receipt_status = 'PROCESSING'
  for update of j, r;
  if not found then
    raise exception using errcode = '40001', message = 'RTDN processing lease is invalid';
  end if;

  select p.result, p.purchase_id, p.effective_premium, p.effective_expires_at
    into v_result, v_purchase_id, v_effective_premium, v_effective_expires_at
  from public.persist_verified_billing_purchase(
    p_user_id,
    p_store_product_id,
    p_purchase_token_hash,
    p_order_id,
    p_purchase_state,
    p_entitlement_state,
    p_acknowledgement_state,
    p_starts_at,
    p_expires_at,
    p_event_payload
  ) p;

  update internal.billing_reconciliation_jobs j
  set job_status = case when v_result = 'TOKEN_ACCOUNT_CONFLICT' then 'REJECTED' else 'COMPLETED' end,
      lease_owner = null,
      lease_expires_at = null,
      next_attempt_at = null,
      last_safe_error_code = case when v_result = 'TOKEN_ACCOUNT_CONFLICT' then v_result else null end,
      updated_at = v_now
  where j.id = p_job_id;
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception using errcode = '40001', message = 'RTDN job finalization failed';
  end if;
  update internal.billing_event_receipts r
  set receipt_status = case when v_result = 'TOKEN_ACCOUNT_CONFLICT' then 'REJECTED' else 'COMPLETED' end,
      safe_result_code = v_result,
      completed_at = v_now,
      updated_at = v_now
  where r.id = p_receipt_id;
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception using errcode = '40001', message = 'RTDN receipt finalization failed';
  end if;

  return query select v_result, v_purchase_id, v_effective_premium, v_effective_expires_at;
end;
$$;

create function public.sweep_billing_reconciliation_jobs(p_limit integer)
returns table(reclaimed_count integer, waiting_count integer)
language plpgsql security definer set search_path = '' as $$
declare
  v_now timestamptz := clock_timestamp();
  v_reclaimed integer := 0;
  v_waiting integer := 0;
begin
  if p_limit is null or p_limit < 1 or p_limit > 500 then
    raise exception using errcode = '22023', message = 'invalid reconciliation batch size';
  end if;

  with candidates as (
    select j.id
    from internal.billing_reconciliation_jobs j
    where (j.job_status = 'PROCESSING' and j.lease_expires_at <= v_now)
      or (j.job_status = 'RETRYABLE' and j.next_attempt_at <= v_now)
    order by coalesce(j.lease_expires_at, j.next_attempt_at), j.created_at
    for update skip locked
    limit p_limit
  )
  update internal.billing_reconciliation_jobs j
  set job_status = 'WAITING_FOR_TOKEN',
      lease_owner = null,
      lease_expires_at = null,
      next_attempt_at = null,
      last_safe_error_code = 'TOKEN_REQUIRED',
      updated_at = v_now
  from candidates c
  where j.id = c.id;
  get diagnostics v_reclaimed = row_count;

  select count(*)::integer into v_waiting
  from internal.billing_reconciliation_jobs j
  where j.job_status = 'WAITING_FOR_TOKEN';

  return query select v_reclaimed, v_waiting;
end;
$$;

create function public.persist_verified_restored_billing_purchase(
  p_user_id uuid,
  p_store_product_id text,
  p_purchase_token_hash text,
  p_order_id text,
  p_purchase_state text,
  p_entitlement_state text,
  p_acknowledgement_state text,
  p_starts_at timestamptz,
  p_expires_at timestamptz,
  p_event_payload jsonb
)
returns table(result text, purchase_id uuid, effective_premium boolean, effective_expires_at timestamptz)
language plpgsql security definer set search_path = '' as $$
declare
  v_result text;
  v_purchase_id uuid;
  v_effective_premium boolean;
  v_effective_expires_at timestamptz;
  v_now timestamptz := clock_timestamp();
  v_job internal.billing_reconciliation_jobs%rowtype;
  v_updated integer;
  v_safe_result_code text;
begin
  if p_user_id is null or p_purchase_token_hash is null
    or p_purchase_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception using errcode = '22023', message = 'invalid restore verification context';
  end if;

  select j.* into v_job
  from internal.billing_reconciliation_jobs j
  where j.purchase_token_hash = p_purchase_token_hash
  for update;

  select p.result, p.purchase_id, p.effective_premium, p.effective_expires_at
    into v_result, v_purchase_id, v_effective_premium, v_effective_expires_at
  from public.persist_verified_billing_purchase(
    p_user_id,
    p_store_product_id,
    p_purchase_token_hash,
    p_order_id,
    p_purchase_state,
    p_entitlement_state,
    p_acknowledgement_state,
    p_starts_at,
    p_expires_at,
    p_event_payload
  ) p;

  if v_result = 'TOKEN_ACCOUNT_CONFLICT' then
    return query select v_result, v_purchase_id, v_effective_premium, v_effective_expires_at;
    return;
  end if;

  if v_job.id is not null
    and v_job.job_status in ('WAITING_FOR_TOKEN','RETRYABLE')
    and (v_job.user_id is null or v_job.user_id = p_user_id)
    and (v_job.lease_expires_at is null or v_job.lease_expires_at <= v_now) then
    v_safe_result_code := case when v_result = 'PENDING'
      then 'RESTORED_PENDING' else 'RESTORED_VERIFIED' end;
    update internal.billing_reconciliation_jobs j
    set user_id = p_user_id,
        job_status = 'COMPLETED',
        lease_owner = null,
        lease_expires_at = null,
        next_attempt_at = null,
        last_safe_error_code = null,
        updated_at = v_now
    where j.id = v_job.id
      and j.latest_event_receipt_id = v_job.latest_event_receipt_id
      and j.job_status in ('WAITING_FOR_TOKEN','RETRYABLE')
      and (j.user_id is null or j.user_id = p_user_id)
      and (j.lease_expires_at is null or j.lease_expires_at <= v_now);
    get diagnostics v_updated = row_count;
    if v_updated = 1 then
      update internal.billing_event_receipts r
      set user_id = p_user_id,
          receipt_status = 'COMPLETED',
          safe_result_code = v_safe_result_code,
          completed_at = v_now,
          updated_at = v_now
      where r.id = v_job.latest_event_receipt_id
        and r.purchase_token_hash = p_purchase_token_hash
        and r.receipt_status in ('WAITING_FOR_TOKEN','RETRYABLE')
        and (r.user_id is null or r.user_id = p_user_id);
      get diagnostics v_updated = row_count;
      if v_updated <> 1 then
        raise exception using errcode = '40001', message = 'restore RTDN receipt finalization failed';
      end if;
    end if;
  end if;

  return query select v_result, v_purchase_id, v_effective_premium, v_effective_expires_at;
end;
$$;

revoke all on function public.begin_billing_rtdn_event(text,text,text,text)
  from public, anon, authenticated;
revoke all on function public.finish_billing_rtdn_event(uuid,uuid,uuid,text,text)
  from public, anon, authenticated;
revoke all on function public.persist_verified_rtdn_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb,uuid,uuid,uuid
) from public, anon, authenticated;
revoke all on function public.sweep_billing_reconciliation_jobs(integer)
  from public, anon, authenticated;
revoke all on function public.persist_verified_restored_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb
) from public, anon, authenticated;
grant execute on function public.begin_billing_rtdn_event(text,text,text,text)
  to service_role;
grant execute on function public.finish_billing_rtdn_event(uuid,uuid,uuid,text,text)
  to service_role;
grant execute on function public.persist_verified_rtdn_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb,uuid,uuid,uuid
) to service_role;
grant execute on function public.sweep_billing_reconciliation_jobs(integer)
  to service_role;
grant execute on function public.persist_verified_restored_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb
) to service_role;
grant billing_verification_executor to postgres with set true;
grant create on schema public to billing_verification_executor;
alter function public.begin_billing_rtdn_event(text,text,text,text) owner to billing_verification_executor;
alter function public.finish_billing_rtdn_event(uuid,uuid,uuid,text,text) owner to billing_verification_executor;
alter function public.persist_verified_rtdn_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb,uuid,uuid,uuid
) owner to billing_verification_executor;
alter function public.sweep_billing_reconciliation_jobs(integer) owner to billing_verification_executor;
alter function public.persist_verified_restored_billing_purchase(
  uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb
) owner to billing_verification_executor;
revoke create on schema public from billing_verification_executor;
revoke billing_verification_executor from postgres;
