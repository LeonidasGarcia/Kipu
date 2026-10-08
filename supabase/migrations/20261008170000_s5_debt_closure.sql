CREATE OR REPLACE FUNCTION public.close_debt_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $close_debt$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_action text := upper(NULLIF(btrim(p_payload->>'action'), ''));
    v_reason text := NULLIF(btrim(p_payload->>'reason'), '');
    v_amount bigint := NULLIF(p_payload->>'amount_minor', '')::bigint;
    v_delta bigint := NULLIF(p_payload->>'principal_delta_minor', '')::bigint;
    v_request_hash text := lower(NULLIF(p_payload->>'request_hash', ''));
    v_command_payload jsonb := p_payload - 'request_hash' - 'user_id';
    v_receipt internal.command_receipts%ROWTYPE;
    v_debt public.debts%ROWTYPE;
    v_remaining numeric;
    v_remaining_after numeric;
    v_event_id uuid;
    v_event_type text;
    v_event_amount bigint;
    v_principal_delta bigint;
    v_status text;
    v_revision bigint;
    v_now timestamptz := clock_timestamp();
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF jsonb_typeof(p_payload) <> 'object'
       OR NOT p_payload ?& ARRAY['contract_version','operation_id','debt_id','expected_revision','action','request_hash']
       OR EXISTS (
           SELECT 1 FROM jsonb_object_keys(p_payload) AS keys(key)
           WHERE key NOT IN ('contract_version','operation_id','request_hash','user_id','debt_id',
               'expected_revision','action','amount_minor','principal_delta_minor','reason')
       ) THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF COALESCE((p_payload->>'contract_version')::integer, 0) <> 1
       OR v_operation_id IS NULL OR v_debt_id IS NULL
       OR v_expected_revision IS NULL OR v_expected_revision < 1
       OR v_request_hash IS NULL OR v_request_hash !~ '^[0-9a-f]{64}$'
       OR v_action IS NULL OR v_action NOT IN ('SETTLE','CANCEL','ADJUST','FORGIVE') THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF v_action <> 'SETTLE' AND (v_reason IS NULL OR length(v_reason) > 500) THEN
        RAISE EXCEPTION 'REASON_REQUIRED' USING ERRCODE = '22023';
    END IF;
    IF v_action = 'ADJUST' AND (v_delta IS NULL OR v_delta = 0 OR abs(v_delta::numeric) > 99999999999999) THEN
        RAISE EXCEPTION 'INVALID_ADJUSTMENT' USING ERRCODE = '22023';
    END IF;
    IF v_action = 'FORGIVE' AND (v_amount IS NULL OR v_amount NOT BETWEEN 1 AND 99999999999999) THEN
        RAISE EXCEPTION 'INVALID_FORGIVENESS' USING ERRCODE = '22023';
    END IF;
    IF v_action IN ('SETTLE','CANCEL') AND (v_amount IS NOT NULL OR v_delta IS NOT NULL) THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF v_action = 'ADJUST' AND v_amount IS NOT NULL OR v_action = 'FORGIVE' AND v_delta IS NOT NULL THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_COMMAND' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'CLOSE_DEBT'
           OR v_receipt.request_hash <> v_request_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_command_payload THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status','DUPLICATE');
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

    v_remaining_after := v_remaining;
    v_status := 'ACTIVE';
    IF v_action = 'CANCEL' THEN
        v_status := 'CANCELLED';
    ELSIF v_action = 'SETTLE' THEN
        IF v_remaining <> 0 THEN RAISE EXCEPTION 'BALANCE_REMAINS' USING ERRCODE = 'P0001'; END IF;
        v_status := 'SETTLED';
    ELSIF v_action = 'ADJUST' THEN
        v_remaining_after := v_remaining + v_delta::numeric;
        IF v_remaining_after < 0 THEN RAISE EXCEPTION 'ADJUSTMENT_EXCEEDS_BALANCE' USING ERRCODE = 'P0001'; END IF;
        v_event_type := 'ADJUSTMENT';
        v_event_amount := abs(v_delta);
        v_principal_delta := v_delta;
        IF v_remaining_after = 0 THEN v_status := 'SETTLED'; END IF;
    ELSIF v_action = 'FORGIVE' THEN
        IF v_amount::numeric > v_remaining THEN RAISE EXCEPTION 'FORGIVENESS_EXCEEDS_BALANCE' USING ERRCODE = 'P0001'; END IF;
        v_remaining_after := v_remaining - v_amount::numeric;
        v_event_type := 'FORGIVENESS';
        v_event_amount := v_amount;
        v_principal_delta := -v_amount;
        IF v_remaining_after = 0 THEN v_status := 'SETTLED'; END IF;
    END IF;

    IF v_event_type IS NOT NULL THEN
        v_event_id := md5('kipu:debt-closure-event:' || v_operation_id::text)::uuid;
        INSERT INTO public.debt_events(
            id,user_id,debt_id,event_type,amount_minor,principal_delta_minor,occurred_at,created_at
        ) VALUES (v_event_id,v_user_id,v_debt_id,v_event_type,v_event_amount,v_principal_delta,v_now,v_now);
        INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
        SELECT v_user_id,'DEBT_EVENT',e.id,1,'UPSERT',to_jsonb(e)
        FROM public.debt_events e WHERE e.user_id=v_user_id AND e.id=v_event_id;
    END IF;

    IF v_action IN ('CANCEL','ADJUST','FORGIVE') OR v_status <> 'ACTIVE' THEN
        UPDATE public.debt_installments
        SET status='CANCELLED',revision=revision+1,updated_at=v_now
        WHERE user_id=v_user_id AND debt_id=v_debt_id AND status IN ('PENDING','PARTIAL') AND deleted_at IS NULL;
        UPDATE public.debts SET reminder_lead_days=NULL
        WHERE user_id=v_user_id AND id=v_debt_id;
        INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
        SELECT v_user_id,'DEBT_INSTALLMENT',i.id,i.revision,'UPSERT',to_jsonb(i)
        FROM public.debt_installments i
        WHERE i.user_id=v_user_id AND i.debt_id=v_debt_id AND i.status='CANCELLED' AND i.updated_at=v_now;
    END IF;

    v_revision := v_debt.revision + 1;
    UPDATE public.debts SET status=v_status,revision=v_revision,updated_at=v_now
    WHERE user_id=v_user_id AND id=v_debt_id;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    SELECT v_user_id,'DEBT',d.id,d.revision,'UPSERT',to_jsonb(d)
    FROM public.debts d WHERE d.user_id=v_user_id AND d.id=v_debt_id;

    v_response := jsonb_build_object('status','APPLIED','operation_id',v_operation_id,
        'action',v_action,'debt_id',v_debt_id,'revision',v_revision,
        'remaining_minor',v_remaining_after,'cash_delta_minor',0,'event_id',v_event_id,
        'reason',v_reason);
    INSERT INTO internal.command_receipts(
        user_id,idempotency_key,command_type,request_hash,response_payload,status,command_payload
    ) VALUES (v_user_id,v_operation_id::text,'CLOSE_DEBT',v_request_hash,v_response,'APPLIED',v_command_payload);
    RETURN v_response;
EXCEPTION
    WHEN numeric_value_out_of_range OR invalid_text_representation OR invalid_datetime_format THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_COMMAND' USING ERRCODE = '22023';
    WHEN check_violation OR foreign_key_violation THEN
        RAISE EXCEPTION 'INVALID_DEBT_CLOSURE_REFERENCE' USING ERRCODE = '22023';
END;
$close_debt$;

REVOKE ALL ON FUNCTION public.close_debt_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.close_debt_v1(jsonb) TO authenticated;
