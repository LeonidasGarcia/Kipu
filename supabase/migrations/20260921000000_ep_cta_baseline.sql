-- Migration: 20260921000000_ep_cta_baseline.sql
-- Description: EP-CTA schema evolution, financial movements, RPCs, and RLS security boundaries.

BEGIN;

-- 1. Ensure columns on accounts
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'initial_balance_minor_units') THEN
        ALTER TABLE public.accounts ADD COLUMN initial_balance_minor_units bigint NOT NULL DEFAULT 0 CHECK (initial_balance_minor_units >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'opened_at') THEN
        ALTER TABLE public.accounts ADD COLUMN opened_at timestamptz NOT NULL DEFAULT now();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'creation_operation_id') THEN
        ALTER TABLE public.accounts ADD COLUMN creation_operation_id uuid NOT NULL DEFAULT extensions.gen_random_uuid();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'preset_id') THEN
        ALTER TABLE public.accounts ADD COLUMN preset_id text;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'color') THEN
        ALTER TABLE public.accounts ADD COLUMN color text;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'accounts' AND column_name = 'icon') THEN
        ALTER TABLE public.accounts ADD COLUMN icon text;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_user_creation_op ON public.accounts(user_id, creation_operation_id);

-- 2. Ensure columns on cards
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'creation_operation_id') THEN
        ALTER TABLE public.cards ADD COLUMN creation_operation_id uuid NOT NULL DEFAULT extensions.gen_random_uuid();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'preset_id') THEN
        ALTER TABLE public.cards ADD COLUMN preset_id text;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'color') THEN
        ALTER TABLE public.cards ADD COLUMN color text;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'icon') THEN
        ALTER TABLE public.cards ADD COLUMN icon text;
    END IF;
END $$;

-- A credit card is a liability and need not link to a liquid account.
ALTER TABLE public.cards ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE public.cards ADD COLUMN IF NOT EXISTS is_archived boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_cards_user_creation_op ON public.cards(user_id, creation_operation_id);

-- 3. Financial movements table
CREATE TABLE IF NOT EXISTS public.financial_movements (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    operation_id uuid NOT NULL,
    operation_sequence integer NOT NULL DEFAULT 0 CHECK (operation_sequence >= 0),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('OPENING', 'ADJUSTMENT', 'REVERSAL', 'CREDIT_PURCHASE', 'CARD_PAYMENT_CASH', 'CARD_PAYMENT_LIABILITY')),
    amount_minor_units bigint NOT NULL,
    currency bpchar(3) NOT NULL REFERENCES public.currencies(code),
    account_id uuid REFERENCES public.accounts(id) ON DELETE CASCADE,
    card_id uuid REFERENCES public.cards(id) ON DELETE CASCADE,
    opening_account_id uuid REFERENCES public.accounts(id) ON DELETE CASCADE,
    effective_at timestamptz NOT NULL DEFAULT now(),
    status text NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED')),
    reverses_movement_id uuid REFERENCES public.financial_movements(id) ON DELETE SET NULL,
    adjusts_movement_id uuid REFERENCES public.financial_movements(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT financial_movements_target_check CHECK (account_id IS NOT NULL OR card_id IS NOT NULL),
    CONSTRAINT financial_movements_unique_op_seq UNIQUE (user_id, operation_id, operation_sequence)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_financial_movements_opening_account 
    ON public.financial_movements(user_id, opening_account_id) 
    WHERE opening_account_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_financial_movements_user_account ON public.financial_movements(user_id, account_id, status, effective_at);
CREATE INDEX IF NOT EXISTS idx_financial_movements_user_card ON public.financial_movements(user_id, card_id, status, effective_at);

ALTER TABLE public.financial_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.financial_movements FORCE ROW LEVEL SECURITY;

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'public' AND tablename = 'financial_movements' AND policyname = 'financial_movements_own'
    ) THEN
        CREATE POLICY financial_movements_own ON public.financial_movements
            FOR ALL TO authenticated
            USING (auth.uid() = user_id)
            WITH CHECK (auth.uid() = user_id);
    END IF;
END $$;

-- 4. RPC: create_account_v1
CREATE OR REPLACE FUNCTION public.create_account_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_account_id uuid;
    v_opening_movement_id uuid;
    v_alias text;
    v_type public.account_type;
    v_currency bpchar(3);
    v_preset_id text;
    v_color text;
    v_icon text;
    v_initial_balance bigint;
    v_opened_at timestamptz;
    v_request_hash text;
    v_existing_receipt record;
    v_computable_count integer;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_opening_movement_id := (p_command->>'opening_movement_id')::uuid;
    v_alias := trim(p_command->>'alias');
    v_type := (p_command->>'type')::public.account_type;
    v_currency := (p_command->>'currency')::bpchar(3);
    v_preset_id := p_command->>'preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_initial_balance := (p_command->>'initial_balance_minor_units')::bigint;
    v_opened_at := (p_command->>'opened_at')::timestamptz;
    v_request_hash := p_command->>'payload_hash';

    IF v_operation_id IS NULL OR v_account_id IS NULL OR v_opening_movement_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Missing required UUIDs';
    END IF;

    IF v_initial_balance < 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: Initial balance for liquid accounts cannot be negative';
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

    -- Free quota check: CASH does not consume quota; others consume 1
    IF v_type <> 'CASH' THEN
        SELECT (
            (SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
            (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)
        ) INTO v_computable_count;

        IF v_computable_count >= 4 THEN
            RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier';
        END IF;
    END IF;

    -- Insert account
    INSERT INTO public.accounts (
        id, user_id, creation_operation_id, name, account_type, currency_code,
        institution_code, color_argb, icon_key, initial_balance_minor_units, opened_at,
        preset_id, color, icon, is_archived, revision
    ) VALUES (
        v_account_id, v_user_id, v_operation_id, v_alias, v_type, v_currency,
        v_preset_id, NULL, v_icon, v_initial_balance, v_opened_at,
        v_preset_id, v_color, v_icon, false, 1
    );

    -- Insert opening movement
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, opening_account_id, effective_at, status
    ) VALUES (
        v_opening_movement_id, v_operation_id, 0, v_user_id, 'OPENING', v_initial_balance,
        v_currency, v_account_id, v_account_id, v_opened_at, 'POSTED'
    );

    -- Sync change
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'ACCOUNT', v_account_id, 1, 'UPSERT', jsonb_build_object(
        'id', v_account_id, 'alias', v_alias, 'type', v_type, 'currency', v_currency,
        'initial_balance_minor_units', v_initial_balance, 'opened_at', v_opened_at
    ));

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'account_id', v_account_id,
        'opening_movement_id', v_opening_movement_id,
        'revision', 1
    );

    -- Receipt
    INSERT INTO internal.command_receipts (user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_user_id, v_operation_id::text, 'CREATE_ACCOUNT', v_request_hash, v_response, 'APPLIED');

    RETURN v_response;
END;
$$;

-- 5. RPC: register_card_v1
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
        preset_id, color, icon, is_archived, revision
    ) VALUES (
        v_card_id, v_user_id, v_operation_id, v_account_id, v_network, COALESCE(v_alias, v_issuer), v_last_four,
        (v_type = 'CREDIT'), v_credit_limit, v_billing_day, v_due_day,
        v_preset_id, v_color, v_icon, false, 1
    );

    -- Sync change
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, 1, 'UPSERT', jsonb_build_object(
        'id', v_card_id, 'alias', v_alias, 'type', v_type, 'network', v_network,
        'issuer', v_issuer, 'last_four_digits', v_last_four
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

-- 6. RPC: update_instrument_appearance_v1
CREATE OR REPLACE FUNCTION public.update_instrument_appearance_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_instrument_id uuid;
    v_instrument_type text;
    v_alias text;
    v_preset_id text;
    v_color text;
    v_icon text;
    v_expected_revision bigint;
    v_current_revision bigint;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_instrument_id := (p_command->>'instrument_id')::uuid;
    v_instrument_type := p_command->>'instrument_type';
    v_alias := trim(p_command->>'alias');
    v_preset_id := p_command->>'preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_expected_revision := (p_command->>'expected_revision')::bigint;

    IF v_instrument_type = 'ACCOUNT' THEN
        SELECT revision INTO v_current_revision FROM public.accounts WHERE id = v_instrument_id AND user_id = v_user_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'NOT_FOUND: Account not found'; END IF;
        IF v_expected_revision IS NOT NULL AND v_current_revision <> v_expected_revision THEN
            RAISE EXCEPTION 'REVISION_CONFLICT: Expected % but found %', v_expected_revision, v_current_revision;
        END IF;

        UPDATE public.accounts
        SET name = v_alias, preset_id = v_preset_id, color = v_color, icon = v_icon,
            revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;

    ELSIF v_instrument_type = 'CARD' THEN
        SELECT revision INTO v_current_revision FROM public.cards WHERE id = v_instrument_id AND user_id = v_user_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'NOT_FOUND: Card not found'; END IF;
        IF v_expected_revision IS NOT NULL AND v_current_revision <> v_expected_revision THEN
            RAISE EXCEPTION 'REVISION_CONFLICT: Expected % but found %', v_expected_revision, v_current_revision;
        END IF;

        UPDATE public.cards
        SET alias = v_alias, preset_id = v_preset_id, color = v_color, icon = v_icon,
            revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown instrument type %', v_instrument_type;
    END IF;

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'instrument_id', v_instrument_id,
        'revision', v_current_revision
    );

    RETURN v_response;
END;
$$;

-- 7. RPC: set_instrument_archived_v1
CREATE OR REPLACE FUNCTION public.set_instrument_archived_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_instrument_id uuid;
    v_instrument_type text;
    v_is_archived boolean;
    v_current_revision bigint;
    v_computable_count integer;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_instrument_id := (p_command->>'instrument_id')::uuid;
    v_instrument_type := p_command->>'instrument_type';
    v_is_archived := (p_command->>'is_archived')::boolean;

    -- Reactivating consumes quota
    IF NOT v_is_archived THEN
        SELECT (
            (SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
            (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)
        ) INTO v_computable_count;

        IF v_computable_count >= 4 THEN
            RAISE EXCEPTION 'FREE_LIMIT_REACHED: Reactivation would exceed Free tier limit of 4 active instruments';
        END IF;
    END IF;

    IF v_instrument_type = 'ACCOUNT' THEN
        UPDATE public.accounts
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSIF v_instrument_type = 'CARD' THEN
        UPDATE public.cards
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown instrument type %', v_instrument_type;
    END IF;

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'instrument_id', v_instrument_id,
        'is_archived', v_is_archived,
        'revision', v_current_revision
    );

    RETURN v_response;
END;
$$;

-- 8. RPC: delete_unused_card_v1
CREATE OR REPLACE FUNCTION public.delete_unused_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_card_id uuid;
    v_movement_count integer;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_card_id := (p_command->>'card_id')::uuid;

    SELECT COUNT(*) INTO v_movement_count
    FROM public.financial_movements
    WHERE user_id = v_user_id AND card_id = v_card_id;

    IF v_movement_count > 0 THEN
        RAISE EXCEPTION 'CARD_HAS_HISTORY: Cannot physically delete card with transaction history. Archive instead.';
    END IF;

    DELETE FROM public.cards WHERE id = v_card_id AND user_id = v_user_id;

    -- Tombstone sync change
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, 0, 'DELETE', jsonb_build_object('id', v_card_id));

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'card_id', v_card_id,
        'deleted', true
    );

    RETURN v_response;
END;
$$;

-- 9. RPC: record_opening_adjustment_v1
CREATE OR REPLACE FUNCTION public.record_opening_adjustment_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_account_id uuid;
    v_reversal_movement_id uuid;
    v_adjustment_movement_id uuid;
    v_new_amount bigint;
    v_new_effective_at timestamptz;
    v_opening_movement record;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_reversal_movement_id := (p_command->>'reversal_movement_id')::uuid;
    v_adjustment_movement_id := (p_command->>'adjustment_movement_id')::uuid;
    v_new_amount := (p_command->>'new_amount_minor_units')::bigint;
    v_new_effective_at := (p_command->>'new_effective_at')::timestamptz;

    IF v_new_amount < 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: Adjusted opening balance cannot be negative';
    END IF;

    SELECT * INTO v_opening_movement
    FROM public.financial_movements
    WHERE user_id = v_user_id AND account_id = v_account_id AND kind = 'OPENING';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'OPENING_NOT_FOUND: Opening movement not found for account';
    END IF;

    -- 1. Insert reversal for original opening
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, effective_at, status, reverses_movement_id
    ) VALUES (
        v_reversal_movement_id, v_operation_id, 0, v_user_id, 'REVERSAL', -v_opening_movement.amount_minor_units,
        v_opening_movement.currency, v_account_id, v_opening_movement.effective_at, 'POSTED', v_opening_movement.id
    );

    -- 2. Insert adjustment with corrected amount
    INSERT INTO public.financial_movements (
        id, operation_id, operation_sequence, user_id, kind, amount_minor_units,
        currency, account_id, effective_at, status, adjusts_movement_id
    ) VALUES (
        v_adjustment_movement_id, v_operation_id, 1, v_user_id, 'ADJUSTMENT', v_new_amount,
        v_opening_movement.currency, v_account_id, v_new_effective_at, 'POSTED', v_opening_movement.id
    );

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'account_id', v_account_id,
        'reversal_movement_id', v_reversal_movement_id,
        'adjustment_movement_id', v_adjustment_movement_id
    );

    RETURN v_response;
END;
$$;

-- 10. RPC: pull_financial_changes_v1
CREATE OR REPLACE FUNCTION public.pull_financial_changes_v1(
    contract_version integer,
    after_sequence bigint,
    "limit" integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_changes jsonb;
    v_next_sequence bigint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    SELECT jsonb_agg(
        jsonb_build_object(
            'sequence', sequence,
            'entity_type', entity_type,
            'entity_id', entity_id,
            'revision', revision,
            'operation', operation,
            'payload', payload,
            'created_at', created_at
        )
    ), COALESCE(MAX(sequence), after_sequence)
    INTO v_changes, v_next_sequence
    FROM (
        SELECT sequence, entity_type, entity_id, revision, operation, payload, created_at
        FROM internal.sync_changes
        WHERE user_id = v_user_id AND sequence > after_sequence
        ORDER BY sequence ASC
        LIMIT "limit"
    ) sub;

    RETURN jsonb_build_object(
        'changes', COALESCE(v_changes, '[]'::jsonb),
        'next_sequence', v_next_sequence,
        'has_more', (v_next_sequence > after_sequence)
    );
END;
$$;

-- 11. Security grants
REVOKE ALL ON FUNCTION public.create_account_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.create_account_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.register_card_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.register_card_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.update_instrument_appearance_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.update_instrument_appearance_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.set_instrument_archived_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.set_instrument_archived_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.delete_unused_card_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.delete_unused_card_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.record_opening_adjustment_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.record_opening_adjustment_v1(jsonb) TO authenticated;

REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer, bigint, integer) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer, bigint, integer) TO authenticated;

COMMIT;
