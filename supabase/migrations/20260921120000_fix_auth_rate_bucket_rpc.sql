-- Expose the rate limiter to the Edge Function through the public PostgREST schema.
-- Only the service role may call it; the raw IP-based key is hashed before storage.
CREATE OR REPLACE FUNCTION public.consume_auth_rate_bucket(p_bucket_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_bucket_hash bytea := extensions.digest(pg_catalog.convert_to(p_bucket_key, 'UTF8'), 'sha256');
BEGIN
    -- Once a cooldown ends, allow one more attempt within the current window.
    -- The old counter otherwise rejects every attempt until the 15-minute window ends.
    UPDATE private.registration_rate_buckets
    SET attempt_count = 4,
        blocked_until = NULL,
        updated_at = pg_catalog.clock_timestamp()
    WHERE bucket_hash = v_bucket_hash
      AND blocked_until IS NOT NULL
      AND blocked_until <= pg_catalog.clock_timestamp()
      AND window_started_at > pg_catalog.clock_timestamp() - INTERVAL '15 minutes';

    RETURN private.consume_rate_bucket(v_bucket_hash);
END;
$$;

REVOKE ALL ON FUNCTION public.consume_auth_rate_bucket(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.consume_auth_rate_bucket(text) TO service_role;
