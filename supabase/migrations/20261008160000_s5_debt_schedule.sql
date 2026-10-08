CREATE OR REPLACE FUNCTION public.set_debt_schedule_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $set_debt_schedule$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_reminder_lead_days smallint;
    v_cancel_schedule boolean := COALESCE((p_payload->>'cancel_schedule')::boolean, false);
    v_installments jsonb := p_payload->'installments';
    v_request_hash text := lower(NULLIF(p_payload->>'request_hash', ''));
    v_command_payload jsonb := p_payload - 'request_hash' - 'user_id';
    v_receipt internal.command_receipts%ROWTYPE;
    v_debt public.debts%ROWTYPE;
    v_remaining numeric;
    v_total numeric;
    v_count integer;
    v_distinct_ids integer;
    v_distinct_numbers integer;
    v_max_number integer;
    v_first_due_date date;
    v_now timestamptz := clock_timestamp();
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF jsonb_typeof(p_payload) <> 'object'
       OR NOT p_payload ?& ARRAY['contract_version','operation_id','debt_id','expected_revision',
           'reminder_lead_days','cancel_schedule','installments','request_hash']
       OR EXISTS (
           SELECT 1 FROM jsonb_object_keys(p_payload) AS keys(key)
           WHERE key NOT IN ('contract_version','operation_id','request_hash','user_id','debt_id',
               'expected_revision','reminder_lead_days','cancel_schedule','installments')
       ) THEN
        RAISE EXCEPTION 'INVALID_DEBT_SCHEDULE_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF COALESCE((p_payload->>'contract_version')::integer, 0) <> 1
       OR v_operation_id IS NULL OR v_debt_id IS NULL
       OR v_expected_revision IS NULL OR v_expected_revision < 1
       OR v_request_hash IS NULL OR v_request_hash !~ '^[0-9a-f]{64}$'
       OR jsonb_typeof(v_installments) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'INVALID_DEBT_SCHEDULE_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF jsonb_typeof(p_payload->'reminder_lead_days') = 'null' THEN
        v_reminder_lead_days := NULL;
    ELSE
        v_reminder_lead_days := NULLIF(p_payload->>'reminder_lead_days', '')::smallint;
        IF v_reminder_lead_days NOT BETWEEN 0 AND 365 THEN
            RAISE EXCEPTION 'INVALID_REMINDER_LEAD_DAYS' USING ERRCODE = '22023';
        END IF;
    END IF;
    IF v_cancel_schedule AND (jsonb_array_length(v_installments) <> 0 OR v_reminder_lead_days IS NOT NULL) THEN
        RAISE EXCEPTION 'INVALID_SCHEDULE_CANCELLATION' USING ERRCODE = '22023';
    END IF;
    IF NOT v_cancel_schedule AND jsonb_array_length(v_installments) NOT BETWEEN 1 AND 120 THEN
        RAISE EXCEPTION 'INVALID_INSTALLMENT_COUNT' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'SET_DEBT_SCHEDULE'
           OR v_receipt.request_hash <> v_request_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_command_payload THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status', 'DUPLICATE');
    END IF;

    SELECT * INTO v_debt FROM public.debts
    WHERE id = v_debt_id AND user_id = v_user_id AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN
        IF EXISTS (SELECT 1 FROM public.debts WHERE id = v_debt_id AND deleted_at IS NULL) THEN
            RAISE EXCEPTION 'DEBT_NOT_OWNED' USING ERRCODE = '42501';
        END IF;
        RAISE EXCEPTION 'DEBT_NOT_FOUND' USING ERRCODE = 'P0002';
    END IF;
    IF v_debt.revision <> v_expected_revision THEN
        RETURN jsonb_build_object('status','CONFLICT','debt_id',v_debt_id,
            'current_revision',v_debt.revision,'error',jsonb_build_object('code','DEBT_REVISION_CONFLICT'));
    END IF;
    IF v_debt.status <> 'ACTIVE' THEN RAISE EXCEPTION 'DEBT_NOT_ACTIVE' USING ERRCODE = 'P0001'; END IF;

    SELECT GREATEST(0::numeric, v_debt.total_minor::numeric + COALESCE(sum(
        CASE
            WHEN e.principal_delta_minor IS NOT NULL THEN e.principal_delta_minor::numeric
            WHEN e.event_type = 'PAYMENT' THEN -e.amount_minor::numeric
            ELSE 0::numeric
        END
    ), 0::numeric)) INTO v_remaining
    FROM public.debt_events e
    LEFT JOIN public.transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
    WHERE e.user_id = v_user_id AND e.debt_id = v_debt_id
      AND (e.transaction_id IS NULL OR t.status <> 'VOIDED');

    IF v_cancel_schedule THEN
        v_count := 0;
        v_total := 0;
    ELSE
        SELECT count(*)::integer, count(DISTINCT i.id)::integer,
               count(DISTINCT i.installment_number)::integer,
               COALESCE(sum(i.principal_minor::numeric), 0::numeric), max(i.installment_number)::integer
        INTO v_count, v_distinct_ids, v_distinct_numbers, v_total, v_max_number
        FROM jsonb_to_recordset(v_installments) AS i(
            id uuid, installment_number integer, due_date date, principal_minor bigint
        );
        IF EXISTS (
            SELECT 1 FROM jsonb_to_recordset(v_installments) AS i(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            )
            WHERE i.id IS NULL OR i.installment_number IS NULL OR i.installment_number < 1
               OR i.installment_number > 32767 OR i.due_date IS NULL
               OR i.principal_minor IS NULL OR i.principal_minor NOT BETWEEN 1 AND 99999999999999
        ) OR v_count <> v_distinct_ids OR v_count <> v_distinct_numbers THEN
            RAISE EXCEPTION 'INVALID_INSTALLMENT' USING ERRCODE = '22023';
        END IF;
        IF v_max_number - v_count + 1 < 1 OR EXISTS (
            SELECT 1 FROM jsonb_to_recordset(v_installments) AS i(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            ) WHERE i.installment_number NOT BETWEEN v_max_number - v_count + 1 AND v_max_number
        ) THEN
            RAISE EXCEPTION 'INVALID_INSTALLMENT_SEQUENCE' USING ERRCODE = '22023';
        END IF;
        SELECT max(i.installment_number)::integer INTO v_max_number
        FROM public.debt_installments i WHERE i.user_id = v_user_id AND i.debt_id = v_debt_id;
        IF EXISTS (
            SELECT 1 FROM jsonb_to_recordset(v_installments) AS incoming(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            )
            WHERE incoming.installment_number <= COALESCE(v_max_number, 0)
        ) THEN
            RAISE EXCEPTION 'INSTALLMENT_SEQUENCE_REUSED' USING ERRCODE = '22023';
        END IF;
        IF EXISTS (
            SELECT 1 FROM jsonb_to_recordset(v_installments) AS incoming(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            )
            JOIN public.debt_installments existing ON existing.id = incoming.id
            WHERE existing.user_id <> v_user_id OR existing.debt_id <> v_debt_id
        ) THEN
            RAISE EXCEPTION 'INSTALLMENT_NOT_OWNED' USING ERRCODE = '42501';
        END IF;
        IF EXISTS (
            SELECT 1 FROM jsonb_to_recordset(v_installments) AS incoming(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            )
            JOIN public.debt_installments existing ON existing.user_id = v_user_id
                AND existing.debt_id = v_debt_id AND existing.id = incoming.id
        ) THEN
            RAISE EXCEPTION 'INSTALLMENT_ID_REUSED' USING ERRCODE = '22023';
        END IF;
        IF v_total <> v_remaining THEN
            RAISE EXCEPTION 'SCHEDULE_TOTAL_MISMATCH' USING ERRCODE = '22023';
        END IF;
        SELECT i.due_date INTO v_first_due_date
        FROM jsonb_to_recordset(v_installments) AS i(
            id uuid, installment_number integer, due_date date, principal_minor bigint
        ) ORDER BY i.installment_number LIMIT 1;
        IF EXISTS (
            SELECT 1
            FROM jsonb_array_elements(v_installments) WITH ORDINALITY AS entry(value, ordinal)
            CROSS JOIN LATERAL jsonb_to_record(entry.value) AS i(
                id uuid, installment_number integer, due_date date, principal_minor bigint
            )
            WHERE i.due_date <> (v_first_due_date + ((entry.ordinal - 1)::text || ' months')::interval)::date
        ) THEN
            RAISE EXCEPTION 'INVALID_INSTALLMENT_DATES' USING ERRCODE = '22023';
        END IF;
    END IF;

    UPDATE public.debt_installments
    SET status = 'CANCELLED', revision = revision + 1, updated_at = v_now
    WHERE user_id = v_user_id AND debt_id = v_debt_id AND status IN ('PENDING', 'PARTIAL') AND deleted_at IS NULL;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT_INSTALLMENT', i.id, i.revision, 'UPSERT', to_jsonb(i)
    FROM public.debt_installments i
    WHERE i.user_id = v_user_id AND i.debt_id = v_debt_id
      AND i.status = 'CANCELLED' AND i.updated_at = v_now;

    IF NOT v_cancel_schedule THEN
        INSERT INTO public.debt_installments(
            id, user_id, debt_id, installment_number, due_date, amount_minor, status, revision, created_at, updated_at
        )
        SELECT i.id, v_user_id, v_debt_id, i.installment_number::smallint, i.due_date,
               i.principal_minor, 'PENDING', 1, v_now, v_now
        FROM jsonb_to_recordset(v_installments) AS i(
            id uuid, installment_number integer, due_date date, principal_minor bigint
        );
    END IF;

    v_revision := v_debt.revision + 1;
    UPDATE public.debts SET reminder_lead_days = v_reminder_lead_days,
        revision = v_revision, updated_at = v_now
    WHERE user_id = v_user_id AND id = v_debt_id;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT_INSTALLMENT', i.id, i.revision, 'UPSERT', to_jsonb(i)
    FROM public.debt_installments i
    WHERE i.user_id = v_user_id AND i.debt_id = v_debt_id AND i.created_at = v_now;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT', d.id, d.revision, 'UPSERT', to_jsonb(d)
    FROM public.debts d WHERE d.user_id = v_user_id AND d.id = v_debt_id;

    v_response := jsonb_build_object('status','APPLIED','operation_id',v_operation_id,
        'debt_id',v_debt_id,'revision',v_revision,'remaining_minor',v_remaining,
        'installments_count',v_count,'cash_delta_minor',0);
    INSERT INTO internal.command_receipts(
        user_id,idempotency_key,command_type,request_hash,response_payload,status,command_payload
    ) VALUES (v_user_id,v_operation_id::text,'SET_DEBT_SCHEDULE',v_request_hash,v_response,'APPLIED',v_command_payload);
    RETURN v_response;
EXCEPTION
    WHEN numeric_value_out_of_range OR invalid_text_representation OR invalid_datetime_format OR datetime_field_overflow THEN
        RAISE EXCEPTION 'INVALID_DEBT_SCHEDULE_COMMAND' USING ERRCODE = '22023';
    WHEN check_violation OR foreign_key_violation THEN
        RAISE EXCEPTION 'INVALID_DEBT_SCHEDULE_REFERENCE' USING ERRCODE = '22023';
END;
$set_debt_schedule$;

REVOKE ALL ON FUNCTION public.set_debt_schedule_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_debt_schedule_v1(jsonb) TO authenticated;
