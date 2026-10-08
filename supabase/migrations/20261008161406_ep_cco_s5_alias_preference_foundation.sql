-- Sprint 5: keep source text, personal alias rules and merchant preferences distinct.
-- This migration is additive; it does not rewrite any existing classification or catalog row.
SET lock_timeout = '5s';
SET statement_timeout = '60s';

ALTER TABLE public.financial_movements
    ADD COLUMN merchant_raw_text text;

COMMENT ON COLUMN public.financial_movements.merchant_raw_text IS
    'Exact owner-scoped source text captured for a movement. Never normalized in storage.';

CREATE TABLE public.merchant_alias_rules (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    normalized_pattern text NOT NULL,
    merchant_id uuid NOT NULL REFERENCES public.merchant_services(id) ON DELETE RESTRICT,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT merchant_alias_rules_pattern_nonblank CHECK (length(btrim(normalized_pattern)) > 0),
    CONSTRAINT merchant_alias_rules_pattern_length CHECK (length(normalized_pattern) <= 160),
    CONSTRAINT merchant_alias_rules_revision_positive CHECK (revision > 0)
);

CREATE INDEX merchant_alias_rules_owner_pattern_active_idx
    ON public.merchant_alias_rules(user_id, normalized_pattern)
    WHERE deleted_at IS NULL;
CREATE INDEX merchant_alias_rules_owner_merchant_idx
    ON public.merchant_alias_rules(user_id, merchant_id);

ALTER TABLE public.merchant_alias_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_alias_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY merchant_alias_rules_select_own
    ON public.merchant_alias_rules
    FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) IS NOT NULL AND (SELECT auth.uid()) = user_id);

REVOKE ALL PRIVILEGES ON TABLE public.merchant_alias_rules FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.merchant_alias_rules TO authenticated;

CREATE TABLE public.merchant_category_preferences (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    merchant_id uuid NOT NULL REFERENCES public.merchant_services(id) ON DELETE RESTRICT,
    category_id uuid NOT NULL REFERENCES public.categories(id) ON DELETE RESTRICT,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT merchant_category_preferences_owner_merchant_unique UNIQUE(user_id, merchant_id),
    CONSTRAINT merchant_category_preferences_revision_positive CHECK (revision > 0)
);

CREATE INDEX merchant_category_preferences_owner_category_idx
    ON public.merchant_category_preferences(user_id, category_id);

ALTER TABLE public.merchant_category_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_category_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY merchant_category_preferences_select_own
    ON public.merchant_category_preferences
    FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) IS NOT NULL AND (SELECT auth.uid()) = user_id);

REVOKE ALL PRIVILEGES ON TABLE public.merchant_category_preferences FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.merchant_category_preferences TO authenticated;

-- Writes are exposed only through owner-checking, idempotent RPCs added in later S5 migrations.
