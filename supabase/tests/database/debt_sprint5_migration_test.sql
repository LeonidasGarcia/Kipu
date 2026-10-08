BEGIN;
SELECT plan(14);

SELECT has_column('public', 'debts', 'opened_on', 'debt opening date was added');
SELECT has_column('public', 'debts', 'reminder_lead_days', 'debt reminder preference was added');
SELECT has_column('public', 'debt_events', 'principal_delta_minor', 'signed principal delta was added');
SELECT has_index('public', 'debt_installments', 'debt_installments_debt_number_unique', 'installment number is unique within a debt');
SELECT is((
    SELECT count(*)::integer FROM (
        SELECT 1 FROM public.debt_installments GROUP BY debt_id, installment_number HAVING count(*) > 1
    ) duplicate_slots
), 0, 'reconciled installment data has no duplicate sequence slots');

SELECT is(
    (SELECT (id::text || ':' || user_id::text || ':' || total_minor::text)::text FROM public.debts
     WHERE id = '55000000-0000-4000-8000-000000000003'),
    '55000000-0000-4000-8000-000000000003:55000000-0000-4000-8000-000000000001:10000',
    'legacy debt id, owner, and principal survive the migration'
);
SELECT is(
    (SELECT (id::text || ':' || user_id::text || ':' || debt_id::text || ':' || installment_number::text)::text FROM public.debt_installments
     WHERE id = '55000000-0000-4000-8000-000000000004'),
    '55000000-0000-4000-8000-000000000004:55000000-0000-4000-8000-000000000001:55000000-0000-4000-8000-000000000003:1',
    'legacy installment id, owner, parent, and sequence survive the migration'
);
SELECT is(
    (SELECT (id::text || ':' || transaction_id::text || ':' || installment_id::text || ':' || event_type || ':' || amount_minor::text)::text
     FROM public.debt_events WHERE id = '55000000-0000-4000-8000-000000000006'),
    '55000000-0000-4000-8000-000000000006:55000000-0000-4000-8000-000000000005:55000000-0000-4000-8000-000000000004:PAYMENT:1250',
    'legacy payment and its movement/installment links survive the migration'
);
SELECT is((SELECT principal_delta_minor FROM public.debt_events WHERE id = '55000000-0000-4000-8000-000000000006'), NULL::bigint,
    'legacy payment remains nullable and uses its old PAYMENT meaning');
SELECT is((SELECT principal_delta_minor FROM public.debt_events WHERE id = '55000000-0000-4000-8000-000000000007'), -300::bigint,
    'legacy adjustment is retained with an explicitly reconciled principal direction');
SELECT is((SELECT principal_delta_minor FROM public.debt_events WHERE id = '55000000-0000-4000-8000-000000000008'), -200::bigint,
    'legacy forgiveness is retained with an explicitly reconciled principal direction');
SELECT is((SELECT status FROM public.transactions WHERE id = '55000000-0000-4000-8000-000000000005'), 'CONFIRMED',
    'legacy linked movement status is unchanged');
SELECT is((SELECT remaining_minor FROM public.v_debt_summary WHERE debt_id = '55000000-0000-4000-8000-000000000003'), 8250::numeric,
    'legacy payment and explicitly reconciled signed events reduce the derived balance once');
SELECT is((SELECT count(*)::integer FROM public.debt_events WHERE debt_id = '55000000-0000-4000-8000-000000000003'), 3,
    'legacy event history is preserved without duplication');

SELECT * FROM finish();
ROLLBACK;
