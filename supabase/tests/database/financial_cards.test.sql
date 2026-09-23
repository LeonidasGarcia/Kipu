BEGIN;
SELECT plan(6);

-- Test 1: Function register_card_v1 exists
SELECT has_function('public', 'register_card_v1', ARRAY['jsonb'], 'register_card_v1 exists');

-- Test 2: Function delete_unused_card_v1 exists
SELECT has_function('public', 'delete_unused_card_v1', ARRAY['jsonb'], 'delete_unused_card_v1 exists');

-- Setup test user and parent account
INSERT INTO auth.users (id, email) VALUES 
    ('33333333-3333-3333-3333-333333333333', 'user3@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, creation_operation_id, name, account_type, currency_code, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('a3333333-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'b3333333-0000-0000-0000-000000000001', 'Cuenta BCP', 'SAVINGS', 'PEN', 10000, now(), now(), now())
ON CONFLICT DO NOTHING;

-- Test 3: Register debit card linked to account
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';

SELECT lives_ok(
    $$
    SELECT public.register_card_v1(
        jsonb_build_object(
            'operation_id', 'c3333333-0000-0000-0000-000000000001',
            'card_id', 'd3333333-0000-0000-0000-000000000001',
            'type', 'DEBIT',
            'issuer', 'BCP',
            'network', 'VISA',
            'last_four_digits', '5555',
            'currency', 'PEN',
            'account_id', 'a3333333-0000-0000-0000-000000000001',
            'payload_hash', 'hash-debit-3333'
        )
    );
    $$,
    'Register debit card linked to savings account succeeds'
);

-- Test 4: Debit card does NOT create financial movements
SELECT is(
    (SELECT COUNT(*)::integer FROM public.financial_movements WHERE card_id = 'd3333333-0000-0000-0000-000000000001'),
    0,
    'Debit card registration creates zero movements'
);

-- Test 5: Register credit card
SELECT lives_ok(
    $$
    SELECT public.register_card_v1(
        jsonb_build_object(
            'operation_id', 'c3333333-0000-0000-0000-000000000002',
            'card_id', 'd3333333-0000-0000-0000-000000000002',
            'type', 'CREDIT',
            'issuer', 'BBVA',
            'network', 'VISA',
            'last_four_digits', '7777',
            'currency', 'PEN',
            'credit_limit_minor_units', 400000,
            'billing_day', 15,
            'due_day', 5,
            'payload_hash', 'hash-credit-3333'
        )
    );
    $$,
    'Register credit card succeeds'
);

-- Test 6: Attempting to provide >4 digits for last_four_digits throws exception
SELECT throws_ok(
    $$
    SELECT public.register_card_v1(
        jsonb_build_object(
            'operation_id', 'c3333333-0000-0000-0000-000000000003',
            'card_id', 'd3333333-0000-0000-0000-000000000003',
            'type', 'CREDIT',
            'issuer', 'BCP',
            'network', 'VISA',
            'last_four_digits', '1234567812345678',
            'currency', 'PEN',
            'credit_limit_minor_units', 100000,
            'billing_day', 15,
            'due_day', 5,
            'payload_hash', 'hash-invalid'
        )
    );
    $$,
    'P0001',
    NULL,
    'Full PAN is rejected by check constraint or validation'
);

SELECT * FROM finish();
ROLLBACK;
