BEGIN;
SELECT plan(8);

-- Test 1: Function create_account_v1 exists
SELECT has_function('public', 'create_account_v1', ARRAY['jsonb'], 'create_account_v1 exists');

-- Test 2: Function record_opening_adjustment_v1 exists
SELECT has_function('public', 'record_opening_adjustment_v1', ARRAY['jsonb'], 'record_opening_adjustment_v1 exists');

-- Test 3: Tables exist
SELECT has_table('public', 'accounts', 'accounts table exists');
SELECT has_table('public', 'financial_movements', 'financial_movements table exists');

-- Setup test users
INSERT INTO auth.users (id, email) VALUES 
    ('11111111-1111-1111-1111-111111111111', 'user1@kipu.app'),
    ('22222222-2222-2222-2222-222222222222', 'user2@kipu.app')
ON CONFLICT (id) DO NOTHING;

-- Test 4: Create account as user 1
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

SELECT lives_ok(
    $$
    SELECT public.create_account_v1(
        jsonb_build_object(
            'operation_id', 'a1111111-0000-0000-0000-000000000001',
            'account_id', 'b1111111-0000-0000-0000-000000000001',
            'opening_movement_id', 'c1111111-0000-0000-0000-000000000001',
            'alias', 'Cuenta Ahorros BCP',
            'type', 'SAVINGS',
            'currency', 'PEN',
            'preset_id', 'BCP',
            'initial_balance_minor_units', 150000,
            'opened_at', now()::text,
            'payload_hash', 'test_hash_1'
        )
    );
    $$,
    'User 1 creates a liquid savings account successfully'
);

-- Test 5: Verify account and opening movement inserted
SELECT is(
    (SELECT COUNT(*)::integer FROM public.accounts WHERE id = 'b1111111-0000-0000-0000-000000000001' AND user_id = '11111111-1111-1111-1111-111111111111'),
    1,
    'Account record exists for user 1'
);

SELECT is(
    (SELECT COUNT(*)::integer FROM public.financial_movements WHERE account_id = 'b1111111-0000-0000-0000-000000000001' AND kind = 'OPENING'),
    1,
    'Opening movement exists with correct account id'
);

-- Test 6: RLS isolation - User 2 cannot see User 1 accounts
SET LOCAL "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';

SELECT is(
    (SELECT COUNT(*)::integer FROM public.accounts WHERE id = 'b1111111-0000-0000-0000-000000000001'),
    0,
    'RLS prevents user 2 from reading user 1 account'
);

SELECT * FROM finish();
ROLLBACK;
