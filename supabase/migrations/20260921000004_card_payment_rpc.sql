-- Migration: 20260921000004_card_payment_rpc.sql
-- Description: RPC pay_credit_card_v1 for symmetric amortization without expense

CREATE OR REPLACE FUNCTION public.pay_credit_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id UUID;
    v_op_id UUID;
    v_card_id TEXT;
    v_source_account_id TEXT;
    v_amount_minor BIGINT;
    v_currency TEXT;
    v_effective_at TIMESTAMPTZ;
    v_existing_receipt RECORD;
    v_source_balance BIGINT;
    v_card_debt BIGINT;
    v_account_curr TEXT;
    v_card_curr TEXT;
    v_card_archived BOOLEAN;
    v_acc_archived BOOLEAN;
    v_mov_cash_id UUID;
    v_mov_liab_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'No authenticated session' USING ERRCODE = '28000';
    END IF;

    v_op_id := (p_command->>'operation_id')::uuid;
    v_card_id := p_command->>'card_id';
    v_source_account_id := p_command->>'source_account_id';
    v_amount_minor := (p_command->>'amount_minor_units')::bigint;
    v_currency := p_command->>'currency';
    v_effective_at := COALESCE((p_command->>'effective_at')::timestamptz, now());

    IF v_amount_minor <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero' USING ERRCODE = '22023';
    END IF;

    -- Idempotency check
    SELECT * INTO v_existing_receipt FROM public.command_receipts
    WHERE user_id = v_user_id AND operation_id = v_op_id;
    IF FOUND THEN
        RETURN v_existing_receipt.response_payload;
    END IF;

    -- Verify source account
    SELECT currency, is_archived INTO v_account_curr, v_acc_archived
    FROM public.accounts
    WHERE user_id = v_user_id AND id = v_source_account_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Source account not found' USING ERRCODE = 'P0002';
    END IF;
    IF v_acc_archived THEN
        RAISE EXCEPTION 'Cannot pay from archived account' USING ERRCODE = '22000';
    END IF;
    IF v_account_curr != v_currency THEN
        RAISE EXCEPTION 'Source account currency mismatch' USING ERRCODE = '22023';
    END IF;

    -- Verify credit card
    SELECT currency, is_archived INTO v_card_curr, v_card_archived
    FROM public.cards
    WHERE user_id = v_user_id AND id = v_card_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Credit card not found' USING ERRCODE = 'P0002';
    END IF;
    IF v_card_archived THEN
        RAISE EXCEPTION 'Cannot pay archived credit card' USING ERRCODE = '22000';
    END IF;
    IF v_card_curr != v_currency THEN
        RAISE EXCEPTION 'Credit card currency mismatch' USING ERRCODE = '22023';
    END IF;

    -- Verify source balance
    SELECT COALESCE(SUM(amount_minor_units), 0) INTO v_source_balance
    FROM public.financial_movements
    WHERE user_id = v_user_id AND account_id = v_source_account_id AND status = 'POSTED';
    IF v_source_balance < v_amount_minor THEN
        RAISE EXCEPTION 'Insufficient funds in source account' USING ERRCODE = '22000';
    END IF;

    -- Verify card debt
    SELECT COALESCE(SUM(amount_minor_units), 0) INTO v_card_debt
    FROM public.financial_movements
    WHERE user_id = v_user_id AND card_id = v_card_id AND status = 'POSTED';
    IF v_card_debt < v_amount_minor THEN
        RAISE EXCEPTION 'Payment amount exceeds card debt' USING ERRCODE = '22000';
    END IF;

    v_mov_cash_id := gen_random_uuid();
    v_mov_liab_id := gen_random_uuid();

    -- 1. Insert CARD_PAYMENT_CASH
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
        account_id, effective_at, status, created_at
    ) VALUES (
        v_mov_cash_id::text, v_op_id, 0, v_user_id, 'CARD_PAYMENT_CASH', -v_amount_minor, v_currency,
        v_source_account_id, v_effective_at, 'POSTED', now()
    );

    -- 2. Insert CARD_PAYMENT_LIABILITY
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
        card_id, effective_at, status, created_at
    ) VALUES (
        v_mov_liab_id::text, v_op_id, 1, v_user_id, 'CARD_PAYMENT_LIABILITY', -v_amount_minor, v_currency,
        v_card_id, v_effective_at, 'POSTED', now()
    );

    -- Receipt
    INSERT INTO public.command_receipts (
        user_id, operation_id, command_type, aggregate_type, aggregate_id, payload_hash, response_payload, created_at
    ) VALUES (
        v_user_id, v_op_id, 'PAY_CREDIT_CARD', 'CARD', v_card_id,
        COALESCE(p_command->>'payload_hash', 'unhashed'),
        jsonb_build_object(
            'status', 'SUCCESS',
            'card_id', v_card_id,
            'source_account_id', v_source_account_id,
            'amount_minor_units', v_amount_minor,
            'currency', v_currency
        ),
        now()
    );

    RETURN jsonb_build_object(
        'status', 'SUCCESS',
        'card_id', v_card_id,
        'source_account_id', v_source_account_id,
        'amount_minor_units', v_amount_minor,
        'currency', v_currency
    );
END;
$$;

REVOKE ALL ON FUNCTION public.pay_credit_card_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.pay_credit_card_v1(jsonb) TO authenticated;
