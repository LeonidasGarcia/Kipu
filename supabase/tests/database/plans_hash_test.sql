begin;

select plan(9);

select is(
  encode(private.plan_selection_payload_hash(
    1,
    '5D92AF34-C725-4A1A-A863-2C93FA214C86'::uuid,
    3,
    'premium_intent',
    '2026-09-14 10:03:12.123456-05'::timestamptz
  ), 'hex'),
  '93914ed97388d65ce6fe38949a17cae841f26654afaa8c168ac6ed85ab797132',
  'canonical SHA-256 matches the approved golden vector'
);

select is(
  private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'),
  private.plan_selection_payload_hash(1, '5D92AF34-C725-4A1A-A863-2C93FA214C86', 3, 'premium_intent', '2026-09-14T10:03:12.123456-05'),
  'UUID, selection case and timestamp offset normalize identically'
);

select is(
  private.plan_selection_payload_hash(
    (jsonb_build_object('selected_at','2026-09-14T15:03:12.123456Z','selection','PREMIUM_INTENT','selection_revision','3','operation_id','5d92af34-c725-4a1a-a863-2c93fa214c86','contract_version',1)->>'contract_version')::smallint,
    (jsonb_build_object('selected_at','2026-09-14T15:03:12.123456Z','selection','PREMIUM_INTENT','selection_revision','3','operation_id','5d92af34-c725-4a1a-a863-2c93fa214c86','contract_version',1)->>'operation_id')::uuid,
    (jsonb_build_object('selected_at','2026-09-14T15:03:12.123456Z','selection','PREMIUM_INTENT','selection_revision','3','operation_id','5d92af34-c725-4a1a-a863-2c93fa214c86','contract_version',1)->>'selection_revision')::bigint,
    jsonb_build_object('selected_at','2026-09-14T15:03:12.123456Z','selection','PREMIUM_INTENT','selection_revision','3','operation_id','5d92af34-c725-4a1a-a863-2c93fa214c86','contract_version',1)->>'selection',
    (jsonb_build_object('selected_at','2026-09-14T15:03:12.123456Z','selection','PREMIUM_INTENT','selection_revision','3','operation_id','5d92af34-c725-4a1a-a863-2c93fa214c86','contract_version',1)->>'selected_at')::timestamptz
  ),
  private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'),
  'JSON property order and whitespace do not participate in identity'
);

select isnt(private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 4, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'), private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'), 'revision participates in the hash');
select isnt(private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'FREE', '2026-09-14T15:03:12.123456Z'), private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'), 'selection participates in the hash');
select isnt(private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123457Z'), private.plan_selection_payload_hash(1, '5d92af34-c725-4a1a-a863-2c93fa214c86', 3, 'PREMIUM_INTENT', '2026-09-14T15:03:12.123456Z'), 'microseconds participate in the hash');

insert into auth.users (id, email) values ('60000000-0000-4000-8000-000000000006', 'hash@example.test');
select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('request.jwt.claim.sub', '60000000-0000-4000-8000-000000000006', true);
select is((select result from public.apply_plan_selection(1, 'cccccccc-0000-4000-8000-000000000001', 1, 'FREE', '2026-09-15T12:00:00Z')), 'APPLIED', 'first operation payload is applied');
select is((select result from public.apply_plan_selection(1, 'cccccccc-0000-4000-8000-000000000001', 1, 'FREE', '2026-09-15T12:00:00Z')), 'DUPLICATE', 'identical operation payload is duplicate');
select is((select result from public.apply_plan_selection(1, 'cccccccc-0000-4000-8000-000000000001', 1, 'PREMIUM_INTENT', '2026-09-15T12:00:00Z')), 'CONFLICT', 'same operation identity with a different canonical hash conflicts');

select * from finish();
rollback;
