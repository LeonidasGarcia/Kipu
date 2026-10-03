-- T093: versioned hash validation with byte-compatible legacy command dispatch.
-- Preserve the full S3 registration chain behind a closed internal endpoint.
ALTER FUNCTION public.register_transaction_v1(jsonb) SET SCHEMA internal;
ALTER FUNCTION internal.register_transaction_v1(jsonb) RENAME TO register_transaction_pre_hash_v2;
REVOKE ALL ON FUNCTION internal.register_transaction_pre_hash_v2(jsonb) FROM PUBLIC, anon, authenticated, service_role;
ALTER TABLE internal.command_receipts ADD COLUMN contract_version smallint NOT NULL DEFAULT 1;
ALTER TABLE internal.command_receipts ADD COLUMN command_payload jsonb;

CREATE OR REPLACE FUNCTION internal.movement_register_hash_v2(p_owner uuid, p_tx jsonb)
RETURNS text LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$
    SELECT pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
        pg_catalog.array_to_json(ARRAY[
            'MOV_REGISTER_V2', p_owner::text, pg_catalog.upper(p_tx->>'type'),
            ((p_tx->>'amount_minor')::bigint)::text, pg_catalog.upper(p_tx->>'currency_code'),
            ((p_tx->>'source_account_id')::uuid)::text,
            ((p_tx->>'destination_account_id')::uuid)::text,
            ((p_tx->>'category_id')::uuid)::text, ((p_tx->>'merchant_id')::uuid)::text,
            p_tx->>'merchant_provisional_text',
            ((extract(epoch from (p_tx->>'occurred_at')::timestamptz) * 1000)::bigint)::text,
            p_tx->>'note'
        ]::text[])::text, 'UTF8')), 'hex');
$$;
REVOKE ALL ON FUNCTION internal.movement_register_hash_v2(uuid,jsonb) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.register_transaction_v2(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    v_owner uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_hash text;
    v_timestamp timestamptz;
    v_field text;
    v_response jsonb;
    v_existing_version smallint;
BEGIN
    IF v_owner IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED','retryable',false));
    END IF;
    IF jsonb_typeof(p_command) IS DISTINCT FROM 'object'
       OR p_command->'contract_version' IS DISTINCT FROM '2'::jsonb
       OR jsonb_typeof(v_tx) IS DISTINCT FROM 'object'
       OR (p_command - ARRAY['contract_version','idempotency_key','request_hash','transaction']) <> '{}'::jsonb
       OR (v_tx - ARRAY['id','type','amount_minor','currency_code','source_account_id',
            'destination_account_id','category_id','merchant_id','merchant_provisional_text','occurred_at','note']) <> '{}'::jsonb
       OR jsonb_typeof(p_command->'idempotency_key') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_command->'request_hash') IS DISTINCT FROM 'string'
       OR (p_command->>'request_hash') !~ '^[0-9a-f]{64}$'
       OR jsonb_typeof(v_tx->'amount_minor') IS DISTINCT FROM 'number'
       OR (v_tx->>'amount_minor') !~ '^[1-9][0-9]*$'
       OR jsonb_typeof(v_tx->'occurred_at') IS DISTINCT FROM 'string'
       OR (v_tx->>'occurred_at') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$'
       OR upper(v_tx->>'type') NOT IN ('EXPENSE','INCOME','TRANSFER') THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END IF;
    FOREACH v_field IN ARRAY ARRAY['id','type','currency_code'] LOOP
        IF jsonb_typeof(v_tx->v_field) IS DISTINCT FROM 'string' THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','field',v_field,'retryable',false));
        END IF;
    END LOOP;
    FOREACH v_field IN ARRAY ARRAY['source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text','note'] LOOP
        IF v_tx ? v_field AND jsonb_typeof(v_tx->v_field) NOT IN ('string','null') THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','field',v_field,'retryable',false));
        END IF;
    END LOOP;
    -- Only canonical UUID spelling is accepted by v2; Kotlin lowercases these same strings.
    FOREACH v_field IN ARRAY ARRAY['id','source_account_id','destination_account_id','category_id','merchant_id'] LOOP
        IF v_tx->>v_field IS NOT NULL AND (v_tx->>v_field) !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_UUID','field',v_field,'retryable',false));
        END IF;
    END LOOP;
    IF (p_command->>'idempotency_key') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_UUID','field','idempotency_key','retryable',false));
    END IF;
    -- UUID validation and timestamp precision precede any financial write.
    PERFORM (p_command->>'idempotency_key')::uuid, (v_tx->>'id')::uuid;
    v_timestamp := (v_tx->>'occurred_at')::timestamptz;
    IF extract(epoch from v_timestamp) * 1000 <> trunc(extract(epoch from v_timestamp) * 1000) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TIMESTAMP_PRECISION','retryable',false));
    END IF;
    v_hash := internal.movement_register_hash_v2(v_owner,v_tx);
    IF v_hash IS DISTINCT FROM p_command->>'request_hash' THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REQUEST_HASH','retryable',false));
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(v_owner::text || ':' || (p_command->>'idempotency_key'),0));
    SELECT contract_version INTO v_existing_version FROM internal.command_receipts
        WHERE user_id=v_owner AND idempotency_key=p_command->>'idempotency_key';
    IF FOUND AND v_existing_version <> 2 THEN
        RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','CONTRACT_VERSION_CONFLICT','retryable',false));
    END IF;
    -- Preserve the original signed request, including null/empty and raw provisional text.
    -- The existing v1 domain normalizes provisional merchant text when storing the movement.
    v_response := internal.register_transaction_pre_hash_v2(jsonb_set(p_command,'{contract_version}','1'::jsonb));
    IF v_response->>'status' IN ('APPLIED','DUPLICATE') THEN
        UPDATE internal.command_receipts SET contract_version=2,command_payload=COALESCE(command_payload,p_command)
            WHERE user_id=v_owner AND idempotency_key=p_command->>'idempotency_key' AND request_hash=v_hash;
    END IF;
    RETURN v_response;
EXCEPTION
    WHEN invalid_text_representation OR datetime_field_overflow OR numeric_value_out_of_range THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
END;
$$;
REVOKE ALL ON FUNCTION public.register_transaction_v2(jsonb) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.register_transaction_v2(jsonb) TO authenticated;


-- Route any declared v2 command through v2 validation, even at the historical endpoint.
CREATE OR REPLACE FUNCTION public.register_transaction_v1(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    v_owner uuid := auth.uid();
    v_version integer := COALESCE((p_command->>'contract_version')::integer,1);
    v_existing_version smallint;
BEGIN
    IF v_version=2 THEN RETURN public.register_transaction_v2(p_command); END IF;
    IF v_version<>1 THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_CONTRACT_VERSION','retryable',false));
    END IF;
    IF v_owner IS NOT NULL AND p_command->>'idempotency_key' IS NOT NULL THEN
        PERFORM pg_advisory_xact_lock(hashtextextended(v_owner::text || ':' || (p_command->>'idempotency_key'),0));
        SELECT contract_version INTO v_existing_version FROM internal.command_receipts
            WHERE user_id=v_owner AND idempotency_key=p_command->>'idempotency_key';
        IF FOUND AND v_existing_version<>1 THEN
            RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','CONTRACT_VERSION_CONFLICT','retryable',false));
        END IF;
    END IF;
    RETURN internal.register_transaction_pre_hash_v2(p_command);
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
END;
$$;
REVOKE ALL ON FUNCTION public.register_transaction_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_transaction_v1(jsonb) TO authenticated,service_role;
