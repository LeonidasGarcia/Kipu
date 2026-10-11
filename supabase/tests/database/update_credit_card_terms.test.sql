BEGIN;
SELECT plan(9);

SELECT has_function('public', 'update_credit_card_terms_v1', ARRAY['jsonb'], 'Credit-card terms update command exists');

INSERT INTO auth.users (id, email) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'card-terms-owner@kipu.test'),
    ('a1000000-0000-0000-0000-000000000002', 'card-terms-other@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cards (
    id, user_id, creation_operation_id, account_id, network, alias, last4,
    is_credit, credit_limit_minor, closing_day, due_day, is_archived
) VALUES
    ('a1000000-0000-0000-0000-000000000101', 'a1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000111', NULL, 'VISA', 'Términos de prueba', '0101', true, 500000, 15, 5, false),
    ('a1000000-0000-0000-0000-000000000102', 'a1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000112', NULL, 'VISA', 'Tarjeta ajena', '0102', true, 200000, 20, 10, false)
ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = 'a1000000-0000-0000-0000-000000000001';

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000201',
        'request_hash', 'client-hash',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000101',
            'credit_limit_minor_units', 750000,
            'billing_day', 18,
            'due_day', 8,
            'expected_revision', 1
        )
    ))->>'status',
    'APPLIED',
    'Owner can update line and cycle dates'
);

SELECT is(
    (SELECT credit_limit_minor::text || '/' || closing_day::text || '/' || due_day::text
       FROM public.cards WHERE id = 'a1000000-0000-0000-0000-000000000101'),
    '750000/18/8',
    'The requested terms are stored on the owned card'
);
SELECT is((SELECT revision FROM public.cards WHERE id = 'a1000000-0000-0000-0000-000000000101'), 2::bigint, 'Accepted terms advance the remote revision');

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000201',
        'request_hash', 'another-client-hash',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000101',
            'credit_limit_minor_units', 750000,
            'billing_day', 18,
            'due_day', 8,
            'expected_revision', 1
        )
    ))->>'status',
    'DUPLICATE',
    'A retry with the same command payload is idempotent'
);

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000201',
        'request_hash', 'changed-command',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000101',
            'credit_limit_minor_units', 750000,
            'billing_day', 18,
            'due_day', 9,
            'expected_revision', 1
        )
    ))->>'status',
    'CONFLICT',
    'An idempotency key cannot be reused with different terms'
);

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000202',
        'request_hash', 'negative-line',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000101',
            'credit_limit_minor_units', -1,
            'billing_day', 18,
            'due_day', 8,
            'expected_revision', 2
        )
    ))->>'status',
    'REJECTED',
    'Negative credit lines are rejected'
);

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000203',
        'request_hash', 'stale-revision',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000101',
            'credit_limit_minor_units', 800000,
            'billing_day', 18,
            'due_day', 8,
            'expected_revision', 1
        )
    ))->>'status',
    'CONFLICT',
    'Stale card revisions are rejected'
);

SELECT is(
    public.update_credit_card_terms_v1(jsonb_build_object(
        'contract_version', 1,
        'idempotency_key', 'a1000000-0000-0000-0000-000000000204',
        'request_hash', 'foreign-card',
        'card', jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000102',
            'credit_limit_minor_units', 300000,
            'billing_day', 18,
            'due_day', 8,
            'expected_revision', 1
        )
    ))->>'status',
    'REJECTED',
    'A user cannot change another owner’s card'
);

SELECT * FROM finish();
ROLLBACK;
