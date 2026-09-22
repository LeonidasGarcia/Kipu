-- Function to check if an email already exists in auth.users
-- Strictly secured: Only executable by service_role (used by auth-access Edge Function)
-- Revoked from PUBLIC, anon, and authenticated to prevent email enumeration (FR-051)

CREATE OR REPLACE FUNCTION public.check_email_exists(p_email text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM auth.users
        WHERE LOWER(email) = LOWER(TRIM(p_email))
    );
END;
$$;

REVOKE ALL ON FUNCTION public.check_email_exists(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_email_exists(text) TO service_role;
