-- Match server-side Free category locks with the five-root limit for each category type.
GRANT CREATE ON SCHEMA private TO plan_quota_selection_executor;
GRANT plan_quota_selection_executor TO postgres WITH SET TRUE;
SET LOCAL ROLE plan_quota_selection_executor;

CREATE OR REPLACE FUNCTION private.is_plan_quota_resource_locked(
    p_user_id uuid,
    p_feature_key text,
    p_resource_id uuid
)
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
BEGIN
    IF feature = 'INSTRUMENTS' THEN
        SELECT EXISTS (
            SELECT 1 FROM public.accounts a
            WHERE a.id = target_id AND a.user_id = p_user_id
              AND a.account_type::text NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')
              AND NOT a.is_archived AND a.deleted_at IS NULL
        ) INTO resource_active;
        IF NOT resource_active THEN
            SELECT EXISTS (
                SELECT 1 FROM public.cards c
                WHERE c.id = target_id AND c.user_id = p_user_id
                  AND NOT c.is_archived AND c.deleted_at IS NULL
            ) INTO resource_active;
        END IF;
        SELECT
            (SELECT count(*) FROM public.accounts a WHERE a.user_id = p_user_id
                AND a.account_type::text NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')
                AND NOT a.is_archived AND a.deleted_at IS NULL)
            + (SELECT count(*) FROM public.cards c WHERE c.user_id = p_user_id
                AND NOT c.is_archived AND c.deleted_at IS NULL)
        INTO active_count;
        allowed := 4;
        IF EXISTS (
            SELECT 1 FROM public.accounts a
            WHERE a.id = target_id AND a.user_id = p_user_id AND a.account_type::text = 'CASH'
        ) THEN
            RETURN false;
        END IF;
    ELSIF feature = 'CUSTOM_CATEGORIES' THEN
        SELECT COALESCE(c.parent_id, c.id), root.category_type::text
        INTO root_id, root_type
        FROM public.categories c
        JOIN public.categories root ON root.id = COALESCE(c.parent_id, c.id)
        WHERE c.id = target_id AND (c.user_id = p_user_id OR c.user_id IS NULL);
        IF root_id IS NULL THEN RETURN false; END IF;
        SELECT EXISTS (
            SELECT 1 FROM public.categories c
            WHERE c.id = root_id AND c.user_id = p_user_id AND c.parent_id IS NULL
              AND c.origin = 'CUSTOM' AND c.is_active AND c.deleted_at IS NULL
        ) INTO resource_active;
        SELECT count(*) INTO active_count
        FROM public.categories c
        WHERE c.user_id = p_user_id AND c.parent_id IS NULL AND c.origin = 'CUSTOM'
          AND c.is_active AND c.deleted_at IS NULL AND c.category_type::text = root_type;
        allowed := 5;
        target_id := root_id;
    ELSE
        RETURN false;
    END IF;

    IF NOT resource_active OR active_count <= allowed THEN RETURN false; END IF;
    SELECT EXISTS (
        SELECT 1 FROM private.plan_quota_selection_items i
        WHERE i.user_id = p_user_id AND i.feature_key = feature AND i.resource_id = target_id
    ) INTO selected;
    RETURN NOT selected;
END;
$function$;

RESET ROLE;
GRANT plan_quota_selection_executor TO postgres WITH SET FALSE;
REVOKE CREATE ON SCHEMA private FROM plan_quota_selection_executor;
