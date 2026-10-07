BEGIN;
SELECT plan(22);

-- Test 1-4: Verify tables exist
SELECT has_table('public', 'categories', 'categories table exists');
SELECT has_table('public', 'category_presentations', 'category_presentations table exists');
SELECT has_table('public', 'merchant_services', 'merchant_services table exists');
SELECT has_table('public', 'category_conflicts', 'category_conflicts table exists');

-- Test 5: Verify movement classification columns on financial_movements
SELECT has_column('public', 'financial_movements', 'category_id', 'financial_movements has category_id column');
SELECT has_column('public', 'financial_movements', 'merchant_id', 'financial_movements has merchant_id column');
SELECT has_column('public', 'financial_movements', 'merchant_provisional_text', 'financial_movements has merchant_provisional_text column');

-- Test 8: Verify RLS / grants on merchant_services
SELECT table_privs_are(
    'public', 'merchant_services', 'authenticated', ARRAY['SELECT'],
    'Authenticated users have only SELECT privilege on merchant_services'
);

-- Test 9: Initial category catalog seed exists
SELECT is(
    (SELECT COUNT(*)::integer FROM public.categories
        WHERE origin = 'SYSTEM' AND is_active = true
          AND id IN (
              '00000000-0000-0000-0000-000000000001',
              '00000000-0000-0000-0000-000000000002',
              '00000000-0000-0000-0000-000000000003'
          )),
    3,
    'The required predetermined categories Alimentación, Transporte, and Servicios are seeded'
);

-- Setup test users
INSERT INTO auth.users (id, email) VALUES 
    ('11111111-1111-1111-1111-111111111111', 'user1@kipu.app'),
    ('22222222-2222-2222-2222-222222222222', 'user2@kipu.app')
ON CONFLICT (id) DO NOTHING;

-- Test 10: Two-level hierarchy trigger rejection
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

SELECT lives_ok(
    $$
    INSERT INTO public.categories (id, user_id, parent_id, name, origin, is_active)
    VALUES ('aaaaaaaa-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', NULL, 'Raíz', 'CUSTOM', true);
    
    INSERT INTO public.categories (id, user_id, parent_id, name, origin, is_active)
    VALUES ('bbbbbbbb-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-1111-1111-1111-000000000001', 'Hija', 'CUSTOM', true);
    $$,
    'Creating root and subcategory succeeds'
);

-- Test 11: Third level hierarchy is rejected by trigger
SELECT throws_ok(
    $$
    INSERT INTO public.categories (id, user_id, parent_id, name, origin, is_active)
    VALUES ('cccccccc-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-1111-1111-1111-000000000001', 'Nieta', 'CUSTOM', true);
    $$,
    'P0001',
    NULL,
    'Attempting to create a third level category fails'
);

-- Test 12: Owner isolation - User 2 cannot see User 1 custom categories
SET LOCAL "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';

SELECT is(
    (SELECT COUNT(*)::integer FROM public.categories WHERE id = 'aaaaaaaa-1111-1111-1111-000000000001'),
    0,
    'User 2 cannot see User 1 private category'
);

-- Test 13: RPC create_category_v1 succeeds
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

SELECT is(
    (public.create_category_v1(jsonb_build_object(
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'name', 'Educación',
        'icon', 'school',
        'color', '#336699'
    ))->>'success')::boolean,
    true,
    'create_category_v1 RPC successfully creates category and presentation'
);

-- Test 14: RPC set_category_active_v1 updates active status
SELECT is(
    (public.set_category_active_v1(jsonb_build_object(
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'is_active', false,
        'expected_revision', 1
    ))->>'status'),
    'UPDATED',
    'set_category_active_v1 successfully deactivates category when revision matches'
);

-- Test 15: RPC set_category_active_v1 with revision mismatch creates a conflict
SELECT is(
    (public.set_category_active_v1(jsonb_build_object(
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'is_active', true,
        'expected_revision', 1 -- actual is 2 now
    ))->>'status'),
    'CONFLICT',
    'set_category_active_v1 produces CONFLICT status on revision mismatch'
);

-- Setup test movement for classification tests
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES ('99999999-9999-9999-9999-000000000002', '11111111-1111-1111-1111-111111111111', 'Cuenta de prueba', 'CASH', 'PEN');

INSERT INTO public.financial_movements (id, operation_id, user_id, kind, amount_minor_units, currency, account_id, status, effective_at, created_at)
VALUES (
    '99999999-9999-9999-9999-000000000001',
    '99999999-9999-9999-9999-000000000003',
    '11111111-1111-1111-1111-111111111111',
    'ADJUSTMENT',
    -5000,
    'PEN',
    '99999999-9999-9999-9999-000000000002',
    'POSTED',
    now(),
    now()
) ON CONFLICT (id) DO NOTHING;

-- Test 16: update_movement_classification_v1 succeeds with category and catalog merchant
SELECT is(
    (public.update_movement_classification_v1(jsonb_build_object(
        'movement_id', '99999999-9999-9999-9999-000000000001',
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'merchant_id', '00000000-0000-0000-0001-000000000001'
    ))->>'success')::boolean,
    true,
    'update_movement_classification_v1 assigns category and catalog merchant'
);

-- Test 17: update_movement_classification_v1 succeeds with category and provisional text
SELECT is(
    (public.update_movement_classification_v1(jsonb_build_object(
        'movement_id', '99999999-9999-9999-9999-000000000001',
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'merchant_provisional_text', 'Bodeguita Don Pepe'
    ))->>'success')::boolean,
    true,
    'update_movement_classification_v1 assigns provisional text when merchant is null'
);

-- Test 18: update_movement_classification_v1 fails when both merchant_id and provisional_text are provided
SELECT throws_ok(
    $$
    SELECT public.update_movement_classification_v1(jsonb_build_object(
        'movement_id', '99999999-9999-9999-9999-000000000001',
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'merchant_id', '00000000-0000-0000-0001-000000000001',
        'merchant_provisional_text', 'Conflicting Text'
    ));
    $$,
    'P0001',
    NULL,
    'update_movement_classification_v1 rejects coexisting merchant_id and provisional text'
);

-- Test 19: update_category_presentation_v1 updates presentation when revision matches
SELECT is(
    (public.update_category_presentation_v1(jsonb_build_object(
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'name', 'Educación y Cultura',
        'icon', 'school',
        'color', '#336699',
        'expected_revision', 1
    ))->>'status'),
    'UPDATED',
    'update_category_presentation_v1 updates presentation when expected revision matches'
);

-- Test 20: update_category_presentation_v1 with revision mismatch creates conflict
SELECT is(
    (public.update_category_presentation_v1(jsonb_build_object(
        'category_id', 'dddddddd-1111-1111-1111-000000000001',
        'name', 'Educación Superior',
        'icon', 'school',
        'color', '#336699',
        'expected_revision', 1 -- current is 2 now
    ))->>'status'),
    'CONFLICT',
    'update_category_presentation_v1 produces CONFLICT status on revision mismatch'
);

-- Setup conflict record for resolution test
INSERT INTO public.category_conflicts (id, category_id, user_id, conflict_type, local_version, remote_version, status)
VALUES (
    '88888888-8888-8888-8888-000000000001',
    'dddddddd-1111-1111-1111-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'PRESENTATION',
    '{"name":"Local"}',
    '{"name":"Remote"}',
    'OPEN'
) ON CONFLICT (id) DO NOTHING;

-- Test 21: resolve_category_conflict_v1 marks conflict as RESOLVED
SELECT is(
    (public.resolve_category_conflict_v1(jsonb_build_object(
        'conflict_id', '88888888-8888-8888-8888-000000000001',
        'chosen_version', 'LOCAL',
        'operation_id', '77777777-7777-7777-7777-000000000001'
    ))->>'status'),
    'RESOLVED',
    'resolve_category_conflict_v1 marks conflict as RESOLVED'
);

-- Test 22: Historical movement preserves category_id link after presentation edit
SELECT is(
    (SELECT category_id FROM public.financial_movements WHERE id = '99999999-9999-9999-9999-000000000001')::text,
    'dddddddd-1111-1111-1111-000000000001',
    'Movement retains its category_id link unchanged after presentation update'
);

SELECT * FROM finish();
ROLLBACK;
