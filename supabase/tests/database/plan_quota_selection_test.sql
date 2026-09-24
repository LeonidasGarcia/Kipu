BEGIN;
SELECT plan(44);

SELECT has_table('private', 'plan_quota_selection_heads', 'quota heads exist');
SELECT has_table('private', 'plan_quota_selection_items', 'selected resources exist');
SELECT has_table('private', 'plan_quota_selection_receipts', 'idempotency receipts exist');
SELECT has_function('public', 'apply_plan_quota_selection', ARRAY['integer','uuid','text','bigint','jsonb'], 'quota selection RPC exists');
SELECT ok(NOT has_table_privilege('authenticated', 'private.plan_quota_selection_items', 'SELECT,INSERT,UPDATE,DELETE'), 'authenticated cannot directly mutate selections');
SELECT ok(NOT has_function_privilege('anon', 'public.apply_plan_quota_selection(integer,uuid,text,bigint,jsonb)', 'EXECUTE'), 'anon cannot call quota RPC');
SELECT ok((SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='private.plan_quota_selection_items'::regclass), 'selection items enforce RLS');

INSERT INTO auth.users(id,email) VALUES
 ('51000000-0000-4000-8000-000000000001','quota-a@example.test'),
 ('51000000-0000-4000-8000-000000000002','quota-b@example.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES
 ('52000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','Ahorros A','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000002','51000000-0000-4000-8000-000000000001','Efectivo A','CASH','PEN'),
 ('52000000-0000-4000-8000-000000000003','51000000-0000-4000-8000-000000000002','Ahorros B','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000004','51000000-0000-4000-8000-000000000001','Cuenta 4','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000005','51000000-0000-4000-8000-000000000001','Cuenta 5','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000006','51000000-0000-4000-8000-000000000001','Cuenta 6','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000007','51000000-0000-4000-8000-000000000001','Cuenta 7','SAVINGS','PEN'),
 ('52000000-0000-4000-8000-000000000008','51000000-0000-4000-8000-000000000001','Cuenta interna crédito','CREDIT_LIABILITY','PEN'),
 ('52000000-0000-4000-8000-000000000009','51000000-0000-4000-8000-000000000001','Cuenta metas virtual','GOALS_VIRTUAL','PEN');
INSERT INTO public.categories(id,user_id,name,origin,is_active) VALUES
 ('58000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','Categoría 1','CUSTOM',true),
 ('58000000-0000-4000-8000-000000000002','51000000-0000-4000-8000-000000000001','Categoría 2','CUSTOM',true),
 ('58000000-0000-4000-8000-000000000003','51000000-0000-4000-8000-000000000001','Categoría 3','CUSTOM',true),
 ('58000000-0000-4000-8000-000000000004','51000000-0000-4000-8000-000000000001','Categoría 4','CUSTOM',true),
 ('58000000-0000-4000-8000-000000000005','51000000-0000-4000-8000-000000000001','Categoría 5','CUSTOM',true),
 ('58000000-0000-4000-8000-000000000006','51000000-0000-4000-8000-000000000001','Categoría 6','CUSTOM',true);

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '51000000-0000-4000-8000-000000000001';

SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000001','INSTRUMENTS',1,
  '[{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"}]'::jsonb
)), 'APPLIED', 'first owned instrument selection is accepted');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM private.plan_quota_selection_items), 1, 'accepted resource is stored');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000001','INSTRUMENTS',1,
  '[{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"}]'::jsonb
)), 'DUPLICATE', 'identical retry is idempotent');
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000001','INSTRUMENTS',1,'[]'::jsonb
)), 'CONFLICT', 'reusing operation ID with another payload conflicts');
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000002','INSTRUMENTS',1,'[]'::jsonb
)), 'STALE', 'concurrent older revision is stale');
RESET ROLE;
SELECT is((SELECT accepted_revision FROM private.plan_quota_selection_heads WHERE feature_key='INSTRUMENTS'), 1::bigint, 'stale request cannot move accepted revision');
SET LOCAL ROLE authenticated;
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000003','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)$$, '22000', NULL, 'another user’s account cannot be selected');
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000004','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000002","resource_type":"ACCOUNT"}]'::jsonb
)$$, '22000', NULL, 'cash is excluded from instrument quota selection');
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000005','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"},{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"}]'::jsonb
)$$, '22000', NULL, 'duplicate selected resources are rejected');
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000008','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"},{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"CARD"}]'::jsonb
)$$, '22000', NULL, 'one resource cannot be selected with multiple types');
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000009','INSTRUMENTS',2,NULL::jsonb
)$$, '22000', NULL, 'null selection payload is rejected');
SELECT throws_ok($$SELECT * FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000012','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000008","resource_type":"ACCOUNT"}]'::jsonb
)$$, '22000', NULL, 'credit liability account cannot consume another instrument slot');

SET LOCAL "request.jwt.claim.sub" = '51000000-0000-4000-8000-000000000002';
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000006','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
 )), 'APPLIED', 'a coalesced revision-2 snapshot advances from an empty remote head');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM private.plan_quota_selection_heads WHERE user_id='51000000-0000-4000-8000-000000000002'), 1, 'second user has independent quota head');
SELECT is((SELECT accepted_revision FROM private.plan_quota_selection_heads WHERE user_id='51000000-0000-4000-8000-000000000002'), 2::bigint, 'second user revision advances independently across a gap');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000007','INSTRUMENTS',4,'[]'::jsonb
)), 'APPLIED', 'later coalesced offline snapshot may also advance by a gap');
SELECT is((SELECT accepted_revision FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000007','INSTRUMENTS',4,'[]'::jsonb
)), '4', 'skipped snapshot revision becomes the accepted head');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM private.plan_quota_selection_items
  WHERE user_id='51000000-0000-4000-8000-000000000002' AND feature_key='INSTRUMENTS'), 0,
  'coalesced empty snapshot replaces the earlier device snapshot');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000015','INSTRUMENTS',4,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), 'STALE', 'a distinct operation at the accepted revision is stale');
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000013','INSTRUMENTS',3,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), 'STALE', 'delayed older second-device snapshot is stale');
SELECT is((SELECT accepted_revision FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000013','INSTRUMENTS',3,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), '4', 'delayed stale response carries the newer accepted revision for rebasing');
SELECT is((SELECT current_items FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000013','INSTRUMENTS',3,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), '[]'::jsonb, 'delayed stale response returns the current remote snapshot');
RESET ROLE;
SELECT is((SELECT accepted_revision FROM private.plan_quota_selection_heads
  WHERE user_id='51000000-0000-4000-8000-000000000002' AND feature_key='INSTRUMENTS'), 4::bigint,
  'delayed stale second-device request cannot move the accepted head');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000014','INSTRUMENTS',5,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), 'APPLIED', 'rebased latest local snapshot advances beyond the accepted revision');
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000014','INSTRUMENTS',5,
  '[{"resource_id":"52000000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"}]'::jsonb
)), 'DUPLICATE', 'replaying a rebased snapshot operation remains idempotent');
RESET ROLE;
SELECT is((SELECT accepted_revision FROM private.plan_quota_selection_heads
  WHERE user_id='51000000-0000-4000-8000-000000000002' AND feature_key='INSTRUMENTS'), 5::bigint,
  'rebased snapshot advances the head monotonically');
SELECT is((SELECT resource_id::text FROM private.plan_quota_selection_items
  WHERE user_id='51000000-0000-4000-8000-000000000002' AND feature_key='INSTRUMENTS'),
  '52000000-0000-4000-8000-000000000003', 'rebased latest local snapshot replaces remote items');

SET LOCAL "request.jwt.claim.sub" = '51000000-0000-4000-8000-000000000001';
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000010','INSTRUMENTS',2,
  '[{"resource_id":"52000000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"},{"resource_id":"52000000-0000-4000-8000-000000000004","resource_type":"ACCOUNT"},{"resource_id":"52000000-0000-4000-8000-000000000005","resource_type":"ACCOUNT"},{"resource_id":"52000000-0000-4000-8000-000000000006","resource_type":"ACCOUNT"}]'::jsonb
)), 'APPLIED', 'four of five active instruments are selected');
RESET ROLE;
SELECT lives_ok($$INSERT INTO public.transactions(id,user_id,account_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000001','EXPENSE',100,'PEN')$$,
  'selected account remains usable');
SELECT throws_ok($$INSERT INTO public.transactions(id,user_id,account_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000002','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000007','EXPENSE',100,'PEN')$$,
  '23514', 'source instrument is locked by the Free plan selection', 'unselected account is blocked at transaction boundary');
SELECT throws_ok($$INSERT INTO internal.ledger_entries(id,transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role)
  VALUES ('55000000-0000-4000-8000-000000000001','54000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000007',-100,'PEN','PRIMARY')$$,
  '23514', 'ledger instrument is locked by the Free plan selection', 'ledger boundary blocks transfers into unselected accounts');
SELECT lives_ok($$INSERT INTO public.transactions(id,user_id,account_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000003','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000002','EXPENSE',100,'PEN')$$,
  'cash remains usable when the instrument group exceeds its quota');
SELECT throws_ok($$INSERT INTO public.financial_movements(id,operation_id,user_id,kind,amount_minor_units,currency,account_id,effective_at)
  VALUES ('56000000-0000-4000-8000-000000000001','57000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','ADJUSTMENT',-100,'PEN','52000000-0000-4000-8000-000000000007',now())$$,
  '23514', 'source instrument is locked by the Free plan selection', 'legacy movement RPCs are also guarded');
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'53000000-0000-4000-8000-000000000011','CUSTOM_CATEGORIES',1,
  '[{"resource_id":"58000000-0000-4000-8000-000000000001","resource_type":"CATEGORY_ROOT"},{"resource_id":"58000000-0000-4000-8000-000000000002","resource_type":"CATEGORY_ROOT"},{"resource_id":"58000000-0000-4000-8000-000000000003","resource_type":"CATEGORY_ROOT"},{"resource_id":"58000000-0000-4000-8000-000000000004","resource_type":"CATEGORY_ROOT"},{"resource_id":"58000000-0000-4000-8000-000000000005","resource_type":"CATEGORY_ROOT"}]'::jsonb
)), 'APPLIED', 'five of six custom category roots are selected independently');
RESET ROLE;
SELECT lives_ok($$INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000004','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000001','58000000-0000-4000-8000-000000000001','EXPENSE',100,'PEN')$$,
  'selected custom category remains usable');
SELECT throws_ok($$INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000005','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000001','58000000-0000-4000-8000-000000000006','EXPENSE',100,'PEN')$$,
  '23514', 'category is locked by the Free plan selection', 'unselected custom category is blocked at transaction boundary');
SELECT lives_ok($$INSERT INTO public.transactions(id,user_id,account_id,transaction_type,amount_minor,currency_code)
  VALUES ('54000000-0000-4000-8000-000000000006','51000000-0000-4000-8000-000000000001','52000000-0000-4000-8000-000000000001','EXPENSE',100,'PEN')$$,
  'transaction without an over-quota group resource remains allowed');

SELECT * FROM finish();
ROLLBACK;
