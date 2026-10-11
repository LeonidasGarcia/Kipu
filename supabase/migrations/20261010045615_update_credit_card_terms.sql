-- Update only the contractual credit line and cycle dates of an owned credit card.
-- This command is idempotent and revision guarded; it never mutates movements or debt.
CREATE OR REPLACE FUNCTION public.update_credit_card_terms_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_card jsonb := p_command->'card';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key', '');
    v_card_id uuid;
    v_credit_limit bigint;
    v_billing_day integer;
    v_due_day integer;
    v_expected_revision bigint;
    v_request_hash text;
    v_existing record;
    v_card_owner uuid;
    v_is_credit boolean;
    v_archived boolean;
    v_revision bigint;
    v_receipt_id uuid;
    v_now timestamptz := clock_timestamp();
    v_response jsonb;
    v_card_payload jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'UNAUTHORIZED', 'field', NULL, 'retryable', false)
        );
    END IF;

    IF COALESCE((p_command->>'contract_version')::integer, 1) <> 1
       OR v_card IS NULL
       OR v_idempotency_key IS NULL
       OR NULLIF(p_command->>'request_hash', '') IS NULL
       OR NOT (v_card ? 'credit_limit_minor_units')
       OR NOT (v_card ? 'billing_day')
       OR NOT (v_card ? 'due_day')
       OR NOT (v_card ? 'expected_revision') THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_COMMAND', 'field', NULL, 'retryable', false)
        );
    END IF;

    BEGIN
        v_card_id := (v_card->>'id')::uuid;
        v_credit_limit := (v_card->>'credit_limit_minor_units')::bigint;
        v_billing_day := (v_card->>'billing_day')::integer;
        v_due_day := (v_card->>'due_day')::integer;
        v_expected_revision := (v_card->>'expected_revision')::bigint;
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', NULL, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_CARD_TERMS', 'field', 'card', 'retryable', false)
        );
    END;

    IF v_card_id IS NULL OR v_credit_limit < 0 OR v_billing_day NOT BETWEEN 1 AND 31
       OR v_due_day NOT BETWEEN 1 AND 31 OR v_expected_revision < 0 THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_CARD_TERMS', 'field', 'card', 'retryable', false)
        );
    END IF;

    v_request_hash := encode(
        extensions.digest(
            convert_to(jsonb_build_object('contract_version', 1, 'card', v_card)::text, 'UTF8'),
            'sha256'
        ),
        'hex'
    );
    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key, 0));

    SELECT id, request_hash, response_payload
      INTO v_existing
      FROM internal.command_receipts
     WHERE user_id = v_user_id AND idempotency_key = v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash = v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload, '{status}', '"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object(
            'status', 'CONFLICT', 'card_id', v_card_id, 'receipt_id', v_existing.id,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'IDEMPOTENCY_CONFLICT', 'field', 'idempotency_key', 'retryable', false)
        );
    END IF;

    SELECT c.user_id, c.is_credit, c.is_archived, c.revision
      INTO v_card_owner, v_is_credit, v_archived, v_revision
      FROM public.cards AS c
     WHERE c.id = v_card_id
     FOR UPDATE;
    IF NOT FOUND OR v_card_owner <> v_user_id OR NOT v_is_credit OR v_archived THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'CREDIT_CARD_UNAVAILABLE', 'field', 'card.id', 'retryable', false)
        );
    END IF;
    IF v_revision <> v_expected_revision THEN
        RETURN jsonb_build_object(
            'status', 'CONFLICT', 'card_id', v_card_id, 'receipt_id', NULL,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'REVISION_CONFLICT', 'field', 'expected_revision', 'retryable', true)
        );
    END IF;

    UPDATE public.cards
       SET credit_limit_minor = v_credit_limit,
           closing_day = v_billing_day,
           due_day = v_due_day,
           revision = revision + 1,
           updated_at = v_now
     WHERE id = v_card_id AND user_id = v_user_id
     RETURNING revision INTO v_revision;

    SELECT jsonb_build_object(
        'id', c.id,
        'creation_operation_id', c.creation_operation_id,
        'account_id', c.account_id,
        'alias', c.alias,
        'type', CASE WHEN c.is_credit THEN 'CREDIT' ELSE 'DEBIT' END,
        'currency', c.currency_code,
        'network', c.network,
        'issuer', c.issuer,
        'last_four_digits', c.last4,
        'credit_limit_minor_units', c.credit_limit_minor,
        'billing_day', c.closing_day,
        'due_day', c.due_day,
        'preset_id', c.preset_id,
        'style_preset_id', c.style_preset_id,
        'color', c.color,
        'icon', c.icon,
        'is_archived', c.is_archived,
        'created_at', c.created_at,
        'personal_tea_bps', c.personal_tea_bps
    )
      INTO v_card_payload
      FROM public.cards AS c
     WHERE c.id = v_card_id AND c.user_id = v_user_id;

    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status', 'APPLIED', 'card_id', v_card_id,
        'credit_limit_minor_units', v_credit_limit,
        'billing_day', v_billing_day,
        'due_day', v_due_day,
        'revision', v_revision,
        'receipt_id', v_receipt_id,
        'server_updated_at', v_now,
        'error', NULL
    );
    INSERT INTO internal.command_receipts(id, user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_receipt_id, v_user_id, v_idempotency_key, 'UPDATE_CREDIT_CARD_TERMS_V1', v_request_hash, v_response, 'APPLIED');
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, v_revision, 'UPSERT', v_card_payload);

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.update_credit_card_terms_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_credit_card_terms_v1(jsonb) TO authenticated;
