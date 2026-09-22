-- Migration: 20260920162500_align_domain_schema_names.sql
-- Description: Align database table and column names with Kipu canonical domain specifications.

BEGIN;

-- ====================================================================
-- 1. DROP DEPENDENT VIEWS (will be recreated with aligned schema)
-- ====================================================================
DROP VIEW IF EXISTS public.v_obligation_summary CASCADE;
DROP VIEW IF EXISTS public.v_goal_summary CASCADE;
DROP VIEW IF EXISTS public.v_credit_summary CASCADE;

-- ====================================================================
-- 2. PROFILES (formerly user_settings)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_settings') THEN
        ALTER TABLE public.user_settings RENAME TO profiles;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'month_start_day') THEN
        ALTER TABLE public.profiles RENAME COLUMN month_start_day TO month_start;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'preferences') THEN
        ALTER TABLE public.profiles ADD COLUMN preferences jsonb NOT NULL DEFAULT '{}'::jsonb;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'profiles' AND policyname = 'user_settings_own') THEN
        ALTER POLICY user_settings_own ON public.profiles RENAME TO profiles_own;
    END IF;
END $$;

-- Update trigger for new users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_display_name text;
BEGIN
    v_display_name := COALESCE(
        new.raw_user_meta_data->>'full_name',
        new.raw_user_meta_data->>'name',
        split_part(new.email, '@', 1)
    );

    INSERT INTO public.profiles (user_id, display_name, currency_code, month_start, preferences)
    VALUES (new.id, v_display_name, 'PEN', 1, '{}'::jsonb)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN new;
END;
$$;

-- ====================================================================
-- 3. GOALS (formerly savings_goals)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'savings_goals') THEN
        ALTER TABLE public.savings_goals RENAME TO goals;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'goals' AND policyname = 'savings_goals_own') THEN
        ALTER POLICY savings_goals_own ON public.goals RENAME TO goals_own;
    END IF;
END $$;

-- ====================================================================
-- 4. DEBTS (formerly obligations)
-- ====================================================================
DO $$
BEGIN
    -- Rename obligations -> debts
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'obligations') THEN
        ALTER TABLE public.obligations RENAME TO debts;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'debts' AND policyname = 'obligations_own') THEN
        ALTER POLICY obligations_own ON public.debts RENAME TO debts_own;
    END IF;

    -- Rename obligation_installments -> debt_installments
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'obligation_installments') THEN
        ALTER TABLE public.obligation_installments RENAME TO debt_installments;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'debt_installments' AND column_name = 'obligation_id') THEN
        ALTER TABLE public.debt_installments RENAME COLUMN obligation_id TO debt_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'debt_installments' AND policyname = 'obligation_installments_own') THEN
        ALTER POLICY obligation_installments_own ON public.debt_installments RENAME TO debt_installments_own;
    END IF;

    -- Rename obligation_events -> debt_events
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'obligation_events') THEN
        ALTER TABLE public.obligation_events RENAME TO debt_events;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'debt_events' AND column_name = 'obligation_id') THEN
        ALTER TABLE public.debt_events RENAME COLUMN obligation_id TO debt_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'debt_events' AND policyname = 'obligation_events_own') THEN
        ALTER POLICY obligation_events_own ON public.debt_events RENAME TO debt_events_own;
    END IF;
END $$;

-- ====================================================================
-- 5. MERCHANT_SERVICES (formerly merchants)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'merchants') THEN
        ALTER TABLE public.merchants RENAME TO merchant_services;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'transactions' AND column_name = 'merchant_id') THEN
        ALTER TABLE public.transactions RENAME COLUMN merchant_id TO merchant_service_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'merchant_rules' AND column_name = 'merchant_id') THEN
        ALTER TABLE public.merchant_rules RENAME COLUMN merchant_id TO merchant_service_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'merchant_services' AND policyname = 'merchants_select') THEN
        ALTER POLICY merchants_select ON public.merchant_services RENAME TO merchant_services_select;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'merchant_services' AND policyname = 'merchants_insert') THEN
        ALTER POLICY merchants_insert ON public.merchant_services RENAME TO merchant_services_insert;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'merchant_services' AND policyname = 'merchants_update') THEN
        ALTER POLICY merchants_update ON public.merchant_services RENAME TO merchant_services_update;
    END IF;
END $$;

-- ====================================================================
-- 6. CATEGORIES (parent_id, color_token)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'categories' AND column_name = 'parent_category_id') THEN
        ALTER TABLE public.categories RENAME COLUMN parent_category_id TO parent_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'categories' AND column_name = 'color_token') THEN
        ALTER TABLE public.categories ADD COLUMN color_token text;
    END IF;
END $$;

-- ====================================================================
-- 7. CARDS (closing_day, due_day, product_id, network, style_preset_id)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'billing_close_day') THEN
        ALTER TABLE public.cards RENAME COLUMN billing_close_day TO closing_day;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'payment_due_day') THEN
        ALTER TABLE public.cards RENAME COLUMN payment_due_day TO due_day;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'credit_product_id') THEN
        ALTER TABLE public.cards RENAME COLUMN credit_product_id TO product_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'card_network') THEN
        ALTER TABLE public.cards RENAME COLUMN card_network TO network;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'cards' AND column_name = 'style_preset_id') THEN
        ALTER TABLE public.cards ADD COLUMN style_preset_id text;
    END IF;
END $$;

-- ====================================================================
-- 8. CREATE TABLE: TRANSACTION_LINKS
-- ====================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'transaction_link_type') THEN
        CREATE TYPE public.transaction_link_type AS ENUM (
            'TRANSFER',
            'CARD_PAYMENT',
            'DEBT_AMORTIZATION',
            'REFUND',
            'REVERSAL'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.transaction_links (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    source_transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    target_transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    link_type public.transaction_link_type NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT transaction_links_no_self_link CHECK (source_transaction_id <> target_transaction_id),
    CONSTRAINT transaction_links_unique_pair UNIQUE (source_transaction_id, target_transaction_id, link_type)
);

CREATE INDEX IF NOT EXISTS idx_transaction_links_user_id ON public.transaction_links(user_id);
CREATE INDEX IF NOT EXISTS idx_transaction_links_source ON public.transaction_links(source_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_links_target ON public.transaction_links(target_transaction_id);

ALTER TABLE public.transaction_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_links FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'public' AND tablename = 'transaction_links' AND policyname = 'transaction_links_own'
    ) THEN
        CREATE POLICY transaction_links_own ON public.transaction_links
            FOR ALL TO authenticated
            USING ((SELECT auth.uid()) = user_id)
            WITH CHECK ((SELECT auth.uid()) = user_id);
    END IF;
END $$;

-- ====================================================================
-- 9. CREATE TABLE: IMPORT_CANDIDATES
-- ====================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'import_candidate_status') THEN
        CREATE TYPE public.import_candidate_status AS ENUM (
            'PENDING',
            'CONFIRMED',
            'DISCARDED'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.import_candidates (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    raw_hash text NOT NULL,
    source_app text NOT NULL,
    raw_text text NOT NULL,
    extracted_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence_score numeric(4,3) NOT NULL DEFAULT 1.000 CHECK (confidence_score >= 0.000 AND confidence_score <= 1.000),
    status public.import_candidate_status NOT NULL DEFAULT 'PENDING',
    confirmed_transaction_id uuid REFERENCES public.transactions(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT import_candidates_user_hash_unique UNIQUE (user_id, raw_hash)
);

CREATE INDEX IF NOT EXISTS idx_import_candidates_user_status ON public.import_candidates(user_id, status);

ALTER TABLE public.import_candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.import_candidates FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'public' AND tablename = 'import_candidates' AND policyname = 'import_candidates_own'
    ) THEN
        CREATE POLICY import_candidates_own ON public.import_candidates
            FOR ALL TO authenticated
            USING ((SELECT auth.uid()) = user_id)
            WITH CHECK ((SELECT auth.uid()) = user_id);
    END IF;
END $$;

-- ====================================================================
-- 10. RECREATE / UPDATE VIEWS
-- ====================================================================
-- Credit Summary View
CREATE OR REPLACE VIEW public.v_credit_summary AS
SELECT c.id AS card_id,
    c.user_id,
    c.alias AS card_alias,
    c.network,
    c.last4,
    c.credit_limit_minor,
    c.closing_day,
    c.due_day,
    c.style_preset_id,
    abs(COALESCE(sum(l.signed_amount_minor), (0)::numeric)) AS used_credit_minor,
    CASE
        WHEN (c.credit_limit_minor IS NOT NULL) THEN GREATEST((0)::numeric, ((c.credit_limit_minor)::numeric - abs(COALESCE(sum(l.signed_amount_minor), (0)::numeric))))
        ELSE NULL::numeric
    END AS available_credit_minor
FROM ((public.cards c
    JOIN public.accounts a ON ((c.account_id = a.id)))
    LEFT JOIN internal.ledger_entries l ON (((a.id = l.account_id) AND (l.user_id = c.user_id))))
WHERE ((c.is_credit = true) AND (c.deleted_at IS NULL))
GROUP BY c.id, c.user_id, c.alias, c.network, c.last4, c.credit_limit_minor, c.closing_day, c.due_day, c.style_preset_id;

-- Goal Summary View
CREATE OR REPLACE VIEW public.v_goal_summary AS
SELECT g.id AS goal_id,
    g.user_id,
    g.name AS goal_name,
    g.target_minor,
    g.currency_code,
    g.target_date,
    g.status,
    COALESCE(sum(ge.amount_minor), (0)::numeric) AS current_saved_minor,
    GREATEST((0)::numeric, ((g.target_minor)::numeric - COALESCE(sum(ge.amount_minor), (0)::numeric))) AS remaining_minor,
    round(((COALESCE(sum(ge.amount_minor), (0)::numeric) / (g.target_minor)::numeric) * (100)::numeric), 2) AS progress_percentage
FROM (public.goals g
    LEFT JOIN public.goal_events ge ON ((g.id = ge.goal_id)))
WHERE (g.deleted_at IS NULL)
GROUP BY g.id, g.user_id, g.name, g.target_minor, g.currency_code, g.target_date, g.status;

-- Debt Summary View
CREATE OR REPLACE VIEW public.v_debt_summary AS
SELECT d.id AS debt_id,
    d.user_id,
    d.obligation_type AS debt_type,
    d.counterparty_name,
    d.total_minor,
    d.currency_code,
    d.due_date,
    d.status,
    COALESCE(sum(
        CASE
            WHEN (de.event_type = 'PAYMENT'::text) THEN de.amount_minor
            ELSE (0)::bigint
        END), (0)::numeric) AS total_paid_minor,
    GREATEST((0)::numeric, ((d.total_minor)::numeric - COALESCE(sum(
        CASE
            WHEN (de.event_type = 'PAYMENT'::text) THEN de.amount_minor
            ELSE (0)::bigint
        END), (0)::numeric))) AS remaining_minor
FROM (public.debts d
    LEFT JOIN public.debt_events de ON ((d.id = de.debt_id)))
WHERE (d.deleted_at IS NULL)
GROUP BY d.id, d.user_id, d.obligation_type, d.counterparty_name, d.total_minor, d.currency_code, d.due_date, d.status;

-- Backward compatibility view alias
CREATE OR REPLACE VIEW public.v_obligation_summary AS
SELECT debt_id AS obligation_id,
    user_id,
    debt_type AS obligation_type,
    counterparty_name,
    total_minor,
    currency_code,
    due_date,
    status,
    total_paid_minor,
    remaining_minor
FROM public.v_debt_summary;

-- ====================================================================
-- 11. UPDATE STORED PROCEDURES / FUNCTIONS
-- ====================================================================
-- allocate_goal_funds
CREATE OR REPLACE FUNCTION public.allocate_goal_funds(
    p_goal_id uuid,
    p_amount_minor bigint,
    p_from_account_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_goal record;
    v_tx_id uuid := NULL;
    v_new_saved bigint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    IF p_amount_minor <= 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: El monto de aporte debe ser mayor a cero';
    END IF;

    SELECT * INTO v_goal
    FROM public.goals
    WHERE id = p_goal_id AND user_id = v_user_id AND deleted_at IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'GOAL_NOT_FOUND: Meta de ahorro no encontrada';
    END IF;

    IF p_from_account_id IS NOT NULL THEN
        v_tx_id := extensions.gen_random_uuid();
        
        INSERT INTO public.transactions (
            id, user_id, account_id, transaction_type, operation_kind,
            amount_minor, currency_code, notes, status, revision
        ) VALUES (
            v_tx_id, v_user_id, p_from_account_id, 'EXPENSE', 'GOAL_FUNDING',
            p_amount_minor, v_goal.currency_code, 'Aporte a meta: ' || v_goal.name, 'CONFIRMED', 1
        );

        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
        ) VALUES (
            v_tx_id, v_user_id, p_from_account_id, -p_amount_minor, v_goal.currency_code, 'PRIMARY'
        );
    END IF;

    INSERT INTO public.goal_events (
        goal_id, user_id, transaction_id, event_type, amount_minor
    ) VALUES (
        p_goal_id, v_user_id, v_tx_id, 'ALLOCATION', p_amount_minor
    );

    SELECT COALESCE(SUM(amount_minor), 0) INTO v_new_saved
    FROM public.goal_events
    WHERE goal_id = p_goal_id AND user_id = v_user_id;

    IF v_new_saved >= v_goal.target_minor AND v_goal.status != 'COMPLETED' THEN
        UPDATE public.goals 
        SET status = 'COMPLETED', updated_at = now() 
        WHERE id = p_goal_id;

        INSERT INTO public.app_notifications (
            user_id, title, body, notification_type, reference_entity_type, reference_entity_id
        ) VALUES (
            v_user_id, '¡Meta Cumplida!', '¡Felicitaciones! Has completado tu meta "' || v_goal.name || '".',
            'GOAL_REACHED', 'GOAL', p_goal_id
        );
    END IF;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'GOAL', p_goal_id, 1, 'UPSERT', jsonb_build_object('id', p_goal_id, 'saved_minor', v_new_saved));

    RETURN jsonb_build_object(
        'success', true,
        'goal_id', p_goal_id,
        'total_saved_minor', v_new_saved,
        'target_minor', v_goal.target_minor,
        'status', (SELECT status FROM public.goals WHERE id = p_goal_id)
    );
END;
$$;

-- release_goal_funds
CREATE OR REPLACE FUNCTION public.release_goal_funds(
    p_goal_id uuid,
    p_amount_minor bigint,
    p_to_account_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_goal record;
    v_tx_id uuid := NULL;
    v_current_saved bigint;
    v_new_saved bigint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    IF p_amount_minor <= 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: El monto a liberar debe ser mayor a cero';
    END IF;

    SELECT * INTO v_goal
    FROM public.goals
    WHERE id = p_goal_id AND user_id = v_user_id AND deleted_at IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'GOAL_NOT_FOUND: Meta de ahorro no encontrada';
    END IF;

    SELECT COALESCE(SUM(amount_minor), 0) INTO v_current_saved
    FROM public.goal_events
    WHERE goal_id = p_goal_id AND user_id = v_user_id;

    IF p_amount_minor > v_current_saved THEN
        RAISE EXCEPTION 'INSUFFICIENT_FUNDS: El monto supera el saldo disponible de la meta (%)', v_current_saved;
    END IF;

    IF p_to_account_id IS NOT NULL THEN
        v_tx_id := extensions.gen_random_uuid();
        
        INSERT INTO public.transactions (
            id, user_id, account_id, transaction_type, operation_kind,
            amount_minor, currency_code, notes, status, revision
        ) VALUES (
            v_tx_id, v_user_id, p_to_account_id, 'INCOME', 'GOAL_FUNDING',
            p_amount_minor, v_goal.currency_code, 'Liberación de fondos de meta: ' || v_goal.name, 'CONFIRMED', 1
        );

        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
        ) VALUES (
            v_tx_id, v_user_id, p_to_account_id, p_amount_minor, v_goal.currency_code, 'PRIMARY'
        );
    END IF;

    INSERT INTO public.goal_events (
        goal_id, user_id, transaction_id, event_type, amount_minor
    ) VALUES (
        p_goal_id, v_user_id, v_tx_id, 'RELEASE', -p_amount_minor
    );

    v_new_saved := v_current_saved - p_amount_minor;

    IF v_goal.status = 'COMPLETED' AND v_new_saved < v_goal.target_minor THEN
        UPDATE public.goals 
        SET status = 'ACTIVE', updated_at = now() 
        WHERE id = p_goal_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'goal_id', p_goal_id,
        'total_saved_minor', v_new_saved,
        'status', (SELECT status FROM public.goals WHERE id = p_goal_id)
    );
END;
$$;

-- record_obligation_payment / record_debt_payment
CREATE OR REPLACE FUNCTION public.record_debt_payment(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_debt_id uuid;
    v_installment_id uuid;
    v_account_id uuid;
    v_amount_minor bigint;
    v_notes text;
    v_debt record;
    v_tx_id uuid;
    v_total_paid bigint;
    v_inst_paid bigint;
    v_inst_total bigint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    v_debt_id := COALESCE((p_command->>'debt_id')::uuid, (p_command->>'obligation_id')::uuid);
    v_installment_id := (p_command->>'installment_id')::uuid;
    v_account_id := (p_command->>'account_id')::uuid;
    v_amount_minor := (p_command->>'amount_minor')::bigint;
    v_notes := p_command->>'notes';

    IF v_amount_minor <= 0 THEN
        RAISE EXCEPTION 'INVALID_AMOUNT: El monto de pago debe ser mayor a cero';
    END IF;

    SELECT * INTO v_debt
    FROM public.debts
    WHERE id = v_debt_id AND user_id = v_user_id AND deleted_at IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'DEBT_NOT_FOUND: Deuda u obligación no encontrada';
    END IF;

    v_tx_id := extensions.gen_random_uuid();

    IF v_debt.obligation_type = 'PAYABLE' THEN
        INSERT INTO public.transactions (
            id, user_id, account_id, transaction_type, operation_kind,
            amount_minor, currency_code, notes, status, revision
        ) VALUES (
            v_tx_id, v_user_id, v_account_id, 'EXPENSE', 'DEBT_PAYMENT',
            v_amount_minor, v_debt.currency_code, 
            COALESCE(v_notes, 'Pago de deuda: ' || v_debt.counterparty_name), 'CONFIRMED', 1
        );

        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
        ) VALUES (
            v_tx_id, v_user_id, v_account_id, -v_amount_minor, v_debt.currency_code, 'PRIMARY'
        );
    ELSE
        INSERT INTO public.transactions (
            id, user_id, account_id, transaction_type, operation_kind,
            amount_minor, currency_code, notes, status, revision
        ) VALUES (
            v_tx_id, v_user_id, v_account_id, 'INCOME', 'DEBT_PAYMENT',
            v_amount_minor, v_debt.currency_code, 
            COALESCE(v_notes, 'Cobro de préstamo: ' || v_debt.counterparty_name), 'CONFIRMED', 1
        );

        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role
        ) VALUES (
            v_tx_id, v_user_id, v_account_id, v_amount_minor, v_debt.currency_code, 'PRIMARY'
        );
    END IF;

    INSERT INTO public.debt_events (
        debt_id, user_id, transaction_id, installment_id, event_type, amount_minor
    ) VALUES (
        v_debt_id, v_user_id, v_tx_id, v_installment_id, 'PAYMENT', v_amount_minor
    );

    IF v_installment_id IS NOT NULL THEN
        SELECT amount_minor INTO v_inst_total
        FROM public.debt_installments WHERE id = v_installment_id;

        SELECT COALESCE(SUM(amount_minor), 0) INTO v_inst_paid
        FROM public.debt_events WHERE installment_id = v_installment_id AND event_type = 'PAYMENT';

        IF v_inst_paid >= v_inst_total THEN
            UPDATE public.debt_installments SET status = 'PAID', updated_at = now() WHERE id = v_installment_id;
        END IF;
    END IF;

    SELECT COALESCE(SUM(amount_minor), 0) INTO v_total_paid
    FROM public.debt_events WHERE debt_id = v_debt_id AND event_type = 'PAYMENT';

    IF v_total_paid >= v_debt.total_minor THEN
        UPDATE public.debts SET status = 'SETTLED', updated_at = now() WHERE id = v_debt_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'transaction_id', v_tx_id,
        'debt_id', v_debt_id,
        'total_paid_minor', v_total_paid,
        'remaining_minor', GREATEST(0, v_debt.total_minor - v_total_paid),
        'status', (SELECT status FROM public.debts WHERE id = v_debt_id)
    );
END;
$$;

-- Backward compatibility alias function
CREATE OR REPLACE FUNCTION public.record_obligation_payment(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN public.record_debt_payment(p_command);
END;
$$;

-- register_transaction (updated with merchant_service_id)
CREATE OR REPLACE FUNCTION public.register_transaction(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_idempotency_key text;
    v_receipt_response jsonb;
    v_tx_id uuid;
    v_account_id uuid;
    v_card_id uuid;
    v_dest_account_id uuid;
    v_category_id uuid;
    v_merchant_service_id uuid;
    v_type public.transaction_type;
    v_kind public.operation_kind;
    v_amount_minor bigint;
    v_currency_code char(3);
    v_installment_count smallint;
    v_occurred_at timestamptz;
    v_notes text;
    v_card_account_id uuid;
    v_base_quota bigint;
    v_rem_cents smallint;
    v_quota_val bigint;
    i smallint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    v_idempotency_key := p_command->>'idempotency_key';
    IF v_idempotency_key IS NOT NULL THEN
        SELECT response_payload INTO v_receipt_response
        FROM internal.command_receipts
        WHERE user_id = v_user_id AND idempotency_key = v_idempotency_key;

        IF FOUND THEN
            RETURN v_receipt_response;
        END IF;
    END IF;

    v_tx_id := COALESCE((p_command->>'id')::uuid, extensions.gen_random_uuid());
    v_account_id := (p_command->>'account_id')::uuid;
    v_card_id := (p_command->>'card_id')::uuid;
    v_dest_account_id := (p_command->>'destination_account_id')::uuid;
    v_category_id := (p_command->>'category_id')::uuid;
    v_merchant_service_id := COALESCE((p_command->>'merchant_service_id')::uuid, (p_command->>'merchant_id')::uuid);
    v_type := (p_command->>'transaction_type')::public.transaction_type;
    v_kind := COALESCE((p_command->>'operation_kind')::public.operation_kind, 'STANDARD');
    v_amount_minor := (p_command->>'amount_minor')::bigint;
    v_currency_code := (p_command->>'currency_code')::char(3);
    v_installment_count := COALESCE((p_command->>'installment_count')::smallint, 1);
    v_occurred_at := COALESCE((p_command->>'occurred_at')::timestamptz, now());
    v_notes := p_command->>'notes';

    IF NOT EXISTS (SELECT 1 FROM public.accounts WHERE id = v_account_id AND user_id = v_user_id) THEN
        RAISE EXCEPTION 'ACCOUNT_NOT_FOUND: Primary account does not belong to user';
    END IF;

    INSERT INTO public.transactions (
        id, user_id, account_id, card_id, category_id, merchant_service_id,
        transaction_type, operation_kind, amount_minor, currency_code,
        occurred_at, installment_count, notes, status, revision
    ) VALUES (
        v_tx_id, v_user_id, v_account_id, v_card_id, v_category_id, v_merchant_service_id,
        v_type, v_kind, v_amount_minor, v_currency_code,
        v_occurred_at, v_installment_count, v_notes, 'CONFIRMED', 1
    );

    CASE v_type
        WHEN 'EXPENSE' THEN
            IF v_kind = 'CARD_PURCHASE' AND v_card_id IS NOT NULL THEN
                SELECT account_id INTO v_card_account_id FROM public.cards WHERE id = v_card_id AND user_id = v_user_id;
                INSERT INTO internal.ledger_entries (
                    transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
                ) VALUES (
                    v_tx_id, v_user_id, v_card_account_id, -v_amount_minor, v_currency_code, 'LIABILITY', v_occurred_at
                );

                v_base_quota := v_amount_minor / v_installment_count;
                v_rem_cents := (v_amount_minor % v_installment_count)::smallint;

                FOR i IN 1..v_installment_count LOOP
                    IF i <= v_rem_cents THEN
                        v_quota_val := v_base_quota + 1;
                    ELSE
                        v_quota_val := v_base_quota;
                    END IF;

                    INSERT INTO public.credit_installments (
                        user_id, transaction_id, installment_number, due_date, principal_minor, status
                    ) VALUES (
                        v_user_id, v_tx_id, i, (v_occurred_at::date + (i || ' month')::interval)::date, v_quota_val, 'PENDING'
                    );
                END LOOP;
            ELSE
                INSERT INTO internal.ledger_entries (
                    transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
                ) VALUES (
                    v_tx_id, v_user_id, v_account_id, -v_amount_minor, v_currency_code, 'PRIMARY', v_occurred_at
                );
            END IF;

        WHEN 'INCOME' THEN
            INSERT INTO internal.ledger_entries (
                transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
            ) VALUES (
                v_tx_id, v_user_id, v_account_id, v_amount_minor, v_currency_code, 'PRIMARY', v_occurred_at
            );

        WHEN 'TRANSFER' THEN
            IF v_dest_account_id IS NULL THEN
                RAISE EXCEPTION 'DESTINATION_REQUIRED: Transfer must have destination_account_id';
            END IF;
            INSERT INTO internal.ledger_entries (
                transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
            ) VALUES 
                (v_tx_id, v_user_id, v_account_id, -v_amount_minor, v_currency_code, 'SOURCE', v_occurred_at),
                (v_tx_id, v_user_id, v_dest_account_id, v_amount_minor, v_currency_code, 'DESTINATION', v_occurred_at);
    END CASE;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'TRANSACTION', v_tx_id, 1, 'UPSERT', jsonb_build_object('id', v_tx_id, 'status', 'CONFIRMED'));

    v_receipt_response := jsonb_build_object(
        'success', true,
        'transaction_id', v_tx_id,
        'status', 'CONFIRMED'
    );

    IF v_idempotency_key IS NOT NULL THEN
        INSERT INTO internal.command_receipts (
            user_id, idempotency_key, command_type, request_hash, response_payload, status
        ) VALUES (
            v_user_id, v_idempotency_key, 'REGISTER_TRANSACTION', md5(p_command::text), v_receipt_response, 'APPLIED'
        );
    END IF;

    RETURN v_receipt_response;
END;
$$;

-- apply_sync_command (updated with cards/debts/goals/merchant_services)
CREATE OR REPLACE FUNCTION public.apply_sync_command(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_device_id uuid;
    v_idempotency_key text;
    v_command_type text;
    v_expected_revision bigint;
    v_payload jsonb;
    v_receipt_response jsonb;
    v_result jsonb;
    v_entity_id uuid;
    v_current_rev bigint;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    v_device_id := (p_command->>'device_id')::uuid;
    v_idempotency_key := p_command->>'idempotency_key';
    v_command_type := p_command->>'command_type';
    v_expected_revision := (p_command->>'expected_revision')::bigint;
    v_payload := p_command->'payload';

    IF v_device_id IS NOT NULL THEN
        UPDATE public.user_devices 
        SET last_sync_at = now() 
        WHERE id = v_device_id AND user_id = v_user_id;
    END IF;

    IF v_idempotency_key IS NOT NULL THEN
        SELECT response_payload INTO v_receipt_response
        FROM internal.command_receipts
        WHERE user_id = v_user_id AND idempotency_key = v_idempotency_key;

        IF FOUND THEN
            RETURN jsonb_build_object(
                'status', 'DUPLICATE',
                'idempotency_key', v_idempotency_key,
                'result', v_receipt_response
            );
        END IF;
    END IF;

    CASE v_command_type
        WHEN 'REGISTER_TRANSACTION' THEN
            v_result := public.register_transaction(v_payload);

        WHEN 'ALLOCATE_CREDIT_PAYMENT' THEN
            v_result := public.allocate_credit_payment(v_payload);

        WHEN 'RECORD_DEBT_PAYMENT', 'RECORD_OBLIGATION_PAYMENT' THEN
            v_result := public.record_debt_payment(v_payload);

        WHEN 'ALLOCATE_GOAL_FUNDS' THEN
            v_result := public.allocate_goal_funds(
                (v_payload->>'goal_id')::uuid,
                (v_payload->>'amount_minor')::bigint,
                (v_payload->>'from_account_id')::uuid
            );

        WHEN 'RELEASE_GOAL_FUNDS' THEN
            v_result := public.release_goal_funds(
                (v_payload->>'goal_id')::uuid,
                (v_payload->>'amount_minor')::bigint,
                (v_payload->>'to_account_id')::uuid
            );

        WHEN 'UPSERT_ACCOUNT' THEN
            v_entity_id := COALESCE((v_payload->>'id')::uuid, extensions.gen_random_uuid());
            
            IF v_expected_revision IS NOT NULL THEN
                SELECT revision INTO v_current_rev FROM public.accounts WHERE id = v_entity_id AND user_id = v_user_id;
                IF FOUND AND v_current_rev != v_expected_revision THEN
                    RETURN jsonb_build_object(
                        'status', 'CONFLICT',
                        'error', 'REVISION_MISMATCH',
                        'current_revision', v_current_rev,
                        'expected_revision', v_expected_revision
                    );
                END IF;
            END IF;

            INSERT INTO public.accounts (
                id, user_id, name, account_type, currency_code, color_argb, icon_key, revision
            ) VALUES (
                v_entity_id, v_user_id, v_payload->>'name', 
                (v_payload->>'account_type')::public.account_type,
                (v_payload->>'currency_code')::char(3),
                (v_payload->>'color_argb')::int,
                v_payload->>'icon_key',
                COALESCE(v_expected_revision, 0) + 1
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                color_argb = EXCLUDED.color_argb,
                icon_key = EXCLUDED.icon_key,
                revision = accounts.revision + 1,
                updated_at = now();

            v_result := jsonb_build_object('success', true, 'account_id', v_entity_id);

        WHEN 'UPSERT_CARD' THEN
            v_entity_id := COALESCE((v_payload->>'id')::uuid, extensions.gen_random_uuid());

            INSERT INTO public.cards (
                id, user_id, account_id, alias, network, last4, is_credit,
                credit_limit_minor, closing_day, due_day, personal_tea_bps, product_id, style_preset_id, revision
            ) VALUES (
                v_entity_id, v_user_id, (v_payload->>'account_id')::uuid, v_payload->>'alias',
                COALESCE(v_payload->>'network', v_payload->>'card_network'), v_payload->>'last4',
                COALESCE((v_payload->>'is_credit')::boolean, false),
                (v_payload->>'credit_limit_minor')::bigint,
                COALESCE((v_payload->>'closing_day')::smallint, (v_payload->>'billing_close_day')::smallint),
                COALESCE((v_payload->>'due_day')::smallint, (v_payload->>'payment_due_day')::smallint),
                (v_payload->>'personal_tea_bps')::int,
                COALESCE((v_payload->>'product_id')::uuid, (v_payload->>'credit_product_id')::uuid),
                v_payload->>'style_preset_id',
                COALESCE(v_expected_revision, 0) + 1
            )
            ON CONFLICT (id) DO UPDATE SET
                alias = EXCLUDED.alias,
                network = EXCLUDED.network,
                credit_limit_minor = EXCLUDED.credit_limit_minor,
                closing_day = EXCLUDED.closing_day,
                due_day = EXCLUDED.due_day,
                personal_tea_bps = EXCLUDED.personal_tea_bps,
                product_id = EXCLUDED.product_id,
                style_preset_id = EXCLUDED.style_preset_id,
                revision = cards.revision + 1,
                updated_at = now();

            v_result := jsonb_build_object('success', true, 'card_id', v_entity_id);

        WHEN 'UPSERT_BUDGET' THEN
            v_entity_id := COALESCE((v_payload->>'id')::uuid, extensions.gen_random_uuid());

            INSERT INTO public.budgets (
                id, user_id, category_id, name, limit_minor, currency_code, period_type, revision
            ) VALUES (
                v_entity_id, v_user_id, (v_payload->>'category_id')::uuid, v_payload->>'name',
                (v_payload->>'limit_minor')::bigint, (v_payload->>'currency_code')::char(3),
                COALESCE((v_payload->>'period_type')::public.budget_period_type, 'MONTHLY'),
                COALESCE(v_expected_revision, 0) + 1
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                limit_minor = EXCLUDED.limit_minor,
                revision = budgets.revision + 1,
                updated_at = now();

            v_result := jsonb_build_object('success', true, 'budget_id', v_entity_id);

        WHEN 'DELETE_ENTITY' THEN
            v_entity_id := (v_payload->>'id')::uuid;
            CASE v_payload->>'entity_type'
                WHEN 'ACCOUNT' THEN
                    UPDATE public.accounts SET deleted_at = now(), revision = revision + 1 WHERE id = v_entity_id AND user_id = v_user_id;
                WHEN 'CARD' THEN
                    UPDATE public.cards SET deleted_at = now(), revision = revision + 1 WHERE id = v_entity_id AND user_id = v_user_id;
                WHEN 'BUDGET' THEN
                    UPDATE public.budgets SET deleted_at = now(), revision = revision + 1 WHERE id = v_entity_id AND user_id = v_user_id;
                WHEN 'GOAL', 'SAVINGS_GOAL' THEN
                    UPDATE public.goals SET deleted_at = now(), revision = revision + 1 WHERE id = v_entity_id AND user_id = v_user_id;
                WHEN 'DEBT', 'OBLIGATION' THEN
                    UPDATE public.debts SET deleted_at = now(), revision = revision + 1 WHERE id = v_entity_id AND user_id = v_user_id;
                ELSE
                    RAISE EXCEPTION 'UNKNOWN_ENTITY: Tipo de entidad no reconocido para borrado: %', v_payload->>'entity_type';
            END CASE;

            v_result := jsonb_build_object('success', true, 'deleted_id', v_entity_id);

        ELSE
            RAISE EXCEPTION 'UNKNOWN_COMMAND: Tipo de comando no soportado: %', v_command_type;
    END CASE;

    IF v_idempotency_key IS NOT NULL THEN
        INSERT INTO internal.command_receipts (
            user_id, idempotency_key, command_type, request_hash, response_payload, status
        ) VALUES (
            v_user_id, v_idempotency_key, v_command_type, md5(p_command::text), v_result, 'APPLIED'
        );
    END IF;

    RETURN jsonb_build_object(
        'status', 'APPLIED',
        'idempotency_key', v_idempotency_key,
        'result', v_result
    );
END;
$$;

-- get_feature_access
CREATE OR REPLACE FUNCTION public.get_feature_access()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id uuid;
    v_access record;
    v_active_devices int;
    v_active_budgets int;
    v_active_debts int;
    v_offline_valid_until timestamptz;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    SELECT * INTO v_access FROM public.v_feature_access WHERE user_id = v_user_id;

    SELECT COUNT(*) INTO v_active_devices FROM public.user_devices WHERE user_id = v_user_id AND is_active = true;
    SELECT COUNT(*) INTO v_active_budgets FROM public.budgets WHERE user_id = v_user_id AND is_active = true AND deleted_at IS NULL;
    SELECT COUNT(*) INTO v_active_debts FROM public.debts WHERE user_id = v_user_id AND status = 'ACTIVE' AND deleted_at IS NULL;

    v_offline_valid_until := now() + interval '72 hours';

    RETURN jsonb_build_object(
        'user_id', v_user_id,
        'plan_tier', v_access.plan_tier,
        'offline_valid_until', v_offline_valid_until,
        'limits', jsonb_build_object(
            'devices', jsonb_build_object('allowed', v_access.max_devices, 'used', v_active_devices),
            'budgets', jsonb_build_object('allowed', v_access.max_budgets, 'used', v_active_budgets),
            'debts', jsonb_build_object('allowed', v_access.max_obligations, 'used', v_active_debts)
        )
    );
END;
$$;

-- ====================================================================
-- 12. GRANTS
-- ====================================================================
GRANT SELECT, INSERT, UPDATE ON public.profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.goals TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.debts TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.debt_installments TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.debt_events TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.merchant_services TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.transaction_links TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.import_candidates TO authenticated;

GRANT SELECT ON public.v_credit_summary TO authenticated;
GRANT SELECT ON public.v_goal_summary TO authenticated;
GRANT SELECT ON public.v_debt_summary TO authenticated;
GRANT SELECT ON public.v_obligation_summary TO authenticated;

GRANT EXECUTE ON FUNCTION public.record_debt_payment(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_obligation_payment(jsonb) TO authenticated;

COMMIT;
