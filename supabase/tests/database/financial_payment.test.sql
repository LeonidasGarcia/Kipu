BEGIN;
SELECT plan(8);

-- Test 1: Function pay_credit_card_v1 exists
SELECT has_function('public', 'pay_credit_card_v1', ARRAY['jsonb'], 'pay_credit_card_v1 exists');

-- Setup test user, account, card, and initial purchase
INSERT INTO auth.users (id, email) VALUES 
    ('44444444-4444-4444-4444-444444444444', 'user4@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, creation_operation_id, alias, type, currency, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('acc-test-4444', '44444444-4444-4444-4444-444444444444', 'op-acc-4444', 'Ahorros BCP', 'SAVINGS', 'PEN', 100000, now(), now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO public.financial_movements (id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency, account_id, effective_at, status, created_at)
VALUES ('mov-open-4444', 'op-acc-4444', 0, '44444444-4444-4444-4444-444444444444', 'OPENING_BALANCE', 100000, 'PEN', 'acc-test-4444', now(), 'POSTED', now())
ON CONFLICT DO NOTHING;

INSERT INTO public.cards (id, user_id, creation_operation_id, type, issuer, network, last_four_digits, currency, credit_limit_minor_units, billing_day, due_day, created_at, updated_at)
VALUES ('card-credit-4444', '44444444-4444-4444-4444-444444444444', 'op-card-4444', 'CREDIT', 'BCP', 'VISA', '4444', 'PEN', 500000, 15, 5, now(), now())
ON CONFLICT DO NOTHING;

-- Initial credit purchase of 30000 (S/ 300.00)
INSERT INTO public.financial_movements (id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency, card_id, effective_at, status, created_at)
VALUES ('mov-purch-4444', 'op-purch-4444', 0, '44444444-4444-4444-4444-444444444444', 'CREDIT_PURCHASE', 30000, 'PEN', 'card-credit-4444', now(), 'POSTED', now())
ON CONFLICT DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';

-- Test 2: Execute card payment of 10000 (S/ 100.00)
SELECT lives_ok(
    $$
    SELECT public.pay_credit_card_v1(
        jsonb_build_object(
            'operation_id', 'a4444444-4444-4444-4444-444444444441',
            'card_id', 'card-credit-4444',
            'source_account_id', 'acc-test-4444',
            'amount_minor_units', 10000,
            'currency', 'PEN',
            'payload_hash', 'hash-pay-1'
        )
    );
    $$,
    'Execute credit card payment succeeds'
);

-- Test 3: Source account balance is reduced by 10000 (100000 - 10000 = 90000)
SELECT is(
    (SELECT SUM(amount_minor_units)::bigint FROM public.financial_movements WHERE account_id = 'acc-test-4444' AND status = 'POSTED'),
    90000::bigint,
    'Source account balance reduced from 100000 to 90000'
);

-- Test 4: Card debt is reduced by 10000 (30000 - 10000 = 20000)
SELECT is(
    (SELECT SUM(amount_minor_units)::bigint FROM public.financial_movements WHERE card_id = 'card-credit-4444' AND status = 'POSTED'),
    20000::bigint,
    'Credit card debt reduced from 30000 to 20000'
);

-- Test 5: Symmetric movements created with correct non-expense kinds
SELECT is(
    (SELECT COUNT(*)::integer FROM public.financial_movements 
     WHERE operation_id = 'a4444444-4444-4444-4444-444444444441' 
       AND kind IN ('CARD_PAYMENT_CASH', 'CARD_PAYMENT_LIABILITY')),
    2,
    'Two symmetric non-expense movements created'
);

-- Test 6: Idempotent re-execution
SELECT lives_ok(
    $$
    SELECT public.pay_credit_card_v1(
        jsonb_build_object(
            'operation_id', 'a4444444-4444-4444-4444-444444444441',
            'card_id', 'card-credit-4444',
            'source_account_id', 'acc-test-4444',
            'amount_minor_units', 10000,
            'currency', 'PEN',
            'payload_hash', 'hash-pay-1'
        )
    );
    $$,
    'Idempotent payment re-execution succeeds'
);

-- Test 7: Overpayment rejection (attempting to pay 25000 when debt is 20000)
SELECT throws_ok(
    $$
    SELECT public.pay_credit_card_v1(
        jsonb_build_object(
            'operation_id', 'a4444444-4444-4444-4444-444444444442',
            'card_id', 'card-credit-4444',
            'source_account_id', 'acc-test-4444',
            'amount_minor_units', 25000,
            'currency', 'PEN',
            'payload_hash', 'hash-pay-over'
        )
    );
    $$,
    '22000',
    NULL,
    'Overpayment exceeding debt is rejected'
);

-- Test 8: Insufficient funds rejection
-- Create an empty account
INSERT INTO public.accounts (id, user_id, creation_operation_id, alias, type, currency, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('acc-empty-4444', '44444444-4444-4444-4444-444444444444', 'op-acc-empty', 'Vacia', 'SAVINGS', 'PEN', 0, now(), now(), now())
ON CONFLICT DO NOTHING;

SELECT throws_ok(
    $$
    SELECT public.pay_credit_card_v1(
        jsonb_build_object(
            'operation_id', 'a4444444-4444-4444-4444-444444444443',
            'card_id', 'card-credit-4444',
            'source_account_id', 'acc-empty-4444',
            'amount_minor_units', 5000,
            'currency', 'PEN',
            'payload_hash', 'hash-pay-insufficient'
        )
    );
    $$,
    '22000',
    NULL,
    'Payment with insufficient funds is rejected'
);

SELECT * FROM finish();
ROLLBACK;
