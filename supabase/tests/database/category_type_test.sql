BEGIN;
SELECT plan(14);

SELECT has_column('public', 'categories', 'category_type', 'categories persist the expense/income/general type');

CREATE TEMP TABLE category_type_test_ids (
    user_id uuid,
    source_account_id uuid,
    destination_account_id uuid,
    expense_root_id uuid,
    income_root_id uuid,
    income_child_id uuid,
    legacy_root_id uuid,
    mismatched_child_id uuid
);
INSERT INTO category_type_test_ids VALUES (
    extensions.gen_random_uuid(), extensions.gen_random_uuid(), extensions.gen_random_uuid(),
    extensions.gen_random_uuid(), extensions.gen_random_uuid(), extensions.gen_random_uuid(),
    extensions.gen_random_uuid(), extensions.gen_random_uuid()
);
GRANT SELECT ON category_type_test_ids TO authenticated;

INSERT INTO auth.users (id, email)
SELECT user_id, user_id::text || '@category-type.kipu.app' FROM category_type_test_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT source_account_id, user_id, 'Cuenta origen', 'SAVINGS', 'PEN' FROM category_type_test_ids;
INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
SELECT destination_account_id, user_id, 'Cuenta destino', 'SAVINGS', 'PEN' FROM category_type_test_ids;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT user_id::text FROM category_type_test_ids), true);

SELECT lives_ok(
    $$
    SELECT public.create_category_v1(jsonb_build_object(
        'category_id', (SELECT expense_root_id FROM category_type_test_ids),
        'category_type', 'EXPENSE', 'name', 'Gastos prueba', 'icon', 'shopping_cart', 'color', '#112233'
    ));
    $$,
    'RPC creates a typed expense root'
);
SELECT is(
    (SELECT category_type FROM public.categories
     WHERE id = (SELECT expense_root_id FROM category_type_test_ids)),
    'EXPENSE',
    'Expense type is persisted'
);

SELECT lives_ok(
    $$
    SELECT public.create_category_v1(jsonb_build_object(
        'category_id', (SELECT income_root_id FROM category_type_test_ids),
        'category_type', 'INCOME', 'name', 'Ingresos prueba', 'icon', 'work', 'color', '#223344'
    ));
    $$,
    'RPC creates a typed income root'
);
SELECT is(
    (SELECT category_type FROM public.categories
     WHERE id = (SELECT income_root_id FROM category_type_test_ids)),
    'INCOME',
    'Income type is persisted'
);

SELECT lives_ok(
    $$
    SELECT public.create_category_v1(jsonb_build_object(
        'category_id', (SELECT income_child_id FROM category_type_test_ids),
        'parent_id', (SELECT income_root_id FROM category_type_test_ids),
        'name', 'Ingreso secundario', 'icon', 'star', 'color', '#334455'
    ));
    $$,
    'RPC creates a subcategory without needing a duplicate type field'
);
SELECT is(
    (SELECT category_type FROM public.categories
     WHERE id = (SELECT income_child_id FROM category_type_test_ids)),
    'INCOME',
    'Subcategory inherits its root type'
);

SELECT throws_ok(
    $$
    SELECT public.create_category_v1(jsonb_build_object(
        'category_id', (SELECT mismatched_child_id FROM category_type_test_ids),
        'parent_id', (SELECT expense_root_id FROM category_type_test_ids),
        'category_type', 'INCOME', 'name', 'Tipo incompatible', 'icon', 'category', 'color', '#445566'
    ));
    $$,
    '23514', NULL,
    'RPC rejects an explicitly mismatched subcategory type'
);

SELECT lives_ok(
    $$
    SELECT public.create_category_v1(jsonb_build_object(
        'category_id', (SELECT legacy_root_id FROM category_type_test_ids),
        'name', 'Categoría anterior', 'icon', 'category', 'color', '#556677'
    ));
    $$,
    'Existing callers can omit category_type'
);
SELECT is(
    (SELECT category_type FROM public.categories
     WHERE id = (SELECT legacy_root_id FROM category_type_test_ids)),
    'GENERAL',
    'An omitted category type remains GENERAL rather than being guessed'
);

SELECT is(
    (SELECT COUNT(*)::integer FROM public.categories
     WHERE user_id = (SELECT user_id FROM category_type_test_ids) AND parent_id IS NULL
       AND category_type IN ('EXPENSE', 'GENERAL')),
    2,
    'Expense tab query includes expense and GENERAL roots'
);
SELECT is(
    (SELECT COUNT(*)::integer FROM public.categories
     WHERE user_id = (SELECT user_id FROM category_type_test_ids) AND parent_id IS NULL
       AND category_type IN ('INCOME', 'GENERAL')),
    2,
    'Income tab query includes income and GENERAL roots'
);

SELECT throws_ok(
    $$
    SELECT public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'category-type-mismatch', 'request_hash', 'category-type-mismatch',
        'transaction', jsonb_build_object(
            'type', 'EXPENSE', 'amount_minor', 100, 'currency_code', 'PEN',
            'source_account_id', (SELECT source_account_id FROM category_type_test_ids),
            'category_id', (SELECT income_root_id FROM category_type_test_ids)
        )
    ));
    $$,
    '23514', NULL,
    'A typed income category cannot classify a new expense transaction'
);
SELECT throws_ok(
    $$
    SELECT public.register_transaction_v1(jsonb_build_object(
        'idempotency_key', 'category-type-transfer', 'request_hash', 'category-type-transfer',
        'transaction', jsonb_build_object(
            'type', 'TRANSFER', 'amount_minor', 100, 'currency_code', 'PEN',
            'source_account_id', (SELECT source_account_id FROM category_type_test_ids),
            'destination_account_id', (SELECT destination_account_id FROM category_type_test_ids),
            'category_id', (SELECT expense_root_id FROM category_type_test_ids)
        )
    ));
    $$,
    '23514', NULL,
    'A transfer cannot be registered with a category'
);

SELECT * FROM finish();
ROLLBACK;
