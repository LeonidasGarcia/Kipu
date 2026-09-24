-- EP-MOV: validate account state, currency, owner-scoped categories, and catalog merchants.
CREATE OR REPLACE FUNCTION public.register_transaction_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_idempotency_key text;
    v_request_hash text;
    v_existing_receipt record;
    v_tx jsonb;
    v_tx_id uuid;
    v_type text;
    v_amount_minor bigint;
    v_currency_code char(3);
    v_source_account_id uuid;
    v_destination_account_id uuid;
    v_category_id uuid;
    v_merchant_id uuid;
    v_merchant_provisional_text text;
    v_occurred_at timestamptz;
    v_note text;
    v_response jsonb;
    v_receipt_id uuid;
    v_now timestamptz := clock_timestamp();
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED',
            'transaction_id', null,
            'receipt_id', null,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'UNAUTHORIZED', 'field', null, 'retryable', false)
        );
    END IF;

    v_idempotency_key := p_command->>'idempotency_key';
    v_request_hash := p_command->>'request_hash';
    v_tx := p_command->'transaction';

    IF v_idempotency_key IS NULL OR v_request_hash IS NULL OR v_tx IS NULL THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED',
            'transaction_id', null,
            'receipt_id', null,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_COMMAND', 'field', null, 'retryable', false)
        );
    END IF;

    -- 1. Idempotency check
    SELECT id, request_hash, response_payload, status INTO v_existing_receipt
    FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_idempotency_key;

    IF FOUND THEN
        IF v_existing_receipt.request_hash = v_request_hash THEN
            -- DUPLICATE: return original result
            RETURN v_existing_receipt.response_payload;
        ELSE
            -- CONFLICT: key reused with different hash
            RETURN jsonb_build_object(
                'status', 'CONFLICT',
                'transaction_id', null,
                'receipt_id', v_existing_receipt.id,
                'server_updated_at', v_now,
                'error', jsonb_build_object('code', 'IDEMPOTENCY_CONFLICT', 'field', 'idempotency_key', 'retryable', false)
            );
        END IF;
    END IF;

    -- 2. Extract transaction fields
    v_tx_id := COALESCE((v_tx->>'id')::uuid, extensions.gen_random_uuid());
    v_type := upper(v_tx->>'type');
    v_amount_minor := (v_tx->>'amount_minor')::bigint;
    v_currency_code := (v_tx->>'currency_code')::char(3);
    v_source_account_id := (v_tx->>'source_account_id')::uuid;
    v_destination_account_id := (v_tx->>'destination_account_id')::uuid;
    v_category_id := (v_tx->>'category_id')::uuid;
    v_merchant_id := (v_tx->>'merchant_id')::uuid;
    v_merchant_provisional_text := NULLIF(trim(v_tx->>'merchant_provisional_text'), '');
    v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz, v_now);
    v_note := v_tx->>'note';

    IF v_merchant_id IS NOT NULL AND v_merchant_provisional_text IS NOT NULL THEN
        RETURN jsonb_build_object('status', 'REJECTED', 'transaction_id', null,
            'receipt_id', null, 'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'MERCHANT_CONFLICT', 'field', 'merchant_id', 'retryable', false));
    END IF;

    IF v_merchant_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.merchant_services
        WHERE id = v_merchant_id AND is_active
    ) THEN
        RETURN jsonb_build_object('status', 'REJECTED', 'transaction_id', null,
            'receipt_id', null, 'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'MERCHANT_UNAVAILABLE', 'field', 'merchant_id', 'retryable', false));
    END IF;
    -- 3. Validations
    IF v_amount_minor IS NULL OR v_amount_minor <= 0 THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED',
            'transaction_id', null,
            'receipt_id', null,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_AMOUNT', 'field', 'amount_minor', 'retryable', false)
        );
    END IF;

    IF v_source_account_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.accounts WHERE id = v_source_account_id AND user_id = v_user_id AND NOT is_archived AND currency_code = v_currency_code
    ) THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED',
            'transaction_id', null,
            'receipt_id', null,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'ACCOUNT_NOT_FOUND', 'field', 'source_account_id', 'retryable', false)
        );
    END IF;

    IF v_type = 'EXPENSE' THEN
        IF v_category_id IS NULL THEN
            RETURN jsonb_build_object(
                'status', 'REJECTED',
                'transaction_id', null,
                'receipt_id', null,
                'server_updated_at', v_now,
                'error', jsonb_build_object('code', 'CATEGORY_REQUIRED', 'field', 'category_id', 'retryable', false)
            );
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM public.categories
            WHERE id = v_category_id AND is_active
              AND (user_id IS NULL OR user_id = v_user_id)
              AND (parent_id IS NULL OR EXISTS (
                  SELECT 1 FROM public.categories parent
                  WHERE parent.id = categories.parent_id AND parent.is_active
              ))
        ) THEN
            RETURN jsonb_build_object('status', 'REJECTED', 'transaction_id', null,
                'receipt_id', null, 'server_updated_at', v_now,
                'error', jsonb_build_object('code', 'CATEGORY_UNAVAILABLE', 'field', 'category_id', 'retryable', false));
        END IF;
    ELSIF v_type = 'TRANSFER' THEN
        IF v_destination_account_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM public.accounts WHERE id = v_destination_account_id AND user_id = v_user_id AND NOT is_archived AND currency_code = v_currency_code
        ) THEN
            RETURN jsonb_build_object(
                'status', 'REJECTED',
                'transaction_id', null,
                'receipt_id', null,
                'server_updated_at', v_now,
                'error', jsonb_build_object('code', 'DESTINATION_ACCOUNT_NOT_FOUND', 'field', 'destination_account_id', 'retryable', false)
            );
        END IF;

        IF v_source_account_id = v_destination_account_id THEN
            RETURN jsonb_build_object(
                'status', 'REJECTED',
                'transaction_id', null,
                'receipt_id', null,
                'server_updated_at', v_now,
                'error', jsonb_build_object('code', 'SAME_ACCOUNT_TRANSFER', 'field', 'destination_account_id', 'retryable', false)
            );
        END IF;
    ELSIF v_type != 'INCOME' THEN
        RETURN jsonb_build_object(
            'status', 'REJECTED',
            'transaction_id', null,
            'receipt_id', null,
            'server_updated_at', v_now,
            'error', jsonb_build_object('code', 'INVALID_TYPE', 'field', 'type', 'retryable', false)
        );
    END IF;

    -- 4. Atomic Insert
    INSERT INTO public.transactions (
        id, user_id, account_id, category_id, merchant_service_id, merchant_provisional_text,
        transaction_type, amount_minor, currency_code,
        occurred_at, notes, status, revision
    ) VALUES (
        v_tx_id, v_user_id, v_source_account_id, v_category_id, v_merchant_id, v_merchant_provisional_text,
        v_type::public.transaction_type, v_amount_minor, v_currency_code,
        v_occurred_at, v_note, 'CONFIRMED', 1
    );

    IF v_type = 'EXPENSE' THEN
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES (
            v_tx_id, v_user_id, v_source_account_id, -v_amount_minor, v_currency_code, 'PRIMARY', v_occurred_at
        );
    ELSIF v_type = 'INCOME' THEN
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES (
            v_tx_id, v_user_id, v_source_account_id, v_amount_minor, v_currency_code, 'PRIMARY', v_occurred_at
        );
    ELSIF v_type = 'TRANSFER' THEN
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES
        (v_tx_id, v_user_id, v_source_account_id, -v_amount_minor, v_currency_code, 'SOURCE', v_occurred_at),
        (v_tx_id, v_user_id, v_destination_account_id, v_amount_minor, v_currency_code, 'DESTINATION', v_occurred_at);
    END IF;

    -- 5. Build Success Response
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'transaction_id', v_tx_id,
        'receipt_id', v_receipt_id,
        'server_updated_at', v_now,
        'error', null
    );

    -- 6. Insert Command Receipt
    INSERT INTO internal.command_receipts (
        id, user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_receipt_id, v_user_id, v_idempotency_key, 'REGISTER_TRANSACTION_V1', v_request_hash, v_response, 'APPLIED'
    );

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.register_transaction_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_transaction_v1(jsonb) TO authenticated, service_role;
