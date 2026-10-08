BEGIN;
SELECT plan(16);

CREATE TEMP TABLE debt_security_ids (
    owner_id uuid,
    other_id uuid,
    owner_account uuid,
    other_account uuid,
    debt_id uuid,
    operation_id uuid,
    operation_payload jsonb,
    request_hash text
);
INSERT INTO debt_security_ids VALUES (
    '66000000-0000-4000-8000-000000000001',
    '66000000-0000-4000-8000-000000000002',
    '66000000-0000-4000-8000-000000000003',
    '66000000-0000-4000-8000-000000000004',
    '66000000-0000-4000-8000-000000000005',
    '66000000-0000-4000-8000-000000000006',
    NULL,
    NULL
);
UPDATE debt_security_ids
SET operation_payload = body.payload,
    request_hash = encode(extensions.digest(body.payload::text, 'sha256'), 'hex')
FROM (
    SELECT jsonb_build_object(
    'contract_version', 1,
    'operation_id', operation_id,
    'debt_id', debt_id,
    'obligation_type', 'PAYABLE',
    'counterparty_name', 'Proveedor',
    'total_minor', 5000,
    'currency_code', 'PEN',
    'opened_on', '2026-10-08',
    'opening_mode', 'HISTORICAL'
    ) AS payload
    FROM debt_security_ids
) body;
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@debt-security.kipu.test' FROM debt_security_ids
UNION ALL
SELECT other_id, other_id::text || '@debt-security.kipu.test' FROM debt_security_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT owner_account, owner_id, 'Owner cash', 'SAVINGS'::public.account_type, 'PEN' FROM debt_security_ids
UNION ALL
SELECT other_account, other_id, 'Other cash', 'SAVINGS'::public.account_type, 'PEN' FROM debt_security_ids;
INSERT INTO public.debts (id, user_id, obligation_type, counterparty_name, total_minor, currency_code)
SELECT '66000000-0000-4000-8000-000000000007'::uuid, owner_id, 'PAYABLE'::public.obligation_type, 'Existing obligation', 10000, 'PEN' FROM debt_security_ids
UNION ALL
SELECT '66000000-0000-4000-8000-000000000008'::uuid, other_id, 'PAYABLE'::public.obligation_type, 'Other obligation', 10000, 'PEN' FROM debt_security_ids;
INSERT INTO public.debt_installments (id, user_id, debt_id, installment_number, due_date, amount_minor)
VALUES ('66000000-0000-4000-8000-000000000009', '66000000-0000-4000-8000-000000000002',
    '66000000-0000-4000-8000-000000000008', 1, DATE '2026-11-01', 1000);
INSERT INTO public.transactions (id, user_id, account_id, transaction_type, amount_minor, currency_code)
VALUES ('66000000-0000-4000-8000-000000000010', '66000000-0000-4000-8000-000000000002',
    '66000000-0000-4000-8000-000000000004', 'INCOME', 1000, 'PEN');
GRANT SELECT ON debt_security_ids TO authenticated;

SELECT has_function('public', 'open_debt_v1', ARRAY['jsonb'], 'opening RPC is present');
SELECT ok((SELECT prosecdef FROM pg_proc WHERE oid = 'public.open_debt_v1(jsonb)'::regprocedure),
    'opening RPC is SECURITY DEFINER and must repeat explicit owner checks');
SELECT has_table('internal', 'command_receipts', 'debt idempotency uses the shared command receipt ledger');
SELECT is((
    SELECT count(*)::integer FROM pg_constraint
    WHERE conrelid = 'public.debt_events'::regclass
      AND conname IN ('debt_events_transaction_owner_fkey', 'debt_events_installment_owner_fkey')
), 2, 'event movement and installment references are constrained to the same owner');
SELECT ok(
    EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='debts' AND policyname='debts_own'),
    'debt table retains owner RLS'
);

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM debt_security_ids), true);

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'RECEIVABLE',
            'counterparty_name', 'Otra persona',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'NEW_CASH_FLOW',
            'account_id', (SELECT other_account FROM debt_security_ids)
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, '42501', NULL, 'foreign account reference is rejected');

SELECT lives_ok($$
    SELECT public.open_debt_v1(
        operation_payload || jsonb_build_object(
            'request_hash', request_hash,
            'user_id', other_id
        )
    ) FROM debt_security_ids;
$$, 'owner can open a historical debt without an account reference');

SELECT throws_ok($$
    INSERT INTO public.debt_events (user_id, debt_id, transaction_id, event_type, amount_minor, principal_delta_minor)
    VALUES ((SELECT owner_id FROM debt_security_ids), '66000000-0000-4000-8000-000000000007',
        '66000000-0000-4000-8000-000000000010', 'PAYMENT', 100, -100);
$$, '23503', NULL, 'a debt event cannot link a transaction owned by another user');

SELECT throws_ok($$
    INSERT INTO public.debt_events (user_id, debt_id, installment_id, event_type, amount_minor, principal_delta_minor)
    VALUES ((SELECT owner_id FROM debt_security_ids), '66000000-0000-4000-8000-000000000007',
        '66000000-0000-4000-8000-000000000009', 'PAYMENT', 100, -100);
$$, '23503', NULL, 'a debt event cannot link an installment from another debt or owner');

SELECT throws_ok($$
    INSERT INTO public.debts (id, user_id, obligation_type, counterparty_name, total_minor, currency_code)
    VALUES (extensions.gen_random_uuid(), (SELECT other_id FROM debt_security_ids), 'PAYABLE', 'Foreign owner', 100, 'PEN');
$$, '42501', NULL, 'authenticated owner cannot insert a debt for another user');

SELECT throws_ok($$
    WITH body AS (
        SELECT jsonb_build_object(
            'contract_version', 1,
            'operation_id', extensions.gen_random_uuid(),
            'debt_id', extensions.gen_random_uuid(),
            'obligation_type', 'RECEIVABLE',
            'counterparty_name', 'Tercera obligación',
            'total_minor', 1000,
            'currency_code', 'PEN',
            'opened_on', '2026-10-08',
            'opening_mode', 'HISTORICAL'
        ) AS payload
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex')
    )) FROM body;
$$, 'P0001', 'FREE_DEBT_QUOTA_EXCEEDED', 'combined Free quota rejects the third active debt');

SELECT is(
    (SELECT public.open_debt_v1(operation_payload || jsonb_build_object(
        'request_hash', request_hash,
        'user_id', other_id
    ))->>'status' FROM debt_security_ids),
    'DUPLICATE', 'same operation and hash is idempotent');

SELECT throws_ok($$
    WITH body AS (
        SELECT operation_payload || jsonb_build_object('counterparty_name', 'Nombre distinto') AS payload,
            operation_id, other_id
        FROM debt_security_ids
    )
    SELECT public.open_debt_v1(payload || jsonb_build_object(
        'request_hash', encode(extensions.digest(payload::text, 'sha256'), 'hex'),
        'user_id', other_id
    )) FROM body;
$$, 'P0001', 'IDEMPOTENCY_KEY_REUSED', 'same operation with different request hash is rejected');

SELECT is((SELECT count(*)::integer FROM public.debts WHERE user_id = (SELECT owner_id FROM debt_security_ids)), 2,
    'opening creates the second owner debt and the third is blocked by the combined Free quota');
SELECT is((SELECT count(*)::integer FROM public.debts WHERE id = (SELECT debt_id FROM debt_security_ids) AND user_id = (SELECT other_id FROM debt_security_ids)), 0,
    'client-supplied owner cannot transfer a debt to another user');

RESET ROLE;
SELECT is((SELECT count(*)::integer FROM internal.command_receipts
    WHERE user_id = (SELECT owner_id FROM debt_security_ids) AND command_type = 'OPEN_DEBT'), 1,
    'opening creates one owner-scoped command receipt');
SELECT * FROM finish();
ROLLBACK;
