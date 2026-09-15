begin;

select plan(24);

select has_schema('private', 'migration creates the private schema');
select has_table('public', 'plan_preferences', 'migration creates public.plan_preferences');
select has_table('private', 'trial_eligibility_snapshots', 'migration creates trial eligibility projection');
select has_table('private', 'plan_selection_heads', 'migration creates revision heads');
select has_table('private', 'plan_selection_receipts', 'migration creates operation receipts');

select columns_are(
  'public', 'plan_preferences',
  array['user_id', 'selection', 'selected_at', 'updated_at'],
  'plan_preferences has the exact four-column business schema'
);
select col_type_is('public', 'plan_preferences', 'user_id', 'uuid', 'preference owner is UUID');
select col_is_pk('public', 'plan_preferences', 'user_id', 'preference owner is the primary key');
select col_not_null('public', 'plan_preferences', 'selection', 'selection is required');
select col_default_is('public', 'plan_preferences', 'selection', '''FREE''::text', 'explicit inserts default defensively to FREE');
select col_type_is('private', 'plan_selection_heads', 'accepted_revision', 'bigint', 'accepted revision is BIGINT');
select col_type_is('private', 'plan_selection_receipts', 'payload_hash', 'bytea', 'canonical hash is BYTEA');
select col_is_pk('private', 'plan_selection_receipts', array['user_id', 'operation_id'], 'receipt identity is scoped to user');

select fk_ok('public', 'plan_preferences', 'user_id', 'auth', 'users', 'id', 'preference cascades from auth user');
select fk_ok('private', 'trial_eligibility_snapshots', 'user_id', 'auth', 'users', 'id', 'eligibility cascades from auth user');
select fk_ok('private', 'plan_selection_heads', 'user_id', 'auth', 'users', 'id', 'head cascades from auth user');
select fk_ok('private', 'plan_selection_receipts', 'user_id', 'auth', 'users', 'id', 'receipt cascades from auth user');

insert into auth.users (id, email)
values ('30000000-0000-4000-8000-000000000003', 'migration@example.test');
insert into public.plan_preferences (user_id, selection, selected_at, updated_at)
values (
  '30000000-0000-4000-8000-000000000003',
  'TRIAL_INTENT',
  '2025-01-02T03:04:05.123456Z',
  '2025-01-02T03:04:06.654321Z'
);

select is((select user_id from public.plan_preferences where user_id = '30000000-0000-4000-8000-000000000003'), '30000000-0000-4000-8000-000000000003'::uuid, 'representative existing owner is preserved');
select is((select selection from public.plan_preferences where user_id = '30000000-0000-4000-8000-000000000003'), 'TRIAL_INTENT', 'representative existing selection is preserved');
select is((select selected_at from public.plan_preferences where user_id = '30000000-0000-4000-8000-000000000003'), '2025-01-02T03:04:05.123456Z'::timestamptz, 'representative selected_at precision is preserved');
select is((select updated_at from public.plan_preferences where user_id = '30000000-0000-4000-8000-000000000003'), '2025-01-02T03:04:06.654321Z'::timestamptz, 'representative updated_at precision is preserved');

select throws_ok(
  $$create table public.plan_preferences (unexpected integer)$$,
  '42P07',
  'relation "plan_preferences" already exists',
  'unexpected pre-existing relation fails loudly rather than being replaced'
);
select throws_ok(
  $$alter table public.plan_preferences add constraint unexpected_selection_shape check (selection = 'PAID')$$,
  '23514',
  null,
  'unexpected incompatible representative data fails safely'
);
select is((select selection from public.plan_preferences where user_id = '30000000-0000-4000-8000-000000000003'), 'TRIAL_INTENT', 'failed drift operation leaves representative data intact');

select * from finish();
rollback;
