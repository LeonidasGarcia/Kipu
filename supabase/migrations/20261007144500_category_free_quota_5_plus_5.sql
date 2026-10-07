-- Incremental migration: Enforce approved 5+5 Free Category Quota (5 EXPENSE + 5 INCOME).
-- Approved product authority (2026-10-07):
-- - Free plan allows up to 5 active custom roots for EXPENSE and 5 active custom roots for INCOME.
-- - Subcategories and SYSTEM categories are exempt (even if customized).
-- - Each active custom root of type GENERAL consumes 1 slot in BOTH quotas (1 in expense, 1 in income).
-- - Replaces missing public.v_feature_access reference with entitlement checks via billing_purchases.
-- - Ensures idempotent retries for create_category_v1 and protects SYSTEM categories in set_category_active_v1.

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
    v_is_pro boolean := false;
    v_expense_count integer;
    v_income_count integer;
    v_general_count integer;
    v_existing public.categories%ROWTYPE;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF v_category_id IS NULL OR v_name IS NULL OR v_name = '' THEN
        RAISE EXCEPTION 'Category id and name are required' USING ERRCODE = '22023';
    END IF;

    -- Idempotent retry check: if category already exists under same owner, return success
    SELECT * INTO v_existing FROM public.categories WHERE id = v_category_id;
    IF FOUND THEN
        IF v_existing.user_id = v_user_id THEN
            RETURN jsonb_build_object('success', true, 'category_id', v_category_id);
        ELSE
            RAISE EXCEPTION 'Category already exists under another owner' USING ERRCODE = '23505';
        END IF;
    END IF;

    IF v_parent_id IS NULL THEN
        v_category_type := COALESCE(v_requested_type, 'GENERAL');
        IF v_category_type NOT IN ('EXPENSE', 'INCOME', 'GENERAL') THEN
            RAISE EXCEPTION 'Invalid category type: %', v_category_type USING ERRCODE = '22023';
        END IF;

        -- Check PRO entitlement via billing_purchases or legacy v_feature_access if present
        IF to_regclass('public.v_feature_access') IS NOT NULL THEN
            BEGIN
                EXECUTE 'SELECT EXISTS (SELECT 1 FROM public.v_feature_access WHERE user_id = $1 AND plan_tier = ''PRO'')'
                INTO v_is_pro
                USING v_user_id;
            EXCEPTION WHEN OTHERS THEN
                v_is_pro := false;
            END;
        END IF;

        IF NOT v_is_pro AND to_regclass('public.billing_purchases') IS NOT NULL THEN
            SELECT EXISTS (
                SELECT 1 FROM public.billing_purchases p
                JOIN public.billing_products prod ON prod.id = p.product_id
                WHERE p.user_id = v_user_id
                  AND prod.plan_type IN ('PRO_MONTHLY', 'PRO_ANNUAL', 'PRO_LIFETIME')
                  AND (
                      (p.purchase_state = 'PURCHASED' AND prod.plan_type = 'PRO_LIFETIME'
                       AND p.entitlement_state = 'ACTIVE' AND p.expires_at IS NULL)
                      OR (p.purchase_state = 'PURCHASED' AND p.entitlement_state IN ('ACTIVE', 'IN_GRACE_PERIOD')
                          AND (p.expires_at IS NULL OR p.expires_at > clock_timestamp()))
                      OR (p.purchase_state = 'CANCELLED' AND p.entitlement_state = 'CANCELED_ACTIVE'
                          AND (p.expires_at IS NULL OR p.expires_at > clock_timestamp()))
                  )
            ) INTO v_is_pro;
        END IF;

        IF NOT v_is_pro THEN
            PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('kipu:category-root-quota:' || v_user_id::text, 0));

            SELECT
                count(*) FILTER (WHERE category_type = 'EXPENSE'),
                count(*) FILTER (WHERE category_type = 'INCOME'),
                count(*) FILTER (WHERE category_type = 'GENERAL')
            INTO v_expense_count, v_income_count, v_general_count
            FROM public.categories
            WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active;

            IF (v_category_type = 'EXPENSE' AND (v_expense_count + v_general_count) >= 5) OR
               (v_category_type = 'INCOME' AND (v_income_count + v_general_count) >= 5) OR
               (v_category_type = 'GENERAL' AND ((v_expense_count + v_general_count) >= 5 OR (v_income_count + v_general_count) >= 5)) THEN
                RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories per type' USING ERRCODE = 'P0001';
            END IF;
        END IF;
    ELSE
        SELECT * INTO v_parent FROM public.categories
        WHERE id = v_parent_id AND (user_id = v_user_id OR user_id IS NULL) FOR KEY SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Parent category not found' USING ERRCODE = 'P0002'; END IF;
        IF v_parent.parent_id IS NOT NULL THEN RAISE EXCEPTION 'Category hierarchy cannot exceed two levels' USING ERRCODE = 'P0001'; END IF;
        IF v_requested_type IS NOT NULL AND v_requested_type <> v_parent.category_type THEN
            RAISE EXCEPTION 'Subcategory type must match its root category' USING ERRCODE = '23514';
        END IF;
        v_category_type := v_parent.category_type;
    END IF;

    INSERT INTO public.categories (id, user_id, parent_id, name, origin, is_active, remote_revision, category_type)
    VALUES (v_category_id, v_user_id, v_parent_id, v_name, 'CUSTOM', true, 1, v_category_type)
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.category_presentations (category_id, user_id, name, icon, color, remote_revision)
    VALUES (v_category_id, v_user_id, v_name, v_icon, v_color, 1)
    ON CONFLICT (category_id, user_id) DO NOTHING;

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
    v_is_pro boolean := false;
    v_expense_count integer;
    v_income_count integer;
    v_general_count integer;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000'; END IF;
    SELECT * INTO v_current_category FROM public.categories
    WHERE id = v_category_id AND (user_id = v_user_id OR user_id IS NULL);
    IF NOT FOUND THEN RAISE EXCEPTION 'Category not found' USING ERRCODE = 'P0002'; END IF;

    IF v_current_category.user_id IS NULL AND v_current_category.origin = 'SYSTEM' THEN
        RAISE EXCEPTION 'Cannot modify system category lifecycle directly' USING ERRCODE = '42501';
    END IF;

    IF v_current_category.remote_revision <> v_expected_revision THEN
        INSERT INTO public.category_conflicts (category_id, user_id, conflict_type, local_version, remote_version, status)
        VALUES (v_category_id, v_user_id, 'LIFECYCLE', p_payload::text, row_to_json(v_current_category)::text, 'OPEN');
        RETURN jsonb_build_object('success', false, 'status', 'CONFLICT');
    END IF;

    IF v_current_category.parent_id IS NULL AND v_current_category.origin = 'CUSTOM'
       AND v_is_active AND NOT v_current_category.is_active THEN

        IF to_regclass('public.v_feature_access') IS NOT NULL THEN
            BEGIN
                EXECUTE 'SELECT EXISTS (SELECT 1 FROM public.v_feature_access WHERE user_id = $1 AND plan_tier = ''PRO'')'
                INTO v_is_pro
                USING v_user_id;
            EXCEPTION WHEN OTHERS THEN
                v_is_pro := false;
            END;
        END IF;

        IF NOT v_is_pro AND to_regclass('public.billing_purchases') IS NOT NULL THEN
            SELECT EXISTS (
                SELECT 1 FROM public.billing_purchases p
                JOIN public.billing_products prod ON prod.id = p.product_id
                WHERE p.user_id = v_user_id
                  AND prod.plan_type IN ('PRO_MONTHLY', 'PRO_ANNUAL', 'PRO_LIFETIME')
                  AND (
                      (p.purchase_state = 'PURCHASED' AND prod.plan_type = 'PRO_LIFETIME'
                       AND p.entitlement_state = 'ACTIVE' AND p.expires_at IS NULL)
                      OR (p.purchase_state = 'PURCHASED' AND p.entitlement_state IN ('ACTIVE', 'IN_GRACE_PERIOD')
                          AND (p.expires_at IS NULL OR p.expires_at > clock_timestamp()))
                      OR (p.purchase_state = 'CANCELLED' AND p.entitlement_state = 'CANCELED_ACTIVE'
                          AND (p.expires_at IS NULL OR p.expires_at > clock_timestamp()))
                  )
            ) INTO v_is_pro;
        END IF;

        IF NOT v_is_pro THEN
            PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('kipu:category-root-quota:' || v_user_id::text, 0));

            SELECT
                count(*) FILTER (WHERE category_type = 'EXPENSE'),
                count(*) FILTER (WHERE category_type = 'INCOME'),
                count(*) FILTER (WHERE category_type = 'GENERAL')
            INTO v_expense_count, v_income_count, v_general_count
            FROM public.categories
            WHERE user_id = v_user_id AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active;

            IF (v_current_category.category_type = 'EXPENSE' AND (v_expense_count + v_general_count) >= 5) OR
               (v_current_category.category_type = 'INCOME' AND (v_income_count + v_general_count) >= 5) OR
               (v_current_category.category_type = 'GENERAL' AND ((v_expense_count + v_general_count) >= 5 OR (v_income_count + v_general_count) >= 5)) THEN
                RAISE EXCEPTION 'Free plan limit reached: maximum 5 active custom root categories per type' USING ERRCODE = 'P0001';
            END IF;
        END IF;
    END IF;

    UPDATE public.categories
    SET is_active = v_is_active, remote_revision = v_current_category.remote_revision + 1, updated_at = now()
    WHERE id = v_category_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true, 'status', 'UPDATED');
END;
$function$;

GRANT CREATE ON SCHEMA public TO plan_quota_selection_executor;
GRANT CREATE ON SCHEMA private TO plan_quota_selection_executor;
GRANT plan_quota_selection_executor TO postgres WITH SET TRUE;
SET LOCAL ROLE plan_quota_selection_executor;

CREATE OR REPLACE FUNCTION public.apply_plan_quota_selection(
    p_contract_version integer, p_operation_id uuid, p_feature_key text, p_selection_revision bigint, p_items jsonb
)
RETURNS TABLE(contract_version smallint, operation_id uuid, result text, feature_key text, accepted_revision text, current_items jsonb, server_time text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    uid uuid := private.request_user_id();
    feature text := upper(p_feature_key);
    item jsonb;
    resource uuid;
    kind text;
    allowed integer;
    unique_resources integer;
    expense_resources integer;
    income_resources integer;
    general_resources integer;
    head bigint;
    known private.plan_quota_selection_receipts;
    canonical jsonb;
    hash bytea;
    outcome text;
    stamp timestamptz := clock_timestamp();
BEGIN
    IF uid IS NULL THEN RAISE insufficient_privilege; END IF;
    IF p_contract_version <> 1 OR p_selection_revision < 1 OR p_items IS NULL OR jsonb_typeof(p_items) <> 'array' THEN RAISE data_exception; END IF;
    allowed := CASE feature WHEN 'INSTRUMENTS' THEN 4 WHEN 'CUSTOM_CATEGORIES' THEN 10
        WHEN 'DEBTS' THEN 2 WHEN 'GOALS' THEN 2 WHEN 'BUDGETS' THEN 2 ELSE NULL END;
    IF allowed IS NULL THEN RAISE data_exception USING MESSAGE = 'Unknown quota group'; END IF;
    IF jsonb_array_length(p_items) > allowed THEN RAISE data_exception USING MESSAGE = 'Selection exceeds Free quota'; END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object('resource_id', resource_id, 'resource_type', resource_type) ORDER BY resource_id), '[]'::jsonb)
      INTO canonical
      FROM (
        SELECT DISTINCT (value->>'resource_id')::uuid AS resource_id, upper(value->>'resource_type') AS resource_type
        FROM jsonb_array_elements(p_items) AS rows(value)
        WHERE jsonb_typeof(value) = 'object'
          AND (SELECT count(*) FROM jsonb_object_keys(value)) = 2
          AND value ? 'resource_id' AND value ? 'resource_type'
      ) normalized;
    SELECT count(DISTINCT (value->>'resource_id')::uuid) INTO unique_resources
      FROM jsonb_array_elements(p_items) AS rows(value)
     WHERE jsonb_typeof(value) = 'object' AND value ? 'resource_id';
    IF jsonb_array_length(canonical) <> jsonb_array_length(p_items) OR unique_resources <> jsonb_array_length(p_items) THEN
        RAISE data_exception USING MESSAGE = 'Malformed or duplicate selection item';
    END IF;

    FOR item IN SELECT value FROM jsonb_array_elements(canonical) AS rows(value) LOOP
        resource := (item->>'resource_id')::uuid;
        kind := item->>'resource_type';
        IF NOT (
            (feature = 'INSTRUMENTS' AND kind = 'ACCOUNT' AND EXISTS (
                SELECT 1 FROM public.accounts a WHERE a.id = resource AND a.user_id = uid AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL
            )) OR
            (feature = 'INSTRUMENTS' AND kind = 'CARD' AND EXISTS (
                SELECT 1 FROM public.cards c WHERE c.id = resource AND c.user_id = uid AND NOT c.is_archived AND c.deleted_at IS NULL
            )) OR
            (feature = 'CUSTOM_CATEGORIES' AND kind = 'CATEGORY_ROOT' AND EXISTS (
                SELECT 1 FROM public.categories c WHERE c.id = resource AND c.user_id = uid AND c.parent_id IS NULL AND c.origin = 'CUSTOM' AND c.is_active AND c.deleted_at IS NULL
            )) OR
            (feature = 'DEBTS' AND kind = 'DEBT' AND EXISTS (
                SELECT 1 FROM public.debts d WHERE d.id = resource AND d.user_id = uid AND d.status = 'ACTIVE' AND d.deleted_at IS NULL
            )) OR
            (feature = 'GOALS' AND kind = 'GOAL' AND EXISTS (
                SELECT 1 FROM public.goals g WHERE g.id = resource AND g.user_id = uid AND g.status = 'ACTIVE' AND g.deleted_at IS NULL
            )) OR
            (feature = 'BUDGETS' AND kind = 'BUDGET' AND EXISTS (
                SELECT 1 FROM public.budgets b WHERE b.id = resource AND b.user_id = uid AND b.is_active AND b.deleted_at IS NULL
            ))
        ) THEN RAISE data_exception USING MESSAGE = 'Selection contains an ineligible resource'; END IF;
    END LOOP;

    IF feature = 'CUSTOM_CATEGORIES' THEN
        SELECT
            count(*) FILTER (WHERE c.category_type::text = 'EXPENSE'),
            count(*) FILTER (WHERE c.category_type::text = 'INCOME'),
            count(*) FILTER (WHERE c.category_type::text = 'GENERAL')
        INTO expense_resources, income_resources, general_resources
        FROM jsonb_array_elements(canonical) AS rows(value)
        JOIN public.categories c ON c.id = (value->>'resource_id')::uuid;

        IF (expense_resources + general_resources) > 5 OR (income_resources + general_resources) > 5 THEN
            RAISE data_exception USING MESSAGE = 'Selection exceeds the five-root Free category limit per type';
        END IF;
    END IF;

    hash := private.plan_quota_selection_hash(p_contract_version, p_operation_id, feature, p_selection_revision, canonical);
    INSERT INTO private.plan_quota_selection_heads(user_id, feature_key) VALUES (uid, feature) ON CONFLICT DO NOTHING;
    SELECT h.accepted_revision INTO head FROM private.plan_quota_selection_heads h WHERE h.user_id = uid AND h.feature_key = feature FOR UPDATE;
    SELECT * INTO known FROM private.plan_quota_selection_receipts r WHERE r.user_id = uid AND r.operation_id = p_operation_id;
    IF known.operation_id IS NOT NULL THEN
        outcome := CASE WHEN known.payload_hash = hash AND known.first_result = 'APPLIED' THEN 'DUPLICATE'
            WHEN known.payload_hash = hash THEN known.first_result ELSE 'CONFLICT' END;
    ELSE
        outcome := CASE WHEN p_selection_revision > head THEN 'APPLIED' ELSE 'STALE' END;
        IF outcome = 'APPLIED' THEN
            DELETE FROM private.plan_quota_selection_items i WHERE i.user_id = uid AND i.feature_key = feature;
            INSERT INTO private.plan_quota_selection_items(user_id, feature_key, resource_id, resource_type, created_at)
            SELECT uid, feature, (value->>'resource_id')::uuid, value->>'resource_type', stamp
            FROM jsonb_array_elements(canonical) AS rows(value);
            UPDATE private.plan_quota_selection_heads h SET accepted_revision = p_selection_revision, updated_at = stamp
             WHERE h.user_id = uid AND h.feature_key = feature;
            head := p_selection_revision;
        END IF;
        INSERT INTO private.plan_quota_selection_receipts(user_id, operation_id, feature_key, contract_version,
            selection_revision, payload_hash, first_result, accepted_revision_at_first_seen, first_seen_at)
        VALUES (uid, p_operation_id, feature, p_contract_version, p_selection_revision, hash, outcome, head, stamp);
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object('resource_id', i.resource_id, 'resource_type', i.resource_type) ORDER BY i.resource_id), '[]'::jsonb)
      INTO canonical FROM private.plan_quota_selection_items i WHERE i.user_id = uid AND i.feature_key = feature;
    RETURN QUERY SELECT 1::smallint, p_operation_id, outcome, feature, head::text, canonical,
        to_char(stamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
END;
$function$;

CREATE OR REPLACE FUNCTION private.is_plan_quota_resource_locked(p_user_id uuid, p_feature_key text, p_resource_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    feature text := upper(p_feature_key);
    target_id uuid := p_resource_id;
    active_count integer;
    allowed integer;
    selected boolean;
    resource_active boolean;
    root_id uuid;
    root_type text;
    expense_count integer;
    income_count integer;
    general_count integer;
BEGIN
    IF feature = 'INSTRUMENTS' THEN
        SELECT EXISTS (SELECT 1 FROM public.accounts a WHERE a.id = target_id AND a.user_id = p_user_id
            AND a.account_type::text NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL) INTO resource_active;
        IF NOT resource_active THEN
            SELECT EXISTS (SELECT 1 FROM public.cards c WHERE c.id = target_id AND c.user_id = p_user_id
                AND NOT c.is_archived AND c.deleted_at IS NULL) INTO resource_active;
        END IF;
        SELECT (SELECT count(*) FROM public.accounts a WHERE a.user_id = p_user_id
                    AND a.account_type::text NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL)
             + (SELECT count(*) FROM public.cards c WHERE c.user_id = p_user_id AND NOT c.is_archived AND c.deleted_at IS NULL)
        INTO active_count;
        allowed := 4;
        IF EXISTS (SELECT 1 FROM public.accounts a WHERE a.id = target_id AND a.user_id = p_user_id AND a.account_type::text = 'CASH') THEN RETURN false; END IF;
        IF NOT resource_active OR active_count <= allowed THEN RETURN false; END IF;
        SELECT EXISTS (
            SELECT 1 FROM (
                SELECT i.resource_id FROM private.plan_quota_selection_items i
                WHERE i.user_id = p_user_id AND i.feature_key = feature
                ORDER BY i.resource_id
                LIMIT allowed
            ) selected_items WHERE selected_items.resource_id = target_id
        ) INTO selected;
        RETURN NOT selected;

    ELSIF feature = 'CUSTOM_CATEGORIES' THEN
        SELECT COALESCE(c.parent_id, c.id), root.category_type::text
        INTO root_id, root_type
        FROM public.categories c
        JOIN public.categories root ON root.id = COALESCE(c.parent_id, c.id)
        WHERE c.id = target_id AND (c.user_id = p_user_id OR c.user_id IS NULL);

        IF root_id IS NULL THEN RETURN false; END IF;

        SELECT EXISTS (SELECT 1 FROM public.categories c WHERE c.id = root_id AND c.user_id = p_user_id
            AND c.parent_id IS NULL AND c.origin = 'CUSTOM' AND c.is_active AND c.deleted_at IS NULL) INTO resource_active;

        IF NOT resource_active THEN RETURN false; END IF;

        SELECT
            count(*) FILTER (WHERE c.category_type::text = 'EXPENSE'),
            count(*) FILTER (WHERE c.category_type::text = 'INCOME'),
            count(*) FILTER (WHERE c.category_type::text = 'GENERAL')
        INTO expense_count, income_count, general_count
        FROM public.categories c
        WHERE c.user_id = p_user_id AND c.parent_id IS NULL AND c.origin = 'CUSTOM'
          AND c.is_active AND c.deleted_at IS NULL;

        target_id := root_id;
        IF root_type = 'EXPENSE' THEN
            IF (expense_count + general_count) <= 5 THEN RETURN false; END IF;
        ELSIF root_type = 'INCOME' THEN
            IF (income_count + general_count) <= 5 THEN RETURN false; END IF;
        ELSIF root_type = 'GENERAL' THEN
            IF (expense_count + general_count) <= 5 AND (income_count + general_count) <= 5 THEN RETURN false; END IF;
        ELSE
            RETURN false;
        END IF;

        SELECT EXISTS (
            SELECT 1 FROM private.plan_quota_selection_items i
            WHERE i.user_id = p_user_id AND i.feature_key = feature AND i.resource_id = target_id
        ) INTO selected;
        RETURN NOT selected;
    ELSE
        RETURN false;
    END IF;
END;
$function$;

RESET ROLE;
GRANT plan_quota_selection_executor TO postgres WITH SET FALSE;
REVOKE CREATE ON SCHEMA public FROM plan_quota_selection_executor;
REVOKE CREATE ON SCHEMA private FROM plan_quota_selection_executor;
