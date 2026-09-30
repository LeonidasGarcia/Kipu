BEGIN;

-- Keep visual card styling separate from the legacy institution preset.
-- public.cards.style_preset_id already exists and is nullable.
CREATE OR REPLACE FUNCTION public.register_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_alias text;
    v_type text;
    v_currency bpchar(3);
    v_network text;
    v_issuer text;
    v_last_four text;
    v_credit_limit bigint;
    v_billing_day integer;
    v_due_day integer;
    v_preset_id text;
    v_style_preset_id text;
    v_color text;
    v_icon text;
    v_request_hash text;
    v_existing_receipt record;
    v_computable_count integer;
    v_linked_account record;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_card_id := (p_command->>'card_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_alias := trim(p_command->>'alias');
    v_type := p_command->>'type';
    v_currency := (p_command->>'currency')::bpchar(3);
    v_network := p_command->>'network';
    v_issuer := trim(p_command->>'issuer');
    v_last_four := trim(p_command->>'last_four_digits');
    v_credit_limit := (p_command->>'credit_limit_minor_units')::bigint;
    v_billing_day := (p_command->>'billing_day')::integer;
    v_due_day := (p_command->>'due_day')::integer;
    v_preset_id := p_command->>'preset_id';
    v_style_preset_id := p_command->>'style_preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_request_hash := p_command->>'payload_hash';

    IF length(v_last_four) <> 4 THEN
        RAISE EXCEPTION 'INVALID_REQUEST: last_four_digits must be exactly 4 digits';
    END IF;

    -- Check idempotency
    SELECT * INTO v_existing_receipt
    FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text;

    IF FOUND THEN
        IF v_existing_receipt.request_hash = v_request_hash THEN
            RETURN v_existing_receipt.response_payload;
        ELSE
            RAISE EXCEPTION 'OPERATION_COLLISION: Operation ID reused with differing payload';
        END IF;
    END IF;

    -- Free quota check
    SELECT (
        (SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
        (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)
    ) INTO v_computable_count;

    IF v_computable_count >= 4 THEN
        RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier';
    END IF;

    IF v_type = 'DEBIT' THEN
        IF v_account_id IS NULL THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Debit card requires linked account_id';
        END IF;
        SELECT * INTO v_linked_account
        FROM public.accounts
        WHERE id = v_account_id AND user_id = v_user_id AND is_archived = false;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'ACCOUNT_NOT_FOUND: Linked savings/bank account must exist and be active';
        END IF;
        v_currency := v_linked_account.currency_code;
    ELSIF v_type = 'CREDIT' THEN
        IF v_credit_limit IS NULL OR v_credit_limit < 0 THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Credit card requires non-negative credit_limit_minor_units';
        END IF;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown card type: %', v_type;
    END IF;

    INSERT INTO public.cards (
        id, user_id, creation_operation_id, account_id, network, alias, last4,
        is_credit, credit_limit_minor, closing_day, due_day,
        preset_id, style_preset_id, color, icon, is_archived, revision
    ) VALUES (
        v_card_id, v_user_id, v_operation_id, v_account_id, v_network, COALESCE(v_alias, v_issuer), v_last_four,
        (v_type = 'CREDIT'), v_credit_limit, v_billing_day, v_due_day,
        v_preset_id, v_style_preset_id, v_color, v_icon, false, 1
    );

    -- Sync change
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, 1, 'UPSERT', jsonb_build_object(
        'id', v_card_id, 'alias', v_alias, 'type', v_type, 'network', v_network,
        'issuer', v_issuer, 'last_four_digits', v_last_four,
        'preset_id', v_preset_id, 'style_preset_id', v_style_preset_id,
        'color', v_color, 'icon', v_icon
    ));

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'card_id', v_card_id,
        'revision', 1
    );

    INSERT INTO internal.command_receipts (user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_user_id, v_operation_id::text, 'REGISTER_CARD', v_request_hash, v_response, 'APPLIED');

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.register_card_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_card_v1(jsonb) TO authenticated;

COMMIT;
