-- PostgreSQL applies the same whitespace/case/diacritic normalization as Android.
CREATE EXTENSION unaccent WITH SCHEMA extensions;

CREATE OR REPLACE FUNCTION public.upsert_merchant_alias_rule_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_rule_id uuid := (p_payload->>'rule_id')::uuid;
    v_merchant_id uuid := (p_payload->>'merchant_id')::uuid;
    v_input_pattern text := p_payload->>'normalized_pattern';
    v_pattern text;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_current public.merchant_alias_rules%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000';
    END IF;
    IF v_operation_id IS NULL OR v_rule_id IS NULL OR v_merchant_id IS NULL
       OR v_input_pattern IS NULL OR pg_catalog.length(pg_catalog.btrim(v_input_pattern)) = 0
       OR pg_catalog.length(v_input_pattern) > 160 THEN
        RAISE EXCEPTION 'INVALID_ALIAS_RULE' USING ERRCODE = 'P0001';
    END IF;

    v_pattern := pg_catalog.lower(extensions.unaccent(
        pg_catalog.regexp_replace(pg_catalog.btrim(v_input_pattern), '[[:space:]]+', ' ', 'g')
    ));
    IF v_pattern <> v_input_pattern THEN
        RAISE EXCEPTION 'ALIAS_PATTERN_NOT_NORMALIZED' USING ERRCODE = 'P0001';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.merchant_services AS m
        WHERE m.id = v_merchant_id AND m.is_system = true AND m.user_id IS NULL
          AND m.is_active = true AND m.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'MERCHANT_NOT_ACTIVE' USING ERRCODE = 'P0001';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-alias:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'UPSERT_MERCHANT_ALIAS_RULE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;

    SELECT * INTO v_current FROM public.merchant_alias_rules AS r
    WHERE r.id = v_rule_id AND r.user_id = v_user_id FOR UPDATE;
    IF FOUND THEN
        IF v_expected_revision IS NULL OR v_current.revision <> v_expected_revision THEN
            RETURN pg_catalog.jsonb_build_object(
                'success', false, 'status', 'CONFLICT', 'rule_id', v_rule_id,
                'current_revision', v_current.revision
            );
        END IF;
        v_revision := v_current.revision + 1;
        UPDATE public.merchant_alias_rules
        SET normalized_pattern = v_pattern, merchant_id = v_merchant_id,
            revision = v_revision, deleted_at = NULL, updated_at = pg_catalog.now()
        WHERE id = v_rule_id AND user_id = v_user_id;
    ELSE
        IF v_expected_revision IS NOT NULL THEN
            RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'REJECTED', 'code', 'RULE_NOT_FOUND');
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM public.billing_purchases AS p
            JOIN public.billing_products AS product ON product.id = p.product_id
            WHERE p.user_id = v_user_id
              AND (
                (p.purchase_state = 'PURCHASED' AND product.plan_type = 'PRO_LIFETIME'
                    AND p.entitlement_state = 'ACTIVE' AND p.expires_at IS NULL)
                OR (product.plan_type IN ('PRO_MONTHLY', 'PRO_ANNUAL')
                    AND p.expires_at > pg_catalog.now()
                    AND ((p.purchase_state = 'PURCHASED' AND p.entitlement_state IN ('ACTIVE', 'IN_GRACE_PERIOD'))
                         OR (p.purchase_state = 'CANCELLED' AND p.entitlement_state = 'CANCELED_ACTIVE')))
              )
        ) THEN
            RAISE EXCEPTION 'PREMIUM_REQUIRED' USING ERRCODE = 'P0001';
        END IF;
        v_revision := 1;
        INSERT INTO public.merchant_alias_rules (
            id, user_id, normalized_pattern, merchant_id, revision
        ) VALUES (v_rule_id, v_user_id, v_pattern, v_merchant_id, v_revision);
    END IF;

    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'rule_id', v_rule_id, 'revision', v_revision
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'UPSERT_MERCHANT_ALIAS_RULE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_ALIAS_RULE', v_rule_id, v_revision, 'UPSERT',
        pg_catalog.jsonb_build_object(
            'id', v_rule_id, 'normalized_pattern', v_pattern, 'merchant_id', v_merchant_id,
            'revision', v_revision, 'deleted_at', NULL
        )
    );
    RETURN v_response;
END;
$function$;

CREATE OR REPLACE FUNCTION public.delete_merchant_alias_rule_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_rule_id uuid := (p_payload->>'rule_id')::uuid;
    v_expected_revision bigint := (p_payload->>'expected_revision')::bigint;
    v_current public.merchant_alias_rules%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000'; END IF;
    IF v_operation_id IS NULL OR v_rule_id IS NULL OR v_expected_revision IS NULL THEN
        RAISE EXCEPTION 'INVALID_ALIAS_DELETE' USING ERRCODE = 'P0001';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-alias:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'DELETE_MERCHANT_ALIAS_RULE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;
    SELECT * INTO v_current FROM public.merchant_alias_rules AS r
    WHERE r.id = v_rule_id AND r.user_id = v_user_id FOR UPDATE;
    IF NOT FOUND OR v_current.revision <> v_expected_revision OR v_current.deleted_at IS NOT NULL THEN
        RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;
    v_revision := v_current.revision + 1;
    UPDATE public.merchant_alias_rules SET revision = v_revision, deleted_at = pg_catalog.now(),
        updated_at = pg_catalog.now() WHERE id = v_rule_id AND user_id = v_user_id;
    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'rule_id', v_rule_id, 'revision', v_revision
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'DELETE_MERCHANT_ALIAS_RULE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_ALIAS_RULE', v_rule_id, v_revision, 'DELETE',
        pg_catalog.jsonb_build_object('id', v_rule_id, 'revision', v_revision, 'deleted_at', pg_catalog.now())
    );
    RETURN v_response;
END;
$function$;

CREATE OR REPLACE FUNCTION public.preserve_merchant_source_text_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_movement_id uuid := (p_payload->>'movement_id')::uuid;
    v_raw_text text := p_payload->>'merchant_raw_text';
    v_existing_text text;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000'; END IF;
    IF v_operation_id IS NULL OR v_movement_id IS NULL OR v_raw_text IS NULL OR v_raw_text = '' THEN
        RAISE EXCEPTION 'INVALID_SOURCE_TEXT' USING ERRCODE = 'P0001';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-source:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'PRESERVE_MERCHANT_SOURCE_TEXT'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;
    SELECT merchant_raw_text INTO v_existing_text FROM public.financial_movements
    WHERE id = v_movement_id AND user_id = v_user_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'MOVEMENT_NOT_FOUND' USING ERRCODE = 'P0001'; END IF;
    IF v_existing_text IS NOT NULL AND v_existing_text <> v_raw_text THEN
        RAISE EXCEPTION 'SOURCE_TEXT_IMMUTABLE' USING ERRCODE = 'P0001';
    END IF;
    UPDATE public.financial_movements SET merchant_raw_text = v_raw_text
    WHERE id = v_movement_id AND user_id = v_user_id AND merchant_raw_text IS NULL;
    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'movement_id', v_movement_id
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'PRESERVE_MERCHANT_SOURCE_TEXT', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MOVEMENT_SOURCE_TEXT', v_movement_id, 1, 'UPSERT',
        pg_catalog.jsonb_build_object('movement_id', v_movement_id, 'merchant_raw_text', v_raw_text)
    );
    RETURN v_response;
END;
$function$;

REVOKE ALL ON FUNCTION public.upsert_merchant_alias_rule_v1(jsonb) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.delete_merchant_alias_rule_v1(jsonb) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.preserve_merchant_source_text_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_merchant_alias_rule_v1(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_merchant_alias_rule_v1(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.preserve_merchant_source_text_v1(jsonb) TO authenticated;
