BEGIN;
SELECT plan(17);

CREATE TEMP TABLE debt_history_ids (
    owner_id uuid,
    other_id uuid,
    debt_id uuid,
    unreferenced_debt_id uuid,
    other_debt_id uuid,
    open_operation uuid,
    edit_operation uuid,
    delete_operation uuid
);
INSERT INTO debt_history_ids VALUES (
    '83000000-0000-4000-8000-000000000001',
    '83000000-0000-4000-8000-000000000002',
    '83000000-0000-4000-8000-000000000003',
    '83000000-0000-4000-8000-000000000004',
    '83000000-0000-4000-8000-000000000005',
    '83000000-0000-4000-8000-000000000006',
    '83000000-0000-4000-8000-000000000007',
    '83000000-0000-4000-8000-000000000008'
);
INSERT INTO auth.users (id, email)
SELECT owner_id, owner_id::text || '@debt-edit.kipu.test' FROM debt_history_ids
UNION ALL
SELECT other_id, other_id::text || '@debt-edit.kipu.test' FROM debt_history_ids;
INSERT INTO public.debts (id, user_id, obligation_type, counterparty_name, total_minor, currency_code)
SELECT unreferenced_debt_id, owner_id, 'PAYABLE'::public.obligation_type, 'Planned only', 1000, 'PEN' FROM debt_history_ids
UNION ALL
SELECT other_debt_id, other_id, 'PAYABLE'::public.obligation_type, 'Other owner', 1500, 'PEN' FROM debt_history_ids;
INSERT INTO public.debt_events (
    id, user_id, debt_id, event_type, amount_minor, principal_delta_minor, occurred_at, created_at
)
SELECT extensions.gen_random_uuid(), owner_id, unreferenced_debt_id, 'DISBURSEMENT', 1000, 0, now(), now()
FROM debt_history_ids;
GRANT SELECT ON debt_history_ids TO authenticated;

SELECT has_function('public', 'edit_debt_details_v1', ARRAY['jsonb'], 'descriptive edit RPC is installed');
SELECT has_function('public', 'delete_debt_if_unreferenced_v1', ARRAY['jsonb'], 'conditional delete RPC is installed');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', (SELECT owner_id::text FROM debt_history_ids), true);

SELECT lives_ok($$
    SELECT public.open_debt_v1(jsonb_build_object(
        'contract_version', 1,
        'operation_id', (SELECT open_operation FROM debt_history_ids),
        'request_hash', repeat('a',64),
        'debt_id', (SELECT debt_id FROM debt_history_ids),
        'obligation_type', 'PAYABLE',
        'counterparty_name', 'Original',
        'total_minor', 7500,
        'currency_code', 'PEN',
        'opened_on', '2026-10-01',
        'opening_mode', 'HISTORICAL'
    ));
$$, 'historical debt with an opening event is established for the edit checks');

SELECT is(
    public.edit_debt_details_v1(jsonb_build_object(
        'operation_id', (SELECT edit_operation FROM debt_history_ids),
        'request_hash', repeat('b',64),
        'debt_id', (SELECT debt_id FROM debt_history_ids),
        'expected_revision', 1,
        'counterparty_name', 'Updated name',
        'due_date', '2026-12-31',
        'reminder_lead_days', 5,
        'notes', 'Acuerdo actualizado'
    ))->>'status', 'APPLIED', 'owner can edit only descriptive fields at the current revision');
SELECT is((SELECT counterparty_name FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids)), 'Updated name',
    'counterparty display name was updated');
SELECT is((SELECT total_minor FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids)), 7500::bigint,
    'descriptive edit cannot change principal');
SELECT is((SELECT currency_code::text FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids)), 'PEN',
    'descriptive edit cannot change currency');
SELECT is((SELECT opened_on FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids)), DATE '2026-10-01',
    'descriptive edit cannot change the opening basis');
SELECT is((SELECT revision FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids)), 2::bigint,
    'descriptive edit increments the debt revision once');
SELECT is((SELECT count(*)::integer FROM public.debt_events WHERE debt_id=(SELECT debt_id FROM debt_history_ids)), 1,
    'descriptive edit preserves all financial history');
SELECT is(
    public.edit_debt_details_v1(jsonb_build_object(
        'operation_id', extensions.gen_random_uuid(),
        'request_hash', repeat('c',64),
        'debt_id', (SELECT debt_id FROM debt_history_ids),
        'expected_revision', 1,
        'counterparty_name', 'Stale name'
    ))->>'status', 'CONFLICT', 'stale descriptive edit returns an explicit revision conflict');
SELECT is(
    public.edit_debt_details_v1(jsonb_build_object(
        'operation_id', (SELECT edit_operation FROM debt_history_ids),
        'request_hash', repeat('b',64),
        'debt_id', (SELECT debt_id FROM debt_history_ids),
        'expected_revision', 1,
        'counterparty_name', 'Updated name',
        'due_date', '2026-12-31',
        'reminder_lead_days', 5,
        'notes', 'Acuerdo actualizado'
    ))->>'status', 'DUPLICATE', 'replayed descriptive edit does not increment the revision again');

SELECT throws_ok($$
    SELECT public.edit_debt_details_v1(jsonb_build_object(
        'operation_id', extensions.gen_random_uuid(),
        'request_hash', repeat('d',64),
        'debt_id', (SELECT other_debt_id FROM debt_history_ids),
        'expected_revision', 1,
        'counterparty_name', 'Tampered'
    ));
$$, 'P0002', 'DEBT_NOT_FOUND', 'owner cannot edit another user debt');

SELECT throws_ok($$
    DELETE FROM public.debts WHERE id=(SELECT debt_id FROM debt_history_ids);
$$, '42501', NULL, 'authenticated clients cannot directly cascade-delete debt history');
SELECT throws_ok($$
    SELECT public.delete_debt_if_unreferenced_v1(jsonb_build_object(
        'operation_id', (SELECT delete_operation FROM debt_history_ids),
        'request_hash', repeat('e',64),
        'debt_id', (SELECT debt_id FROM debt_history_ids)
    ));
$$, 'P0001', 'HISTORY_PRESERVED', 'conditional delete keeps a debt that has financial history');

SELECT is(
    public.delete_debt_if_unreferenced_v1(jsonb_build_object(
        'operation_id', extensions.gen_random_uuid(),
        'request_hash', repeat('f',64),
        'debt_id', (SELECT unreferenced_debt_id FROM debt_history_ids)
    ))->>'status', 'APPLIED', 'conditional delete removes a historical opening without a linked cash movement');

RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.debts WHERE id=(SELECT unreferenced_debt_id FROM debt_history_ids)), 0,
    'unreferenced debt is physically deleted');
SELECT * FROM finish();
ROLLBACK;
