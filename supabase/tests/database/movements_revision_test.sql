BEGIN;
SELECT plan(42);

INSERT INTO auth.users(id,email) VALUES
 ('80000000-0000-4000-8000-000000000001','s4-revision-owner@kipu.test'),
 ('80000000-0000-4000-8000-000000000002','s4-revision-other@kipu.test')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES
 ('85100000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Bank 1','BANK','PEN'),
 ('85100000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001','Bank 2','BANK','PEN'),
 ('85100000-0000-4000-8000-000000000003','80000000-0000-4000-8000-000000000001','Bank 3','BANK','PEN'),
 ('85100000-0000-4000-8000-000000000004','80000000-0000-4000-8000-000000000001','Bank 4','BANK','PEN'),
 ('85100000-0000-4000-8000-000000000005','80000000-0000-4000-8000-000000000001','Bank 5','BANK','PEN');

INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES
 ('81000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Cash A','CASH','PEN'),
 ('81000000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001','Cash B','CASH','PEN')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.categories(id,user_id,name,origin,category_type) VALUES
 ('86000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001','Alimentos','USER_CREATED','EXPENSE')
ON CONFLICT(id) DO NOTHING;

INSERT INTO public.transactions(
 id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,
 occurred_at,status,revision
) VALUES
 ('82000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001','86000000-0000-4000-8000-000000000001','EXPENSE','STANDARD',2000,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1),
 ('82000000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001','86000000-0000-4000-8000-000000000001','EXPENSE','CARD_PURCHASE',500,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1),
 ('82000000-0000-4000-8000-000000000003','80000000-0000-4000-8000-000000000001',
  '81000000-0000-4000-8000-000000000001',NULL,'TRANSFER','STANDARD',1000,'PEN',
  '2026-09-15T12:00:00Z','CONFIRMED',1);

INSERT INTO internal.ledger_entries(
 id,transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
) VALUES
 ('83000000-0000-4000-8000-000000000001','82000000-0000-4000-8000-000000000001',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-2000,'PEN','PRIMARY','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000002','82000000-0000-4000-8000-000000000002',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-500,'PEN','PRIMARY','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000003','82000000-0000-4000-8000-000000000003',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001',-1000,'PEN','SOURCE','2026-09-15T12:00:00Z'),
 ('83000000-0000-4000-8000-000000000004','82000000-0000-4000-8000-000000000003',
  '80000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000002',1000,'PEN','DESTINATION','2026-09-15T12:00:00Z');

SELECT ok(has_function_privilege('authenticated','public.revise_transaction_v1(jsonb)','EXECUTE'),
 'authenticated can call revision RPC');
SELECT ok(has_function_privilege('authenticated','public.void_transaction_v1(jsonb)','EXECUTE'),
 'authenticated can call void RPC');
SELECT ok(NOT has_function_privilege('anon','public.revise_transaction_v1(jsonb)','EXECUTE'),
 'anonymous cannot call revision RPC');
SELECT ok(NOT has_function_privilege('authenticated','internal.apply_movement_revision_v1(jsonb,text)','EXECUTE'),
 'internal mutator is not callable by clients');

SELECT is(internal.movement_revision_hash_v1(jsonb_build_object(
 'contract_version',1,'command_type','VOID_TRANSACTION',
 'idempotency_key','33333333-3333-3333-3333-333333333333',
 'transaction_id','22222222-2222-2222-2222-222222222222',
 'expected_revision',1,'depends_on_command_id',NULL,
 'reason','anulación ñ 🦙; "x"' || chr(92) || chr(10),
 'revised_payload',NULL
)), '44deef93fef683489a80af88b387f2e5e34bf0c43e9751793b52f5518743135d',
 'server reproduces Kotlin canonical VOID golden hash');

SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000001';
SET LOCAL ROLE authenticated;
SELECT is((SELECT result FROM public.apply_plan_quota_selection(
  1,'85200000-0000-4000-8000-000000000001','INSTRUMENTS',1,
  '[{"resource_id":"85100000-0000-4000-8000-000000000001","resource_type":"ACCOUNT"},{"resource_id":"85100000-0000-4000-8000-000000000002","resource_type":"ACCOUNT"},{"resource_id":"85100000-0000-4000-8000-000000000003","resource_type":"ACCOUNT"},{"resource_id":"85100000-0000-4000-8000-000000000004","resource_type":"ACCOUNT"}]'::jsonb
)), 'APPLIED','owner can set the Free instrument quota selection');
RESET ROLE;
SELECT is((
 SELECT public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000001',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',1,'depends_on_command_id',NULL,'reason','correct amount',
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','STANDARD','amount_minor',1500,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note','adjusted'
   )
 ) AS c) command
), 'APPLIED','revision commits');
SELECT is((
 SELECT (public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->'error'->>'code')
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000008',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',2,'depends_on_command_id',NULL,'reason','use locked instrument',
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','STANDARD','amount_minor',1400,'currency_code','PEN',
     'source_account_id','85100000-0000-4000-8000-000000000005',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note','locked'
   )
 ) AS c) command
), 'INVALID_REFERENCE','a new plan-locked instrument cannot be selected for a revision');
SELECT is((SELECT revision FROM public.transactions WHERE id='82000000-0000-4000-8000-000000000001'),
  2::bigint,'a locked-reference rejection preserves the current revision');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
  3::bigint,'a locked-reference rejection appends no ledger effects');
SELECT is((SELECT response_payload->>'result' FROM internal.command_receipts WHERE idempotency_key='84000000-0000-4000-8000-000000000001'),
 'REVISED','receipt records the resulting state');
SELECT is((SELECT revision FROM public.transactions WHERE id='82000000-0000-4000-8000-000000000001'),
 2::bigint,'revision increments once');
SELECT is((SELECT status FROM public.transactions WHERE id='82000000-0000-4000-8000-000000000001'),
 'REVISED','transaction remains a live movement');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 -1500::numeric,'20 to 15 appends reversal and replacement effect');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 3::bigint,'original accounting row is preserved');

SELECT is((
 SELECT public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000004',
   'transaction_id','82000000-0000-4000-8000-000000000003',
   'expected_revision',1,'depends_on_command_id',NULL,'reason','revise transfer',
   'revised_payload',jsonb_build_object(
     'type','TRANSFER','operation_kind','STANDARD','amount_minor',800,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id','81000000-0000-4000-8000-000000000002',
     'category_id',NULL,'merchant_id',NULL,'merchant_provisional_text',NULL,
     'occurred_at','2026-09-15T12:00:00.000Z','note','transfer adjusted'
   )
 ) AS c) command
), 'APPLIED','transfer revision commits both accounts');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000003'),
  6::bigint,'transfer correction appends both reversals and both replacement effects');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000003'),
  0::numeric,'transfer correction preserves zero-sum cash');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries
           WHERE transaction_id='82000000-0000-4000-8000-000000000003'
             AND account_id='81000000-0000-4000-8000-000000000001'),
  -800::numeric,'transfer source receives the revised amount');

SELECT is((
 SELECT public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000001',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',1,'depends_on_command_id',NULL,'reason','correct amount',
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','STANDARD','amount_minor',1500,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note','adjusted'
   )
 ) AS c) command
), 'DUPLICATE','replay returns the original receipt');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 3::bigint,'replay does not add ledger effects');

SELECT is((
 SELECT public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000001',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',2,'depends_on_command_id',NULL,'reason','different command',
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','STANDARD','amount_minor',1400,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note','different'
   )
 ) AS c) command
), 'CONFLICT','a key cannot be reused for another request');
SELECT is((
 SELECT public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000005',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',1,'depends_on_command_id',NULL,'reason',NULL,
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','STANDARD','amount_minor',1500,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note','adjusted'
   )
 ) AS c) command
), 'CONFLICT','stale expected revision cannot overwrite the head');

SELECT is((
 SELECT public.void_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','VOID_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000002',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',2,'depends_on_command_id',NULL,'reason','duplicate purchase',
   'revised_payload',NULL
 ) AS c) command
), 'APPLIED','void commits');
SELECT is((SELECT status FROM public.transactions WHERE id='82000000-0000-4000-8000-000000000001'),
 'VOIDED','void uses status, not row deletion');
SELECT is((SELECT revision FROM public.transactions WHERE id='82000000-0000-4000-8000-000000000001'),
 3::bigint,'void receives a new official revision');
SELECT is((SELECT sum(signed_amount_minor) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 0::numeric,'void balances the complete original and revised ledger');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 4::bigint,'void appends a contra entry and preserves all prior rows');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE id='83000000-0000-4000-8000-000000000001'),
 1::bigint,'original ledger entry remains auditable');

SELECT is((
 SELECT public.void_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'status'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','VOID_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000002',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',2,'depends_on_command_id',NULL,'reason','duplicate purchase',
   'revised_payload',NULL
 ) AS c) command
), 'DUPLICATE','void replay returns the first result');
SELECT is((
 SELECT public.void_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->>'result'
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','VOID_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000003',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',3,'depends_on_command_id',NULL,'reason','already void',
   'revised_payload',NULL
 ) AS c) command
), 'ALREADY_VOIDED','new void on current voided head adds only a receipt');
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE transaction_id='82000000-0000-4000-8000-000000000001'),
 4::bigint,'repeat void creates no new accounting rows');
SELECT is((SELECT count(*) FROM internal.sync_changes
           WHERE user_id='80000000-0000-4000-8000-000000000001'
             AND entity_type='TRANSACTION' AND entity_id='82000000-0000-4000-8000-000000000001'
             AND revision IN (2,3)),
  2::bigint,'revision and void emit atomic owner-scoped sync changes');

SELECT is((
 SELECT (public.revise_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->'error'->>'code')
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','REVISE_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000006',
   'transaction_id','82000000-0000-4000-8000-000000000002',
   'expected_revision',1,'depends_on_command_id',NULL,'reason',NULL,
   'revised_payload',jsonb_build_object(
     'type','EXPENSE','operation_kind','CARD_PURCHASE','amount_minor',500,'currency_code','PEN',
     'source_account_id','81000000-0000-4000-8000-000000000001',
     'destination_account_id',NULL,'category_id','86000000-0000-4000-8000-000000000001','merchant_id',NULL,
     'merchant_provisional_text',NULL,'occurred_at','2026-09-15T12:00:00.000Z','note',NULL
   )
 ) AS c) command
), 'OPERATION_SPECIALIZED','specialized financial operation cannot use generic edit');

SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000002';
SELECT is((
 SELECT (public.void_transaction_v1(
   c || jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c))
 )->'error'->>'code')
 FROM (SELECT jsonb_build_object(
   'contract_version',1,'command_type','VOID_TRANSACTION',
   'idempotency_key','84000000-0000-4000-8000-000000000007',
   'transaction_id','82000000-0000-4000-8000-000000000001',
   'expected_revision',3,'depends_on_command_id',NULL,'reason',NULL,'revised_payload',NULL
 ) AS c) command
), 'NOT_FOUND','another owner cannot infer or mutate the transaction');

SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000001';
SET LOCAL ROLE authenticated;
SELECT is((SELECT count(*)::integer FROM public.transaction_revisions
           WHERE transaction_id='82000000-0000-4000-8000-000000000001'),3,
          'owner can read own revision evidence');
SET LOCAL "request.jwt.claim.sub"='80000000-0000-4000-8000-000000000002';
SELECT is((SELECT count(*)::integer FROM public.transaction_revisions
           WHERE transaction_id='82000000-0000-4000-8000-000000000001'),0,
          'RLS hides another owner revision evidence');
SELECT throws_ok($$UPDATE public.transaction_revisions SET change_reason='tamper'$$,
                 '42501',NULL,'authenticated cannot rewrite revision evidence');
SELECT throws_ok($$INSERT INTO public.movement_official_revisions(
                 user_id,transaction_id,official_revision,revision_id,official_revision_id)
                 VALUES('80000000-0000-4000-8000-000000000002',
                        '82000000-0000-4000-8000-000000000001',99,
                        '82000000-0000-4000-8000-000000000001',
                        '82000000-0000-4000-8000-000000000001')$$,
                 '42501',NULL,'authenticated cannot assign official revisions directly');
RESET ROLE;

SELECT throws_ok($$UPDATE public.transaction_revisions SET change_reason='tamper'$$,
                 '55000',NULL,'database trigger makes revision snapshots append-only');
SET LOCAL ROLE authenticated;
SELECT throws_ok($$DELETE FROM internal.ledger_entries WHERE id='83000000-0000-4000-8000-000000000001'$$,
                 '42501',NULL,'client role has no physical ledger delete path');
RESET ROLE;
SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE id='83000000-0000-4000-8000-000000000001'),
 1::bigint,'no RPC physically deleted the original ledger row');
SELECT * FROM finish();
ROLLBACK;
