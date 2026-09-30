-- Enforce the custom-root category quota only for Free entitlements and serialize concurrent mutations.
CREATE OR REPLACE FUNCTION public.create_category_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
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
    v_plan_tier text;
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
        v_plan_tier := COALESCE(
            (SELECT fa.plan_tier FROM public.v_feature_access AS fa WHERE fa.user_id = v_user_id),
            'FREE'
        );
        IF v_plan_tier <> 'PRO' THEN
            PERFORM pg_catalog.pg_advisory_xact_lock(
                pg_catalog.hashtextextended('kipu:category-root-quota:' || v_user_id::text, 0)
            );
            SELECT COUNT(*) INTO v_active_custom_roots
            FROM public.categories
            WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active;
            IF v_active_custom_roots >= 5 THEN
                RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories'
                    USING ERRCODE = 'P0001';
            END IF;
        END IF;
    ELSE
        SELECT * INTO v_parent
        FROM public.categories
        WHERE id = v_parent_id AND (user_id = v_user_id OR user_id IS NULL)
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
    ) VALUES (v_category_id, v_user_id, v_parent_id, v_name, 'CUSTOM', true, 1, v_category_type);
    INSERT INTO public.category_presentations (
        category_id, user_id, name, icon, color, remote_revision
    ) VALUES (v_category_id, v_user_id, v_name, v_icon, v_color, 1);
    RETURN jsonb_build_object('success', true, 'category_id', v_category_id);
END;
$function$;

CREATE OR REPLACE FUNCTION public.set_category_active_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_is_active boolean := (p_payload->>'is_active')::boolean;
    v_expected_revision bigint := (p_payload->>'expected_revision')::bigint;
    v_current_category record;
    v_active_custom_roots integer;
    v_plan_tier text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    SELECT * INTO v_current_category FROM public.categories
    WHERE id = v_category_id AND (user_id = v_user_id OR user_id IS NULL);
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Category not found' USING ERRCODE = 'P0002';
    END IF;

    IF v_current_category.remote_revision <> v_expected_revision THEN
        INSERT INTO public.category_conflicts (
            category_id, user_id, conflict_type, local_version, remote_version, status
        ) VALUES (
            v_category_id, v_user_id, 'LIFECYCLE', p_payload::text,
            row_to_json(v_current_category)::text, 'OPEN'
        );
        RETURN jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;

    IF v_current_category.parent_id IS NULL AND v_current_category.origin = 'CUSTOM'
       AND v_is_active AND NOT v_current_category.is_active THEN
        v_plan_tier := COALESCE(
            (SELECT fa.plan_tier FROM public.v_feature_access AS fa WHERE fa.user_id = v_user_id),
            'FREE'
        );
        IF v_plan_tier <> 'PRO' THEN
            PERFORM pg_catalog.pg_advisory_xact_lock(
                pg_catalog.hashtextextended('kipu:category-root-quota:' || v_user_id::text, 0)
            );
            SELECT COUNT(*) INTO v_active_custom_roots
            FROM public.categories
            WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = true;
            IF v_active_custom_roots >= 5 THEN
                RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories'
                    USING ERRCODE = 'P0001';
            END IF;
        END IF;
    END IF;

    UPDATE public.categories
    SET is_active = v_is_active,
        remote_revision = v_current_category.remote_revision + 1,
        updated_at = now()
    WHERE id = v_category_id;
    RETURN jsonb_build_object('success', true, 'status', 'UPDATED');
END;
$function$;
