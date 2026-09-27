begin;
select plan(36);

select has_table('public', 'billing_products', 'billing_products exists');
select has_table('public', 'billing_purchases', 'billing_purchases exists');
select has_table('internal', 'billing_events', 'private billing event audit exists');
select has_column('public', 'billing_purchases', 'acknowledgement_state', 'purchase acknowledgement state is persisted');
select ok((select relrowsecurity from pg_class where oid='public.billing_purchases'::regclass), 'purchase RLS is enabled');
select ok((select relforcerowsecurity from pg_class where oid='public.billing_purchases'::regclass), 'purchase RLS is forced');
select ok((select relrowsecurity from pg_class where oid='internal.billing_events'::regclass), 'private event RLS is enabled');
select ok((select relforcerowsecurity from pg_class where oid='internal.billing_events'::regclass), 'private event RLS is forced');
select ok(exists(select 1 from pg_indexes where schemaname='public' and tablename='billing_purchases' and indexdef ilike '%unique%purchase_token_hash%'), 'purchase token hash has a global unique index');
select ok(exists(select 1 from pg_constraint where conrelid='public.billing_products'::regclass and contype='c' and pg_get_constraintdef(oid) ilike '%plan_type%' and pg_get_constraintdef(oid) ilike '%PRO_LIFETIME%'), 'product constraint includes PRO_LIFETIME');
select ok(exists(select 1 from pg_constraint where conrelid='public.billing_purchases'::regclass and contype='c' and pg_get_constraintdef(oid) ilike '%entitlement_state%' and pg_get_constraintdef(oid) ilike '%REVOKED%'), 'lifecycle constraint includes REVOKED');
select ok(exists(select 1 from pg_policies where schemaname='public' and tablename='billing_purchases' and roles @> array['authenticated']::name[] and qual ilike '%auth.uid()%'), 'purchase reads are scoped to the JWT owner');
select ok(not has_table_privilege('authenticated', 'public.billing_purchases', 'INSERT'), 'authenticated cannot insert purchase rows');
select ok(not has_table_privilege('authenticated', 'public.billing_purchases', 'UPDATE'), 'authenticated cannot update purchase rows');
select ok(not has_table_privilege('authenticated', 'public.billing_purchases', 'DELETE'), 'authenticated cannot delete purchase rows');
select ok(not has_table_privilege('authenticated', 'internal.billing_events', 'SELECT'), 'authenticated cannot read private billing events');
select ok(not has_schema_privilege('authenticated', 'internal', 'USAGE'), 'internal schema is not exposed to authenticated clients');
select ok(not has_function_privilege('authenticated', 'public.persist_verified_billing_purchase(uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb)', 'EXECUTE'), 'authenticated cannot execute the server writer');
select ok(has_function_privilege('service_role', 'public.persist_verified_billing_purchase(uuid,text,text,text,text,text,text,timestamptz,timestamptz,jsonb)', 'EXECUTE'), 'server role can call the atomic writer');
select ok(has_table_privilege('service_role', 'public.billing_products', 'INSERT'), 'server role can maintain the product catalog');
select ok(not has_function_privilege('authenticated', 'public.claim_billing_purchase_acknowledgement(text)', 'EXECUTE'), 'client cannot claim acknowledgement work');

insert into auth.users (id, email) values
  ('73000000-0000-4000-8000-000000000001', 'billing-a@example.test'),
  ('73000000-0000-4000-8000-000000000002', 'billing-b@example.test');
insert into public.billing_products (id, store_product_id, name, plan_type)
values ('kipu_monthly', 'kipu_pro_monthly', 'Kipu Pro mensual', 'PRO_MONTHLY');
insert into public.billing_purchases (id,user_id,purchase_token_hash,product_id,purchase_state,entitlement_state,acknowledgement_state,starts_at,verified_at)
values ('74000000-0000-4000-8000-000000000001','73000000-0000-4000-8000-000000000001',repeat('a',64),'kipu_monthly','PURCHASED','ACTIVE','ACKNOWLEDGED',now(),now());
insert into internal.billing_events (user_id,purchase_token_hash,event_type,raw_payload)
values ('73000000-0000-4000-8000-000000000001',repeat('a',64),'VERIFICATION','{"purchaseState":"PURCHASED"}'::jsonb);
select throws_ok($$insert into public.billing_purchases (user_id,purchase_token_hash,product_id,purchase_state,entitlement_state,acknowledgement_state,starts_at,verified_at) values ('73000000-0000-4000-8000-000000000002',repeat('a',64),'kipu_monthly','PURCHASED','ACTIVE','ACKNOWLEDGED',now(),now())$$, '23505', null, 'one token hash cannot bind to a second account');
select throws_ok($$insert into public.billing_purchases (user_id,purchase_token_hash,product_id,purchase_state,entitlement_state,acknowledgement_state,starts_at,verified_at) values ('73000000-0000-4000-8000-000000000002',repeat('b',64),'kipu_monthly','PENDING','ACTIVE','PENDING',now(),now())$$, '23514', null, 'a pending provider purchase cannot carry an entitlement');
select throws_ok($$update internal.billing_events set raw_payload='{}'::jsonb$$, '42501', 'billing events are append-only', 'verification events cannot be updated');
select throws_ok($$delete from internal.billing_events$$, '42501', 'billing events are append-only', 'verification events cannot be deleted');
select lives_ok($$select * from public.persist_verified_billing_purchase(
  '73000000-0000-4000-8000-000000000001', 'kipu_pro_monthly', repeat('c',64), 'GPA.CANCELLED',
  'CANCELLED', 'CANCELED_ACTIVE', 'PENDING', now(), now() + interval '2 days', '{"provider":"GOOGLE_PLAY","subscriptionState":"SUBSCRIPTION_STATE_CANCELED"}'::jsonb
)$$, 'server persists a canceled subscription before its paid term expires');
select ok(exists(select 1 from public.billing_purchases where purchase_token_hash=repeat('c',64) and purchase_state='CANCELLED' and entitlement_state='CANCELED_ACTIVE'), 'provider and Kipu lifecycle states remain separate');
select ok((select effective_premium from public.persist_verified_billing_purchase(
  '73000000-0000-4000-8000-000000000001', 'kipu_pro_monthly', repeat('c',64), 'GPA.CANCELLED',
  'CANCELLED', 'CANCELED_ACTIVE', 'PENDING', now(), now() + interval '2 days', '{"provider":"GOOGLE_PLAY","subscriptionState":"SUBSCRIPTION_STATE_CANCELED"}'::jsonb
)), 'canceled subscription retains Premium through the verified expiry');
select ok((select effective_expires_at > now() from public.persist_verified_billing_purchase(
  '73000000-0000-4000-8000-000000000001', 'kipu_pro_monthly', repeat('c',64), 'GPA.CANCELLED',
  'CANCELLED', 'CANCELED_ACTIVE', 'PENDING', now(), now() + interval '2 days', '{"provider":"GOOGLE_PLAY","subscriptionState":"SUBSCRIPTION_STATE_CANCELED"}'::jsonb
)), 'effective canceled-term expiry is returned to the caller');
set local role service_role;
select is(public.claim_billing_purchase_acknowledgement(repeat('c',64)), true, 'first verifier claims the acknowledgement lease');
select is(public.claim_billing_purchase_acknowledgement(repeat('c',64)), false, 'concurrent verifier cannot claim the same acknowledgement lease');
select is(public.complete_billing_purchase_acknowledgement(repeat('c',64)), true, 'server marks provider acknowledgement complete');
select is(public.claim_billing_purchase_acknowledgement(repeat('c',64)), false, 'acknowledged purchase cannot be acknowledged a second time');
reset role;
select throws_ok($$delete from auth.users where id='73000000-0000-4000-8000-000000000001'$$, '23503', null, 'account deletion cannot erase financial purchase history');
select set_config('request.jwt.claim.role','authenticated',true);
select set_config('request.jwt.claim.sub','73000000-0000-4000-8000-000000000001',true);
set local role authenticated;
select is((select count(*)::integer from public.billing_purchases), 2, 'owner can read both own purchases');
reset role;
select set_config('request.jwt.claim.sub','73000000-0000-4000-8000-000000000002',true);
set local role authenticated;
select is((select count(*)::integer from public.billing_purchases), 0, 'another account cannot read purchase');
reset role;
select * from finish();
rollback;
