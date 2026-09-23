BEGIN;
SELECT plan(15);

-- Test 1: Function register_transaction_v1 exists
SELECT has_function('public', 'register_transaction_v1', ARRAY['jsonb'], 'register_transaction_v1 exists');
SELECT ok(NOT has_function_privilege('anon', 'public.register_transaction_v1(jsonb)', 'EXECUTE'), 'anon cannot execute financial RPC');

-- Test 2: Tables exist
SELECT has_table('public', 'transactions', 'transactions table exists');
SELECT has_table('internal', 'ledger_entries', 'ledger_entries table exists');
SELECT has_table('internal', 'command_receipts', 'command_receipts table exists');

-- Setup test users & accounts
INSERT INTO auth.users (id, email) VALUES 
    ('33333333-3333-3333-3333-333333333333', 'user3@kipu.app'),
    ('44444444-4444-4444-4444-444444444444', 'user4@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code) VALUES
    ('a3333333-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'BCP Ahorros', 'SAVINGS', 'PEN'),
    ('a3333333-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'Efectivo', 'CASH', 'PEN')
ON CONFLICT (id) DO NOTHING;

-- Test 5: Register Expense as user 3
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';

SELECT is(
    (public.register_transaction_v1(
        jsonb_build_object(
            'contract_version', 1,
            'idempotency_key', 'idemp-tx-1',
            'request_hash', 'hash-tx-1',
            'transaction', jsonb_build_object(
                'id', 'b3333333-0000-0000-0000-000000000001',
                'type', 'EXPENSE',
                'amount_minor', 2500,
                'currency_code', 'PEN',
                'source_account_id', 'a3333333-0000-0000-0000-000000000001',
                'category_id', '00000000-0000-0000-0000-000000000001',
                'occurred_at', now()::text,
                'note', 'Almuerzo'
            )
        )
    )->>'status'),
    'APPLIED',
    'Expense registered successfully with APPLIED status'
);

-- Test 6: Verify duplicate returns original response without creating duplicate transaction
SELECT is(
    (public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'idemp-provisional-1', 'request_hash', 'hash-provisional-1',
        'transaction', jsonb_build_object(
            'id', 'b3333333-0000-0000-0000-000000000003',
            'type', 'EXPENSE', 'amount_minor', 800, 'currency_code', 'PEN',
            'source_account_id', 'a3333333-0000-0000-0000-000000000001',
            'category_id', '00000000-0000-0000-0000-000000000001',
            'merchant_provisional_text', 'Bodega del barrio'
        )))->>'status'), 'APPLIED', 'Provisional merchant is accepted');
SELECT is(
    (SELECT merchant_provisional_text FROM public.transactions
     WHERE id = 'b3333333-0000-0000-0000-000000000003'),
    'Bodega del barrio', 'Provisional merchant text is persisted');
SELECT is(
    (public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'idemp-provisional-conflict', 'request_hash', 'hash-provisional-conflict',
        'transaction', jsonb_build_object(
            'id', 'b3333333-0000-0000-0000-000000000004',
            'type', 'EXPENSE', 'amount_minor', 800, 'currency_code', 'PEN',
            'source_account_id', 'a3333333-0000-0000-0000-000000000001',
            'category_id', '00000000-0000-0000-0000-000000000001',
            'merchant_id', '00000000-0000-0000-0000-000000000001',
            'merchant_provisional_text', 'Bodega del barrio'
        )))->'error'->>'code'), 'MERCHANT_CONFLICT', 'Merchant UUID and text are exclusive');

SELECT is(
    (public.register_transaction_v1(
        jsonb_build_object(
            'contract_version', 1,
            'idempotency_key', 'idemp-tx-1',
            'request_hash', 'hash-tx-1',
            'transaction', jsonb_build_object(
                'id', 'b3333333-0000-0000-0000-000000000001',
                'type', 'EXPENSE',
                'amount_minor', 2500,
                'currency_code', 'PEN',
                'source_account_id', 'a3333333-0000-0000-0000-000000000001',
                'category_id', '00000000-0000-0000-0000-000000000001',
                'occurred_at', now()::text,
                'note', 'Almuerzo'
            )
        )
    )->>'status'),
    'APPLIED',
    'Re-sending same idempotency key and hash returns original APPLIED result'
);

-- Test 7: Conflict when same key has different hash
SELECT is(
    (public.register_transaction_v1(
        jsonb_build_object(
            'contract_version', 1,
            'idempotency_key', 'idemp-tx-1',
            'request_hash', 'different-hash',
            'transaction', jsonb_build_object(
                'id', 'b3333333-0000-0000-0000-000000000002',
                'type', 'EXPENSE',
                'amount_minor', 5000,
                'currency_code', 'PEN',
                'source_account_id', 'a3333333-0000-0000-0000-000000000001',
                'category_id', '00000000-0000-0000-0000-000000000001',
                'occurred_at', now()::text
            )
        )
    )->>'status'),
    'CONFLICT',
    'Re-using idempotency key with different hash returns CONFLICT'
);

-- Test 8: RLS isolation - User 4 cannot see User 3 transactions
SELECT is(
    (public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'invalid-category', 'request_hash', 'invalid-category',
        'transaction', jsonb_build_object(
            'type', 'EXPENSE', 'amount_minor', 100, 'currency_code', 'PEN',
            'source_account_id', 'a3333333-0000-0000-0000-000000000001',
            'category_id', '00000000-0000-0000-0000-000000000099'
        )))->'error'->>'code'), 'CATEGORY_UNAVAILABLE', 'Unknown category is rejected');
SELECT is(
    (public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'invalid-merchant', 'request_hash', 'invalid-merchant',
        'transaction', jsonb_build_object(
            'type', 'EXPENSE', 'amount_minor', 100, 'currency_code', 'PEN',
            'source_account_id', 'a3333333-0000-0000-0000-000000000001',
            'category_id', '00000000-0000-0000-0000-000000000001',
            'merchant_id', '00000000-0000-0000-0000-000000000099'
        )))->'error'->>'code'), 'MERCHANT_UNAVAILABLE', 'Unknown merchant is rejected');
SELECT is(
    (public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'invalid-currency', 'request_hash', 'invalid-currency',
        'transaction', jsonb_build_object(
            'type', 'EXPENSE', 'amount_minor', 100, 'currency_code', 'USD',
            'source_account_id', 'a3333333-0000-0000-0000-000000000001',
            'category_id', '00000000-0000-0000-0000-000000000001'
        )))->'error'->>'code'), 'ACCOUNT_NOT_FOUND', 'Account currency mismatch is rejected');

SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';

SELECT is(
    (SELECT COUNT(*)::integer FROM public.transactions WHERE user_id = '33333333-3333-3333-3333-333333333333'),
    0,
    'RLS prevents user 4 from seeing user 3 transactions'
);

SELECT * FROM finish();
ROLLBACK;
