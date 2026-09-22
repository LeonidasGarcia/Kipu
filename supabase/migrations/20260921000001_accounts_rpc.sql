-- Migration: 20260921000001_accounts_rpc.sql
-- Description: Compatibility alias for create_liquid_account_v1

CREATE OR REPLACE FUNCTION public.create_liquid_account_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN public.create_account_v1(p_command);
END;
$$;

REVOKE ALL ON FUNCTION public.create_liquid_account_v1(jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.create_liquid_account_v1(jsonb) TO authenticated;
