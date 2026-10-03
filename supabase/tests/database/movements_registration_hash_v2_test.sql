BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;
SELECT no_plan();
SELECT has_function('public','register_transaction_v2',ARRAY['jsonb'],'v2 RPC exists');
SELECT ok(NOT has_function_privilege('anon','public.register_transaction_v2(jsonb)','EXECUTE'),'anon denied');
SELECT ok(has_function_privilege('authenticated','public.register_transaction_v2(jsonb)','EXECUTE'),'authenticated can call v2');
SELECT ok(NOT has_function_privilege('authenticated','internal.movement_register_hash_v2(uuid,jsonb)','EXECUTE'),'hash helper private');
SELECT is(internal.movement_register_hash_v2('11111111-1111-1111-1111-111111111111',
    jsonb_build_object(
        'type', 'EXPENSE',
        'amount_minor', 2500,
        'currency_code', 'PEN',
        'source_account_id', '22222222-2222-2222-2222-222222222222',
        'merchant_provisional_text', 'Bodega ñ 🦙',
        'occurred_at', '1970-01-01T00:16:40Z',
        'note', E'a;note:b "x"\\\n'
    )),
    'de3c19c4dae2a263d61fb22d62e1d46847ae83379e954fdd5e3377b63b2d5218','shared Kotlin/Postgres golden vector');
INSERT INTO auth.users(id,email) VALUES ('11111111-1111-1111-1111-111111111111','hash-v2@kipu.test') ON CONFLICT DO NOTHING;
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code)
 VALUES('22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','Hash v2','CASH','PEN') ON CONFLICT DO NOTHING;
CREATE FUNCTION pg_temp.command_v2(p_note text DEFAULT NULL,p_merchant text DEFAULT NULL)
RETURNS jsonb LANGUAGE sql AS $$
 SELECT jsonb_build_object('contract_version',2,'idempotency_key','33333333-3333-3333-3333-333333333333',
    'request_hash',internal.movement_register_hash_v2('11111111-1111-1111-1111-111111111111',tx),'transaction',tx)
 FROM (SELECT jsonb_build_object('id','44444444-4444-4444-4444-444444444444',
    'type','INCOME','amount_minor',2500,'currency_code','PEN','source_account_id','22222222-2222-2222-2222-222222222222',
    'occurred_at','2026-10-02T12:34:56.789Z','note',p_note,'merchant_provisional_text',p_merchant) tx) t;
$$;
SELECT isnt(pg_temp.command_v2('c','a;note:b')->>'request_hash',pg_temp.command_v2('b;note:c','a')->>'request_hash','delimiters do not collide');
SELECT isnt(pg_temp.command_v2(NULL)->>'request_hash',pg_temp.command_v2('')->>'request_hash','null differs from empty');
SELECT isnt(pg_temp.command_v2('ñ')->>'request_hash',pg_temp.command_v2(U&'n\0303')->>'request_hash','Unicode text is preserved without normalization');
SELECT is(internal.movement_register_hash_v2('11111111-1111-1111-1111-111111111111',jsonb_set(pg_temp.command_v2()->'transaction','{occurred_at}','"2026-10-02T07:34:56.789-05:00"')),
 pg_temp.command_v2()->>'request_hash','equivalent timezone timestamps hash identically');
CREATE TEMP TABLE commands_v2 AS SELECT pg_temp.command_v2('original') command;
GRANT SELECT ON commands_v2 TO authenticated;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub"='11111111-1111-1111-1111-111111111111';
SELECT is(public.register_transaction_v2(jsonb_set((SELECT command FROM commands_v2),'{request_hash}',to_jsonb(repeat('0',64))))->'error'->>'code','INVALID_REQUEST_HASH','altered hash rejected before writes');
SELECT is(public.register_transaction_v2(jsonb_set((SELECT command FROM commands_v2),'{transaction,operation_kind}','"CARD_PURCHASE"'))->'error'->>'code','INVALID_COMMAND','unsigned specialized fields rejected');
SELECT is(public.register_transaction_v2(jsonb_set((SELECT command FROM commands_v2),'{transaction,occurred_at}','"2026-10-02T12:34:56.7891Z"'))->'error'->>'code','INVALID_TIMESTAMP_PRECISION','submillisecond precision rejected');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE id='44444444-4444-4444-4444-444444444444'),0,'invalid commands have no transaction');
SELECT is(public.register_transaction_v1(jsonb_set((SELECT command FROM commands_v2),'{request_hash}',to_jsonb(repeat('0',64))))->'error'->>'code','INVALID_REQUEST_HASH','declared v2 cannot bypass hash via v1 endpoint');
SELECT is(public.register_transaction_v2(jsonb_set((SELECT command FROM commands_v2),'{transaction,source_account_id}','"22222222222222222222222222222222"'))->'error'->>'code','INVALID_UUID','noncanonical UUID is explicitly rejected');
SELECT is(public.register_transaction_v2((SELECT command FROM commands_v2))->>'status','APPLIED','valid command applied');
SELECT is(public.register_transaction_v1(jsonb_set((SELECT command FROM commands_v2),'{contract_version}','1'))->'error'->>'code','CONTRACT_VERSION_CONFLICT','v2 receipt cannot be downgraded to unverified v1');
SELECT is(public.register_transaction_v2((SELECT command FROM commands_v2))->>'transaction_id','44444444-4444-4444-4444-444444444444','replay preserves original result');
RESET ROLE;
UPDATE commands_v2 SET command=pg_temp.command_v2('different');
SET LOCAL ROLE authenticated;
SELECT is(public.register_transaction_v2((SELECT command FROM commands_v2))->>'status','CONFLICT','same key with signed different content conflicts');
SET LOCAL "request.jwt.claim.sub"='55555555-5555-5555-5555-555555555555';
SELECT is(public.register_transaction_v2((SELECT command FROM commands_v2))->'error'->>'code','INVALID_REQUEST_HASH','hash bound to server owner');
SET LOCAL "request.jwt.claim.sub"='';
SELECT is(public.register_transaction_v2((SELECT command FROM commands_v2))->'error'->>'code','UNAUTHORIZED','no owner denied');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE id='44444444-4444-4444-4444-444444444444'),1,'replay/conflict leave one transaction');
SELECT is((SELECT count(*)::integer FROM internal.ledger_entries WHERE transaction_id='44444444-4444-4444-4444-444444444444'),1,'replay/conflict leave one financial effect');
SELECT is((SELECT count(*)::integer FROM internal.command_receipts WHERE user_id='11111111-1111-1111-1111-111111111111' AND idempotency_key='33333333-3333-3333-3333-333333333333'),1,'one original receipt');
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub"='11111111-1111-1111-1111-111111111111';
SELECT is(public.register_transaction_v1(jsonb_build_object('contract_version',1,'idempotency_key','legacy-original-key','request_hash','legacy-original-hash',
 'transaction',jsonb_build_object('id','66666666-6666-6666-6666-666666666666','type','INCOME','amount_minor',100,'currency_code','PEN','source_account_id','22222222-2222-2222-2222-222222222222','occurred_at','2026-10-02T00:00:00Z')))->>'status','APPLIED','legacy v1 hash accepted unchanged');
RESET ROLE;
SELECT is((SELECT contract_version::integer FROM internal.command_receipts WHERE user_id='11111111-1111-1111-1111-111111111111' AND idempotency_key='33333333-3333-3333-3333-333333333333'),2,'v2 receipt is versioned');
SELECT is((SELECT command_payload->'transaction'->>'note' FROM internal.command_receipts WHERE user_id='11111111-1111-1111-1111-111111111111' AND idempotency_key='33333333-3333-3333-3333-333333333333'),'original','original signed payload is retained');
SELECT ok(NOT has_function_privilege('authenticated','internal.register_transaction_pre_hash_v2(jsonb)','EXECUTE'),'closed legacy implementation cannot bypass dispatch');
SELECT * FROM finish();
ROLLBACK;
