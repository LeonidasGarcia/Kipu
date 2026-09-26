BEGIN;
SELECT plan(8);

SELECT has_function('public', 'pay_credit_card_v1', ARRAY['jsonb'], 'pay_credit_card_v1 compatibility RPC exists');

INSERT INTO auth.users (id, email) VALUES
    ('44444444-4444-4444-4444-444444444444', 'user4@kipu.app')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, creation_operation_id, name, account_type, currency_code, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-000000000001', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000011', 'Ahorros BCP', 'SAVINGS', 'PEN', 100000, now(), now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO public.cards (id, user_id, creation_operation_id, network, alias, last4, is_credit, credit_limit_minor, closing_day, due_day, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-000000000002', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000012', 'VISA', 'BCP', '4444', true, 500000, 15, 5, now(), now())
ON CONFLICT DO NOTHING;

-- Seed the already accepted S/300 debt through the canonical schedule model.
INSERT INTO public.transactions (
    id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
    occurred_at,installment_count,interest_mode,status,revision
) VALUES (
    '44444444-4444-4444-4444-000000000022','44444444-4444-4444-4444-444444444444',NULL,
    '44444444-4444-4444-4444-000000000002','EXPENSE','CARD_PURCHASE',30000,'PEN',
    now()-interval '20 days',1,'NONE','CONFIRMED',1
);
INSERT INTO public.credit_installments (user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status)
VALUES ('44444444-4444-4444-4444-444444444444','44444444-4444-4444-4444-000000000022',1,current_date,30000,0,'PENDING');

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';

SELECT is(
    public.pay_credit_card_v1(jsonb_build_object(
        'operation_id','a4444444-4444-4444-4444-444444444441',
        'card_id','44444444-4444-4444-4444-000000000002',
        'source_account_id','44444444-4444-4444-4444-000000000001',
        'amount_minor_units',10000,'currency','PEN','payload_hash','hash-pay-1'
    ))->>'status',
    'APPLIED',
    'Legacy payment command delegates to the canonical allocator'
);

RESET ROLE;
SELECT is(
    (SELECT (a.initial_balance_minor_units + COALESCE(sum(le.signed_amount_minor),0))::bigint
     FROM public.accounts a LEFT JOIN internal.ledger_entries le ON le.account_id=a.id AND le.user_id=a.user_id
     WHERE a.id='44444444-4444-4444-4444-000000000001' GROUP BY a.initial_balance_minor_units),
    90000::bigint,
    'Source asset decreases by S/100 exactly once'
);
SELECT is(
    (SELECT (ci.principal_minor-COALESCE(sum(cpa.allocated_minor),0))::bigint
     FROM public.credit_installments ci LEFT JOIN public.credit_payment_allocations cpa ON cpa.installment_id=ci.id AND cpa.user_id=ci.user_id
     WHERE ci.transaction_id='44444444-4444-4444-4444-000000000022'
     GROUP BY ci.principal_minor),
    20000::bigint,
    'Liability principal decreases by the same S/100'
);
SELECT is(
    (SELECT count(*)::integer FROM public.financial_movements
     WHERE operation_id='a4444444-4444-4444-4444-444444444441' AND kind IN ('CARD_PAYMENT_CASH','CARD_PAYMENT_LIABILITY')),
    0,
    'Compatibility RPC does not create legacy movement-only rows'
);

SET LOCAL ROLE authenticated;
SELECT is(
    public.pay_credit_card_v1(jsonb_build_object(
        'operation_id','a4444444-4444-4444-4444-444444444441',
        'card_id','44444444-4444-4444-4444-000000000002',
        'source_account_id','44444444-4444-4444-4444-000000000001',
        'amount_minor_units',10000,'currency','PEN','payload_hash','hash-pay-1'
    ))->>'status',
    'DUPLICATE',
    'Legacy retry remains idempotent through the canonical receipt'
);
SELECT is(
    public.pay_credit_card_v1(jsonb_build_object(
        'operation_id','a4444444-4444-4444-4444-444444444442',
        'card_id','44444444-4444-4444-4444-000000000002',
        'source_account_id','44444444-4444-4444-4444-000000000001',
        'amount_minor_units',25000,'currency','PEN','payload_hash','hash-pay-over'
    ))->'error'->>'code',
    'PAYMENT_EXCEEDS_OUTSTANDING_DEBT',
    'Overpayment is rejected with a stable canonical error'
);

INSERT INTO public.accounts (id, user_id, creation_operation_id, name, account_type, currency_code, initial_balance_minor_units, opened_at, created_at, updated_at)
VALUES ('44444444-4444-4444-4444-000000000003', '44444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-000000000014', 'Vacia', 'SAVINGS', 'PEN', 0, now(), now(), now());
SELECT is(
    public.pay_credit_card_v1(jsonb_build_object(
        'operation_id','a4444444-4444-4444-4444-444444444443',
        'card_id','44444444-4444-4444-4444-000000000002',
        'source_account_id','44444444-4444-4444-4444-000000000003',
        'amount_minor_units',5000,'currency','PEN','payload_hash','hash-pay-insufficient'
    ))->'error'->>'code',
    'INSUFFICIENT_FUNDS',
    'Payment rejects a source account without liquid funds'
);

SELECT * FROM finish();
ROLLBACK;
