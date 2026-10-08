BEGIN;

CREATE OR REPLACE FUNCTION public.edit_debt_details_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $edit_debt$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_request_hash text := lower(NULLIF(p_payload->>'request_hash', ''));
    v_payload jsonb := p_payload - 'request_hash' - 'user_id';
    v_receipt internal.command_receipts%ROWTYPE;
    v_debt public.debts%ROWTYPE;
    v_counterparty text := regexp_replace(btrim(COALESCE(p_payload->>'counterparty_name', '')), '[[:space:]]+', ' ', 'g');
    v_due_date date := NULLIF(p_payload->>'due_date', '')::date;
    v_reminder_lead_days smallint := NULLIF(p_payload->>'reminder_lead_days', '')::smallint;
    v_notes text := NULLIF(btrim(p_payload->>'notes'), '');
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000'; END IF;
    IF v_operation_id IS NULL OR v_debt_id IS NULL OR v_expected_revision IS NULL
       OR v_request_hash IS NULL OR v_request_hash !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'INVALID_DEBT_EDIT_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF v_counterparty = '' THEN RAISE EXCEPTION 'COUNTERPARTY_REQUIRED' USING ERRCODE = '22023'; END IF;
    IF v_reminder_lead_days IS NOT NULL AND v_reminder_lead_days NOT BETWEEN 0 AND 365 THEN
        RAISE EXCEPTION 'INVALID_REMINDER_LEAD_DAYS' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'EDIT_DEBT_DETAILS' OR v_receipt.request_hash <> v_request_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_payload THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status', 'DUPLICATE');
    END IF;

    SELECT * INTO v_debt FROM public.debts
    WHERE id = v_debt_id AND user_id = v_user_id AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'DEBT_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_debt.revision <> v_expected_revision THEN
        RETURN jsonb_build_object('status', 'CONFLICT', 'debt_id', v_debt_id, 'current_revision', v_debt.revision);
    END IF;

    UPDATE public.debts
    SET counterparty_name = v_counterparty,
        due_date = v_due_date,
        reminder_lead_days = v_reminder_lead_days,
        notes = v_notes,
        revision = revision + 1,
        updated_at = clock_timestamp()
    WHERE id = v_debt_id AND user_id = v_user_id
    RETURNING * INTO v_debt;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'DEBT', v_debt_id, v_debt.revision, 'UPSERT', to_jsonb(v_debt));
    v_response := jsonb_build_object('status', 'APPLIED', 'debt_id', v_debt_id, 'revision', v_debt.revision);
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status, command_payload
    ) VALUES (
        v_user_id, v_operation_id::text, 'EDIT_DEBT_DETAILS', v_request_hash, v_response, 'APPLIED', v_payload
    );
    RETURN v_response;
END;
$edit_debt$;

CREATE OR REPLACE FUNCTION public.delete_debt_if_unreferenced_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $delete_debt$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_request_hash text := lower(NULLIF(p_payload->>'request_hash', ''));
    v_payload jsonb := p_payload - 'request_hash' - 'user_id';
    v_receipt internal.command_receipts%ROWTYPE;
    v_debt public.debts%ROWTYPE;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000'; END IF;
    IF v_operation_id IS NULL OR v_debt_id IS NULL OR v_request_hash IS NULL
       OR v_request_hash !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'INVALID_DEBT_DELETE_COMMAND' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'DELETE_DEBT_IF_UNREFERENCED' OR v_receipt.request_hash <> v_request_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_payload THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status', 'DUPLICATE');
    END IF;

    SELECT * INTO v_debt FROM public.debts
    WHERE id = v_debt_id AND user_id = v_user_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'DEBT_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF EXISTS (
        SELECT 1 FROM public.debt_events e
        WHERE e.user_id = v_user_id AND e.debt_id = v_debt_id
          AND (e.event_type <> 'DISBURSEMENT' OR e.transaction_id IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'HISTORY_PRESERVED' USING ERRCODE = 'P0001';
    END IF;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'DEBT', v_debt_id, v_debt.revision + 1, 'DELETE',
        jsonb_build_object('id', v_debt_id, 'user_id', v_user_id, 'revision', v_debt.revision + 1)
    );
    DELETE FROM public.debt_events
    WHERE user_id = v_user_id AND debt_id = v_debt_id
      AND event_type = 'DISBURSEMENT' AND transaction_id IS NULL;
    DELETE FROM public.debt_installments WHERE user_id = v_user_id AND debt_id = v_debt_id;
    DELETE FROM public.debts WHERE id = v_debt_id AND user_id = v_user_id;

    v_response := jsonb_build_object('status', 'APPLIED', 'debt_id', v_debt_id, 'result', 'DELETED');
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status, command_payload
    ) VALUES (
        v_user_id, v_operation_id::text, 'DELETE_DEBT_IF_UNREFERENCED', v_request_hash,
        v_response, 'APPLIED', v_payload
    );
    RETURN v_response;
END;
$delete_debt$;

REVOKE ALL ON FUNCTION public.edit_debt_details_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.edit_debt_details_v1(jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.delete_debt_if_unreferenced_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_debt_if_unreferenced_v1(jsonb) TO authenticated;
REVOKE DELETE ON public.debts, public.debt_installments, public.debt_events FROM authenticated;

COMMIT;
