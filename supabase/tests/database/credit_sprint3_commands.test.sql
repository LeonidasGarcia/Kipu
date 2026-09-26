BEGIN;
SELECT plan(46);

SELECT has_function('public','register_transaction_v1',ARRAY['jsonb'],'Canonical transaction RPC exists');
SELECT has_function('public','allocate_credit_payment_v1',ARRAY['jsonb'],'Canonical FIFO credit payment RPC exists');
SELECT has_function('public','update_card_personal_tea_v1',ARRAY['jsonb'],'Personal card TEA command exists');

INSERT INTO auth.users (id,email) VALUES
    ('93000000-0000-0000-0000-000000000001','s3-credit-owner@kipu.test'),
    ('93000000-0000-0000-0000-000000000002','s3-credit-other@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id,user_id,creation_operation_id,name,account_type,currency_code,initial_balance_minor_units,opened_at,is_archived)
VALUES
    ('93000000-0000-0000-0000-000000000101','93000000-0000-0000-0000-000000000001','93000000-0000-0000-0000-000000000111','Cuenta PEN','BANK','PEN',50000,now(),false),
    ('93000000-0000-0000-0000-000000000102','93000000-0000-0000-0000-000000000001','93000000-0000-0000-0000-000000000112','Cuenta PEN vacía','BANK','PEN',0,now(),false),
    ('93000000-0000-0000-0000-000000000103','93000000-0000-0000-0000-000000000001','93000000-0000-0000-0000-000000000113','Cuenta USD','BANK','USD',50000,now(),false),
    ('93000000-0000-0000-0000-000000000201','93000000-0000-0000-0000-000000000002','93000000-0000-0000-0000-000000000211','Cuenta externa','BANK','PEN',50000,now(),false);

INSERT INTO public.cards (id,user_id,creation_operation_id,account_id,network,alias,last4,is_credit,credit_limit_minor,closing_day,due_day,personal_tea_bps,is_archived)
VALUES
    ('93000000-0000-0000-0000-000000000301','93000000-0000-0000-0000-000000000001','93000000-0000-0000-0000-000000000311',NULL,'VISA','Tarjeta Sprint 3','3001',true,20000,20,10,NULL,false),
    ('93000000-0000-0000-0000-000000000302','93000000-0000-0000-0000-000000000002','93000000-0000-0000-0000-000000000312',NULL,'VISA','Tarjeta de otro usuario','3002',true,20000,20,10,NULL,false);

INSERT INTO public.categories (id,user_id,parent_id,name,origin,is_active,remote_revision,category_type)
VALUES ('93000000-0000-0000-0000-000000000401','93000000-0000-0000-0000-000000000001',NULL,'Compras de prueba','CUSTOM',true,1,'EXPENSE');

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '93000000-0000-0000-0000-000000000001';

SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000501','request_hash','client-value-is-not-authoritative',
        'transaction',jsonb_build_object(
            'id','93000000-0000-0000-0000-000000000501','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',10000,'currency_code','PEN','source_account_id',NULL,
            'card_id','93000000-0000-0000-0000-000000000301','category_id','93000000-0000-0000-0000-000000000401',
            'merchant_provisional_text','Comercio de prueba','occurred_at','2026-09-20T12:00:00Z','installment_count',3,'interest_mode','NONE'
        )
    ))->>'status',
    'APPLIED',
    'S/100 purchase is accepted without a liquid source account'
);
SELECT is(
    (SELECT transaction_type::text||'/'||operation_kind::text||'/'||COALESCE(account_id::text,'NULL')
     FROM public.transactions WHERE id='93000000-0000-0000-0000-000000000501'),
    'EXPENSE/CARD_PURCHASE/NULL',
    'Purchase is one expense event linked to the card and no cash account'
);
SELECT is(
    (SELECT string_agg(principal_minor::text,',' ORDER BY installment_number)
     FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000501'),
    '3334,3333,3333',
    'The cent remainder is assigned to the first installment'
);
SELECT is(
    (SELECT string_agg(due_date::text,',' ORDER BY installment_number)
     FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000501'),
    '2026-10-10,2026-11-10,2026-12-10',
    'Inclusive closing-day purchase is due in the following monthly card cycle'
);
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM internal.ledger_entries WHERE transaction_id='93000000-0000-0000-0000-000000000501'),0,'Credit purchase does not debit liquid assets');
SELECT is((SELECT count(*)::integer FROM public.financial_movements WHERE user_id='93000000-0000-0000-0000-000000000001' AND card_id='93000000-0000-0000-0000-000000000301'),0,'New credit command does not use movement-only storage');

SET LOCAL ROLE authenticated;
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000501','request_hash','a-different-ui-hash-is-ignored',
        'transaction',jsonb_build_object(
            'id','93000000-0000-0000-0000-000000000501','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',10000,'currency_code','PEN','source_account_id',NULL,
            'card_id','93000000-0000-0000-0000-000000000301','category_id','93000000-0000-0000-0000-000000000401',
            'merchant_provisional_text','Comercio de prueba','occurred_at','2026-09-20T12:00:00Z','installment_count',3,'interest_mode','NONE'
        )
    ))->>'status',
    'DUPLICATE',
    'A retry derives the same hash server-side even when client hash differs'
);
SELECT is((SELECT count(*)::integer FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000501'),3,'Idempotent retry does not duplicate installments');
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000501','request_hash','same-client-hash',
        'transaction',jsonb_build_object(
            'id','93000000-0000-0000-0000-000000000501','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',10001,'currency_code','PEN','source_account_id',NULL,
            'card_id','93000000-0000-0000-0000-000000000301','category_id','93000000-0000-0000-0000-000000000401',
            'merchant_provisional_text','Comercio de prueba','occurred_at','2026-09-20T12:00:00Z','installment_count',3,'interest_mode','NONE'
        )
    ))->>'status',
    'CONFLICT',
    'Reusing an idempotency key with changed financial fields conflicts despite a reused client hash'
);

SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000502','request_hash','hash-2',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000502','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',7000,'currency_code','PEN','card_id','93000000-0000-0000-0000-000000000301',
            'category_id','93000000-0000-0000-0000-000000000401','merchant_provisional_text','Segundo comercio',
            'occurred_at','2026-09-21T12:00:00Z','installment_count',1,'interest_mode','NONE')
    ))->>'status','APPLIED','Second purchase adds debt without moving cash');
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000503','request_hash','hash-3',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000503','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',3000,'currency_code','PEN','card_id','93000000-0000-0000-0000-000000000301',
            'category_id','93000000-0000-0000-0000-000000000401','occurred_at','2026-09-21T13:00:00Z','installment_count',1,'interest_mode','NONE')
    ))->>'status','APPLIED','Third purchase crosses the full credit line exactly');
SELECT is((SELECT count(*)::integer FROM public.app_notifications WHERE user_id='93000000-0000-0000-0000-000000000001' AND notification_type='CREDIT_UTILIZATION_THRESHOLD_CROSSED'),3,'One shared notification is emitted for each newly crossed 50/80/100 threshold');
SELECT is(
    (SELECT string_agg((event_payload->>'threshold_bps'),',' ORDER BY (event_payload->>'threshold_bps')::integer)
     FROM public.app_notifications WHERE user_id='93000000-0000-0000-0000-000000000001' AND notification_type='CREDIT_UTILIZATION_THRESHOLD_CROSSED'),
    '5000,8000,10000',
    'Notification payload exposes deterministic threshold basis points'
);

SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000601','request_hash','client-payment-hash',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000601','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',4000,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000101',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-22T12:00:00Z')
    ))->>'status','APPLIED','Partial payment is accepted against available funds');
SELECT is(
    (SELECT string_agg(ci.installment_number::text||':'||cpa.allocated_minor::text,',' ORDER BY ci.due_date,purchase.occurred_at,ci.installment_number,ci.id)
     FROM public.credit_payment_allocations cpa
     JOIN public.credit_installments ci ON ci.id=cpa.installment_id AND ci.user_id=cpa.user_id
     JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
     WHERE cpa.payment_transaction_id='93000000-0000-0000-0000-000000000601'),
    '1:3334,2:666',
    'FIFO pays the earliest due installment then partially amortizes the next principal'
);
RESET ROLE;
SELECT is(
    (SELECT response_payload->>'remaining_debt_minor' FROM internal.command_receipts WHERE idempotency_key='93000000-0000-0000-0000-000000000601'),
    '16000',
    'Payment response reports the exact remaining principal'
);
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000501' AND installment_number=2),'PARTIAL','Partial amount updates the in-progress installment state');
SELECT is((SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries WHERE transaction_id='93000000-0000-0000-0000-000000000601'),-4000::bigint,'Payment debits the real source account once');
SELECT is((SELECT transaction_type::text||'/'||operation_kind::text FROM public.transactions WHERE id='93000000-0000-0000-0000-000000000601'),'TRANSFER/CARD_PAYMENT','Card payment is a transfer/amortization transaction, not an expense');
SELECT is((SELECT count(*)::integer FROM internal.ledger_entries le JOIN public.transactions tx ON tx.id=le.transaction_id WHERE tx.card_id='93000000-0000-0000-0000-000000000301' AND tx.operation_kind='CARD_PAYMENT' AND tx.transaction_type='EXPENSE'),0,'Card payments create no expense ledger entry');

SET LOCAL ROLE authenticated;
SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000601','request_hash','different-client-hash',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000601','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',4000,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000101',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-22T12:00:00Z')
    ))->>'status','DUPLICATE','Payment retry is deduplicated from the server-derived command hash');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE id='93000000-0000-0000-0000-000000000601'),1,'Retry creates no second payment transaction');

SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000602','request_hash','hash-payment-2',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000602','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',11000,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000101',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-23T12:00:00Z')
    ))->>'status','APPLIED','Second FIFO payment fully amortizes oldest dues before later dues');
SELECT is(
    (SELECT sum(GREATEST(ci.principal_minor-COALESCE(a.allocated_minor,0),0))::bigint
     FROM public.credit_installments ci
     JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
     LEFT JOIN LATERAL (SELECT sum(x.allocated_minor)::bigint AS allocated_minor FROM public.credit_payment_allocations x WHERE x.installment_id=ci.id AND x.user_id=ci.user_id) a ON true
     WHERE purchase.card_id='93000000-0000-0000-0000-000000000301' AND purchase.currency_code='PEN' AND ci.status IN ('PENDING','PARTIAL')),
    5000::bigint,
    'Debt balance reflects all FIFO principal allocations exactly'
);
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000501' AND installment_number=1),'PAID','Oldest installment is fully paid first');
SELECT is((SELECT status FROM public.credit_installments WHERE transaction_id='93000000-0000-0000-0000-000000000503'),'PARTIAL','Payment spanning dates partially amortizes the next oldest installment');
RESET ROLE;
SELECT is((SELECT sum(signed_amount_minor)::bigint FROM internal.ledger_entries le JOIN public.transactions tx ON tx.id=le.transaction_id WHERE tx.operation_kind='CARD_PAYMENT' AND tx.card_id='93000000-0000-0000-0000-000000000301'),-15000::bigint,'Two payments reduce liquid assets by exactly the amount paid');

SET LOCAL ROLE authenticated;
SELECT is(
    public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000504','request_hash','hash-rearm',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000504','type','EXPENSE','operation_kind','CARD_PURCHASE',
            'amount_minor',5000,'currency_code','PEN','card_id','93000000-0000-0000-0000-000000000301',
            'category_id','93000000-0000-0000-0000-000000000401','occurred_at','2026-09-24T12:00:00Z','installment_count',1,'interest_mode','NONE')
    ))->>'status','APPLIED','A later purchase can cross a threshold again after payments re-arm it');
SELECT is((SELECT count(*)::integer FROM public.app_notifications WHERE user_id='93000000-0000-0000-0000-000000000001' AND notification_type='CREDIT_UTILIZATION_THRESHOLD_CROSSED'),4,'The re-armed 50% crossing emits one new shared notification');
SELECT is((SELECT count(*)::integer FROM public.app_notifications WHERE user_id='93000000-0000-0000-0000-000000000001' AND event_payload->>'threshold_bps'='5000'),2,'Separate accepted operations have separate stable event identities for a re-cross');

SELECT is(
    public.update_card_personal_tea_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000701','request_hash','client-rate-hash',
        'card',jsonb_build_object('id','93000000-0000-0000-0000-000000000301','personal_tea_bps',2345)
    ))->>'status','APPLIED','Owner can persist a personal contractual TEA');
SELECT is((SELECT personal_tea_bps FROM public.cards WHERE id='93000000-0000-0000-0000-000000000301'),2345,'Personal rate persists in basis points');
SELECT is(
    public.update_card_personal_tea_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000701','request_hash','different-client-value',
        'card',jsonb_build_object('id','93000000-0000-0000-0000-000000000301','personal_tea_bps',2345)
    ))->>'status','DUPLICATE','Personal rate retry uses the server-derived payload hash');
SELECT is(
    public.update_card_personal_tea_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000701','request_hash','same-client-value',
        'card',jsonb_build_object('id','93000000-0000-0000-0000-000000000301','personal_tea_bps',3000)
    ))->>'status','CONFLICT','Personal rate key cannot be reused with a different value');
SELECT is(
    public.update_card_personal_tea_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000702','request_hash','bad-rate',
        'card',jsonb_build_object('id','93000000-0000-0000-0000-000000000301','personal_tea_bps',100001)
    ))->>'status','REJECTED','Personal rate above the domain maximum is rejected');

SELECT is(
    public.update_card_personal_tea_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000703','request_hash','foreign-card',
        'card',jsonb_build_object('id','93000000-0000-0000-0000-000000000302','personal_tea_bps',2000)
    ))->>'status','REJECTED','A user cannot update another owner card rate');
SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000603','request_hash','no-funds',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000603','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',1,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000102',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-24T13:00:00Z')
    ))->'error'->>'code','INSUFFICIENT_FUNDS','Payment rejects a source account without funds');
SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000604','request_hash','wrong-owner-account',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000604','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',1,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000201',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-24T13:00:00Z')
    ))->'error'->>'code','SOURCE_ACCOUNT_UNAVAILABLE_OR_CURRENCY_MISMATCH','Payment rejects another user source account');
SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000605','request_hash','wrong-currency',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000605','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',1,'currency_code','USD','source_account_id','93000000-0000-0000-0000-000000000103',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-24T13:00:00Z')
    ))->'error'->>'code','NO_OUTSTANDING_DEBT','Payment cannot allocate USD funds against PEN debt');
SELECT is(
    public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','93000000-0000-0000-0000-000000000606','request_hash','over-debt',
        'transaction',jsonb_build_object('id','93000000-0000-0000-0000-000000000606','type','TRANSFER','operation_kind','CARD_PAYMENT',
            'amount_minor',10001,'currency_code','PEN','source_account_id','93000000-0000-0000-0000-000000000101',
            'card_id','93000000-0000-0000-0000-000000000301','occurred_at','2026-09-24T13:00:00Z')
    ))->'error'->>'code','PAYMENT_EXCEEDS_OUTSTANDING_DEBT','Payment above current debt is rejected');

SELECT is((SELECT count(*)::integer FROM public.app_notifications WHERE user_id='93000000-0000-0000-0000-000000000001'),4,'Authenticated HU-42 reader sees the four owner notifications via the shared contract');
SELECT ok(NOT has_table_privilege('anon','public.app_notifications','SELECT'),'Anonymous clients cannot read notification rows directly');
SELECT ok(NOT has_table_privilege('authenticated','public.credit_installments','INSERT'),'Clients cannot bypass atomic schedule RPCs with direct table writes');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
