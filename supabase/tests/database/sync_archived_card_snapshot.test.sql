BEGIN;
SELECT plan(2);

INSERT INTO auth.users (id, email) VALUES
    ('95000000-0000-0000-0000-000000000001', 'sync-archived-card@kipu.test')
ON CONFLICT (id) DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '95000000-0000-0000-0000-000000000001';

SELECT public.register_card_v1(jsonb_build_object(
    'operation_id', '95000000-0000-0000-0000-000000000101',
    'card_id', '95000000-0000-0000-0000-000000000201',
    'type', 'CREDIT',
    'issuer', 'BCP',
    'network', 'VISA',
    'last_four_digits', '9501',
    'currency', 'PEN',
    'credit_limit_minor_units', 500000,
    'billing_day', 15,
    'due_day', 5,
    'payload_hash', 'client-hash-is-not-authoritative'
));

SELECT public.set_instrument_archived_v1(jsonb_build_object(
    'operation_id', '95000000-0000-0000-0000-000000000102',
    'instrument_id', '95000000-0000-0000-0000-000000000201',
    'instrument_type', 'CARD',
    'is_archived', true
));

SELECT is(
    (
        SELECT string_agg(change->>'operation', ',' ORDER BY (change->>'sequence')::bigint)
        FROM jsonb_array_elements(public.pull_financial_changes_v1(1, 0, 100)->'changes') AS change
        WHERE change->>'entity_type' = 'CARD'
          AND change->>'entity_id' = '95000000-0000-0000-0000-000000000201'
    ),
    'UPSERT,ARCHIVE',
    'A fresh-device pull preserves card creation before the archive operation'
);

SELECT ok(
    (
        SELECT (change->'payload'->>'is_archived')::boolean
        FROM jsonb_array_elements(public.pull_financial_changes_v1(1, 0, 100)->'changes') AS change
        WHERE change->>'entity_type' = 'CARD'
          AND change->>'entity_id' = '95000000-0000-0000-0000-000000000201'
        ORDER BY (change->>'sequence')::bigint
        LIMIT 1
    ),
    'The original UPSERT contains the current archived snapshot'
);

SELECT * FROM finish();
ROLLBACK;
