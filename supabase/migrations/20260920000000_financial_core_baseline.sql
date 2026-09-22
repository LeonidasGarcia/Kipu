-- Migration: 20260920000000_financial_core_baseline.sql
-- Description: Core financial schema baseline establishing foundational tables, enums, ledger, and RLS.

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS internal;

-- Enums
DO $$ BEGIN
    CREATE TYPE public.account_type AS ENUM (
        'CASH', 'BANK', 'WALLET', 'SAVINGS', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.transaction_type AS ENUM (
        'EXPENSE', 'INCOME', 'TRANSFER'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.operation_kind AS ENUM (
        'STANDARD', 'CARD_PURCHASE', 'CARD_PAYMENT', 'DEBT_DISBURSEMENT', 'DEBT_PAYMENT', 'GOAL_FUNDING', 'REFUND', 'ADJUSTMENT'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE internal.entry_role AS ENUM (
        'PRIMARY', 'SOURCE', 'DESTINATION', 'LIABILITY', 'REVERSAL'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.obligation_type AS ENUM (
        'PAYABLE', 'RECEIVABLE'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.goal_status AS ENUM (
        'ACTIVE', 'COMPLETED', 'ARCHIVED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.budget_period_type AS ENUM (
        'MONTHLY', 'WEEKLY', 'CUSTOM'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.recurrence_frequency AS ENUM (
        'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'ANNUALLY', 'CUSTOM'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Currencies
CREATE TABLE IF NOT EXISTS public.currencies (
    code bpchar(3) PRIMARY KEY,
    symbol text NOT NULL,
    name text NOT NULL,
    scale smallint NOT NULL DEFAULT 2 CHECK (scale >= 0 AND scale <= 4),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.currencies (code, symbol, name, scale, is_active)
VALUES 
    ('PEN', 'S/', 'Sol Peruano', 2, true),
    ('USD', '$', 'Dólar Estadounidense', 2, true),
    ('EUR', '€', 'Euro', 2, true)
ON CONFLICT (code) DO NOTHING;

-- User Settings (legacy name before rename to profiles)
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text,
    currency_code bpchar(3) NOT NULL DEFAULT 'PEN' REFERENCES public.currencies(code),
    time_zone text NOT NULL DEFAULT 'America/Lima',
    month_start_day smallint NOT NULL DEFAULT 1 CHECK (month_start_day >= 1 AND month_start_day <= 28),
    hide_balances boolean NOT NULL DEFAULT false,
    theme_mode text NOT NULL DEFAULT 'SYSTEM' CHECK (theme_mode IN ('LIGHT', 'DARK', 'SYSTEM')),
    biometric_enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 1
);

-- Accounts
CREATE TABLE IF NOT EXISTS public.accounts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    account_type public.account_type NOT NULL,
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    institution_code text,
    color_argb integer,
    icon_key text,
    is_archived boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON public.accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_user_archived ON public.accounts(user_id, is_archived);

-- Categories
CREATE TABLE IF NOT EXISTS public.categories (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    icon_key text,
    color_argb integer,
    parent_category_id uuid REFERENCES public.categories(id) ON DELETE SET NULL,
    is_system boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_categories_user_id ON public.categories(user_id);

-- Merchants (legacy name before rename to merchant_services)
CREATE TABLE IF NOT EXISTS public.merchants (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    normalized_name text NOT NULL,
    is_system boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

-- Merchant Rules
CREATE TABLE IF NOT EXISTS public.merchant_rules (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    pattern text NOT NULL,
    merchant_id uuid REFERENCES public.merchants(id) ON DELETE CASCADE,
    category_id uuid REFERENCES public.categories(id) ON DELETE CASCADE,
    priority integer NOT NULL DEFAULT 100,
    is_system boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

-- Credit Products
CREATE TABLE IF NOT EXISTS public.credit_products (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    institution_code text NOT NULL,
    product_name text NOT NULL,
    card_network text CHECK (card_network IN ('VISA', 'MASTERCARD', 'AMEX', 'DINERS', 'OTHER')),
    reference_tea_bps integer CHECK (reference_tea_bps >= 0),
    effective_from date NOT NULL DEFAULT CURRENT_DATE,
    effective_to date,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Cards
CREATE TABLE IF NOT EXISTS public.cards (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    card_network text CHECK (card_network IN ('VISA', 'MASTERCARD', 'AMEX', 'DINERS', 'OTHER')),
    alias text NOT NULL,
    last4 bpchar(4) CHECK (last4 IS NULL OR length(last4) = 4),
    is_credit boolean NOT NULL DEFAULT false,
    credit_limit_minor bigint CHECK (credit_limit_minor IS NULL OR credit_limit_minor >= 0),
    billing_close_day smallint CHECK (billing_close_day IS NULL OR (billing_close_day >= 1 AND billing_close_day <= 28)),
    payment_due_day smallint CHECK (payment_due_day IS NULL OR (payment_due_day >= 1 AND payment_due_day <= 28)),
    personal_tea_bps integer CHECK (personal_tea_bps IS NULL OR personal_tea_bps >= 0),
    credit_product_id uuid REFERENCES public.credit_products(id) ON DELETE SET NULL,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_cards_user_id ON public.cards(user_id);
CREATE INDEX IF NOT EXISTS idx_cards_account_id ON public.cards(account_id);

-- Transactions
CREATE TABLE IF NOT EXISTS public.transactions (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    card_id uuid REFERENCES public.cards(id) ON DELETE SET NULL,
    category_id uuid REFERENCES public.categories(id) ON DELETE SET NULL,
    merchant_id uuid REFERENCES public.merchants(id) ON DELETE SET NULL,
    transaction_type public.transaction_type NOT NULL,
    operation_kind public.operation_kind NOT NULL DEFAULT 'STANDARD',
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    transfer_group_id uuid,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    installment_count smallint NOT NULL DEFAULT 1 CHECK (installment_count >= 1),
    interest_mode text NOT NULL DEFAULT 'NONE' CHECK (interest_mode IN ('NONE', 'INCLUDED', 'REFERENTIAL')),
    merchant_raw_text text,
    notes text,
    refund_of_transaction_id uuid REFERENCES public.transactions(id) ON DELETE SET NULL,
    status text NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'REVISED', 'VOIDED')),
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_occurred ON public.transactions(account_id, occurred_at);

-- Transaction Revisions
CREATE TABLE IF NOT EXISTS public.transaction_revisions (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    revision_number bigint NOT NULL,
    previous_payload jsonb NOT NULL,
    new_payload jsonb NOT NULL,
    change_reason text,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Ledger Entries
CREATE TABLE IF NOT EXISTS internal.ledger_entries (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    signed_amount_minor bigint NOT NULL CHECK (signed_amount_minor <> 0),
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    entry_role internal.entry_role NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_user_account ON internal.ledger_entries(user_id, account_id);

-- Credit Installments
CREATE TABLE IF NOT EXISTS public.credit_installments (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    installment_number smallint NOT NULL CHECK (installment_number >= 1),
    due_date date NOT NULL,
    principal_minor bigint NOT NULL CHECK (principal_minor >= 0),
    interest_minor bigint NOT NULL DEFAULT 0 CHECK (interest_minor >= 0),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'VOIDED')),
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

-- Credit Payment Allocations
CREATE TABLE IF NOT EXISTS public.credit_payment_allocations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    payment_transaction_id uuid NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    installment_id uuid NOT NULL REFERENCES public.credit_installments(id) ON DELETE CASCADE,
    allocated_minor bigint NOT NULL CHECK (allocated_minor > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Obligations (legacy name before rename to debts)
CREATE TABLE IF NOT EXISTS public.obligations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    obligation_type public.obligation_type NOT NULL,
    counterparty_name text NOT NULL,
    total_minor bigint NOT NULL CHECK (total_minor > 0),
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    due_date date,
    notes text,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SETTLED', 'CANCELLED')),
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.obligation_installments (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    obligation_id uuid NOT NULL REFERENCES public.obligations(id) ON DELETE CASCADE,
    installment_number smallint NOT NULL CHECK (installment_number >= 1),
    due_date date NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.obligation_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    obligation_id uuid NOT NULL REFERENCES public.obligations(id) ON DELETE CASCADE,
    transaction_id uuid REFERENCES public.transactions(id) ON DELETE SET NULL,
    installment_id uuid REFERENCES public.obligation_installments(id) ON DELETE SET NULL,
    event_type text NOT NULL CHECK (event_type IN ('DISBURSEMENT', 'PAYMENT', 'ADJUSTMENT', 'FORGIVENESS')),
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Savings Goals (legacy name before rename to goals)
CREATE TABLE IF NOT EXISTS public.savings_goals (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    target_minor bigint NOT NULL CHECK (target_minor > 0),
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    target_date date,
    icon_key text,
    color_argb integer,
    status public.goal_status NOT NULL DEFAULT 'ACTIVE',
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.goal_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    goal_id uuid NOT NULL REFERENCES public.savings_goals(id) ON DELETE CASCADE,
    transaction_id uuid REFERENCES public.transactions(id) ON DELETE SET NULL,
    event_type text NOT NULL CHECK (event_type IN ('ALLOCATION', 'RELEASE', 'DIRECT_DEPOSIT', 'ADJUSTMENT')),
    amount_minor bigint NOT NULL CHECK (amount_minor <> 0),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Budgets
CREATE TABLE IF NOT EXISTS public.budgets (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    category_id uuid REFERENCES public.categories(id) ON DELETE SET NULL,
    name text NOT NULL,
    limit_minor bigint NOT NULL CHECK (limit_minor > 0),
    currency_code bpchar(3) NOT NULL REFERENCES public.currencies(code),
    period_type public.budget_period_type NOT NULL DEFAULT 'MONTHLY',
    alert_thresholds_bps smallint[] NOT NULL DEFAULT '{5000,8000,10000}'::smallint[],
    is_active boolean NOT NULL DEFAULT true,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

-- App Notifications
CREATE TABLE IF NOT EXISTS public.app_notifications (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title text NOT NULL,
    body text NOT NULL,
    notification_type text NOT NULL,
    reference_entity_type text,
    reference_entity_id uuid,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Command Receipts
CREATE TABLE IF NOT EXISTS internal.command_receipts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    idempotency_key text NOT NULL,
    command_type text NOT NULL,
    request_hash text NOT NULL,
    response_payload jsonb NOT NULL,
    status text NOT NULL DEFAULT 'APPLIED',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT command_receipts_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

-- Sync Changes
CREATE TABLE IF NOT EXISTS internal.sync_changes (
    sequence bigserial PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    revision bigint NOT NULL,
    operation text NOT NULL CHECK (operation IN ('UPSERT', 'DELETE')),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sync_changes_user_sequence ON internal.sync_changes(user_id, sequence);

-- Enable RLS
ALTER TABLE public.currencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_payment_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.obligations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.obligation_installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.obligation_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.savings_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goal_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.command_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal.sync_changes ENABLE ROW LEVEL SECURITY;

-- Force RLS
ALTER TABLE public.user_settings FORCE ROW LEVEL SECURITY;
ALTER TABLE public.accounts FORCE ROW LEVEL SECURITY;
ALTER TABLE public.categories FORCE ROW LEVEL SECURITY;
ALTER TABLE public.merchants FORCE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE public.cards FORCE ROW LEVEL SECURITY;
ALTER TABLE public.transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_revisions FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.ledger_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE public.credit_installments FORCE ROW LEVEL SECURITY;
ALTER TABLE public.credit_payment_allocations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.obligations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.obligation_installments FORCE ROW LEVEL SECURITY;
ALTER TABLE public.obligation_events FORCE ROW LEVEL SECURITY;
ALTER TABLE public.savings_goals FORCE ROW LEVEL SECURITY;
ALTER TABLE public.goal_events FORCE ROW LEVEL SECURITY;
ALTER TABLE public.budgets FORCE ROW LEVEL SECURITY;
ALTER TABLE public.app_notifications FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.command_receipts FORCE ROW LEVEL SECURITY;
ALTER TABLE internal.sync_changes FORCE ROW LEVEL SECURITY;

-- Baseline Policies
CREATE POLICY currencies_read ON public.currencies FOR SELECT TO authenticated USING (true);
CREATE POLICY user_settings_own ON public.user_settings FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY accounts_own ON public.accounts FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY categories_own ON public.categories FOR ALL TO authenticated USING (auth.uid() = user_id OR user_id IS NULL) WITH CHECK (auth.uid() = user_id);
CREATE POLICY merchants_select ON public.merchants FOR SELECT TO authenticated USING (auth.uid() = user_id OR is_system = true);
CREATE POLICY merchants_insert ON public.merchants FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY merchants_update ON public.merchants FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY merchant_rules_own ON public.merchant_rules FOR ALL TO authenticated USING (auth.uid() = user_id OR is_system = true) WITH CHECK (auth.uid() = user_id);
CREATE POLICY credit_products_read ON public.credit_products FOR SELECT TO authenticated USING (true);
CREATE POLICY cards_own ON public.cards FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY transactions_own ON public.transactions FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY transaction_revisions_own ON public.transaction_revisions FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY ledger_entries_own ON internal.ledger_entries FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY credit_installments_own ON public.credit_installments FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY credit_payment_allocations_own ON public.credit_payment_allocations FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY obligations_own ON public.obligations FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY obligation_installments_own ON public.obligation_installments FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY obligation_events_own ON public.obligation_events FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY savings_goals_own ON public.savings_goals FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY goal_events_own ON public.goal_events FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY budgets_own ON public.budgets FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY app_notifications_own ON public.app_notifications FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY command_receipts_own ON internal.command_receipts FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY sync_changes_own ON internal.sync_changes FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

COMMIT;
