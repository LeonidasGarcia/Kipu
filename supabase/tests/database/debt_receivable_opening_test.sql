BEGIN;
SELECT plan(14);

CREATE TEMP TABLE receivable_opening_ids (
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
INSERT INTO receivable_opening_ids VALUES (
    '83000000-0000-4000-8000-000000000001',
    '83000000-0000-4000-8000-000000000002',
    '83000000-0000-4000-8000-000000000003',
    '83000000-0000-4000-8000-000000000004',
    '83000000-0000-4000-8000-000000000005',
    '83000000-0000-4000-8000-000000000006',
    '83000000-0000-4000-8000-000000000007',
    '83000000-0000-4000-8000-000000000008',
    '83000000-0000-4000-8000-000000000009'
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@receivable.kipu.test' FROM receivable_opening_ids
UNION ALL
SELECT other_id, other_id::text || '@receivable.kipu.test' FROM receivable_opening_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT owner_account, owner_id, 'PEN cash', 'SAVINGS'::public.account_type, 'PEN' FROM receivable_opening_ids
UNION ALL
SELECT owner_usd_account, owner_id, 'USD cash', 'SAVINGS'::public.account_type, 'USD' FROM receivable_opening_ids
UNION ALL
SELECT other_account, other_id, 'Other cash', 'SAVINGS'::public.account_type, 'PEN' FROM receivable_opening_ids;
GRANT SELECT ON receivable_opening_ids TO authenticated;

SELECT has_function('private', 'open_receivable_debt_v1', ARRAY['jsonb'], 'receivable opening handler is installed');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM receivable_opening_ids), true);

SELECT lives_ok($$
    WITH body AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', (SELECT operation_one FROM receivable_opening_ids),
        'debt_id', (SELECT new_debt_id FROM receivable_opening_ids),
        'obligation_type', 'RECEIVABLE',
        'counterparty_name', 'Amiga del barrio',
        'total_minor', 5000,
        'currency_code', 'PEN',
        'opened_on', '2026-10-08',
        'opening_mode', 'NEW_CASH_FLOW',
        'account_id', (SELECT owner_account FROM receivable_opening_ids)
    ) AS payload)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'new receivable records cash paid and receivable principal together');

RESET ROLE;

SELECT is((SELECT transaction_type::text FROM public.transactions
    WHERE id = (SELECT operation_one FROM receivable_opening_ids)), 'EXPENSE',
    'cash paid is represented as an expense-shaped account movement');
SELECT is((SELECT operation_kind::text FROM public.transactions
    WHERE id = (SELECT operation_one FROM receivable_opening_ids)), 'DEBT_DISBURSEMENT',
    'receivable principal is explicitly non-operating');
SELECT is((SELECT signed_amount_minor FROM internal.ledger_entries
    WHERE transaction_id = (SELECT operation_one FROM receivable_opening_ids)), -5000::bigint,
    'new receivable decreases account cash by its principal');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary
    WHERE debt_id = (SELECT new_debt_id FROM receivable_opening_ids)), 5000::numeric,
    'new receivable opens with the full principal outstanding');
SELECT is((SELECT count(*)::integer FROM public.transactions
    WHERE user_id = (SELECT owner_id FROM receivable_opening_ids) AND operation_kind = 'STANDARD'), 0,
    'principal opening does not create an operating-expense transaction');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM receivable_opening_ids), true);

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'RECEIVABLE',
            'counterparty_name', 'Cuenta USD',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'NEW_CASH_FLOW',
            'account_id', (SELECT owner_usd_account FROM receivable_opening_ids)
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '22023', 'ACCOUNT_CURRENCY_MISMATCH', 'receivable rejects an account with another currency');

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'RECEIVABLE',
            'counterparty_name', 'Cuenta ajena',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'NEW_CASH_FLOW',
            'account_id', (SELECT other_account FROM receivable_opening_ids)
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '42501', 'ACCOUNT_NOT_OWNED', 'receivable rejects a foreign account reference');

SELECT throws_ok($$
    WITH body AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', extensions.gen_random_uuid(),
        'debt_id', extensions.gen_random_uuid(),
        'obligation_type', 'RECEIVABLE',
        'counterparty_name', 'Cuenta faltante',
        'total_minor', 1000,
        'currency_code', 'PEN',
        'opened_on', '2026-10-08',
        'opening_mode', 'NEW_CASH_FLOW'
    ) AS payload)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '22023', 'ACCOUNT_REQUIRED', 'new receivable requires an owned source account');

SELECT lives_ok($$
    WITH body AS (SELECT jsonb_build_object(
        'contract_version', 1,
        'operation_id', (SELECT operation_two FROM receivable_opening_ids),
        'debt_id', (SELECT historical_debt_id FROM receivable_opening_ids),
        'obligation_type', 'RECEIVABLE',
        'counterparty_name', 'Historial de préstamo',
        'total_minor', 9000,
        'currency_code', 'PEN',
        'opened_on', '2026-09-01',
        'opening_mode', 'HISTORICAL'
    ) AS payload)
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'historical receivable records principal without repeating a cash deduction');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.transactions
    WHERE user_id = (SELECT owner_id FROM receivable_opening_ids)), 1,
    'historical opening creates no additional cash transaction');

RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.debts
    WHERE user_id = (SELECT owner_id FROM receivable_opening_ids)), 2,
    'valid new and historical obligations remain after rejected commands');
SELECT is((SELECT count(*)::integer FROM public.debt_events
    WHERE user_id = (SELECT owner_id FROM receivable_opening_ids)), 2,
    'each valid opening has one opening event');

SELECT * FROM finish();
ROLLBACK;
