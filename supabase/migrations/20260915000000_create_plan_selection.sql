create extension if not exists pgcrypto;
create schema if not exists private;
do $$ begin create role plan_selection_executor nologin; exception when duplicate_object then null; end $$;
do $$ begin create role plan_eligibility_writer nologin; exception when duplicate_object then null; end $$;

create table public.plan_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  selection text not null default 'FREE' check (selection in ('FREE','TRIAL_INTENT','PREMIUM_INTENT')),
  selected_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table private.trial_eligibility_snapshots (
  user_id uuid primary key references auth.users(id) on delete cascade,
  status text not null check (status in ('ELIGIBLE','INELIGIBLE')),
  source text not null default 'VERIFIED_ACCOUNT_HISTORY' check (source='VERIFIED_ACCOUNT_HISTORY'),
  verified_at timestamptz not null, valid_until timestamptz,
  check ((status='ELIGIBLE' and valid_until>verified_at) or status='INELIGIBLE')
);
create table private.plan_selection_heads (
  user_id uuid primary key references auth.users(id) on delete cascade,
  accepted_revision bigint not null default 0 check (accepted_revision>=0), updated_at timestamptz not null default now()
);
create table private.plan_selection_receipts (
  user_id uuid not null references auth.users(id) on delete cascade, operation_id uuid not null,
  contract_version smallint not null check (contract_version=1), selection_revision bigint not null check(selection_revision>0),
  payload_hash bytea not null check(octet_length(payload_hash)=32), first_result text not null check(first_result in ('APPLIED','STALE','CONFLICT')),
  accepted_revision_at_first_seen bigint not null, first_seen_at timestamptz not null default now(), primary key(user_id,operation_id)
);
create index plan_receipts_user_revision on private.plan_selection_receipts(user_id,selection_revision);

alter table public.plan_preferences enable row level security; alter table public.plan_preferences force row level security;
alter table private.trial_eligibility_snapshots enable row level security; alter table private.trial_eligibility_snapshots force row level security;
alter table private.plan_selection_heads enable row level security; alter table private.plan_selection_heads force row level security;
alter table private.plan_selection_receipts enable row level security; alter table private.plan_selection_receipts force row level security;

create policy preference_own on public.plan_preferences to plan_selection_executor using(auth.uid()=user_id) with check(auth.uid()=user_id);
create policy eligibility_own_read on private.trial_eligibility_snapshots for select to plan_selection_executor using(auth.uid()=user_id);
create policy heads_own on private.plan_selection_heads to plan_selection_executor using(auth.uid()=user_id) with check(auth.uid()=user_id);
create policy receipts_own on private.plan_selection_receipts to plan_selection_executor using(auth.uid()=user_id) with check(auth.uid()=user_id);
create policy eligibility_writer on private.trial_eligibility_snapshots to plan_eligibility_writer using(true) with check(true);

grant usage on schema public, private to plan_selection_executor;
grant select,insert,update on public.plan_preferences to plan_selection_executor;
grant select on private.trial_eligibility_snapshots to plan_selection_executor;
grant select,insert,update on private.plan_selection_heads to plan_selection_executor;
grant select,insert on private.plan_selection_receipts to plan_selection_executor;
grant usage on schema private to plan_eligibility_writer;
grant select,insert,update on private.trial_eligibility_snapshots to plan_eligibility_writer;
revoke all on public.plan_preferences from anon, authenticated;
revoke all on private.trial_eligibility_snapshots, private.plan_selection_heads, private.plan_selection_receipts from anon, authenticated;

create function private.plan_selection_payload_hash(p_contract smallint,p_operation uuid,p_revision bigint,p_selection text,p_selected_at timestamptz) returns bytea
language sql immutable set search_path='' as $$ select extensions.digest(convert_to(p_contract::text||chr(10)||lower(p_operation::text)||chr(10)||p_revision::text||chr(10)||upper(p_selection)||chr(10)||to_char(p_selected_at at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'UTF8'),'sha256') $$;

create function public.apply_plan_selection(p_contract_version smallint,p_operation_id uuid,p_selection_revision bigint,p_selection text,p_selected_at timestamptz)
returns table(contract_version smallint,operation_id uuid,result text,accepted_revision text,current_preference jsonb,free_limits jsonb,server_time text)
language plpgsql security definer set search_path='' as $$
declare uid uuid:=auth.uid(); head bigint; known private.plan_selection_receipts; hash bytea; outcome text; pref public.plan_preferences; stamp timestamptz:=clock_timestamp();
begin
 if uid is null then raise insufficient_privilege; end if;
 if p_contract_version<>1 or p_selection_revision<1 or p_selection not in ('FREE','TRIAL_INTENT','PREMIUM_INTENT') then raise data_exception; end if;
 hash:=private.plan_selection_payload_hash(p_contract_version,p_operation_id,p_selection_revision,p_selection,p_selected_at);
 select * into known from private.plan_selection_receipts r where r.user_id=uid and r.operation_id=p_operation_id;
 insert into private.plan_selection_heads(user_id) values(uid) on conflict do nothing;
 select h.accepted_revision into head from private.plan_selection_heads h where h.user_id=uid for update;
 if found and known.operation_id is not null then outcome:=case when known.payload_hash=hash and known.first_result='APPLIED' then 'DUPLICATE' when known.payload_hash=hash then known.first_result else 'CONFLICT' end;
 else
    outcome:=case when p_selection_revision>head then 'APPLIED' when p_selection_revision<head then 'STALE' else 'CONFLICT' end;
    if outcome='APPLIED' then
      insert into public.plan_preferences(user_id,selection,selected_at,updated_at) values(uid,p_selection,p_selected_at,stamp) on conflict(user_id) do update set selection=excluded.selection,selected_at=excluded.selected_at,updated_at=excluded.updated_at;
      update private.plan_selection_heads set accepted_revision=p_selection_revision,updated_at=stamp where user_id=uid; head:=p_selection_revision;
    end if;
    insert into private.plan_selection_receipts values(uid,p_operation_id,p_contract_version,p_selection_revision,hash,outcome,head,stamp);
 end if;
 select * into pref from public.plan_preferences p where p.user_id=uid;
  return query select 1::smallint,p_operation_id,outcome,head::text,jsonb_build_object('selection',pref.selection,'selected_at',pref.selected_at,'updated_at',pref.updated_at),jsonb_build_object('policy_version',1,'instruments',4,'custom_categories',5,'debts',2,'goals',2,'budgets',2),to_char(stamp at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
end $$;

create function public.get_trial_eligibility() returns jsonb language plpgsql security definer set search_path='' as $$ declare row private.trial_eligibility_snapshots; begin if auth.uid() is null then raise insufficient_privilege; end if; select * into row from private.trial_eligibility_snapshots where user_id=auth.uid(); if row.user_id is null or (row.status='ELIGIBLE' and row.valid_until<=now()) then return jsonb_build_object('status','UNKNOWN','source','UNAVAILABLE','verified_at',null,'valid_until',null); end if; return jsonb_build_object('status',row.status,'source',row.source,'verified_at',row.verified_at,'valid_until',row.valid_until); end $$;
create function private.write_trial_eligibility(p_user_id uuid,p_status text,p_verified_at timestamptz,p_valid_until timestamptz,p_evidence_verified boolean) returns void language plpgsql security definer set search_path='' as $$ begin if not p_evidence_verified then raise exception using errcode='22023',message='verified evidence is required'; end if; insert into private.trial_eligibility_snapshots(user_id,status,verified_at,valid_until) values(p_user_id,p_status,p_verified_at,p_valid_until) on conflict(user_id) do update set status=excluded.status,verified_at=excluded.verified_at,valid_until=excluded.valid_until; end $$;

alter function public.apply_plan_selection(smallint,uuid,bigint,text,timestamptz) owner to plan_selection_executor;
alter function public.get_trial_eligibility() owner to plan_selection_executor;
alter function private.write_trial_eligibility(uuid,text,timestamptz,timestamptz,boolean) owner to plan_eligibility_writer;
revoke all on function public.apply_plan_selection(smallint,uuid,bigint,text,timestamptz), public.get_trial_eligibility() from public,anon;
grant execute on function public.apply_plan_selection(smallint,uuid,bigint,text,timestamptz), public.get_trial_eligibility() to authenticated;
revoke all on function private.write_trial_eligibility(uuid,text,timestamptz,timestamptz,boolean) from public,anon,authenticated;
grant execute on function private.write_trial_eligibility(uuid,text,timestamptz,timestamptz,boolean) to plan_eligibility_writer;
