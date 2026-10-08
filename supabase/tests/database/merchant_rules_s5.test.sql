BEGIN;
SELECT plan(39);

SELECT has_table('public', 'merchant_alias_rules', 'owner-scoped alias rules have a dedicated table');
SELECT has_table('public', 'merchant_category_preferences', 'owner-scoped merchant preferences have a dedicated table');
SELECT has_column('public', 'financial_movements', 'merchant_raw_text', 'source merchant text is persisted separately');
SELECT ok((SELECT relrowsecurity FROM pg_class WHERE oid = 'public.merchant_alias_rules'::regclass), 'alias rules have RLS enabled');
SELECT ok((SELECT relrowsecurity FROM pg_class WHERE oid = 'public.merchant_category_preferences'::regclass), 'preferences have RLS enabled');
SELECT table_privs_are('public', 'merchant_alias_rules', 'authenticated', ARRAY['SELECT'], 'clients can only read alias rows');
SELECT table_privs_are('public', 'merchant_category_preferences', 'authenticated', ARRAY['SELECT'], 'clients can only read preference rows');
SELECT table_privs_are('public', 'merchant_services', 'authenticated', ARRAY['SELECT'], 'the shared merchant catalog remains read-only');

INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-1111-1111-111111111111', 'merchant-rules-a@kipu.test'),
    ('22222222-2222-2222-2222-222222222222', 'merchant-rules-b@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.merchant_services
    (id, user_id, name, normalized_name, is_system, revision, is_active, version, priority)
VALUES ('33333333-3333-3333-3333-333333333334', NULL, 'Inactive test merchant', 'inactive test merchant', true, 1, false, 1, 'B')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.categories (id, user_id, name, origin, is_active, category_type)
VALUES
    ('88888888-0000-4000-8000-000000000011', '22222222-2222-2222-2222-222222222222', 'Other owner category', 'CUSTOM', true, 'GENERAL'),
    ('88888888-0000-4000-8000-000000000012', '11111111-1111-1111-1111-111111111111', 'Inactive test category', 'CUSTOM', false, 'GENERAL')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.merchant_services
    (id, user_id, name, normalized_name, is_system, revision, is_active, version, priority)
VALUES
    ('33333333-3333-3333-3333-333333333333', NULL, 'Kipu Test Merchant', 'kipu test merchant', true, 1, true, 1, 'B')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.billing_products (id, store_product_id, name, plan_type, features, is_active)
VALUES ('merchant-rules-pro', 'merchant-rules-pro', 'Test Premium', 'PRO_ANNUAL', '{}', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.billing_purchases (
    id, user_id, purchase_token_hash, product_id, purchase_state, entitlement_state,
    starts_at, expires_at, verified_at, acknowledgement_state
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    repeat('a', 64), 'merchant-rules-pro', 'PURCHASED', 'ACTIVE',
    now(), now() + interval '30 days', now(), 'ACKNOWLEDGED'
)
ON CONFLICT DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES (
    '55555555-5555-5555-5555-555555555556',
    '11111111-1111-1111-1111-111111111111', 'Merchant rule test cash', 'CASH', 'PEN'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.financial_movements (
    id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
    account_id, effective_at, status
) VALUES (
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666', 0,
    '11111111-1111-1111-1111-111111111111', 'ADJUSTMENT', -100, 'PEN',
    '55555555-5555-5555-5555-555555555556', now(), 'POSTED'
) ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

SELECT is(
    public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '77777777-7777-7777-7777-777777777777',
        'payload_hash', 'client-hash-a',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'normalized_pattern', 'izipay*tambo',
        'merchant_id', '33333333-3333-3333-3333-333333333333'
    ))->>'status',
    'APPLIED',
    'a verified Premium owner can create an alias rule'
);
SELECT is(
    public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '77777777-7777-7777-7777-777777777777',
        'payload_hash', 'client-hash-a',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'normalized_pattern', 'izipay*tambo',
        'merchant_id', '33333333-3333-3333-3333-333333333333'
    ))->>'status',
    'DUPLICATE',
    'replaying the same operation returns its receipt without a second rule'
);
SELECT is((SELECT count(*)::integer FROM public.merchant_alias_rules WHERE user_id = auth.uid()), 1, 'the alias is owner-scoped and stored once');
SELECT throws_ok(
    $$ SELECT public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '71717171-7171-7171-7171-717171717171',
        'payload_hash', 'inactive-merchant',
        'rule_id', '81818181-8181-8181-8181-818181818181',
        'normalized_pattern', 'inactive merchant',
        'merchant_id', '33333333-3333-3333-3333-333333333334'
    )) $$,
    'P0001', 'MERCHANT_NOT_ACTIVE', 'inactive catalog merchants cannot be aliased'
);
SELECT is(
    public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '72727272-7272-7272-7272-727272727272',
        'payload_hash', 'alias-edit-a',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'normalized_pattern', 'izipay*tambo lima',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'expected_revision', 1
    ))->>'status', 'APPLIED', 'an owner can revise an existing alias without creating a second row'
);
SELECT is((SELECT revision::integer FROM public.merchant_alias_rules WHERE id = '88888888-8888-8888-8888-888888888888'), 2, 'alias edits advance the revision');
SELECT is(
    public.delete_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '73737373-7373-7373-7373-737373737373',
        'payload_hash', 'alias-delete-a',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'expected_revision', 2
    ))->>'status', 'APPLIED', 'alias removal is recorded as an owner-scoped tombstone'
);
SELECT is((SELECT revision::integer FROM public.merchant_alias_rules WHERE id = '88888888-8888-8888-8888-888888888888' AND deleted_at IS NOT NULL), 3, 'alias tombstones retain the row and advance revision');
SELECT is(
    public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '74747474-7474-7474-7474-747474747474',
        'payload_hash', 'stale-alias-edit',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'normalized_pattern', 'izipay*tambo',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'expected_revision', 2
    ))->>'status', 'CONFLICT', 'stale alias revisions do not overwrite newer state'
);
SELECT is(
    public.delete_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '76767676-7676-7676-7676-767676767676',
        'payload_hash', 'stale-alias-delete',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'expected_revision', 2
    ))->>'status', 'CONFLICT', 'a stale alias delete cannot overwrite a newer tombstone'
);
SELECT throws_ok(
    $$ INSERT INTO public.merchant_alias_rules (id, user_id, normalized_pattern, merchant_id)
       VALUES ('84848484-8484-8484-8484-848484848484', auth.uid(), 'direct write', '33333333-3333-3333-3333-333333333333') $$,
    '42501', 'permission denied for table merchant_alias_rules', 'clients cannot bypass alias command RPCs with direct DML'
);

SELECT is(
    public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '99999999-9999-9999-9999-999999999999',
        'payload_hash', 'client-hash-pref',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '00000000-0000-0000-0000-000000000001'
    ))->>'status',
    'APPLIED',
    'a preference does not require Premium'
);
SELECT is((SELECT count(*)::integer FROM public.merchant_category_preferences WHERE user_id = auth.uid()), 1, 'one preference is stored for this owner and merchant');
SELECT throws_ok(
    $$ SELECT public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '91919191-9191-9191-9191-919191919191',
        'payload_hash', 'foreign-category',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '88888888-0000-4000-8000-000000000011',
        'expected_revision', 1
    )) $$,
    'P0001', 'CATEGORY_NOT_ACTIVE_OR_OWNED', 'a preference cannot reference another owner category'
);
SELECT throws_ok(
    $$ SELECT public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '92929292-9292-9292-9292-929292929292',
        'payload_hash', 'inactive-category',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '88888888-0000-4000-8000-000000000012',
        'expected_revision', 1
    )) $$,
    'P0001', 'CATEGORY_NOT_ACTIVE_OR_OWNED', 'inactive categories cannot receive a merchant preference'
);
SELECT is(
    public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '93939393-9393-9393-9393-939393939393',
        'payload_hash', 'preference-edit-a',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '00000000-0000-0000-0000-000000000001',
        'expected_revision', 1
    ))->>'status', 'APPLIED', 'a preference update keeps one row for its owner and merchant'
);
SELECT is(
    public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '93939393-9393-9393-9393-939393939393',
        'payload_hash', 'preference-edit-a',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '00000000-0000-0000-0000-000000000001',
        'expected_revision', 1
    ))->>'status', 'DUPLICATE', 'replaying a preference update returns its original receipt'
);
SELECT is((SELECT revision::integer FROM public.merchant_category_preferences WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'), 2, 'preference revisions advance once for an idempotent update');
SELECT is((SELECT count(*)::integer FROM public.merchant_category_preferences WHERE user_id = auth.uid() AND merchant_id = '33333333-3333-3333-3333-333333333333'), 1, 'preference updates preserve the unique owner/merchant row');
SELECT is(
    public.delete_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '94949494-9494-9494-9494-949494949494',
        'payload_hash', 'preference-delete-a',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'expected_revision', 2
    ))->>'status', 'APPLIED', 'preference removal is recorded as a tombstone'
);
SELECT is((SELECT revision::integer FROM public.merchant_category_preferences WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' AND deleted_at IS NOT NULL), 3, 'preference tombstones retain history and advance revision');
SELECT is(
    public.upsert_merchant_category_preference_v1(jsonb_build_object(
        'operation_id', '95959595-9595-9595-9595-959595959595',
        'payload_hash', 'stale-preference-edit',
        'preference_id', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'merchant_id', '33333333-3333-3333-3333-333333333333',
        'category_id', '00000000-0000-0000-0000-000000000001',
        'expected_revision', 2
    ))->>'status', 'CONFLICT', 'stale preference revisions do not overwrite newer state'
);

SELECT is(
    public.preserve_merchant_source_text_v1(jsonb_build_object(
        'operation_id', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'payload_hash', 'client-hash-source',
        'movement_id', '55555555-5555-5555-5555-555555555555',
        'merchant_raw_text', U&'  IZIPAY*T\00C1MBO  '
    ))->>'status',
    'APPLIED',
    'the source merchant text is persisted verbatim'
);
SELECT is(
    public.preserve_merchant_source_text_v1(jsonb_build_object(
        'operation_id', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'payload_hash', 'client-hash-source',
        'movement_id', '55555555-5555-5555-5555-555555555555',
        'merchant_raw_text', U&'  IZIPAY*T\00C1MBO  '
    ))->>'status',
    'DUPLICATE',
    'replaying the source-text command returns its receipt'
);
SELECT is(
    (SELECT merchant_raw_text FROM public.financial_movements WHERE id = '55555555-5555-5555-5555-555555555555'),
    U&'  IZIPAY*T\00C1MBO  ',
    'normalization never replaces the original source text'
);
SELECT is(
    (SELECT pg_catalog.jsonb_build_object('amount', amount_minor_units, 'status', status, 'source', merchant_raw_text)::text
       FROM public.financial_movements WHERE id = '55555555-5555-5555-5555-555555555555'),
    pg_catalog.jsonb_build_object('amount', -100, 'status', 'POSTED', 'source', U&'  IZIPAY*T\00C1MBO  ')::text,
    'source text persistence leaves the confirmed financial amount and status unchanged'
);

SET LOCAL "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
SELECT is((SELECT count(*)::integer FROM public.merchant_alias_rules), 0, 'another owner cannot read alias rules');
SELECT is((SELECT count(*)::integer FROM public.merchant_category_preferences), 0, 'another owner cannot read merchant preferences');
SELECT throws_ok(
    $$ SELECT public.preserve_merchant_source_text_v1(jsonb_build_object(
        'operation_id', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        'payload_hash', 'client-hash-owner',
        'movement_id', '55555555-5555-5555-5555-555555555555',
        'merchant_raw_text', 'some other source'
    )) $$,
    'P0001',
    'MOVEMENT_NOT_FOUND',
    'a different owner cannot overwrite source text'
);
SELECT throws_ok(
    $$ SELECT public.upsert_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '75757575-7575-7575-7575-757575757575',
        'payload_hash', 'no-premium',
        'rule_id', '85858585-8585-8585-8585-858585858585',
        'normalized_pattern', 'new free alias',
        'merchant_id', '33333333-3333-3333-3333-333333333333'
    )) $$,
    'P0001', 'PREMIUM_REQUIRED', 'a Free owner cannot create a new alias rule'
);
SELECT is(
    public.delete_merchant_alias_rule_v1(jsonb_build_object(
        'operation_id', '77777777-7777-7777-7777-777777777777',
        'payload_hash', 'other-owner-delete',
        'rule_id', '88888888-8888-8888-8888-888888888888',
        'expected_revision', 3
    ))->>'status', 'CONFLICT', 'another owner cannot edit or tombstone a private alias'
);

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
