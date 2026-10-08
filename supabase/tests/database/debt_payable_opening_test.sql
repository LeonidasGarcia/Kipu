BEGIN;
SELECT plan(18);

CREATE TEMP TABLE payable_opening_ids (
    owner_id uuid,
    other_id uuid,
    owner_account uuid,
    owner_usd_account uuid,
    other_account uuid,
    new_debt_id uuid,
    historical_debt_id uuid,
    operation_one uuid,
    operation_two uuid
);
INSERT INTO payable_opening_ids VALUES (
    '82000000-0000-4000-8000-000000000001',
    '82000000-0000-4000-8000-000000000002',
    '82000000-0000-4000-8000-000000000003',
    '82000000-0000-4000-8000-000000000004',
    '82000000-0000-4000-8000-000000000005',
    '82000000-0000-4000-8000-000000000006',
    '82000000-0000-4000-8000-000000000007',
    '82000000-0000-4000-8000-000000000008',
    '82000000-0000-4000-8000-000000000009'
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@payable.kipu.test' FROM payable_opening_ids
UNION ALL
SELECT other_id, other_id::text || '@payable.kipu.test' FROM payable_opening_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT owner_account, owner_id, 'PEN cash', 'SAVINGS'::public.account_type, 'PEN' FROM payable_opening_ids
UNION ALL
SELECT owner_usd_account, owner_id, 'USD cash', 'SAVINGS'::public.account_type, 'USD' FROM payable_opening_ids
UNION ALL
SELECT other_account, other_id, 'Other cash', 'SAVINGS'::public.account_type, 'PEN' FROM payable_opening_ids;
GRANT SELECT ON payable_opening_ids TO authenticated;

SELECT has_function('public', 'open_debt_v1', ARRAY['jsonb'], 'shared opening RPC is installed');
SELECT has_function('public', 'delete_debt_if_unreferenced_v1', ARRAY['jsonb'], 'conditional delete RPC is installed');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM payable_opening_ids), true);

SELECT lives_ok($$
    WITH input AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', (SELECT operation_one FROM payable_opening_ids),
        'user_id', (SELECT other_id FROM payable_opening_ids),
        'debt_id', (SELECT new_debt_id FROM payable_opening_ids),
        'obligation_type', 'PAYABLE',
        'counterparty_name', 'Banco local',
        'total_minor', 5000,
        'currency_code', 'PEN',
        'opened_on', '2026-10-08',
        'opening_mode', 'NEW_CASH_FLOW',
        'account_id', (SELECT owner_account FROM payable_opening_ids)
    ) AS payload), body AS (SELECT payload, payload - 'user_id' AS canonical FROM input)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(canonical::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'new payable records cash received and liability together');

RESET ROLE;

SELECT is((SELECT transaction_type::text FROM public.transactions
    WHERE id = (SELECT operation_one FROM payable_opening_ids)), 'INCOME',
    'cash received is represented as an income-shaped account movement');
SELECT is((SELECT operation_kind::text FROM public.transactions
    WHERE id = (SELECT operation_one FROM payable_opening_ids)), 'DEBT_DISBURSEMENT',
    'payable principal is explicitly non-operating');
SELECT is((SELECT signed_amount_minor FROM internal.ledger_entries
    WHERE transaction_id = (SELECT operation_one FROM payable_opening_ids)), 5000::bigint,
    'new payable increases account cash by its principal');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary
    WHERE debt_id = (SELECT new_debt_id FROM payable_opening_ids)), 5000::numeric,
    'new payable opens with the full principal outstanding');
SELECT is((SELECT opening_mode FROM public.debts
    WHERE id = (SELECT new_debt_id FROM payable_opening_ids)), 'NEW_CASH_FLOW',
    'new payable persists its opening basis');
SELECT is((SELECT count(*)::integer FROM public.transactions
    WHERE user_id = (SELECT owner_id FROM payable_opening_ids) AND operation_kind = 'STANDARD'), 0,
    'principal opening does not create an operating-income transaction');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM payable_opening_ids), true);

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'PAYABLE',
            'counterparty_name', 'Cuenta USD',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'NEW_CASH_FLOW',
            'account_id', (SELECT owner_usd_account FROM payable_opening_ids)
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '22023', 'ACCOUNT_CURRENCY_MISMATCH', 'payable opening rejects an account with another currency');

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'PAYABLE',
            'counterparty_name', 'Cuenta ajena',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'NEW_CASH_FLOW',
            'account_id', (SELECT other_account FROM payable_opening_ids)
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '42501', 'ACCOUNT_NOT_OWNED', 'payable opening rejects a foreign account reference');

SELECT lives_ok($$
    WITH body AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', (SELECT operation_two FROM payable_opening_ids),
        'debt_id', (SELECT historical_debt_id FROM payable_opening_ids),
        'obligation_type', 'PAYABLE',
        'counterparty_name', 'Deuda histórica',
        'total_minor', 9000,
        'currency_code', 'PEN',
        'opened_on', '2026-09-01',
        'opening_mode', 'HISTORICAL'
    ) AS payload)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'historical payable records principal without moving cash a second time');
RESET ROLE;
SELECT is((SELECT opening_mode FROM public.debts
    WHERE id = (SELECT historical_debt_id FROM payable_opening_ids)), 'HISTORICAL',
    'historical payable persists its opening basis');
SELECT is((SELECT count(*)::integer FROM public.transactions
    WHERE user_id = (SELECT owner_id FROM payable_opening_ids)), 1,
    'historical opening creates no second cash transaction');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM payable_opening_ids), true);
SELECT throws_ok($$
    WITH body AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', extensions.gen_random_uuid(),
        'debt_id', extensions.gen_random_uuid(),
        'obligation_type', 'RECEIVABLE',
        'counterparty_name', 'Tercera obligación',
        'total_minor', 1000,
        'currency_code', 'PEN',
        'opened_on', '2026-10-08',
        'opening_mode', 'HISTORICAL'
    ) AS payload)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'P0001', 'FREE_DEBT_QUOTA_EXCEEDED', 'combined Free limit blocks the third active obligation');

SELECT throws_ok($$
    SELECT public.delete_debt_if_unreferenced_v1(jsonb_build_object(
        'operation_id', extensions.gen_random_uuid(),
        'request_hash', repeat('f', 64),
        'debt_id', (SELECT new_debt_id FROM payable_opening_ids)
    ));
$$, 'P0001', 'HISTORY_PRESERVED', 'debt with a cash movement cannot be physically deleted');

RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.debts
    WHERE user_id = (SELECT owner_id FROM payable_opening_ids)), 2,
    'rejected currency, owner, quota, and delete commands leave only the two valid obligations');
SELECT is((SELECT count(*)::integer FROM public.debt_events
    WHERE user_id = (SELECT owner_id FROM payable_opening_ids)), 2,
    'each valid opening has one opening event');

SELECT * FROM finish();
ROLLBACK;
