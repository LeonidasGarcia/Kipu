BEGIN;
SELECT plan(7);

INSERT INTO auth.users (id,email) VALUES
    ('94000000-0000-0000-0000-000000000001','s3-legacy-payment@kipu.test')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.accounts (id,user_id,creation_operation_id,name,account_type,currency_code,initial_balance_minor_units,opened_at,is_archived)
VALUES ('94000000-0000-0000-0000-000000000101','94000000-0000-0000-0000-000000000001','94000000-0000-0000-0000-000000000111','Cuenta histórica','BANK','PEN',5000,now(),false);
INSERT INTO public.cards (id,user_id,creation_operation_id,account_id,network,alias,last4,is_credit,credit_limit_minor,closing_day,due_day,is_archived)
VALUES ('94000000-0000-0000-0000-000000000301','94000000-0000-0000-0000-000000000001','94000000-0000-0000-0000-000000000311',NULL,'VISA','Tarjeta histórica','9401',true,10000,20,10,false);

-- A later canonical expense has left only S/5.00 available today.
INSERT INTO public.transactions (
    id,user_id,account_id,transaction_type,operation_kind,amount_minor,currency_code,occurred_at,installment_count,interest_mode,status,revision
) VALUES (
    '94000000-0000-0000-0000-000000000401','94000000-0000-0000-0000-000000000001',
    '94000000-0000-0000-0000-000000000101','EXPENSE','STANDARD',4500,'PEN',now()-interval '1 day',1,'NONE','CONFIRMED',1
);
INSERT INTO internal.ledger_entries (transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at)
VALUES ('94000000-0000-0000-0000-000000000401','94000000-0000-0000-0000-000000000001','94000000-0000-0000-0000-000000000101',-4500,'PEN','PRIMARY',now()-interval '1 day');

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '94000000-0000-0000-0000-000000000001';
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','94000000-0000-0000-0000-000000000501','request_hash','purchase-hash',
        'transaction',jsonb_build_object('id','94000000-0000-0000-0000-000000000501','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',5000,'currency_code','PEN','card_id','94000000-0000-0000-0000-000000000301',
            'occurred_at','2026-08-01T12:00:00Z','installment_count',2,'interest_mode','NONE')
    ))->>'status','APPLIED','Canonical source debt is available for historical amortization');

RESET ROLE;
SELECT is(
    (SELECT (a.initial_balance_minor_units+COALESCE(sum(le.signed_amount_minor),0))::bigint
     FROM public.accounts a LEFT JOIN internal.ledger_entries le ON le.account_id=a.id AND le.user_id=a.user_id
     WHERE a.id='94000000-0000-0000-0000-000000000101' GROUP BY a.initial_balance_minor_units),
    500::bigint,
    'Current available funds are below the already accepted historical payment'
);

-- The legacy RPC accepted this posting earlier. The importer must preserve it even
-- though re-running today's funds check would reject S/20.00 against S/5.00.
INSERT INTO public.financial_movements (id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,account_id,effective_at,status,created_at)
VALUES ('94000000-0000-0000-0000-000000000601','94000000-0000-0000-0000-000000000611',0,'94000000-0000-0000-0000-000000000001','CARD_PAYMENT_CASH',-2000,'PEN','94000000-0000-0000-0000-000000000101','2026-08-15T12:00:00Z','POSTED','2026-08-15T12:00:01Z');
INSERT INTO public.financial_movements (id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,card_id,effective_at,status,created_at)
VALUES ('94000000-0000-0000-0000-000000000602','94000000-0000-0000-0000-000000000611',1,'94000000-0000-0000-0000-000000000001','CARD_PAYMENT_LIABILITY',-2000,'PEN','94000000-0000-0000-0000-000000000301','2026-08-15T12:00:00Z','POSTED','2026-08-15T12:00:01Z');

SELECT is(
    private.import_legacy_credit_payment_v1(
        '94000000-0000-0000-0000-000000000601','94000000-0000-0000-0000-000000000001',
        '94000000-0000-0000-0000-000000000101','94000000-0000-0000-0000-000000000301',
        2000,'PEN','2026-08-15T12:00:00Z','2026-08-15T12:00:01Z'
    )->>'status',
    'APPLIED',
    'Accepted legacy payment imports without rechecking the current account balance'
);
SELECT is(
    (SELECT (a.initial_balance_minor_units+COALESCE(sum(le.signed_amount_minor),0))::bigint
     FROM public.accounts a LEFT JOIN internal.ledger_entries le ON le.account_id=a.id AND le.user_id=a.user_id
     WHERE a.id='94000000-0000-0000-0000-000000000101' GROUP BY a.initial_balance_minor_units),
    -1500::bigint,
    'Imported posting preserves the exact S/20.00 asset debit and resulting net balance'
);
SELECT is(
    (SELECT sum(ci.principal_minor-COALESCE(a.allocated_minor,0))::bigint
     FROM public.credit_installments ci
     LEFT JOIN LATERAL (SELECT sum(x.allocated_minor)::bigint AS allocated_minor FROM public.credit_payment_allocations x WHERE x.user_id=ci.user_id AND x.installment_id=ci.id) a ON true
     WHERE ci.transaction_id='94000000-0000-0000-0000-000000000501'),
    3000::bigint,
    'The matching credit liability is amortized by the same S/20.00'
);
SELECT is(
    (SELECT ci.installment_number::text||':'||sum(cpa.allocated_minor)::text
     FROM public.credit_payment_allocations cpa JOIN public.credit_installments ci ON ci.id=cpa.installment_id AND ci.user_id=cpa.user_id
     WHERE cpa.payment_transaction_id='94000000-0000-0000-0000-000000000601'
     GROUP BY ci.installment_number),
    '1:2000',
    'Historical payment retains canonical FIFO order across installment rows'
);
SELECT is(
    private.import_legacy_credit_payment_v1(
        '94000000-0000-0000-0000-000000000601','94000000-0000-0000-0000-000000000001',
        '94000000-0000-0000-0000-000000000101','94000000-0000-0000-0000-000000000301',
        2000,'PEN','2026-08-15T12:00:00Z','2026-08-15T12:00:01Z'
    )->>'status',
    'DUPLICATE',
    'Historical importer is idempotent for the stable legacy cash movement ID'
);

SELECT * FROM finish();
ROLLBACK;
