BEGIN;
SELECT plan(10);

-- Setup test user
INSERT INTO auth.users (id, email)
VALUES ('33333333-3333-3333-3333-333333333333', 'quota5plus5@kipu.app')
ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';

-- 1. Create 5 EXPENSE roots (allowed)
SELECT lives_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'e0000000-0000-0000-0000-00000000000' || i::text,
        'category_type', 'EXPENSE',
        'name', 'Gasto ' || i::text,
        'icon', 'shopping_cart',
        'color', '#112233'
    )) FROM generate_series(1, 5) AS i;
    $$,
    'Creating 5 custom EXPENSE root categories succeeds'
);

-- 2. 6th EXPENSE is blocked even though 0 INCOME roots exist
SELECT throws_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'e0000000-0000-0000-0000-000000000006',
        'category_type', 'EXPENSE',
        'name', 'Gasto 6',
        'icon', 'shopping_cart',
        'color', '#112233'
    ));
    $$,
    'P0001',
    'Free plan limit reached: maximum 5 active custom root categories per type',
    '6th EXPENSE custom root is blocked under Free plan'
);

-- 3. Subcategories under EXPENSE do not consume quota (allowed)
SELECT lives_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'e0000000-0000-0000-0000-000000000011',
        'parent_id', 'e0000000-0000-0000-0000-000000000001',
        'name', 'Subgasto 1',
        'icon', 'star',
        'color', '#112233'
    ));
    $$,
    'Creating subcategories does not consume root quota'
);

-- 4. Create 5 INCOME roots (allowed alongside 5 EXPENSE roots)
SELECT lives_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'i0000000-0000-0000-0000-00000000000' || i::text,
        'category_type', 'INCOME',
        'name', 'Ingreso ' || i::text,
        'icon', 'work',
        'color', '#223344'
    )) FROM generate_series(1, 5) AS i;
    $$,
    'Creating 5 custom INCOME roots is allowed alongside 5 EXPENSE roots (5+5 model)'
);

-- 5. 6th INCOME is blocked
SELECT throws_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'i0000000-0000-0000-0000-000000000006',
        'category_type', 'INCOME',
        'name', 'Ingreso 6',
        'icon', 'work',
        'color', '#223344'
    ));
    $$,
    'P0001',
    'Free plan limit reached: maximum 5 active custom root categories per type',
    '6th INCOME custom root is blocked under Free plan'
);

-- 6. GENERAL category consumes both: creating GENERAL when expense or income is full is blocked
SELECT throws_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'g0000000-0000-0000-0000-000000000001',
        'category_type', 'GENERAL',
        'name', 'General 1',
        'icon', 'category',
        'color', '#334455'
    ));
    $$,
    'P0001',
    'Free plan limit reached: maximum 5 active custom root categories per type',
    'GENERAL custom root is blocked when EXPENSE is at limit'
);

-- 7. Deactivate 1 EXPENSE root (revision 1 -> 2)
SELECT is(
    (public.set_category_active_v1(jsonb_build_object(
        'category_id', 'e0000000-0000-0000-0000-000000000005',
        'is_active', false,
        'expected_revision', 1
    ))->>'status'),
    'UPDATED',
    'Deactivating an EXPENSE root succeeds'
);

-- 8. Still blocked for GENERAL because INCOME is still at 5
SELECT throws_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'g0000000-0000-0000-0000-000000000001',
        'category_type', 'GENERAL',
        'name', 'General 1',
        'icon', 'category',
        'color', '#334455'
    ));
    $$,
    'P0001',
    'Free plan limit reached: maximum 5 active custom root categories per type',
    'GENERAL root remains blocked while INCOME is still at 5'
);

-- 9. Deactivate 1 INCOME root (revision 1 -> 2)
SELECT is(
    (public.set_category_active_v1(jsonb_build_object(
        'category_id', 'i0000000-0000-0000-0000-000000000005',
        'is_active', false,
        'expected_revision', 1
    ))->>'status'),
    'UPDATED',
    'Deactivating an INCOME root succeeds'
);

-- 10. Now creating 1 GENERAL root succeeds (4 EXPENSE + 4 INCOME + 1 GENERAL = 5 in each quota)
SELECT lives_ok(
    $$
    PERFORM public.create_category_v1(jsonb_build_object(
        'category_id', 'g0000000-0000-0000-0000-000000000001',
        'category_type', 'GENERAL',
        'name', 'General 1',
        'icon', 'category',
        'color', '#334455'
    ));
    $$,
    '1 GENERAL + 4 EXPENSE + 4 INCOME succeeds (consuming 1 slot in both quotas)'
);

SELECT * FROM finish();
ROLLBACK;
