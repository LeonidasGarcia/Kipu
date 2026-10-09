create schema if not exists internal;

do $$ begin
  create role billing_verification_executor nologin;
exception when duplicate_object then null;
end $$;

grant billing_verification_executor to postgres;

create table if not exists public.billing_products (
  id text primary key,
  store_product_id text not null,
  base_plan_id text,
  name text not null,
  plan_type text not null,
  features jsonb not null default '{}'::jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

alter table public.billing_products add column if not exists store_product_id text;
alter table public.billing_products add column if not exists base_plan_id text;
alter table public.billing_products add column if not exists name text;
alter table public.billing_products add column if not exists plan_type text;
alter table public.billing_products add column if not exists features jsonb not null default '{}'::jsonb;
alter table public.billing_products add column if not exists is_active boolean not null default true;
alter table public.billing_products add column if not exists created_at timestamptz not null default now();

alter table public.billing_products drop constraint if exists billing_products_plan_type_check;
alter table public.billing_products drop constraint if exists billing_products_store_product_id_key;
alter table public.billing_products drop constraint if exists billing_products_store_base_plan_key;
alter table public.billing_products drop constraint if exists billing_products_lifetime_without_base_plan_check;
alter table public.billing_products
  add constraint billing_products_plan_type_check
  check (plan_type in ('FREE','PRO_MONTHLY','PRO_ANNUAL','PRO_LIFETIME'));
-- Google Play uses one product with multiple base_plan_id; unique on composite
alter table public.billing_products
  add constraint billing_products_store_base_plan_key unique (store_product_id, base_plan_id);
alter table public.billing_products
  add constraint billing_products_lifetime_without_base_plan_check
  check (plan_type <> 'PRO_LIFETIME' or base_plan_id is null);

create table if not exists public.billing_purchases (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  order_id text,
  purchase_token_hash text not null,
  product_id text not null references public.billing_products(id) on delete restrict,
  purchase_state text not null,
  entitlement_state text,
  acknowledgement_state text not null default 'ACKNOWLEDGED',
  starts_at timestamptz not null default now(),
  expires_at timestamptz,
  verified_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.billing_purchases add column if not exists order_id text;
alter table public.billing_purchases add column if not exists acknowledgement_state text not null default 'ACKNOWLEDGED';
alter table public.billing_purchases add column if not exists acknowledgement_attempted_at timestamptz;
alter table public.billing_purchases add column if not exists starts_at timestamptz not null default now();
alter table public.billing_purchases add column if not exists expires_at timestamptz;
alter table public.billing_purchases add column if not exists verified_at timestamptz not null default now();
alter table public.billing_purchases add column if not exists created_at timestamptz not null default now();
alter table public.billing_purchases add column if not exists updated_at timestamptz not null default now();
alter table public.billing_purchases drop constraint if exists billing_purchases_user_id_fkey;
alter table public.billing_purchases
  add constraint billing_purchases_user_id_fkey foreign key (user_id) references auth.users(id) on delete restrict;

alter table public.billing_purchases drop constraint if exists billing_purchases_purchase_state_check;
alter table public.billing_purchases drop constraint if exists billing_purchases_entitlement_state_check;
alter table public.billing_purchases drop constraint if exists billing_purchases_acknowledgement_state_check;
alter table public.billing_purchases drop constraint if exists billing_purchases_pending_without_entitlement_check;
alter table public.billing_purchases drop constraint if exists billing_purchases_token_hash_format_check;
update public.billing_purchases
set entitlement_state = case entitlement_state
  when 'GRACE_PERIOD' then 'IN_GRACE_PERIOD'
  when 'ON_HOLD' then 'ACCOUNT_HOLD'
  when 'PENDING' then null
  else entitlement_state
end;
update public.billing_purchases set entitlement_state = null where purchase_state = 'PENDING';
alter table public.billing_purchases
  add constraint billing_purchases_purchase_state_check
  check (purchase_state in ('PURCHASED','PENDING','CANCELLED'));
alter table public.billing_purchases
  add constraint billing_purchases_entitlement_state_check
  check (entitlement_state is null or entitlement_state in ('ACTIVE','IN_GRACE_PERIOD','ACCOUNT_HOLD','CANCELED_ACTIVE','EXPIRED','REVOKED','PAUSED'));
alter table public.billing_purchases
  add constraint billing_purchases_acknowledgement_state_check
  check (acknowledgement_state in ('PENDING','ACKNOWLEDGING','ACKNOWLEDGED'));
alter table public.billing_purchases
  add constraint billing_purchases_pending_without_entitlement_check
  check (purchase_state <> 'PENDING' or entitlement_state is null);
alter table public.billing_purchases
  add constraint billing_purchases_token_hash_format_check
  check (purchase_token_hash ~ '^[0-9a-f]{64}$');
create unique index if not exists billing_purchases_purchase_token_hash_uidx
  on public.billing_purchases (purchase_token_hash);
create index if not exists billing_purchases_user_id_idx
  on public.billing_purchases (user_id);
create index if not exists billing_purchases_entitlement_state_idx
  on public.billing_purchases (entitlement_state);
create index if not exists billing_purchases_user_effective_idx
  on public.billing_purchases (user_id, purchase_state, entitlement_state, expires_at);

create table if not exists internal.billing_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete restrict,
  purchase_token_hash text not null,
  event_type text not null,
  raw_payload jsonb not null default '{}'::jsonb,
  received_at timestamptz not null default now()
);
alter table internal.billing_events drop constraint if exists billing_events_user_id_fkey;
alter table internal.billing_events
  add constraint billing_events_user_id_fkey foreign key (user_id) references auth.users(id) on delete restrict;
alter table internal.billing_events drop constraint if exists billing_events_event_type_check;
alter table internal.billing_events drop constraint if exists billing_events_token_hash_format_check;
alter table internal.billing_events
  add constraint billing_events_event_type_check
  check (event_type in ('PURCHASE','RENEWAL','CANCELLATION','RTDN_UPDATE','VERIFICATION'));
alter table internal.billing_events
  add constraint billing_events_token_hash_format_check
  check (purchase_token_hash ~ '^[0-9a-f]{64}$');
create index if not exists billing_events_purchase_token_hash_idx
  on internal.billing_events (purchase_token_hash);
create index if not exists billing_events_user_received_idx
  on internal.billing_events (user_id, received_at desc);

alter table public.billing_products enable row level security;
alter table public.billing_products force row level security;
alter table public.billing_purchases enable row level security;
alter table public.billing_purchases force row level security;
alter table internal.billing_events enable row level security;
alter table internal.billing_events force row level security;

drop policy if exists billing_products_active_read on public.billing_products;
create policy billing_products_active_read on public.billing_products
  for select to authenticated using (is_active);
drop policy if exists billing_products_executor_read on public.billing_products;
create policy billing_products_executor_read on public.billing_products
  for select to billing_verification_executor using (true);
drop policy if exists billing_purchases_owner_read on public.billing_purchases;
create policy billing_purchases_owner_read on public.billing_purchases
  for select to authenticated using (auth.uid() is not null and user_id = auth.uid());
drop policy if exists billing_purchases_executor_all on public.billing_purchases;
create policy billing_purchases_executor_all on public.billing_purchases
  to billing_verification_executor using (true) with check (true);
drop policy if exists billing_events_executor_insert on internal.billing_events;
create policy billing_events_executor_insert on internal.billing_events
  for insert to billing_verification_executor with check (true);

grant usage on schema public to billing_verification_executor;
grant usage on schema internal to billing_verification_executor;
grant select on public.billing_products to billing_verification_executor;
revoke update on public.billing_products from billing_verification_executor;
revoke update (id) on public.billing_products from billing_verification_executor;
grant select, insert, update on public.billing_purchases to billing_verification_executor;
grant insert on internal.billing_events to billing_verification_executor;
grant select on public.billing_products, public.billing_purchases to authenticated;
revoke all on public.billing_products, public.billing_purchases from anon;
revoke all on public.billing_products, public.billing_purchases from service_role;
grant select, insert, update, delete on public.billing_products to service_role;
revoke insert, update, delete, truncate, references, trigger on public.billing_products from authenticated;
revoke insert, update, delete, truncate, references, trigger on public.billing_purchases from authenticated;
revoke all on schema internal from public, anon, authenticated, service_role;
revoke all on internal.billing_events from public, anon, authenticated, service_role;
revoke all on internal.billing_events from billing_verification_executor;
grant usage on schema internal to billing_verification_executor;
grant insert on internal.billing_events to billing_verification_executor;

create or replace function internal.reject_billing_event_mutation()
returns trigger language plpgsql set search_path = '' as $$
begin
  raise exception using errcode = '42501', message = 'billing events are append-only';
end;
$$;
revoke all on function internal.reject_billing_event_mutation() from public, anon, authenticated, service_role;
drop trigger if exists billing_events_append_only on internal.billing_events;
create trigger billing_events_append_only
  before update or delete on internal.billing_events
  for each row execute function internal.reject_billing_event_mutation();

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
    return query select 'TOKEN_ACCOUNT_CONFLICT'::text, null::uuid, false;
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

revoke all on function public.persist_verified_billing_purchase(uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb) from public, anon, authenticated;
grant execute on function public.persist_verified_billing_purchase(uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb) to service_role;

create or replace function public.claim_billing_purchase_acknowledgement(p_purchase_token_hash text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  v_claimed boolean := false;
begin
  update public.billing_purchases
  set acknowledgement_state = 'ACKNOWLEDGING', acknowledgement_attempted_at = clock_timestamp(), updated_at = clock_timestamp()
  where purchase_token_hash = p_purchase_token_hash
    and (acknowledgement_state = 'PENDING'
      or (acknowledgement_state = 'ACKNOWLEDGING' and acknowledgement_attempted_at < clock_timestamp() - interval '5 minutes'))
  returning true into v_claimed;
  return coalesce(v_claimed, false);
end;
$$;

create or replace function public.complete_billing_purchase_acknowledgement(p_purchase_token_hash text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  v_updated boolean := false;
begin
  update public.billing_purchases
  set acknowledgement_state = 'ACKNOWLEDGED', acknowledgement_attempted_at = clock_timestamp(), updated_at = clock_timestamp()
  where purchase_token_hash = p_purchase_token_hash and acknowledgement_state = 'ACKNOWLEDGING'
  returning true into v_updated;
  return coalesce(v_updated, false);
end;
$$;

create or replace function public.release_billing_purchase_acknowledgement(p_purchase_token_hash text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  v_updated boolean := false;
begin
  update public.billing_purchases
  set acknowledgement_state = 'PENDING', acknowledgement_attempted_at = null, updated_at = clock_timestamp()
  where purchase_token_hash = p_purchase_token_hash and acknowledgement_state = 'ACKNOWLEDGING'
  returning true into v_updated;
  return coalesce(v_updated, false);
end;
$$;

revoke all on function public.claim_billing_purchase_acknowledgement(text) from public, anon, authenticated;
revoke all on function public.complete_billing_purchase_acknowledgement(text) from public, anon, authenticated;
revoke all on function public.release_billing_purchase_acknowledgement(text) from public, anon, authenticated;
grant execute on function public.claim_billing_purchase_acknowledgement(text) to service_role;
grant execute on function public.complete_billing_purchase_acknowledgement(text) to service_role;
grant execute on function public.release_billing_purchase_acknowledgement(text) to service_role;

grant create on schema public to billing_verification_executor;
alter function public.persist_verified_billing_purchase(uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb) owner to billing_verification_executor;
alter function public.claim_billing_purchase_acknowledgement(text) owner to billing_verification_executor;
alter function public.complete_billing_purchase_acknowledgement(text) owner to billing_verification_executor;
alter function public.release_billing_purchase_acknowledgement(text) owner to billing_verification_executor;
revoke create on schema public from billing_verification_executor;
revoke billing_verification_executor from postgres;
