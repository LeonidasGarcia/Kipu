grant billing_verification_executor to postgres with set true;
grant create on schema public to billing_verification_executor;
set role billing_verification_executor;

-- Forward-fix the S3 purchase writer. Environments that already recorded the S3
-- migration keep its original body, whose token-owner conflict path returned only
-- three columns from a four-column TABLE function.
create or replace function public.persist_verified_billing_purchase(
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
  v_product public.billing_products%rowtype;
  v_entitlement_state text := p_entitlement_state;
  v_purchase_id uuid;
  v_effective boolean;
  v_effective_expires_at timestamptz;
  v_now timestamptz := clock_timestamp();
begin
  if p_user_id is null or p_purchase_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception using errcode = '22023', message = 'invalid verified purchase input';
  end if;
  if p_purchase_state not in ('PURCHASED','PENDING','CANCELLED') or
     p_acknowledgement_state not in ('PENDING','ACKNOWLEDGING','ACKNOWLEDGED') then
    raise exception using errcode = '22023', message = 'invalid provider purchase state';
  end if;
  if p_event_payload is null or jsonb_typeof(p_event_payload) <> 'object' or
     p_event_payload ?| array['purchaseToken','token','accessToken','refreshToken','serviceAccount'] then
    raise exception using errcode = '22023', message = 'billing event payload must be sanitized';
  end if;

  select * into v_product from public.billing_products p
  where p.store_product_id = p_store_product_id and p.is_active = true;
  if not found then
    raise exception using errcode = '22023', message = 'unknown or inactive billing product';
  end if;

  if p_purchase_state = 'PENDING' then
    v_entitlement_state := null;
    p_acknowledgement_state := 'PENDING';
    p_expires_at := null;
  elsif p_purchase_state = 'PURCHASED' then
    if v_entitlement_state is null or v_entitlement_state not in
      ('ACTIVE','IN_GRACE_PERIOD','ACCOUNT_HOLD','CANCELED_ACTIVE','EXPIRED','REVOKED','PAUSED') then
      raise exception using errcode = '22023', message = 'verified purchase requires a normalized lifecycle state';
    end if;
    if v_product.plan_type = 'PRO_LIFETIME' and p_expires_at is not null then
      raise exception using errcode = '22023', message = 'Lifetime cannot have a commercial expiry';
    end if;
    if v_product.plan_type <> 'PRO_LIFETIME' and v_entitlement_state = 'ACTIVE' and p_expires_at is null then
      raise exception using errcode = '22023', message = 'subscription expiry is required';
    end if;
  else
    v_entitlement_state := coalesce(v_entitlement_state, 'REVOKED');
  end if;

  insert into public.billing_purchases as existing
    (user_id, order_id, purchase_token_hash, product_id, purchase_state,
     entitlement_state, acknowledgement_state, starts_at, expires_at, verified_at, updated_at)
  values
    (p_user_id, p_order_id, p_purchase_token_hash, v_product.id, p_purchase_state,
     v_entitlement_state, p_acknowledgement_state, coalesce(p_starts_at, v_now), p_expires_at, v_now, v_now)
  on conflict (purchase_token_hash) do update set
    order_id = excluded.order_id,
    product_id = excluded.product_id,
    purchase_state = excluded.purchase_state,
    entitlement_state = excluded.entitlement_state,
    acknowledgement_state = excluded.acknowledgement_state,
    acknowledgement_attempted_at = case
      when existing.acknowledgement_state in ('ACKNOWLEDGING','ACKNOWLEDGED') then existing.acknowledgement_attempted_at
      else null
    end,
    starts_at = excluded.starts_at,
    expires_at = excluded.expires_at,
    verified_at = excluded.verified_at,
    updated_at = excluded.updated_at
  where existing.user_id = excluded.user_id
  returning existing.id into v_purchase_id;

  if v_purchase_id is null then
    return query select 'TOKEN_ACCOUNT_CONFLICT'::text, null::uuid, false, null::timestamptz;
    return;
  end if;

  insert into internal.billing_events
    (user_id, purchase_token_hash, event_type, raw_payload, received_at)
  values
    (p_user_id, p_purchase_token_hash, 'VERIFICATION', p_event_payload, v_now);

  select exists (
    select 1 from public.billing_purchases p
    join public.billing_products product on product.id = p.product_id
    where p.user_id = p_user_id
      and (
        (p.purchase_state = 'PURCHASED' and product.plan_type = 'PRO_LIFETIME'
          and p.entitlement_state = 'ACTIVE' and p.expires_at is null)
        or (p.purchase_state = 'PURCHASED' and p.entitlement_state in ('ACTIVE','IN_GRACE_PERIOD') and p.expires_at > v_now)
        or (p.purchase_state = 'CANCELLED' and p.entitlement_state = 'CANCELED_ACTIVE' and p.expires_at > v_now)
      )
  ) into v_effective;

  if exists (
    select 1 from public.billing_purchases p
    join public.billing_products product on product.id = p.product_id
    where p.user_id = p_user_id and p.purchase_state = 'PURCHASED'
      and product.plan_type = 'PRO_LIFETIME' and p.entitlement_state = 'ACTIVE' and p.expires_at is null
  ) then
    v_effective_expires_at := null;
  else
    select max(p.expires_at) into v_effective_expires_at
    from public.billing_purchases p
    where p.user_id = p_user_id and p.expires_at > v_now
      and ((p.purchase_state = 'PURCHASED' and p.entitlement_state in ('ACTIVE','IN_GRACE_PERIOD'))
        or (p.purchase_state = 'CANCELLED' and p.entitlement_state = 'CANCELED_ACTIVE'));
  end if;

  return query select
    case when p_purchase_state = 'PENDING' then 'PENDING' else 'VERIFIED' end,
    v_purchase_id,
    v_effective,
    v_effective_expires_at;
end;
$$;

-- Return a retryable response to a concurrent Pub/Sub redelivery without changing
-- the receipt that the active worker must atomically complete.
create or replace function public.begin_billing_rtdn_event(
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
  where r.event_identity_hash = p_event_identity_hash;

  if v_receipt.provider_message_id <> p_provider_message_id
    or v_receipt.notification_type <> p_notification_type
    or (v_receipt.purchase_token_hash is not null
      and p_purchase_token_hash is distinct from v_receipt.purchase_token_hash) then
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
    select r.* into v_receipt
    from internal.billing_event_receipts r
    where r.id = v_receipt.id
    for update;
    if v_receipt.provider_message_id <> p_provider_message_id
      or v_receipt.notification_type <> p_notification_type
      or v_receipt.purchase_token_hash is not null then
      return query select v_receipt.id, true, 'REJECTED'::text, v_receipt.user_id,
        null::text, null::text, null::uuid, null::uuid, 'MESSAGE_ID_CONFLICT'::text;
      return;
    end if;
    if v_receipt.receipt_status in ('COMPLETED','REJECTED') then
      return query select v_receipt.id, true, v_receipt.receipt_status, v_receipt.user_id,
        null::text, null::text, null::uuid, null::uuid, v_receipt.safe_result_code;
      return;
    end if;
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

  insert into internal.billing_reconciliation_jobs as existing
    (purchase_token_hash, user_id, latest_event_receipt_id, job_status, updated_at)
  values (p_purchase_token_hash, v_user_id, v_receipt.id, 'WAITING_FOR_TOKEN', v_now)
  on conflict (purchase_token_hash) do nothing;

  -- Lock in the same job-then-receipt order as atomic persistence and lease finish.
  select j.* into v_job
  from internal.billing_reconciliation_jobs j
  where j.purchase_token_hash = p_purchase_token_hash
  for update;

  select r.* into v_receipt
  from internal.billing_event_receipts r
  where r.id = v_receipt.id
  for update;

  if v_receipt.provider_message_id <> p_provider_message_id
    or v_receipt.notification_type <> p_notification_type
    or (v_receipt.purchase_token_hash is not null
      and v_receipt.purchase_token_hash is distinct from p_purchase_token_hash) then
    return query select v_receipt.id, true, 'REJECTED'::text, v_receipt.user_id,
      null::text, null::text, null::uuid, null::uuid, 'MESSAGE_ID_CONFLICT'::text;
    return;
  end if;
  if v_receipt.receipt_status in ('COMPLETED','REJECTED') then
    return query select v_receipt.id, true, v_receipt.receipt_status, v_receipt.user_id,
      null::text, null::text, null::uuid, null::uuid, v_receipt.safe_result_code;
    return;
  end if;

  if v_job.lease_expires_at > v_now then
    if v_job.latest_event_receipt_id = v_receipt.id then
      return query select v_receipt.id, v_duplicate, 'RETRYABLE'::text, v_user_id,
        v_product_id, v_billing_product_id, v_job.id, null::uuid, 'PURCHASE_PROCESSING'::text;
      return;
    end if;
    update internal.billing_event_receipts r
    set purchase_token_hash = p_purchase_token_hash,
        user_id = v_user_id,
        receipt_status = 'RETRYABLE',
        safe_result_code = 'PURCHASE_PROCESSING',
        updated_at = v_now
    where r.id = v_receipt.id returning r.* into v_receipt;
    return query select v_receipt.id, v_duplicate, v_receipt.receipt_status, v_user_id,
      v_product_id, v_billing_product_id, v_job.id, null::uuid, v_receipt.safe_result_code;
    return;
  end if;

  if v_user_id is null then
    update internal.billing_reconciliation_jobs j
    set latest_event_receipt_id = v_receipt.id,
        job_status = 'WAITING_FOR_TOKEN',
        lease_owner = null,
        lease_expires_at = null,
        next_attempt_at = null,
        updated_at = v_now
    where j.id = v_job.id
    returning * into v_job;
    update internal.billing_event_receipts r
    set purchase_token_hash = p_purchase_token_hash,
        user_id = null,
        receipt_status = 'WAITING_FOR_TOKEN',
        safe_result_code = 'OWNER_UNAVAILABLE',
        updated_at = v_now
    where r.id = v_receipt.id returning r.* into v_receipt;
    return query select v_receipt.id, v_duplicate, 'WAITING_FOR_TOKEN'::text, null::uuid,
      null::text, null::text, v_job.id, null::uuid, 'OWNER_UNAVAILABLE'::text;
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

  update internal.billing_event_receipts r
  set purchase_token_hash = p_purchase_token_hash,
      user_id = v_user_id,
      receipt_status = 'PROCESSING',
      safe_result_code = null,
      updated_at = v_now
  where r.id = v_receipt.id returning r.* into v_receipt;

  return query select v_receipt.id, v_duplicate, 'PROCESSING'::text, v_user_id,
    v_product_id, v_billing_product_id, v_job.id, v_lease_owner, null::text;
end;
$$;

reset role;
revoke create on schema public from billing_verification_executor;
revoke billing_verification_executor from postgres;
