CREATE OR REPLACE FUNCTION public.upsert_merchant_category_preference_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_preference_id uuid := (p_payload->>'preference_id')::uuid;
    v_merchant_id uuid := (p_payload->>'merchant_id')::uuid;
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_category public.categories%ROWTYPE;
    v_root public.categories%ROWTYPE;
    v_current public.merchant_category_preferences%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000';
    END IF;
    IF v_operation_id IS NULL OR v_preference_id IS NULL OR v_merchant_id IS NULL OR v_category_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_CATEGORY_PREFERENCE' USING ERRCODE = 'P0001';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.merchant_services AS m
        WHERE m.id = v_merchant_id AND m.is_system = true AND m.user_id IS NULL
          AND m.is_active = true AND m.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'MERCHANT_NOT_ACTIVE' USING ERRCODE = 'P0001';
    END IF;

    SELECT * INTO v_category FROM public.categories AS c
    WHERE c.id = v_category_id
      AND (c.user_id = v_user_id OR (c.user_id IS NULL AND c.origin = 'SYSTEM'));
    IF NOT FOUND OR NOT v_category.is_active OR v_category.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'CATEGORY_NOT_ACTIVE_OR_OWNED' USING ERRCODE = 'P0001';
    END IF;
    IF v_category.parent_id IS NULL THEN
        v_root := v_category;
    ELSE
        SELECT * INTO v_root FROM public.categories AS root
        WHERE root.id = v_category.parent_id
          AND (root.user_id = v_user_id OR (root.user_id IS NULL AND root.origin = 'SYSTEM'));
        IF NOT FOUND OR NOT v_root.is_active OR v_root.deleted_at IS NOT NULL OR v_root.parent_id IS NOT NULL THEN
            RAISE EXCEPTION 'CATEGORY_ROOT_NOT_ACTIVE_OR_OWNED' USING ERRCODE = 'P0001';
        END IF;
    END IF;
    IF private.is_plan_quota_resource_locked(v_user_id, 'CUSTOM_CATEGORIES', v_root.id) THEN
        RAISE EXCEPTION 'CATEGORY_PLAN_LOCKED' USING ERRCODE = 'P0001';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-preference:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'UPSERT_MERCHANT_CATEGORY_PREFERENCE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;

    SELECT * INTO v_current FROM public.merchant_category_preferences AS p
    WHERE p.user_id = v_user_id AND p.merchant_id = v_merchant_id FOR UPDATE;
    IF FOUND THEN
        IF v_current.id <> v_preference_id OR v_expected_revision IS NULL
           OR v_current.revision <> v_expected_revision THEN
            RETURN pg_catalog.jsonb_build_object(
                'success', false, 'status', 'CONFLICT', 'preference_id', v_current.id,
                'current_revision', v_current.revision
            );
        END IF;
        v_revision := v_current.revision + 1;
        UPDATE public.merchant_category_preferences
        SET category_id = v_category_id, revision = v_revision,
            deleted_at = NULL, updated_at = pg_catalog.now()
        WHERE id = v_current.id AND user_id = v_user_id;
    ELSE
        IF v_expected_revision IS NOT NULL THEN
            RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'REJECTED', 'code', 'PREFERENCE_NOT_FOUND');
        END IF;
        v_revision := 1;
        INSERT INTO public.merchant_category_preferences (
            id, user_id, merchant_id, category_id, revision
        ) VALUES (v_preference_id, v_user_id, v_merchant_id, v_category_id, v_revision);
    END IF;

    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'preference_id', v_preference_id,
        'merchant_id', v_merchant_id, 'category_id', v_category_id, 'revision', v_revision
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'UPSERT_MERCHANT_CATEGORY_PREFERENCE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_CATEGORY_PREFERENCE', v_preference_id, v_revision, 'UPSERT',
        pg_catalog.jsonb_build_object(
            'id', v_preference_id, 'merchant_id', v_merchant_id, 'category_id', v_category_id,
            'revision', v_revision, 'deleted_at', NULL
        )
    );
    RETURN v_response;
END;
$function$;

CREATE OR REPLACE FUNCTION public.delete_merchant_category_preference_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_preference_id uuid := (p_payload->>'preference_id')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_current public.merchant_category_preferences%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_deleted_at timestamptz;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000'; END IF;
    IF v_operation_id IS NULL OR v_preference_id IS NULL OR v_expected_revision IS NULL THEN
        RAISE EXCEPTION 'INVALID_CATEGORY_PREFERENCE_DELETE' USING ERRCODE = 'P0001';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-preference:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'DELETE_MERCHANT_CATEGORY_PREFERENCE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;
    SELECT * INTO v_current FROM public.merchant_category_preferences AS p
    WHERE p.id = v_preference_id AND p.user_id = v_user_id FOR UPDATE;
    IF NOT FOUND OR v_current.revision <> v_expected_revision OR v_current.deleted_at IS NOT NULL THEN
        RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;
    v_revision := v_current.revision + 1;
    v_deleted_at := pg_catalog.now();
    UPDATE public.merchant_category_preferences
    SET revision = v_revision, deleted_at = v_deleted_at, updated_at = v_deleted_at
    WHERE id = v_preference_id AND user_id = v_user_id;
    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'preference_id', v_preference_id,
        'revision', v_revision, 'deleted_at', v_deleted_at
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'DELETE_MERCHANT_CATEGORY_PREFERENCE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_CATEGORY_PREFERENCE', v_preference_id, v_revision, 'DELETE',
        pg_catalog.jsonb_build_object('id', v_preference_id, 'revision', v_revision, 'deleted_at', v_deleted_at)
    );
    RETURN v_response;
END;
$function$;

REVOKE ALL ON FUNCTION public.upsert_merchant_category_preference_v1(jsonb) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.delete_merchant_category_preference_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_merchant_category_preference_v1(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_merchant_category_preference_v1(jsonb) TO authenticated;
