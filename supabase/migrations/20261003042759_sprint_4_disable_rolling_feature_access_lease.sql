-- The signed offline entitlement grant is issued by verify-purchase.
-- This legacy response remains compatible but must never mint a rolling lease.
CREATE OR REPLACE FUNCTION public.get_feature_access()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid;
    v_access record;
    v_active_devices integer;
    v_active_budgets integer;
    v_active_debts integer;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated';
    END IF;

    SELECT * INTO v_access
    FROM public.v_feature_access
    WHERE user_id = v_user_id;

    SELECT count(*) INTO v_active_devices
    FROM public.user_devices
    WHERE user_id = v_user_id AND is_active = true;

    SELECT count(*) INTO v_active_budgets
    FROM public.budgets
    WHERE user_id = v_user_id AND is_active = true AND deleted_at IS NULL;

    SELECT count(*) INTO v_active_debts
    FROM public.debts
    WHERE user_id = v_user_id AND status = 'ACTIVE' AND deleted_at IS NULL;

    RETURN pg_catalog.jsonb_build_object(
        'user_id', v_user_id,
        'plan_tier', v_access.plan_tier,
        'offline_valid_until', NULL::timestamptz,
        'limits', pg_catalog.jsonb_build_object(
            'devices', pg_catalog.jsonb_build_object('allowed', v_access.max_devices, 'used', v_active_devices),
            'budgets', pg_catalog.jsonb_build_object('allowed', v_access.max_budgets, 'used', v_active_budgets),
            'debts', pg_catalog.jsonb_build_object('allowed', v_access.max_obligations, 'used', v_active_debts)
        )
    );
END;
$function$;

REVOKE ALL ON FUNCTION public.get_feature_access() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_feature_access() TO authenticated;
