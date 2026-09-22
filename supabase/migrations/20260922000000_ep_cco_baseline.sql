-- Migration: 20260922000000_ep_cco_baseline.sql
-- Description: EP-CCO schema evolution, categories, category_presentations, merchant_services, conflicts, movement classification, RLS, and RPCs.

BEGIN;

-- 1. Categories Table
CREATE TABLE IF NOT EXISTS public.categories (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    parent_id uuid REFERENCES public.categories(id) ON DELETE CASCADE,
    origin text NOT NULL CHECK (origin IN ('SYSTEM', 'CUSTOM')),
    is_active boolean NOT NULL DEFAULT true,
    remote_revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_categories_no_self_parent CHECK (id <> parent_id)
);

CREATE INDEX IF NOT EXISTS idx_categories_user_id ON public.categories(user_id);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON public.categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_user_active ON public.categories(user_id, is_active);
CREATE INDEX IF NOT EXISTS idx_categories_origin ON public.categories(origin);

-- Trigger / validation for 2 levels max (parent must be a root)
CREATE OR REPLACE FUNCTION public.check_category_two_levels()
RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL THEN
        -- Verify parent is a root category (parent.parent_id is null)
        IF EXISTS (SELECT 1 FROM public.categories WHERE id = NEW.parent_id AND parent_id IS NOT NULL) THEN
            RAISE EXCEPTION 'Category hierarchy cannot exceed two levels: parent % is already a subcategory', NEW.parent_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_category_two_levels ON public.categories;
CREATE TRIGGER trg_check_category_two_levels
BEFORE INSERT OR UPDATE ON public.categories
FOR EACH ROW EXECUTE FUNCTION public.check_category_two_levels();

-- 2. Category Presentations Table
CREATE TABLE IF NOT EXISTS public.category_presentations (
    category_id uuid NOT NULL REFERENCES public.categories(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL CHECK (char_length(trim(name)) > 0),
    icon text NOT NULL,
    color text NOT NULL,
    remote_revision bigint NOT NULL DEFAULT 1,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_category_presentations_category_id ON public.category_presentations(category_id);
CREATE INDEX IF NOT EXISTS idx_category_presentations_user_id ON public.category_presentations(user_id);

-- 3. Merchant Services Catalog Table
CREATE TABLE IF NOT EXISTS public.merchant_services (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    name text NOT NULL CHECK (char_length(trim(name)) > 0),
    normalized_name text NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_merchant_services_normalized_name ON public.merchant_services(normalized_name);
CREATE INDEX IF NOT EXISTS idx_merchant_services_is_active ON public.merchant_services(is_active);

-- 4. Category Conflicts Table
CREATE TABLE IF NOT EXISTS public.category_conflicts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    category_id uuid NOT NULL REFERENCES public.categories(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    conflict_type text NOT NULL CHECK (conflict_type IN ('PRESENTATION', 'LIFECYCLE')),
    local_version text NOT NULL,
    remote_version text NOT NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution_operation_id uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_category_conflicts_user_status ON public.category_conflicts(user_id, status);
CREATE INDEX IF NOT EXISTS idx_category_conflicts_category_id ON public.category_conflicts(category_id);

-- 5. Extend financial_movements with classification columns
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'financial_movements' AND column_name = 'category_id') THEN
        ALTER TABLE public.financial_movements ADD COLUMN category_id uuid REFERENCES public.categories(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'financial_movements' AND column_name = 'merchant_id') THEN
        ALTER TABLE public.financial_movements ADD COLUMN merchant_id uuid REFERENCES public.merchant_services(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'financial_movements' AND column_name = 'merchant_provisional_text') THEN
        ALTER TABLE public.financial_movements ADD COLUMN merchant_provisional_text text;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_financial_movements_user_category ON public.financial_movements(user_id, category_id);
CREATE INDEX IF NOT EXISTS idx_financial_movements_merchant_id ON public.financial_movements(merchant_id);

-- 6. Seed Predetermined Categories (Alimentación, Transporte, Servicios)
INSERT INTO public.categories (id, user_id, parent_id, origin, is_active, remote_revision)
VALUES 
    ('00000000-0000-0000-0000-000000000001', NULL, NULL, 'SYSTEM', true, 1),
    ('00000000-0000-0000-0000-000000000002', NULL, NULL, 'SYSTEM', true, 1),
    ('00000000-0000-0000-0000-000000000003', NULL, NULL, 'SYSTEM', true, 1)
ON CONFLICT (id) DO NOTHING;

-- Seed Initial Merchants
INSERT INTO public.merchant_services (id, name, normalized_name, is_active, version)
VALUES
    ('00000000-0000-0000-0001-000000000001', 'Tambo', 'tambo', true, 1),
    ('00000000-0000-0000-0001-000000000002', 'Starbucks Coffee', 'starbucks coffee', true, 1),
    ('00000000-0000-0000-0001-000000000003', 'Plaza Vea', 'plaza vea', true, 1)
ON CONFLICT (id) DO NOTHING;

-- 7. Row Level Security (RLS)
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.category_presentations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.category_conflicts ENABLE ROW LEVEL SECURITY;

-- Categories RLS
DROP POLICY IF EXISTS p_categories_select ON public.categories;
CREATE POLICY p_categories_select ON public.categories
    FOR SELECT TO authenticated
    USING (user_id = auth.uid() OR user_id IS NULL);

DROP POLICY IF EXISTS p_categories_insert ON public.categories;
CREATE POLICY p_categories_insert ON public.categories
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS p_categories_update ON public.categories;
CREATE POLICY p_categories_update ON public.categories
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS p_categories_delete ON public.categories;
CREATE POLICY p_categories_delete ON public.categories
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Category Presentations RLS
DROP POLICY IF EXISTS p_category_presentations_all ON public.category_presentations;
CREATE POLICY p_category_presentations_all ON public.category_presentations
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- Category Conflicts RLS
DROP POLICY IF EXISTS p_category_conflicts_all ON public.category_conflicts;
CREATE POLICY p_category_conflicts_all ON public.category_conflicts
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- Merchant Services RLS
DROP POLICY IF EXISTS p_merchant_services_select ON public.merchant_services;
CREATE POLICY p_merchant_services_select ON public.merchant_services
    FOR SELECT TO authenticated
    USING (is_active = true);

-- Revoke direct DML grants on merchant_services from authenticated/anon
REVOKE INSERT, UPDATE, DELETE ON public.merchant_services FROM authenticated, anon;
GRANT SELECT ON public.merchant_services TO authenticated, anon;

-- 8. RPC: create_category_v1
CREATE OR REPLACE FUNCTION public.create_category_v1(p_payload jsonb)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_parent_id uuid := NULL;
    v_name text := trim(p_payload->>'name');
    v_icon text := p_payload->>'icon';
    v_color text := p_payload->>'color';
    v_active_custom_roots integer;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    IF p_payload ? 'parent_id' AND p_payload->>'parent_id' IS NOT NULL THEN
        v_parent_id := (p_payload->>'parent_id')::uuid;
    END IF;

    -- Validate Free quota if creating custom root
    IF v_parent_id IS NULL THEN
        SELECT COUNT(*) INTO v_active_custom_roots
        FROM public.categories
        WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = true;

        IF v_active_custom_roots >= 5 THEN
            RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    -- Insert category
    INSERT INTO public.categories (id, user_id, parent_id, origin, is_active, remote_revision)
    VALUES (v_category_id, v_user_id, v_parent_id, 'CUSTOM', true, 1);

    -- Insert presentation
    INSERT INTO public.category_presentations (category_id, user_id, name, icon, color, remote_revision)
    VALUES (v_category_id, v_user_id, v_name, v_icon, v_color, 1);

    RETURN jsonb_build_object('success', true, 'category_id', v_category_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 9. RPC: update_category_presentation_v1
CREATE OR REPLACE FUNCTION public.update_category_presentation_v1(p_payload jsonb)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_expected_revision bigint := (p_payload->>'expected_revision')::bigint;
    v_name text := trim(p_payload->>'name');
    v_icon text := p_payload->>'icon';
    v_color text := p_payload->>'color';
    v_current_revision bigint;
    v_current_presentation record;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    SELECT * INTO v_current_presentation
    FROM public.category_presentations
    WHERE user_id = v_user_id AND category_id = v_category_id;

    IF NOT FOUND THEN
        -- Insert new user-scoped presentation
        INSERT INTO public.category_presentations (category_id, user_id, name, icon, color, remote_revision)
        VALUES (v_category_id, v_user_id, v_name, v_icon, v_color, 1);
        RETURN jsonb_build_object('success', true, 'status', 'CREATED');
    END IF;

    IF v_current_presentation.remote_revision <> v_expected_revision THEN
        -- Conflict detected: persist conflict instead of overwriting!
        INSERT INTO public.category_conflicts (
            category_id, user_id, conflict_type, local_version, remote_version, status
        ) VALUES (
            v_category_id,
            v_user_id,
            'PRESENTATION',
            p_payload::text,
            row_to_json(v_current_presentation)::text,
            'OPEN'
        );
        RETURN jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;

    -- Update presentation and increment revision
    UPDATE public.category_presentations
    SET name = v_name,
        icon = v_icon,
        color = v_color,
        remote_revision = v_current_presentation.remote_revision + 1,
        updated_at = now()
    WHERE user_id = v_user_id AND category_id = v_category_id;

    RETURN jsonb_build_object('success', true, 'status', 'UPDATED');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 10. RPC: update_movement_classification_v1
CREATE OR REPLACE FUNCTION public.update_movement_classification_v1(p_payload jsonb)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_movement_id uuid := (p_payload->>'movement_id')::uuid;
    v_category_id uuid := NULL;
    v_merchant_id uuid := NULL;
    v_provisional_text text := NULL;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    IF p_payload ? 'category_id' AND p_payload->>'category_id' IS NOT NULL THEN
        v_category_id := (p_payload->>'category_id')::uuid;
    END IF;

    IF p_payload ? 'merchant_id' AND p_payload->>'merchant_id' IS NOT NULL THEN
        v_merchant_id := (p_payload->>'merchant_id')::uuid;
    END IF;

    IF p_payload ? 'merchant_provisional_text' AND p_payload->>'merchant_provisional_text' IS NOT NULL THEN
        v_provisional_text := trim(p_payload->>'merchant_provisional_text');
    END IF;

    IF v_merchant_id IS NOT NULL AND v_provisional_text IS NOT NULL THEN
        RAISE EXCEPTION 'Merchant ID and provisional text cannot coexist' USING ERRCODE = 'P0001';
    END IF;

    UPDATE public.financial_movements
    SET category_id = v_category_id,
        merchant_id = v_merchant_id,
        merchant_provisional_text = v_provisional_text
    WHERE id = v_movement_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 11. RPC: set_category_active_v1
CREATE OR REPLACE FUNCTION public.set_category_active_v1(p_payload jsonb)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_is_active boolean := (p_payload->>'is_active')::boolean;
    v_expected_revision bigint := (p_payload->>'expected_revision')::bigint;
    v_current_category record;
    v_active_custom_roots integer;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    SELECT * INTO v_current_category
    FROM public.categories
    WHERE id = v_category_id AND (user_id = v_user_id OR user_id IS NULL);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Category not found' USING ERRCODE = 'P0002';
    END IF;

    -- Lifecycle conflict check if revision does not match
    IF v_current_category.remote_revision <> v_expected_revision THEN
        INSERT INTO public.category_conflicts (
            category_id, user_id, conflict_type, local_version, remote_version, status
        ) VALUES (
            v_category_id,
            v_user_id,
            'LIFECYCLE',
            p_payload::text,
            row_to_json(v_current_category)::text,
            'OPEN'
        );
        RETURN jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;

    -- Check Free quota if activating a custom root
    IF v_current_category.parent_id IS NULL AND v_current_category.origin = 'CUSTOM' AND v_is_active AND NOT v_current_category.is_active THEN
        SELECT COUNT(*) INTO v_active_custom_roots
        FROM public.categories
        WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = true;

        IF v_active_custom_roots >= 5 THEN
            RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    UPDATE public.categories
    SET is_active = v_is_active,
        remote_revision = v_current_category.remote_revision + 1,
        updated_at = now()
    WHERE id = v_category_id;

    RETURN jsonb_build_object('success', true, 'status', 'UPDATED');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 12. RPC: resolve_category_conflict_v1
CREATE OR REPLACE FUNCTION public.resolve_category_conflict_v1(p_payload jsonb)
RETURNS jsonb AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_conflict_id uuid := (p_payload->>'conflict_id')::uuid;
    v_chosen_version text := p_payload->>'chosen_version';
    v_conflict record;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    SELECT * INTO v_conflict
    FROM public.category_conflicts
    WHERE id = v_conflict_id AND user_id = v_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conflict not found' USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.category_conflicts
    SET status = 'RESOLVED',
        resolution_operation_id = p_payload->>'operation_id',
        updated_at = now()
    WHERE id = v_conflict_id;

    RETURN jsonb_build_object('success', true, 'status', 'RESOLVED');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMIT;
