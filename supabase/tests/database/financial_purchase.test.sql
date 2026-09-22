BEGIN;
SELECT plan(7);

-- Test 1: Function confirm_credit_purchase_v1 exists
SELECT has_function('public', 'confirm_credit_purchase_v1', ARRAY['jsonb'], 'confirm_credit_purchase_v1 exists');

-- Setup test user and credit card
INSERT INTO auth.users (id, email) VALUES 
    ('55555555-5555-5555-5555-555555555555', 'user5@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cards (id, user_id, creation_operation_id, type, issuer, network, last_four_digits, currency, credit_limit_minor_units, billing_day, due_day, created_at, updated_at)
VALUES ('card-credit-5555', '55555555-5555-5555-5555-555555555555', 'op-card-5555', 'CREDIT', 'Interbank', 'VISA', '5555', 'PEN', 300000, 20, 10, now(), now())
ON CONFLICT DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '55555555-5555-5555-5555-555555555555';

-- Test 2: Confirm credit purchase of 12000 (S/ 120.00) in 3 installments
SELECT lives_ok(
    $$
    SELECT public.confirm_credit_purchase_v1(
        jsonb_build_object(
            'operation_id', 'a5555555-5555-5555-5555-555555555551',
            'card_id', 'card-credit-5555',
            'amount_minor_units', 12000,
            'currency', 'PEN',
            'merchant', 'Ripley',
            'installments', 3,
            'payload_hash', 'hash-purch-1'
        )
    );
    $$,
    'Confirm credit purchase succeeds'
);

-- Test 3: Verify single movement created
SELECT is(
    (SELECT COUNT(*)::integer FROM public.financial_movements 
     WHERE operation_id = 'a5555555-5555-5555-5555-555555555551' AND kind = 'CREDIT_PURCHASE'),
    1,
    'Exactly one CREDIT_PURCHASE movement created'
);

-- Test 4: Verify card debt increased by exactly 12000
SELECT is(
    (SELECT SUM(amount_minor_units)::bigint FROM public.financial_movements 
     WHERE card_id = 'card-credit-5555' AND status = 'POSTED'),
    12000::bigint,
    'Card debt increased to 12000'
);

-- Test 5: Idempotency check
SELECT lives_ok(
    $$
    SELECT public.confirm_credit_purchase_v1(
        jsonb_build_object(
            'operation_id', 'a5555555-5555-5555-5555-555555555551',
            'card_id', 'card-credit-5555',
            'amount_minor_units', 12000,
            'currency', 'PEN',
            'merchant', 'Ripley',
            'installments', 3,
            'payload_hash', 'hash-purch-1'
        )
    );
    $$,
    'Idempotent purchase confirmation succeeds'
);

-- Test 6: Verify debt remains 12000 after re-execution
SELECT is(
    (SELECT SUM(amount_minor_units)::bigint FROM public.financial_movements 
     WHERE card_id = 'card-credit-5555' AND status = 'POSTED'),
    12000::bigint,
    'Card debt unchanged after idempotent re-execution'
);

-- Test 7: Reject invalid installment count (> 36)
SELECT throws_ok(
    $$
    SELECT public.confirm_credit_purchase_v1(
        jsonb_build_object(
            'operation_id', 'a5555555-5555-5555-5555-555555555552',
            'card_id', 'card-credit-5555',
            'amount_minor_units', 5000,
            'currency', 'PEN',
            'merchant', 'Saga',
            'installments', 48,
            'payload_hash', 'hash-purch-invalid'
        )
    );
    $$,
    '22023',
    NULL,
    'Installments > 36 is rejected'
);

SELECT * FROM finish();
ROLLBACK;
