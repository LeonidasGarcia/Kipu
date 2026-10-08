BEGIN;
SELECT plan(22);

CREATE TEMP TABLE debt_settlement_ids (
    owner_id uuid,
    other_id uuid,
    account_id uuid,
    foreign_account_id uuid,
    expense_category_id uuid,
    income_category_id uuid,
    payable_debt_id uuid,
    receivable_debt_id uuid,
    installment_id uuid,
    payable_operation_id uuid,
    receivable_operation_id uuid,
    request_payload jsonb,
    void_payload jsonb
);
INSERT INTO debt_settlement_ids VALUES (
    '67000000-0000-4000-8000-000000000001',
    '67000000-0000-4000-8000-000000000002',
    '67000000-0000-4000-8000-000000000003',
    '67000000-0000-4000-8000-000000000004',
    '67000000-0000-4000-8000-000000000005',
    '67000000-0000-4000-8000-000000000006',
    '67000000-0000-4000-8000-000000000007',
    '67000000-0000-4000-8000-000000000008',
    '67000000-0000-4000-8000-000000000009',
    '67000000-0000-4000-8000-000000000010',
    '67000000-0000-4000-8000-000000000011',
    NULL, NULL
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@debt-settlement.kipu.test' FROM debt_settlement_ids
UNION ALL
SELECT other_id, other_id::text || '@debt-settlement.kipu.test' FROM debt_settlement_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT account_id, owner_id, 'Owner cash', 'SAVINGS'::public.account_type, 'PEN' FROM debt_settlement_ids
UNION ALL
SELECT foreign_account_id, other_id, 'Other cash', 'SAVINGS'::public.account_type, 'PEN' FROM debt_settlement_ids;
INSERT INTO public.categories (id, user_id, name, category_type, origin, is_active)
SELECT expense_category_id, owner_id, 'Debt interest', 'EXPENSE', 'CUSTOM', true FROM debt_settlement_ids
UNION ALL
SELECT income_category_id, owner_id, 'Other income', 'INCOME', 'CUSTOM', true FROM debt_settlement_ids;
INSERT INTO public.debts (
    id, user_id, obligation_type, counterparty_name, total_minor, currency_code,
    opened_on, opening_mode, status, revision
)
SELECT payable_debt_id, owner_id, 'PAYABLE'::public.obligation_type, 'Supplier', 10000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1
FROM debt_settlement_ids
UNION ALL
SELECT receivable_debt_id, owner_id, 'RECEIVABLE'::public.obligation_type, 'Friend', 10000, 'PEN', DATE '2026-10-01', 'HISTORICAL', 'ACTIVE', 1
FROM debt_settlement_ids;
INSERT INTO public.debt_installments (id, user_id, debt_id, installment_number, due_date, amount_minor)
SELECT installment_id, owner_id, payable_debt_id, 1, DATE '2026-11-01', 5000 FROM debt_settlement_ids;

UPDATE debt_settlement_ids d
SET request_payload = jsonb_build_object(
    'contract_version', 1,
    'operation_id', payable_operation_id,
    'debt_id', payable_debt_id,
    'expected_revision', 1,
    'account_id', account_id,
    'principal_minor', 2000,
    'interest_minor', 100,
    'interest_category_id', expense_category_id,
    'installment_id', installment_id,
    'occurred_at', '2026-10-08T10:00:00Z',
    'notes', 'First installment'
);
UPDATE debt_settlement_ids d
SET request_payload = d.request_payload || jsonb_build_object(
    'request_hash', encode(extensions.digest(d.request_payload::text, 'sha256'), 'hex')
);
UPDATE debt_settlement_ids d
SET void_payload = (
    SELECT body.command || jsonb_build_object(
        'request_hash', internal.movement_revision_hash_v1(body.command)
    )
    FROM (
    SELECT jsonb_build_object(
        'contract_version', 1,
        'command_type', 'VOID_TRANSACTION',
        'idempotency_key', '67000000-0000-4000-8000-000000000012',
        'transaction_id', md5('kipu:debt-interest:' || d.payable_operation_id::text)::uuid,
        'expected_revision', 1,
        'depends_on_command_id', NULL,
        'reason', 'Correct debt settlement',
        'revised_payload', NULL
    ) AS command
    ) body
);
GRANT SELECT ON debt_settlement_ids TO authenticated;

SELECT has_function('public', 'settle_debt_v1', ARRAY['jsonb'], 'settlement RPC is available');
SELECT ok((SELECT prosecdef FROM pg_proc WHERE oid = 'public.settle_debt_v1(jsonb)'::regprocedure),
    'settlement RPC is SECURITY DEFINER with explicit owner checks');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM debt_settlement_ids), true);

SELECT is((SELECT public.settle_debt_v1(request_payload)->>'status' FROM debt_settlement_ids),
    'APPLIED', 'payable principal and interest settle atomically');
SELECT is((
    SELECT jsonb_build_array(e.amount_minor, e.principal_delta_minor, e.interest_transaction_id)
    FROM public.debt_events e WHERE e.debt_id = (SELECT payable_debt_id FROM debt_settlement_ids)
), jsonb_build_array(2000, -2000, md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid),
    'the debt event records principal and its linked interest transaction');
SELECT is((
    SELECT count(*)::integer FROM public.transactions t
    WHERE t.id IN (
        (SELECT payable_operation_id FROM debt_settlement_ids),
        md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid
    ) AND t.user_id = (SELECT owner_id FROM debt_settlement_ids)
      AND t.operation_kind::text IN ('DEBT_PAYMENT', 'DEBT_AMORTIZATION')
), 2, 'principal and categorized interest have distinct movement kinds');
RESET ROLE;
SELECT is((
    SELECT sum(le.signed_amount_minor)::bigint FROM internal.ledger_entries le
    WHERE le.transaction_id IN (
        (SELECT payable_operation_id FROM debt_settlement_ids),
        md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid
    )
), -2100::bigint, 'payable cash decreases by principal plus interest');
SET LOCAL ROLE authenticated;
SELECT is((SELECT status FROM public.debt_installments WHERE id = (SELECT installment_id FROM debt_settlement_ids)),
    'PARTIAL', 'installment allocation is recalculated from principal paid');
SELECT is((SELECT public.settle_debt_v1(request_payload)->>'status' FROM debt_settlement_ids),
    'DUPLICATE', 'retry with the same request returns the stored receipt');
SELECT is((SELECT count(*)::integer FROM public.debt_events WHERE debt_id = (SELECT payable_debt_id FROM debt_settlement_ids)),
    1, 'retry creates no duplicate debt event');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE user_id = (SELECT owner_id FROM debt_settlement_ids)
    AND id IN ((SELECT payable_operation_id FROM debt_settlement_ids),
        md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid)),
    2, 'retry creates at most one principal and one interest movement');

SELECT is((
    SELECT public.settle_debt_v1(jsonb_build_object(
        'contract_version', 1, 'operation_id', receivable_operation_id, 'debt_id', receivable_debt_id,
        'expected_revision', 1, 'account_id', account_id, 'principal_minor', 3000, 'interest_minor', 0,
        'interest_category_id', NULL, 'installment_id', NULL, 'occurred_at', '2026-10-08T10:05:00Z',
        'notes', 'Collection', 'request_hash', repeat('a', 64)
    ))->>'status' FROM debt_settlement_ids
), 'APPLIED', 'receivable principal can be settled without operating expense category');
RESET ROLE;
SELECT is((
    SELECT sum(le.signed_amount_minor)::bigint FROM internal.ledger_entries le
    WHERE le.transaction_id = (SELECT receivable_operation_id FROM debt_settlement_ids)
), 3000::bigint, 'receivable principal increases cash');
SET LOCAL ROLE authenticated;
SELECT throws_ok($$
    SELECT public.settle_debt_v1(
        request_payload || jsonb_build_object('operation_id', '67000000-0000-4000-8000-000000000013',
            'expected_revision', 2, 'account_id', (SELECT foreign_account_id FROM debt_settlement_ids), 'request_hash', repeat('b', 64))
    ) FROM debt_settlement_ids;
$$, '42501', 'ACCOUNT_NOT_OWNED', 'another owner account cannot be used');
SELECT throws_ok($$
    SELECT public.settle_debt_v1(
        request_payload || jsonb_build_object('operation_id', '67000000-0000-4000-8000-000000000014',
            'expected_revision', 2, 'interest_category_id', (SELECT income_category_id FROM debt_settlement_ids), 'request_hash', repeat('c', 64))
    ) FROM debt_settlement_ids;
$$, '22023', 'INVALID_INTEREST_CATEGORY', 'payable interest rejects an income category');
SELECT throws_ok($$
    SELECT public.settle_debt_v1(
        request_payload || jsonb_build_object('operation_id', '67000000-0000-4000-8000-000000000015',
            'expected_revision', 2, 'principal_minor', 9000, 'request_hash', repeat('d', 64))
    ) FROM debt_settlement_ids;
$$, 'P0001', 'PRINCIPAL_EXCEEDS_REMAINING', 'overpayment is rejected');
SELECT is((
    SELECT public.settle_debt_v1(request_payload || jsonb_build_object(
        'operation_id', '67000000-0000-4000-8000-000000000016', 'expected_revision', 1,
        'request_hash', repeat('e', 64)
    ))->>'status' FROM debt_settlement_ids
), 'CONFLICT', 'stale debt revision does not apply a second payment');

SELECT is((SELECT public.void_transaction_v1(void_payload)->>'status' FROM debt_settlement_ids),
    'APPLIED', 'voiding either settlement movement invalidates the linked pair atomically');
SELECT is((
    SELECT count(*)::integer FROM public.transactions t
    WHERE t.id IN (
        (SELECT payable_operation_id FROM debt_settlement_ids),
        md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid
    ) AND t.status = 'VOIDED'
), 2, 'grouped void marks principal and interest as void');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id = (SELECT payable_debt_id FROM debt_settlement_ids)),
    10000::numeric, 'void restores the derived principal balance');
SELECT is((SELECT status FROM public.debt_installments WHERE id = (SELECT installment_id FROM debt_settlement_ids)),
    'PENDING', 'void restores installment state');
RESET ROLE;
SELECT is((
    SELECT sum(le.signed_amount_minor)::bigint FROM internal.ledger_entries le
    WHERE le.transaction_id IN (
        (SELECT payable_operation_id FROM debt_settlement_ids),
        md5('kipu:debt-interest:' || (SELECT payable_operation_id::text FROM debt_settlement_ids))::uuid
    )
), 0::bigint, 'grouped void reverses both cash effects');

SELECT is((SELECT count(*)::integer FROM internal.command_receipts
    WHERE user_id = (SELECT owner_id FROM debt_settlement_ids) AND command_type = 'SETTLE_DEBT'),
    2, 'one settlement receipt exists for each applied obligation command');
SELECT * FROM finish();
ROLLBACK;
