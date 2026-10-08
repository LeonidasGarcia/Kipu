BEGIN;
SELECT plan(28);

CREATE TEMP TABLE debt_schedule_ids (
    owner_id uuid,
    other_id uuid,
    schedule_debt_id uuid,
    adjust_debt_id uuid,
    forgive_debt_id uuid,
    foreign_debt_id uuid,
    schedule_operation_id uuid,
    cancel_plan_operation_id uuid,
    cancel_debt_operation_id uuid,
    adjust_operation_id uuid,
    forgive_operation_id uuid,
    bad_sum_operation_id uuid,
    schedule_payload jsonb,
    cancel_plan_payload jsonb,
    cancel_debt_payload jsonb,
    adjust_payload jsonb,
    forgive_payload jsonb,
    bad_sum_payload jsonb
);
INSERT INTO debt_schedule_ids VALUES (
    '68000000-0000-4000-8000-000000000001',
    '68000000-0000-4000-8000-000000000002',
    '68000000-0000-4000-8000-000000000003',
    '68000000-0000-4000-8000-000000000004',
    '68000000-0000-4000-8000-000000000005',
    '68000000-0000-4000-8000-000000000006',
    '68000000-0000-4000-8000-000000000010',
    '68000000-0000-4000-8000-000000000011',
    '68000000-0000-4000-8000-000000000012',
    '68000000-0000-4000-8000-000000000013',
    '68000000-0000-4000-8000-000000000014',
    '68000000-0000-4000-8000-000000000015',
    NULL, NULL, NULL, NULL, NULL, NULL
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@debt-schedule.kipu.test' FROM debt_schedule_ids
UNION ALL
SELECT other_id, other_id::text || '@debt-schedule.kipu.test' FROM debt_schedule_ids;
INSERT INTO public.debts (
    id, user_id, obligation_type, counterparty_name, total_minor, currency_code,
    opened_on, opening_mode, status, revision
)
SELECT schedule_debt_id, owner_id, 'PAYABLE'::public.obligation_type, 'Proveedor', 10001, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1 FROM debt_schedule_ids
UNION ALL
SELECT adjust_debt_id, owner_id, 'RECEIVABLE'::public.obligation_type, 'Amistad', 1000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1 FROM debt_schedule_ids
UNION ALL
SELECT forgive_debt_id, owner_id, 'PAYABLE'::public.obligation_type, 'Acuerdo', 1000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1 FROM debt_schedule_ids
UNION ALL
SELECT foreign_debt_id, other_id, 'PAYABLE'::public.obligation_type, 'Otra deuda', 1000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1 FROM debt_schedule_ids;

UPDATE debt_schedule_ids d SET schedule_payload = jsonb_build_object(
    'contract_version', 1, 'operation_id', schedule_operation_id, 'debt_id', schedule_debt_id,
    'expected_revision', 1, 'reminder_lead_days', 3, 'cancel_schedule', false,
    'installments', jsonb_build_array(
        jsonb_build_object('id','68000000-0000-4000-8000-000000000020','installment_number',1,'due_date','2026-11-10','principal_minor',3334),
        jsonb_build_object('id','68000000-0000-4000-8000-000000000021','installment_number',2,'due_date','2026-12-10','principal_minor',3334),
        jsonb_build_object('id','68000000-0000-4000-8000-000000000022','installment_number',3,'due_date','2027-01-10','principal_minor',3333)
    ), 'request_hash', repeat('a',64)
);
UPDATE debt_schedule_ids d SET cancel_plan_payload = jsonb_build_object(
    'contract_version',1,'operation_id',cancel_plan_operation_id,'debt_id',schedule_debt_id,
    'expected_revision',2,'reminder_lead_days',NULL,'cancel_schedule',true,'installments','[]'::jsonb,
    'request_hash',repeat('b',64)
);
UPDATE debt_schedule_ids d SET cancel_debt_payload = jsonb_build_object(
    'contract_version',1,'operation_id',cancel_debt_operation_id,'debt_id',schedule_debt_id,
    'expected_revision',3,'action','CANCEL','reason','Acuerdo cancelado','request_hash',repeat('c',64)
);
UPDATE debt_schedule_ids d SET adjust_payload = jsonb_build_object(
    'contract_version',1,'operation_id',adjust_operation_id,'debt_id',adjust_debt_id,
    'expected_revision',1,'action','ADJUST','principal_delta_minor',100,'reason','Ajuste confirmado','request_hash',repeat('d',64)
);
UPDATE debt_schedule_ids d SET forgive_payload = jsonb_build_object(
    'contract_version',1,'operation_id',forgive_operation_id,'debt_id',forgive_debt_id,
    'expected_revision',1,'action','FORGIVE','amount_minor',1000,'reason','Condonación acordada','request_hash',repeat('e',64)
);
UPDATE debt_schedule_ids d SET bad_sum_payload = schedule_payload || jsonb_build_object(
    'operation_id',bad_sum_operation_id,'request_hash',repeat('f',64),'expected_revision',2,
    'installments',jsonb_build_array(
        jsonb_build_object('id','68000000-0000-4000-8000-000000000030','installment_number',4,'due_date','2026-11-10','principal_minor',3333),
        jsonb_build_object('id','68000000-0000-4000-8000-000000000031','installment_number',5,'due_date','2026-12-10','principal_minor',3333),
        jsonb_build_object('id','68000000-0000-4000-8000-000000000032','installment_number',6,'due_date','2027-01-10','principal_minor',3333)
    )
);
GRANT SELECT ON debt_schedule_ids TO authenticated;

SELECT has_function('public','set_debt_schedule_v1',ARRAY['jsonb'],'schedule RPC is available');
SELECT has_function('public','close_debt_v1',ARRAY['jsonb'],'closure RPC is available');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub',(SELECT owner_id::text FROM debt_schedule_ids),true);
SELECT is((SELECT public.set_debt_schedule_v1(schedule_payload)->>'status' FROM debt_schedule_ids),'APPLIED','exact-sum schedule is accepted');
SELECT is((SELECT sum(amount_minor)::bigint FROM public.debt_installments WHERE debt_id=(SELECT schedule_debt_id FROM debt_schedule_ids) AND status='PENDING'),10001::bigint,'planned installments sum to the outstanding principal');
SELECT is((SELECT count(*)::integer FROM public.debt_installments WHERE debt_id=(SELECT schedule_debt_id FROM debt_schedule_ids)),3,'schedule creates three owned installments');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id=(SELECT schedule_debt_id FROM debt_schedule_ids)),10001::numeric,'planning leaves the derived balance unchanged');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE user_id=(SELECT owner_id FROM debt_schedule_ids)),0,'planning creates no financial movement');
SELECT is((SELECT public.set_debt_schedule_v1(schedule_payload)->>'status' FROM debt_schedule_ids),'DUPLICATE','schedule replay is idempotent');
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT throws_ok($$SELECT public.set_debt_schedule_v1(bad_sum_payload) FROM debt_schedule_ids$$,'22023','SCHEDULE_TOTAL_MISMATCH','server rejects a schedule whose amounts do not sum exactly');
SELECT set_config('request.jwt.claim.sub',(SELECT other_id::text FROM debt_schedule_ids),true);
SELECT throws_ok($$SELECT public.set_debt_schedule_v1(schedule_payload) FROM debt_schedule_ids$$,'42501','DEBT_NOT_OWNED','another owner cannot schedule this debt');
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub',(SELECT owner_id::text FROM debt_schedule_ids),true);
SELECT is((SELECT public.set_debt_schedule_v1(cancel_plan_payload)->>'status' FROM debt_schedule_ids),'APPLIED','schedule can be cancelled independently of the debt');
SELECT is((SELECT count(*)::integer FROM public.debt_installments WHERE debt_id=(SELECT schedule_debt_id FROM debt_schedule_ids) AND status='PENDING'),0,'schedule cancellation clears pending installments');
SELECT is((SELECT public.close_debt_v1(cancel_debt_payload)->>'status' FROM debt_schedule_ids),'APPLIED','cancellation is an explicit closure action');
SELECT is((SELECT status FROM public.debts WHERE id=(SELECT schedule_debt_id FROM debt_schedule_ids)),'CANCELLED','cancelled debt is not shown as settled');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id=(SELECT schedule_debt_id FROM debt_schedule_ids)),10001::numeric,'debt cancellation preserves the outstanding balance');
SELECT is((SELECT count(*)::integer FROM public.debts WHERE user_id=(SELECT owner_id FROM debt_schedule_ids) AND status='ACTIVE'),2,'cancellation releases one active-obligation slot');
RESET ROLE;
SELECT is((SELECT command_payload->>'reason' FROM internal.command_receipts WHERE user_id=(SELECT owner_id FROM debt_schedule_ids) AND idempotency_key=(SELECT cancel_debt_operation_id::text FROM debt_schedule_ids)),'Acuerdo cancelado','cancellation reason is retained in the audit receipt');
SET LOCAL ROLE authenticated;
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE user_id=(SELECT owner_id FROM debt_schedule_ids)),0,'schedule and debt cancellation create no financial movements');
SELECT is((SELECT public.close_debt_v1(adjust_payload)->>'status' FROM debt_schedule_ids),'APPLIED','auditable adjustment is accepted');
SELECT is((SELECT principal_delta_minor FROM public.debt_events WHERE debt_id=(SELECT adjust_debt_id FROM debt_schedule_ids) AND event_type='ADJUSTMENT'),100::bigint,'adjustment stores its signed principal delta');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id=(SELECT adjust_debt_id FROM debt_schedule_ids)),1100::numeric,'positive adjustment increases the derived balance');
SELECT throws_ok($$SELECT public.close_debt_v1((adjust_payload - 'principal_delta_minor') || jsonb_build_object('operation_id','68000000-0000-4000-8000-000000000016','request_hash',repeat('1',64),'expected_revision',2,'action','SETTLE')) FROM debt_schedule_ids$$,'P0001','BALANCE_REMAINS','SETTLE is rejected while a derived balance remains');
SELECT is((SELECT public.close_debt_v1(forgive_payload)->>'status' FROM debt_schedule_ids),'APPLIED','forgiveness is recorded as a closure action');
SELECT is((SELECT principal_delta_minor FROM public.debt_events WHERE debt_id=(SELECT forgive_debt_id FROM debt_schedule_ids) AND event_type='FORGIVENESS'),-1000::bigint,'forgiveness records a negative delta and audit event');
RESET ROLE;
SELECT is((SELECT command_payload->>'reason' FROM internal.command_receipts WHERE user_id=(SELECT owner_id FROM debt_schedule_ids) AND idempotency_key=(SELECT forgive_operation_id::text FROM debt_schedule_ids)),U&'Condonaci\00F3n acordada','forgiveness reason is retained in the audit receipt');
SET LOCAL ROLE authenticated;
SELECT is((SELECT status FROM public.debts WHERE id=(SELECT forgive_debt_id FROM debt_schedule_ids)),'SETTLED','zero derived balance leaves the debt inactive');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id=(SELECT forgive_debt_id FROM debt_schedule_ids)),0::numeric,'forgiveness closes only the verified remaining principal');
SELECT is((SELECT count(*)::integer FROM public.debt_events WHERE user_id=(SELECT owner_id FROM debt_schedule_ids) AND event_type IN ('ADJUSTMENT','FORGIVENESS')),2,'closure actions retain an auditable event history');

SELECT * FROM finish();
ROLLBACK;
