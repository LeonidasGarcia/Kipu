-- Precondition check: Ensure no legacy biometric_enabled=true exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'profiles'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'biometric_enabled'
        ) THEN
            IF EXISTS (
                SELECT 1 FROM public.profiles WHERE biometric_enabled = true
            ) THEN
                RAISE EXCEPTION 'Aborting migration: active legacy biometric_enabled=true detected. Manual remediation required.';
            END IF;
        END IF;
    END IF;
END $$;

-- 1. Create or update public.profiles baseline
CREATE TABLE IF NOT EXISTS public.profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text NOT NULL DEFAULT '',
    currency_code char(3) NOT NULL DEFAULT 'PEN',
    time_zone text,
    month_start smallint NOT NULL DEFAULT 1 CHECK (month_start >= 1 AND month_start <= 28),
    hide_balances boolean NOT NULL DEFAULT false,
    theme_mode text NOT NULL DEFAULT 'SYSTEM' CHECK (theme_mode IN ('SYSTEM', 'LIGHT', 'DARK')),
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    preferences jsonb NOT NULL DEFAULT '{}'::jsonb,
    biometric_enabled boolean NOT NULL DEFAULT false
);

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;

-- Backfill existing Auth users into public.profiles
INSERT INTO public.profiles (user_id)
SELECT id FROM auth.users
ON CONFLICT (user_id) DO NOTHING;

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles FORCE ROW LEVEL SECURITY;

-- 2. Create private.profile_preference_receipts for idempotent replays and conflict detection
CREATE SCHEMA IF NOT EXISTS private;

CREATE TABLE IF NOT EXISTS private.profile_preference_receipts (
    user_id uuid NOT NULL,
    operation_id uuid NOT NULL,
    payload_hash bytea NOT NULL,
    result jsonb NOT NULL,
    resulting_revision bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (user_id, operation_id)
);

ALTER TABLE private.profile_preference_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.profile_preference_receipts FORCE ROW LEVEL SECURITY;

-- 3. Dedicated executor roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'profile_preferences_executor') THEN
        CREATE ROLE profile_preferences_executor NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'profile_bootstrap_executor') THEN
        CREATE ROLE profile_bootstrap_executor NOLOGIN NOINHERIT;
    END IF;
END $$;

GRANT profile_preferences_executor TO postgres;
GRANT profile_bootstrap_executor TO postgres;
GRANT USAGE, CREATE ON SCHEMA public TO profile_preferences_executor, profile_bootstrap_executor;

-- Table Grants
REVOKE ALL ON TABLE public.profiles FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.profiles TO authenticated;
GRANT SELECT, UPDATE ON TABLE public.profiles TO profile_preferences_executor;
GRANT INSERT, SELECT ON TABLE public.profiles TO profile_bootstrap_executor;

REVOKE ALL ON TABLE private.profile_preference_receipts FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT ON TABLE private.profile_preference_receipts TO profile_preferences_executor;

-- RLS Policies on profiles
DROP POLICY IF EXISTS profiles_own_select ON public.profiles;
CREATE POLICY profiles_own_select ON public.profiles
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = user_id);

DROP POLICY IF EXISTS profiles_executor_update ON public.profiles;
CREATE POLICY profiles_executor_update ON public.profiles
    FOR ALL TO profile_preferences_executor
    USING ((select auth.uid()) = user_id)
    WITH CHECK ((select auth.uid()) = user_id);

DROP POLICY IF EXISTS profiles_bootstrap_insert ON public.profiles;
CREATE POLICY profiles_bootstrap_insert ON public.profiles
    FOR INSERT TO profile_bootstrap_executor
    WITH CHECK ((select auth.uid()) IS NULL OR (select auth.uid()) = user_id);

DROP POLICY IF EXISTS profiles_bootstrap_select ON public.profiles;
CREATE POLICY profiles_bootstrap_select ON public.profiles
    FOR SELECT TO profile_bootstrap_executor
    USING ((select auth.uid()) = user_id);

-- RLS Policy on receipts
DROP POLICY IF EXISTS receipts_executor_policy ON private.profile_preference_receipts;
CREATE POLICY receipts_executor_policy ON private.profile_preference_receipts
    FOR ALL TO profile_preferences_executor
    USING ((select auth.uid()) = user_id)
    WITH CHECK ((select auth.uid()) = user_id);

-- 4. RPC Functions
CREATE OR REPLACE FUNCTION public.update_profile_preferences(
    p_operation_id uuid,
    p_expected_revision bigint,
    p_payload jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_now timestamptz := clock_timestamp();
    v_hash bytea;
    v_existing_receipt private.profile_preference_receipts%ROWTYPE;
    v_profile public.profiles%ROWTYPE;
    v_result jsonb;
    v_new_revision bigint;
    v_month_start smallint;
    v_currency_code text;
    v_hide_balances boolean;
    v_theme_mode text;
    v_display_name text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Canonical SHA-256 hash
    v_hash := sha256(('v1\n' || p_operation_id::text || '\n' || p_expected_revision::text || '\n' || p_payload::text)::bytea);

    -- Check duplicate receipt
    SELECT * INTO v_existing_receipt
    FROM private.profile_preference_receipts
    WHERE user_id = v_user_id AND operation_id = p_operation_id;

    IF FOUND THEN
        IF v_existing_receipt.payload_hash = v_hash THEN
            RETURN jsonb_build_object(
                'result', 'DUPLICATE',
                'accepted_revision', v_existing_receipt.resulting_revision,
                'data', v_existing_receipt.result
            );
        ELSE
            RETURN jsonb_build_object('result', 'OPERATION_COLLISION');
        END IF;
    END IF;

    -- Lock profile row
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE user_id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('result', 'NOT_FOUND');
    END IF;

    -- Optimistic revision check
    IF v_profile.revision != p_expected_revision THEN
        v_result := jsonb_build_object(
            'result', 'CONFLICT',
            'current_revision', v_profile.revision,
            'current_profile', row_to_json(v_profile)
        );
        INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
        VALUES (v_user_id, p_operation_id, v_hash, v_result, v_profile.revision, v_now);
        RETURN v_result;
    END IF;

    -- Extract values from payload
    v_month_start := COALESCE((p_payload->>'month_start')::smallint, v_profile.month_start);
    v_currency_code := COALESCE(p_payload->>'currency_code', v_profile.currency_code);
    v_hide_balances := COALESCE((p_payload->>'hide_balances')::boolean, v_profile.hide_balances);
    v_theme_mode := COALESCE(p_payload->>'theme_mode', v_profile.theme_mode);
    v_display_name := COALESCE(p_payload->>'display_name', v_profile.display_name);

    IF v_month_start < 1 OR v_month_start > 28 THEN
        RAISE EXCEPTION 'Invalid month_start %: must be between 1 and 28', v_month_start;
    END IF;

    v_new_revision := v_profile.revision + 1;

    UPDATE public.profiles
    SET display_name = v_display_name,
        currency_code = v_currency_code,
        month_start = v_month_start,
        hide_balances = v_hide_balances,
        theme_mode = v_theme_mode,
        revision = v_new_revision,
        updated_at = v_now
    WHERE user_id = v_user_id;

    v_result := jsonb_build_object(
        'result', 'APPLIED',
        'accepted_revision', v_new_revision,
        'updated_at', v_now
    );

    INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
    VALUES (v_user_id, p_operation_id, v_hash, v_result, v_new_revision, v_now);

    RETURN v_result;
END;
$$;

ALTER FUNCTION public.update_profile_preferences(uuid, bigint, jsonb) OWNER TO profile_preferences_executor;
REVOKE ALL ON FUNCTION public.update_profile_preferences(uuid, bigint, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_profile_preferences(uuid, bigint, jsonb) TO authenticated;

-- Ensure profile function
CREATE OR REPLACE FUNCTION public.ensure_profile()
RETURNS public.profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_profile public.profiles%ROWTYPE;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    INSERT INTO public.profiles (user_id)
    VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT * INTO v_profile
    FROM public.profiles
    WHERE user_id = v_user_id;

    RETURN v_profile;
END;
$$;

ALTER FUNCTION public.ensure_profile() OWNER TO profile_bootstrap_executor;
REVOKE ALL ON FUNCTION public.ensure_profile() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ensure_profile() TO authenticated;

-- Trigger for auth.users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.profiles (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$;

ALTER FUNCTION public.handle_new_user() OWNER TO profile_bootstrap_executor;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
