begin;

select plan(39);

insert into auth.users (id, email)
values
  ('40000000-0000-4000-8000-000000000004', 'rls-owner@example.test'),
  ('50000000-0000-4000-8000-000000000005', 'rls-other@example.test');

select ok(relrowsecurity and relforcerowsecurity, 'RLS is enabled and forced on public.plan_preferences')
from pg_class where oid = 'public.plan_preferences'::regclass;
select ok(relrowsecurity and relforcerowsecurity, 'RLS is enabled and forced on private.trial_eligibility_snapshots')
from pg_class where oid = 'private.trial_eligibility_snapshots'::regclass;
select ok(relrowsecurity and relforcerowsecurity, 'RLS is enabled and forced on private.plan_selection_heads')
from pg_class where oid = 'private.plan_selection_heads'::regclass;
select ok(relrowsecurity and relforcerowsecurity, 'RLS is enabled and forced on private.plan_selection_receipts')
from pg_class where oid = 'private.plan_selection_receipts'::regclass;

select has_role('plan_selection_executor', 'dedicated selection executor exists');
select has_role('plan_eligibility_writer', 'isolated eligibility writer exists');
select ok(not rolcanlogin and not rolbypassrls and not rolsuper, 'selection executor is NOLOGIN, non-superuser and cannot bypass RLS')
from pg_roles where rolname = 'plan_selection_executor';
select ok(not rolcanlogin and not rolbypassrls and not rolsuper, 'eligibility writer is NOLOGIN, non-superuser and cannot bypass RLS')
from pg_roles where rolname = 'plan_eligibility_writer';

select ok(not has_table_privilege('authenticated', 'public.plan_preferences', 'SELECT,INSERT,UPDATE,DELETE'), 'authenticated has no direct preference DML');
select ok(not has_table_privilege('anon', 'public.plan_preferences', 'SELECT,INSERT,UPDATE,DELETE'), 'anonymous has no direct preference DML');
select ok(not has_table_privilege('authenticated', 'private.plan_selection_heads', 'SELECT,INSERT,UPDATE,DELETE'), 'authenticated has no direct head DML');
select ok(not has_table_privilege('authenticated', 'private.plan_selection_receipts', 'SELECT,INSERT,UPDATE,DELETE'), 'authenticated has no direct receipt DML');
select ok(not has_table_privilege('authenticated', 'private.trial_eligibility_snapshots', 'SELECT,INSERT,UPDATE,DELETE'), 'authenticated has no direct eligibility DML');

select function_privs_are('public', 'apply_plan_selection', array['integer','uuid','bigint','text','timestamp with time zone'], 'authenticated', array['EXECUTE'], 'authenticated can execute only the selection boundary');
select function_privs_are('public', 'get_trial_eligibility', array[]::text[], 'authenticated', array['EXECUTE'], 'authenticated can execute eligibility read boundary');
select function_privs_are('public', 'apply_plan_selection', array['integer','uuid','bigint','text','timestamp with time zone'], 'anon', array[]::text[], 'anonymous cannot execute selection RPC');
select function_privs_are('public', 'get_trial_eligibility', array[]::text[], 'anon', array[]::text[], 'anonymous cannot execute eligibility RPC');
select ok(not exists(select 1 from aclexplode(coalesce(proacl,acldefault('f',proowner))) where grantee=0 and privilege_type='EXECUTE'), 'PUBLIC execute is revoked from selection RPC') from pg_proc where oid='public.apply_plan_selection(integer,uuid,bigint,text,timestamptz)'::regprocedure;
select ok(not exists(select 1 from aclexplode(coalesce(proacl,acldefault('f',proowner))) where grantee=0 and privilege_type='EXECUTE'), 'PUBLIC execute is revoked from eligibility RPC') from pg_proc where oid='public.get_trial_eligibility()'::regprocedure;
select function_privs_are('private', 'request_user_id', array[]::text[], 'plan_selection_executor', array['EXECUTE'], 'executor can resolve the verified request user');
select function_privs_are('private', 'request_user_id', array[]::text[], 'anon', array[]::text[], 'anonymous cannot execute the request-user helper');
select function_privs_are('private', 'plan_selection_payload_hash', array['integer','uuid','bigint','text','timestamp with time zone'], 'plan_selection_executor', array['EXECUTE'], 'executor can hash an accepted selection payload');
select function_privs_are('private', 'plan_selection_payload_hash', array['integer','uuid','bigint','text','timestamp with time zone'], 'anon', array[]::text[], 'anonymous cannot execute the hash helper');

select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('request.jwt.claim.sub', '40000000-0000-4000-8000-000000000004', true);
select lives_ok(
  $$select * from public.apply_plan_selection(1, 'dddddddd-0000-4000-8000-000000000001', 1, 'FREE', '2026-09-15T10:00:00Z')$$,
  'owner can apply through the RPC'
);

grant plan_selection_executor to postgres;
-- pgTAP lives in extensions; grant only within this rolled-back test transaction.
grant usage on schema extensions to plan_selection_executor;
grant execute on all functions in schema extensions to plan_selection_executor;
set local role plan_selection_executor;
select set_config('request.jwt.claim.sub', '50000000-0000-4000-8000-000000000005', true);
select is((select count(*)::integer from public.plan_preferences where user_id = '40000000-0000-4000-8000-000000000004'), 0, 'executor cannot read another user preference');
select is((select count(*)::integer from private.plan_selection_heads where user_id = '40000000-0000-4000-8000-000000000004'), 0, 'executor cannot read another user head');
select is((select count(*)::integer from private.plan_selection_receipts where user_id = '40000000-0000-4000-8000-000000000004'), 0, 'executor cannot read another user receipt');
select throws_ok(
  $$insert into public.plan_preferences(user_id, selection) values ('40000000-0000-4000-8000-000000000004', 'FREE')$$,
  '42501', null, 'executor cannot insert another user preference'
);
select results_eq($$update public.plan_preferences set selection = 'PREMIUM_INTENT' where user_id = '40000000-0000-4000-8000-000000000004' returning selection$$, array[]::text[], 'executor cannot update another user preference');
select throws_ok(
  $$insert into private.plan_selection_heads(user_id, accepted_revision, updated_at) values ('40000000-0000-4000-8000-000000000004', 9, now())$$,
  '42501', null, 'executor cannot insert another user head'
);
select throws_ok(
  $$insert into private.plan_selection_receipts(user_id, operation_id, contract_version, selection_revision, payload_hash, first_result, accepted_revision_at_first_seen, first_seen_at) values ('40000000-0000-4000-8000-000000000004', gen_random_uuid(), 1, 9, decode(repeat('00', 32), 'hex'), 'STALE', 9, now())$$,
  '42501', null, 'executor cannot insert another user receipt'
);
select ok(not has_table_privilege('plan_selection_executor', 'private.trial_eligibility_snapshots', 'INSERT,UPDATE,DELETE'), 'selection executor has no eligibility write privilege');

reset role;
select ok(has_table_privilege('plan_eligibility_writer', 'private.trial_eligibility_snapshots', 'SELECT,INSERT,UPDATE'), 'server-only writer has only the projection DML it needs');
select ok(not has_table_privilege('plan_eligibility_writer', 'public.plan_preferences', 'SELECT,INSERT,UPDATE,DELETE'), 'eligibility writer cannot access preferences');
select ok(not has_table_privilege('plan_eligibility_writer', 'private.plan_selection_heads', 'SELECT,INSERT,UPDATE,DELETE'), 'eligibility writer cannot access heads');
select ok(not has_table_privilege('plan_eligibility_writer', 'private.plan_selection_receipts', 'SELECT,INSERT,UPDATE,DELETE'), 'eligibility writer cannot access receipts');
select function_privs_are('private', 'write_trial_eligibility', array['uuid','text','timestamp with time zone','timestamp with time zone','boolean'], 'plan_eligibility_writer', array['EXECUTE'], 'server-only writer owns the validated eligibility boundary');
select throws_ok(
  $$set local role anon; select * from public.get_trial_eligibility()$$,
  '42501', null, 'anonymous eligibility access is denied'
);
grant plan_eligibility_writer to postgres;
grant usage on schema extensions to plan_eligibility_writer;
grant execute on all functions in schema extensions to plan_eligibility_writer;
set local role plan_eligibility_writer;
select throws_ok(
  $$select private.write_trial_eligibility('40000000-0000-4000-8000-000000000004', 'ELIGIBLE', now(), now() + interval '7 days', false)$$,
  '22023', null, 'eligibility writer rejects a target without verified evidence'
);

select * from finish();
rollback;
