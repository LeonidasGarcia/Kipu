-- Sprint 3 closeout: harden RLS, RPC privileges and high-traffic foreign keys.
-- This migration changes privileges and indexes only; it does not modify user rows.

-- Remove the permissive merchant policy that exposes private active merchants.
DROP POLICY IF EXISTS p_merchant_services_select ON public.merchant_services;

-- Remove superseded category policies; category writes are handled by owner-checked RPCs.
DROP POLICY IF EXISTS p_categories_insert ON public.categories;
DROP POLICY IF EXISTS p_categories_select ON public.categories;
DROP POLICY IF EXISTS p_categories_update ON public.categories;
DROP POLICY IF EXISTS p_categories_delete ON public.categories;

-- Remove duplicate profile read policy; profiles_own covers SELECT and writes.
DROP POLICY IF EXISTS profiles_own_select ON public.profiles;

-- The product catalog is authenticated-only and active products only.
DROP POLICY IF EXISTS billing_products_read_all ON public.billing_products;
REVOKE ALL PRIVILEGES ON TABLE public.billing_products FROM anon;
GRANT SELECT ON TABLE public.billing_products TO authenticated;

-- Keep the stricter owner-only policy and remove its duplicate.
DROP POLICY IF EXISTS billing_purchases_select_own ON public.billing_purchases;

-- Views must evaluate underlying RLS as the calling user.
ALTER VIEW public.v_credit_summary SET (security_invoker = true);
ALTER VIEW public.v_goal_summary SET (security_invoker = true);
ALTER VIEW public.v_debt_summary SET (security_invoker = true);
ALTER VIEW public.v_obligation_summary SET (security_invoker = true);

-- Remove equivalent indexes; purchase-token uniqueness is the owner-binding invariant.
DROP INDEX IF EXISTS public.idx_merchants_normalized;
DROP INDEX IF EXISTS public.idx_billing_purchases_user_id;
DROP INDEX IF EXISTS internal.idx_billing_events_token_hash;

-- Close direct table writes. The app reads these tables with RLS and mutates them through
-- SECURITY DEFINER command RPCs (including the category quota checks).
REVOKE ALL PRIVILEGES ON TABLE
    public.accounts,
    public.cards,
    public.transactions,
    public.financial_movements,
    public.categories
FROM anon, authenticated;

GRANT SELECT ON TABLE
    public.accounts,
    public.cards,
    public.transactions,
    public.financial_movements,
    public.categories
TO authenticated;

-- Merchant catalog is read through the authenticated API; it has no anonymous use.
REVOKE ALL PRIVILEGES ON TABLE public.merchant_services FROM anon;

-- Views/legacy helpers/triggers and operational RPCs must not be executable by anon.
-- Keep explicit authenticated/service_role grants on user-scoped RPCs.
REVOKE EXECUTE ON FUNCTION internal.card_sync_projection_v1(public.cards) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION internal.emit_card_sync_projection_v1() FROM PUBLIC, anon;
DO $optional_internal_trigger_grants$
BEGIN
    IF pg_catalog.to_regprocedure('internal.trg_emit_sync_change()') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION internal.trg_emit_sync_change() FROM PUBLIC, anon';
    END IF;
    IF pg_catalog.to_regprocedure('internal.trg_set_updated_at_and_revision()') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION internal.trg_set_updated_at_and_revision() FROM PUBLIC, anon';
    END IF;
END;
$optional_internal_trigger_grants$;
DO $optional_private_email_helper$
BEGIN
    IF pg_catalog.to_regprocedure('private.check_email_exists(text)') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION private.check_email_exists(text) FROM PUBLIC, anon, authenticated, service_role';
    END IF;
END;
$optional_private_email_helper$;

REVOKE EXECUTE ON FUNCTION public.allocate_goal_funds(uuid, bigint, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.apply_sync_command(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_feature_access() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.record_debt_payment(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.record_obligation_payment(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.register_transaction(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.release_goal_funds(uuid, bigint, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_category_active_v1(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.update_category_presentation_v1(jsonb) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.update_movement_classification_v1(jsonb) FROM PUBLIC, anon;
DO $optional_project_rpcs$
DECLARE
    v_signature text;
    v_function regprocedure;
BEGIN
    FOREACH v_signature IN ARRAY ARRAY[
        'public.allocate_credit_payment(jsonb)',
        'public.get_budget_status(uuid,date)',
        'public.project_recurrence_occurrences(uuid,date)',
        'public.select_plan_capacity(text,uuid[])',
        'public.sync_pull(bigint,integer)',
        'public.verify_play_purchase(jsonb)'
    ] LOOP
        v_function := pg_catalog.to_regprocedure(v_signature);
        IF v_function IS NOT NULL THEN
            EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon', v_function);
        END IF;
    END LOOP;

    v_function := pg_catalog.to_regprocedure('public.process_rtdn_event(text,jsonb)');
    IF v_function IS NOT NULL THEN
        EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon, authenticated', v_function);
        EXECUTE pg_catalog.format('GRANT EXECUTE ON FUNCTION %s TO service_role', v_function);
    END IF;

    v_function := pg_catalog.to_regprocedure('public.trg_check_budget_alerts()');
    IF v_function IS NOT NULL THEN
        EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon, authenticated, service_role', v_function);
        EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON FUNCTION %s FROM PUBLIC, anon, authenticated, service_role', v_function);
    END IF;
END;
$optional_project_rpcs$;

-- Email enumeration is server-only; preserve the intended Edge Function access.
REVOKE ALL PRIVILEGES ON FUNCTION public.check_email_exists(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_email_exists(text) TO service_role;

-- Trigger-only helpers are not RPC endpoints. Existing triggers continue to invoke them.
REVOKE ALL PRIVILEGES ON FUNCTION internal.card_sync_projection_v1(public.cards) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL PRIVILEGES ON FUNCTION internal.emit_card_sync_projection_v1() FROM PUBLIC, anon, authenticated, service_role;
DO $optional_internal_trigger_privileges$
BEGIN
    IF pg_catalog.to_regprocedure('internal.trg_emit_sync_change()') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON FUNCTION internal.trg_emit_sync_change() FROM PUBLIC, anon, authenticated, service_role';
    END IF;
    IF pg_catalog.to_regprocedure('internal.trg_set_updated_at_and_revision()') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON FUNCTION internal.trg_set_updated_at_and_revision() FROM PUBLIC, anon, authenticated, service_role';
    END IF;
END;
$optional_internal_trigger_privileges$;

-- Pin search_path on remaining SECURITY DEFINER routines flagged by the advisor.
-- Client roles cannot CREATE in these trusted schemas; pg_temp is last to prevent shadowing.
ALTER FUNCTION public.allocate_goal_funds(uuid, bigint, uuid)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.register_transaction(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.release_goal_funds(uuid, bigint, uuid)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.apply_sync_command(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.record_obligation_payment(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.get_feature_access()
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.record_debt_payment(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.update_movement_classification_v1(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.check_category_two_levels()
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.update_category_presentation_v1(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;
ALTER FUNCTION public.set_category_active_v1(jsonb)
    SET search_path = pg_catalog, public, extensions, auth, internal, private, pg_temp;

-- Add the missing direct indexes for frequently joined/cascaded movement references.
CREATE INDEX IF NOT EXISTS idx_financial_movements_account_id
    ON public.financial_movements(account_id);
CREATE INDEX IF NOT EXISTS idx_financial_movements_card_id
    ON public.financial_movements(card_id);
CREATE INDEX IF NOT EXISTS idx_financial_movements_category_id
    ON public.financial_movements(category_id);
CREATE INDEX IF NOT EXISTS idx_financial_movements_opening_account_id
    ON public.financial_movements(opening_account_id);
