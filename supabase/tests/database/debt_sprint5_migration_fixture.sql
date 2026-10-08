-- Apply on a local database stopped at 20261007144500, before the S5 debt migration.
-- The fixed IDs let debt_sprint5_migration_test.sql verify that an upgrade preserves
-- existing obligations, installment identity, ownership, events, and transaction links.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.debt_installments
        GROUP BY debt_id, installment_number
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'DEBT_INSTALLMENT_SEQUENCE_DUPLICATE: reconcile existing sequence duplicates before applying S5';
    END IF;
END;
$$;

INSERT INTO auth.users (id, email)
VALUES ('55000000-0000-4000-8000-000000000001', 'debt-migration@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES ('55000000-0000-4000-8000-000000000002', '55000000-0000-4000-8000-000000000001', 'Migration account', 'SAVINGS', 'PEN')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.debts (id, user_id, obligation_type, counterparty_name, total_minor, currency_code, status)
VALUES ('55000000-0000-4000-8000-000000000003', '55000000-0000-4000-8000-000000000001', 'RECEIVABLE', 'Legacy debt', 10000, 'PEN', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.debt_installments (id, user_id, debt_id, installment_number, due_date, amount_minor)
VALUES ('55000000-0000-4000-8000-000000000004', '55000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000003', 1, DATE '2026-11-01', 5000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.transactions (id, user_id, account_id, transaction_type, operation_kind, amount_minor, currency_code, status)
VALUES ('55000000-0000-4000-8000-000000000005', '55000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000002', 'INCOME', 'STANDARD', 1250, 'PEN', 'CONFIRMED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.debt_events (id, user_id, debt_id, transaction_id, installment_id, event_type, amount_minor)
VALUES
    ('55000000-0000-4000-8000-000000000006', '55000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000003', '55000000-0000-4000-8000-000000000005', '55000000-0000-4000-8000-000000000004', 'PAYMENT', 1250),
    ('55000000-0000-4000-8000-000000000007', '55000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000003', NULL, NULL, 'ADJUSTMENT', 300),
    ('55000000-0000-4000-8000-000000000008', '55000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000003', NULL, NULL, 'FORGIVENESS', 200);

COMMIT;
