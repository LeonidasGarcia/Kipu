-- Migration: 20260921000005_credit_purchase_rpc.sql
-- Description: RPC confirm_credit_purchase_v1 for recognizing debt and expense exactly once

CREATE OR REPLACE FUNCTION public.confirm_credit_purchase_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id UUID;
    v_op_id UUID;
    v_card_id UUID;
    v_amount_minor BIGINT;
    v_currency TEXT;
    v_merchant TEXT;
    v_installments INT;
    v_effective_at TIMESTAMPTZ;
    v_existing_receipt RECORD;
    v_card_is_credit BOOLEAN;
    v_card_archived BOOLEAN;
    v_mov_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'No authenticated session' USING ERRCODE = '28000';
    END IF;

    v_op_id := (p_command->>'operation_id')::uuid;
    v_card_id := (p_command->>'card_id')::uuid;
    v_amount_minor := (p_command->>'amount_minor_units')::bigint;
    v_currency := p_command->>'currency';
    v_merchant := COALESCE(p_command->>'merchant', 'Compra');
    v_installments := COALESCE((p_command->>'installments')::int, 1);
    v_effective_at := COALESCE((p_command->>'effective_at')::timestamptz, now());

    IF v_amount_minor <= 0 THEN
        RAISE EXCEPTION 'Purchase amount must be greater than zero' USING ERRCODE = '22023';
    END IF;

    IF v_installments < 1 OR v_installments > 36 THEN
        RAISE EXCEPTION 'Installments must be between 1 and 36' USING ERRCODE = '22023';
    END IF;

    -- Idempotency check
    SELECT * INTO v_existing_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_op_id::text;
    IF FOUND THEN
        RETURN v_existing_receipt.response_payload;
    END IF;

    -- Verify credit card
    SELECT is_credit, is_archived INTO v_card_is_credit, v_card_archived
    FROM public.cards
    WHERE user_id = v_user_id AND id = v_card_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Card not found' USING ERRCODE = 'P0002';
    END IF;
    IF NOT v_card_is_credit THEN
        RAISE EXCEPTION 'Only credit cards can record credit purchases' USING ERRCODE = '22000';
    END IF;
    IF v_card_archived THEN
        RAISE EXCEPTION 'Cannot record purchase on archived card' USING ERRCODE = '22000';
    END IF;

    v_mov_id := gen_random_uuid();

    -- Insert single CREDIT_PURCHASE movement
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
        card_id, effective_at, status, created_at
    ) VALUES (
        v_mov_id, v_op_id, 0, v_user_id, 'CREDIT_PURCHASE', v_amount_minor, v_currency,
        v_card_id, v_effective_at, 'POSTED', now()
    );

    -- Receipt
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status, created_at
    ) VALUES (
        v_user_id, v_op_id::text, 'CONFIRM_CREDIT_PURCHASE',
        COALESCE(p_command->>'payload_hash', 'unhashed'),
        jsonb_build_object(
            'status', 'SUCCESS',
            'card_id', v_card_id,
            'amount_minor_units', v_amount_minor,
            'currency', v_currency,
            'merchant', v_merchant,
            'installments', v_installments
        ),
        'APPLIED',
        now()
    );

    RETURN jsonb_build_object(
        'status', 'SUCCESS',
        'card_id', v_card_id,
        'amount_minor_units', v_amount_minor,
        'currency', v_currency,
        'merchant', v_merchant,
        'installments', v_installments
    );
END;
$$;

REVOKE ALL ON FUNCTION public.confirm_credit_purchase_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.confirm_credit_purchase_v1(jsonb) TO authenticated;
