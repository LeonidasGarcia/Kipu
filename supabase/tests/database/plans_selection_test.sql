begin;

select plan(30);

insert into auth.users (id, email)
values
  ('10000000-0000-4000-8000-000000000001', 'plans-a@example.test'),
  ('20000000-0000-4000-8000-000000000002', 'plans-b@example.test');

select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);

create temporary table rpc_results (label text primary key, body jsonb not null);

insert into rpc_results
select 'a-1', to_jsonb(response)
from public.apply_plan_selection(
  1,
  'aaaaaaaa-0000-4000-8000-000000000001'::uuid,
  1,
  'TRIAL_INTENT',
  '2026-09-14T15:03:12.123456Z'::timestamptz
) as response;

select is(body ->> 'result', 'APPLIED', 'a new higher revision is APPLIED') from rpc_results where label = 'a-1';
select is(body ->> 'accepted_revision', '1', 'APPLIED returns BIGINT revision as a string') from rpc_results where label = 'a-1';
select is(body #>> '{current_preference,selection}', 'TRIAL_INTENT', 'APPLIED returns the current preference') from rpc_results where label = 'a-1';
select is(
  body -> 'free_limits',
  '{"policy_version":1,"instruments":4,"custom_categories":5,"debts":2,"goals":2,"budgets":2}'::jsonb,
  'every success returns the fixed Free v1 limits'
) from rpc_results where label = 'a-1';
select is((select selection from public.plan_preferences where user_id = '10000000-0000-4000-8000-000000000001'), 'TRIAL_INTENT', 'APPLIED writes the preference');
select is((select accepted_revision from private.plan_selection_heads where user_id = '10000000-0000-4000-8000-000000000001'), 1::bigint, 'APPLIED advances the head');
select is((select count(*) from private.plan_selection_receipts where user_id = '10000000-0000-4000-8000-000000000001'), 1::bigint, 'APPLIED inserts one receipt atomically');
select is((select octet_length(payload_hash) from private.plan_selection_receipts where operation_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 32, 'receipt stores a SHA-256 hash');

insert into rpc_results
select 'a-1-replay', to_jsonb(response)
from public.apply_plan_selection(1, 'aaaaaaaa-0000-4000-8000-000000000001', 1, 'TRIAL_INTENT', '2026-09-14T15:03:12.123456Z') as response;

select is(body ->> 'result', 'DUPLICATE', 'an identical replay of an applied operation is DUPLICATE') from rpc_results where label = 'a-1-replay';
select is((select count(*) from private.plan_selection_receipts where user_id = '10000000-0000-4000-8000-000000000001'), 1::bigint, 'DUPLICATE performs no receipt write');
select is((select count(*) from public.plan_preferences where user_id = '10000000-0000-4000-8000-000000000001'), 1::bigint, 'DUPLICATE performs no additional preference write');

insert into rpc_results
select 'a-1-conflict', to_jsonb(response)
from public.apply_plan_selection(1, 'aaaaaaaa-0000-4000-8000-000000000001', 1, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z') as response;

select is(body ->> 'result', 'CONFLICT', 'a known operation with changed payload is CONFLICT') from rpc_results where label = 'a-1-conflict';
select is((select selection from public.plan_preferences where user_id = '10000000-0000-4000-8000-000000000001'), 'TRIAL_INTENT', 'operation payload conflict preserves current preference');
select is((select first_result from private.plan_selection_receipts where operation_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'APPLIED', 'conflicting replay does not rewrite the retained receipt');

insert into rpc_results
select 'a-3', to_jsonb(response)
from public.apply_plan_selection(1, 'aaaaaaaa-0000-4000-8000-000000000003', 3, 'PREMIUM_INTENT', '2026-09-14T15:05:00.000000Z') as response;

insert into rpc_results
select 'a-2', to_jsonb(response)
from public.apply_plan_selection(1, 'aaaaaaaa-0000-4000-8000-000000000002', 2, 'FREE', '2026-09-14T15:04:00.000000Z') as response;

select is((select body ->> 'result' from rpc_results where label = 'a-3'), 'APPLIED', 'delivery of revision 3 is APPLIED');
select is((select body ->> 'result' from rpc_results where label = 'a-2'), 'STALE', 'later delivery of revision 2 is STALE');
select is((select selection from public.plan_preferences where user_id = '10000000-0000-4000-8000-000000000001'), 'PREMIUM_INTENT', 'STALE does not replace revision 3');
select is((select accepted_revision from private.plan_selection_heads where user_id = '10000000-0000-4000-8000-000000000001'), 3::bigint, 'STALE does not move the accepted head');
select is((select first_result from private.plan_selection_receipts where operation_id = 'aaaaaaaa-0000-4000-8000-000000000002'), 'STALE', 'STALE receipt is retained for account lifetime');

insert into rpc_results
select 'a-3-other', to_jsonb(response)
from public.apply_plan_selection(1, 'aaaaaaaa-0000-4000-8000-000000000099', 3, 'FREE', '2026-09-14T15:06:00.000000Z') as response;

select is(body ->> 'result', 'CONFLICT', 'a different operation at the accepted revision is CONFLICT') from rpc_results where label = 'a-3-other';
select is((select first_result from private.plan_selection_receipts where operation_id = 'aaaaaaaa-0000-4000-8000-000000000099'), 'CONFLICT', 'same-revision conflict is retained');

select set_config('request.jwt.claim.sub', '20000000-0000-4000-8000-000000000002', true);

insert into rpc_results
select 'b-3', to_jsonb(response) from public.apply_plan_selection(1, 'bbbbbbbb-0000-4000-8000-000000000003', 3, 'PREMIUM_INTENT', '2026-09-14T16:03:00Z') response;
insert into rpc_results
select 'b-2', to_jsonb(response) from public.apply_plan_selection(1, 'bbbbbbbb-0000-4000-8000-000000000002', 2, 'TRIAL_INTENT', '2026-09-14T16:02:00Z') response;
insert into rpc_results
select 'b-1', to_jsonb(response) from public.apply_plan_selection(1, 'bbbbbbbb-0000-4000-8000-000000000001', 1, 'FREE', '2026-09-14T16:01:00Z') response;

select is((select body ->> 'result' from rpc_results where label = 'b-3'), 'APPLIED', '3-2-1 delivery applies revision 3');
select is((select body ->> 'result' from rpc_results where label = 'b-2'), 'STALE', '3-2-1 delivery marks revision 2 stale');
select is((select body ->> 'result' from rpc_results where label = 'b-1'), 'STALE', '3-2-1 delivery marks revision 1 stale');
select is((select accepted_revision from private.plan_selection_heads where user_id = '20000000-0000-4000-8000-000000000002'), 3::bigint, '3-2-1 delivery leaves head at revision 3');
select is((select selection from public.plan_preferences where user_id = '20000000-0000-4000-8000-000000000002'), 'PREMIUM_INTENT', '3-2-1 delivery leaves revision 3 effective');
select is((select count(*) from private.plan_selection_receipts where user_id = '20000000-0000-4000-8000-000000000002'), 3::bigint, 'all first-seen 3-2-1 operations retain receipts');

delete from auth.users where id = '20000000-0000-4000-8000-000000000002';
select is((select count(*) from public.plan_preferences where user_id = '20000000-0000-4000-8000-000000000002'), 0::bigint, 'account deletion cascades preference retention');
select is((select count(*) from private.plan_selection_heads where user_id = '20000000-0000-4000-8000-000000000002'), 0::bigint, 'account deletion cascades head retention');
select is((select count(*) from private.plan_selection_receipts where user_id = '20000000-0000-4000-8000-000000000002'), 0::bigint, 'account deletion cascades receipt retention');

select * from finish();
rollback;
