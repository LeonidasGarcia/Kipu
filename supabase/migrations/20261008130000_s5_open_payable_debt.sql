BEGIN;

CREATE OR REPLACE FUNCTION private.open_payable_debt_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $open_payable$
DECLARE
    v_user_id uuid := auth.uid();
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_account_id uuid := NULLIF(p_payload->>'account_id', '')::uuid;
    v_principal bigint := NULLIF(p_payload->>'total_minor', '')::bigint;
    v_currency text := upper(NULLIF(btrim(p_payload->>'currency_code'), ''));
    v_opening_mode text := upper(NULLIF(btrim(p_payload->>'opening_mode'), ''));
    v_counterparty text := regexp_replace(btrim(COALESCE(p_payload->>'counterparty_name', '')), '[[:space:]]+', ' ', 'g');
    v_opened_on date := NULLIF(p_payload->>'opened_on', '')::date;
    v_due_date date := NULLIF(p_payload->>'due_date', '')::date;
    v_reminder_lead_days smallint := NULLIF(p_payload->>'reminder_lead_days', '')::smallint;
    v_account public.accounts%ROWTYPE;
    v_transaction_id uuid;
    v_event_id uuid;
    v_created_at timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF upper(NULLIF(btrim(p_payload->>'obligation_type'), '')) <> 'PAYABLE' THEN
        RAISE EXCEPTION 'WRONG_OBLIGATION_TYPE' USING ERRCODE = '22023';
    END IF;
    IF v_debt_id IS NULL OR v_operation_id IS NULL OR v_principal IS NULL
       OR v_principal NOT BETWEEN 1 AND 99999999999999 OR v_currency NOT IN ('PEN', 'USD')
       OR v_opened_on IS NULL OR v_opening_mode NOT IN ('NEW_CASH_FLOW', 'HISTORICAL')
       OR v_counterparty = '' THEN
        RAISE EXCEPTION 'INVALID_PAYABLE_OPENING' USING ERRCODE = '22023';
    END IF;
    IF v_reminder_lead_days IS NOT NULL AND v_reminder_lead_days NOT BETWEEN 0 AND 365 THEN
        RAISE EXCEPTION 'INVALID_REMINDER_LEAD_DAYS' USING ERRCODE = '22023';
    END IF;
    IF v_opening_mode = 'NEW_CASH_FLOW' AND v_account_id IS NULL THEN
        RAISE EXCEPTION 'ACCOUNT_REQUIRED' USING ERRCODE = '22023';
    END IF;
    IF v_account_id IS NOT NULL THEN
        SELECT * INTO v_account FROM public.accounts WHERE id = v_account_id FOR KEY SHARE;
        IF NOT FOUND OR v_account.user_id <> v_user_id THEN
            RAISE EXCEPTION 'ACCOUNT_NOT_OWNED' USING ERRCODE = '42501';
        END IF;
        IF v_account.deleted_at IS NOT NULL OR v_account.is_archived THEN
            RAISE EXCEPTION 'ACCOUNT_NOT_ELIGIBLE' USING ERRCODE = '22023';
        END IF;
        IF v_account.currency_code::text <> v_currency THEN
            RAISE EXCEPTION 'ACCOUNT_CURRENCY_MISMATCH' USING ERRCODE = '22023';
        END IF;
    END IF;

    INSERT INTO public.debts (
        id, user_id, obligation_type, counterparty_name, total_minor, currency_code,
        opened_on, opening_mode, due_date, reminder_lead_days, notes, status, revision, created_at, updated_at
    ) VALUES (
        v_debt_id, v_user_id, 'PAYABLE', v_counterparty, v_principal, v_currency,
        v_opened_on, v_opening_mode, v_due_date, v_reminder_lead_days, NULLIF(btrim(p_payload->>'notes'), ''),
        'ACTIVE', 1, v_created_at, v_created_at
    );

    IF v_opening_mode = 'NEW_CASH_FLOW' THEN
        v_transaction_id := v_operation_id;
        INSERT INTO public.transactions (
            id, user_id, account_id, transaction_type, operation_kind, amount_minor,
            currency_code, occurred_at, notes, status, revision, created_at, updated_at
        ) VALUES (
            v_transaction_id, v_user_id, v_account_id, 'INCOME', 'DEBT_DISBURSEMENT', v_principal,
            v_currency, (v_opened_on::timestamp AT TIME ZONE 'America/Lima'),
            'Préstamo recibido: ' || v_counterparty, 'CONFIRMED', 1, v_created_at, v_created_at
        );
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
        ) VALUES (
            v_transaction_id, v_user_id, v_account_id, v_principal, v_currency, 'PRIMARY'
        );
        INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
        SELECT v_user_id, 'TRANSACTION', t.id, t.revision, 'UPSERT', to_jsonb(t)
        FROM public.transactions t WHERE t.id = v_transaction_id AND t.user_id = v_user_id;
    END IF;

    v_event_id := extensions.gen_random_uuid();
    INSERT INTO public.debt_events (
        id, user_id, debt_id, transaction_id, installment_id, event_type,
        amount_minor, principal_delta_minor, occurred_at, created_at
    ) VALUES (
        v_event_id, v_user_id, v_debt_id, v_transaction_id, NULL, 'DISBURSEMENT',
        v_principal, 0, (v_opened_on::timestamp AT TIME ZONE 'America/Lima'), v_created_at
    );

    RETURN jsonb_build_object(
        'debt_id', v_debt_id,
        'event_id', v_event_id,
        'transaction_id', v_transaction_id,
        'revision', 1,
        'remaining_minor', v_principal,
        'currency_code', v_currency
    );
END;
$open_payable$;

REVOKE ALL ON FUNCTION private.open_payable_debt_v1(jsonb) FROM PUBLIC, anon, authenticated, service_role;

COMMIT;
