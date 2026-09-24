BEGIN;

-- Existing categories have no reliable historical expense/income evidence.
-- Keep their transaction links untouched and expose them in both selectors as GENERAL.
ALTER TABLE public.categories
    ADD COLUMN IF NOT EXISTS category_type text DEFAULT 'GENERAL';

UPDATE public.categories
SET category_type = 'GENERAL'
WHERE category_type IS NULL;

ALTER TABLE public.categories
    ALTER COLUMN category_type SET DEFAULT 'GENERAL',
    ALTER COLUMN category_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.categories'::regclass
          AND conname = 'chk_categories_category_type'
    ) THEN
        ALTER TABLE public.categories
            ADD CONSTRAINT chk_categories_category_type
            CHECK (category_type IN ('EXPENSE', 'INCOME', 'GENERAL'));
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_categories_category_type
    ON public.categories(category_type);

CREATE OR REPLACE FUNCTION private.inherit_category_type_from_root()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    v_parent public.categories%ROWTYPE;
BEGIN
    IF NEW.category_type IS NULL OR NEW.category_type NOT IN ('EXPENSE', 'INCOME', 'GENERAL') THEN
        RAISE EXCEPTION 'Invalid category type: %', NEW.category_type
            USING ERRCODE = '23514';
    END IF;

    IF NEW.parent_id IS NULL THEN
        IF TG_OP = 'UPDATE'
           AND OLD.category_type IS DISTINCT FROM NEW.category_type
           AND EXISTS (SELECT 1 FROM public.categories child WHERE child.parent_id = NEW.id) THEN
            RAISE EXCEPTION 'A root category with children cannot change type'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO v_parent
    FROM public.categories
    WHERE id = NEW.parent_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Parent category does not exist: %', NEW.parent_id
            USING ERRCODE = '23503';
    END IF;

    IF v_parent.parent_id IS NOT NULL THEN
        RAISE EXCEPTION 'Category hierarchy cannot exceed two levels'
            USING ERRCODE = '23514';
    END IF;

    IF v_parent.user_id IS NOT NULL AND NEW.user_id IS DISTINCT FROM v_parent.user_id THEN
        RAISE EXCEPTION 'Parent category belongs to another owner'
            USING ERRCODE = '23514';
    END IF;

    -- Root type is authoritative. The RPC rejects an explicit mismatch; this trigger
    -- also keeps direct/legacy inserts from persisting an inconsistent child type.
    NEW.category_type := v_parent.category_type;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_inherit_category_type_from_root ON public.categories;
CREATE TRIGGER trg_inherit_category_type_from_root
BEFORE INSERT OR UPDATE OF parent_id, category_type, user_id
ON public.categories
FOR EACH ROW
EXECUTE FUNCTION private.inherit_category_type_from_root();

CREATE OR REPLACE FUNCTION public.create_category_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_parent_id uuid := NULLIF(p_payload->>'parent_id', '')::uuid;
    v_requested_type text := NULLIF(upper(trim(p_payload->>'category_type')), '');
    v_category_type text;
    v_name text := trim(p_payload->>'name');
    v_icon text := p_payload->>'icon';
    v_color text := p_payload->>'color';
    v_parent public.categories%ROWTYPE;
    v_active_custom_roots integer;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;

    IF v_category_id IS NULL OR v_name IS NULL OR v_name = '' THEN
        RAISE EXCEPTION 'Category id and name are required' USING ERRCODE = '22023';
    END IF;

    IF v_parent_id IS NULL THEN
        v_category_type := COALESCE(v_requested_type, 'GENERAL');
        IF v_category_type NOT IN ('EXPENSE', 'INCOME', 'GENERAL') THEN
            RAISE EXCEPTION 'Invalid category type: %', v_category_type USING ERRCODE = '22023';
        END IF;

        SELECT COUNT(*) INTO v_active_custom_roots
        FROM public.categories
        WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active;

        IF v_active_custom_roots >= 5 THEN
            RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories'
                USING ERRCODE = 'P0001';
        END IF;
    ELSE
        SELECT * INTO v_parent
        FROM public.categories
        WHERE id = v_parent_id
          AND (user_id = v_user_id OR user_id IS NULL)
        FOR KEY SHARE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Parent category not found' USING ERRCODE = 'P0002';
        END IF;
        IF v_parent.parent_id IS NOT NULL THEN
            RAISE EXCEPTION 'Category hierarchy cannot exceed two levels' USING ERRCODE = 'P0001';
        END IF;
        IF v_requested_type IS NOT NULL AND v_requested_type <> v_parent.category_type THEN
            RAISE EXCEPTION 'Subcategory type must match its root category' USING ERRCODE = '23514';
        END IF;
        v_category_type := v_parent.category_type;
    END IF;

    INSERT INTO public.categories (
        id, user_id, parent_id, name, origin, is_active, remote_revision, category_type
    ) VALUES (
        v_category_id, v_user_id, v_parent_id, v_name, 'CUSTOM', true, 1, v_category_type
    );

    INSERT INTO public.category_presentations (
        category_id, user_id, name, icon, color, remote_revision
    ) VALUES (
        v_category_id, v_user_id, v_name, v_icon, v_color, 1
    );

    RETURN jsonb_build_object('success', true, 'category_id', v_category_id);
END;
$$;

REVOKE ALL ON FUNCTION public.create_category_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_category_v1(jsonb) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION private.validate_transaction_category_type()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    v_category_type text;
    v_transaction_type text := NEW.transaction_type::text;
BEGIN
    IF v_transaction_type = 'TRANSFER' AND NEW.category_id IS NOT NULL THEN
        RAISE EXCEPTION 'Transfers cannot have a category'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.category_id IS NULL OR v_transaction_type NOT IN ('EXPENSE', 'INCOME') THEN
        RETURN NEW;
    END IF;

    SELECT category_type INTO v_category_type
    FROM public.categories
    WHERE id = NEW.category_id;

    IF FOUND AND v_category_type NOT IN ('GENERAL', v_transaction_type) THEN
        RAISE EXCEPTION 'Category type % is incompatible with transaction type %',
            v_category_type, v_transaction_type
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_transaction_category_type ON public.transactions;
CREATE TRIGGER trg_validate_transaction_category_type
BEFORE INSERT OR UPDATE OF category_id, transaction_type
ON public.transactions
FOR EACH ROW
EXECUTE FUNCTION private.validate_transaction_category_type();

COMMIT;
