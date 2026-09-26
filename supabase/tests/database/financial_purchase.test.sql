BEGIN;
SELECT plan(8);

SELECT has_function('public', 'confirm_credit_purchase_v1', ARRAY['jsonb'], 'confirm_credit_purchase_v1 compatibility RPC exists');

INSERT INTO auth.users (id, email) VALUES
    ('55555555-5555-5555-5555-555555555555', 'user5@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cards (id, user_id, creation_operation_id, network, alias, last4, is_credit, credit_limit_minor, closing_day, due_day, created_at, updated_at)
VALUES ('55555555-5555-5555-5555-000000000001', '55555555-5555-5555-5555-555555555555', '55555555-5555-5555-5555-000000000011', 'VISA', 'Interbank', '5555', true, 300000, 20, 10, now(), now())
ON CONFLICT DO NOTHING;

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '55555555-5555-5555-5555-555555555555';

SELECT is(
    public.confirm_credit_purchase_v1(jsonb_build_object(
        'operation_id','a5555555-5555-5555-5555-555555555551',
        'card_id','55555555-5555-5555-5555-000000000001',
        'amount_minor_units',12000,'currency','PEN','merchant','Ripley','installments',3,'payload_hash','hash-purch-1'
    ))->>'status',
    'APPLIED',
    'Legacy purchase command delegates to canonical credit purchase'
);

SELECT is(
    (SELECT transaction_type::text||'/'||operation_kind::text||'/'||amount_minor::text
     FROM public.transactions WHERE id='a5555555-5555-5555-5555-555555555551'),
    'EXPENSE/CARD_PURCHASE/12000',
    'Purchase is classified as one expense at the time it occurs'
);
SELECT is(
    (SELECT string_agg(principal_minor::text,',' ORDER BY installment_number)
     FROM public.credit_installments WHERE transaction_id='a5555555-5555-5555-5555-555555555551'),
    '4000,4000,4000',
    'Legacy purchase creates the requested canonical three-installment schedule'
);
SELECT is(
    (SELECT count(*)::integer FROM public.financial_movements
     WHERE operation_id='a5555555-5555-5555-5555-555555555551' AND kind='CREDIT_PURCHASE'),
    0,
    'Compatibility RPC no longer writes a movement-only purchase'
);

SELECT is(
    public.confirm_credit_purchase_v1(jsonb_build_object(
        'operation_id','a5555555-5555-5555-5555-555555555551',
        'card_id','55555555-5555-5555-5555-000000000001',
        'amount_minor_units',12000,'currency','PEN','merchant','Ripley','installments',3,'payload_hash','hash-purch-1'
    ))->>'status',
    'DUPLICATE',
    'Legacy retry stays idempotent through the canonical receipt'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_installments WHERE transaction_id='a5555555-5555-5555-5555-555555555551'),
    3,
    'Retry does not duplicate the installment schedule'
);
SELECT is(
    public.confirm_credit_purchase_v1(jsonb_build_object(
        'operation_id','a5555555-5555-5555-5555-555555555552',
        'card_id','55555555-5555-5555-5555-000000000001',
        'amount_minor_units',5000,'currency','PEN','merchant','Saga','installments',48,'payload_hash','hash-purch-invalid'
    ))->'error'->>'code',
    'INVALID_INSTALLMENT_COUNT',
    'Legacy adapter returns a stable rejection for more than 36 installments'
);

SELECT * FROM finish();
ROLLBACK;
