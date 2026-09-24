BEGIN;
SELECT plan(4);

INSERT INTO auth.users (id, email)
VALUES ('66666666-6666-4666-8666-666666666666', 'movement-idempotency-stress@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES (
    'a6666666-0000-4000-8000-000000000001',
    '66666666-6666-4666-8666-666666666666',
    'Stress-test account',
    'SAVINGS',
    'PEN'
)
ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '66666666-6666-4666-8666-666666666666';

SELECT ok(
    (
        SELECT count(*) = 100
            AND bool_and(response->>'status' = 'APPLIED')
            AND count(DISTINCT response->>'transaction_id') = 1
        FROM (
            SELECT public.register_transaction_v1(jsonb_build_object(
                'contract_version', 1,
                'idempotency_key', 'stress-retry-operation-66666666',
                'request_hash', 'stress-retry-payload-hash-v1',
                'transaction', jsonb_build_object(
                    'id', 'b6666666-0000-4000-8000-000000000001',
                    'type', 'EXPENSE',
                    'amount_minor', 12500,
                    'currency_code', 'PEN',
                    'source_account_id', 'a6666666-0000-4000-8000-000000000001',
                    'category_id', '00000000-0000-0000-0000-000000000001',
                    'occurred_at', '2026-09-24T12:00:00Z',
                    'note', 'Idempotency retry test'
                )
            )) AS response
            FROM generate_series(1, 100)
        ) retries
    ),
    '100 identical retries return the original result and transaction identity'
);

RESET ROLE;

SELECT is(
    (SELECT count(*)::integer FROM public.transactions
     WHERE id = 'b6666666-0000-4000-8000-000000000001'),
    1,
    '100 retries produce exactly one transaction'
);

SELECT is(
    (SELECT count(*)::integer FROM internal.command_receipts
     WHERE user_id = '66666666-6666-4666-8666-666666666666'
       AND idempotency_key = 'stress-retry-operation-66666666'),
    1,
    '100 retries produce exactly one command receipt'
);

SELECT is(
    (SELECT count(*)::integer FROM internal.ledger_entries
     WHERE user_id = '66666666-6666-4666-8666-666666666666'
       AND transaction_id = 'b6666666-0000-4000-8000-000000000001'),
    1,
    '100 retries produce exactly one ledger effect'
);

SELECT * FROM finish();
ROLLBACK;
