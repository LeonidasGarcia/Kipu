CREATE SCHEMA IF NOT EXISTS private;

CREATE TABLE IF NOT EXISTS private.registration_rate_buckets (
    bucket_hash bytea PRIMARY KEY,
    window_started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    attempt_count integer NOT NULL DEFAULT 1,
    blocked_until timestamptz,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE private.registration_rate_buckets ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.registration_rate_buckets FORCE ROW LEVEL SECURITY;

REVOKE ALL ON SCHEMA private FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE private.registration_rate_buckets FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA private TO service_role;
GRANT ALL ON TABLE private.registration_rate_buckets TO service_role;

CREATE OR REPLACE FUNCTION private.consume_rate_bucket(
    p_bucket_hash bytea,
    p_window_seconds integer DEFAULT 900,
    p_max_attempts integer DEFAULT 5,
    p_cooldown_base_seconds integer DEFAULT 1
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_now timestamptz := clock_timestamp();
    v_row private.registration_rate_buckets%ROWTYPE;
    v_retry_after integer := 0;
    v_cooldown integer;
BEGIN
    SELECT * INTO v_row
    FROM private.registration_rate_buckets
    WHERE bucket_hash = p_bucket_hash
    FOR UPDATE;

    IF FOUND THEN
        IF v_row.blocked_until IS NOT NULL AND v_row.blocked_until > v_now THEN
            v_retry_after := CEIL(EXTRACT(epoch FROM (v_row.blocked_until - v_now)))::integer;
            RETURN jsonb_build_object(
                'allowed', false,
                'retry_after_seconds', GREATEST(v_retry_after, 1),
                'attempt_count', v_row.attempt_count
            );
        END IF;

        IF v_now - v_row.window_started_at > (p_window_seconds * interval '1 second') THEN
            UPDATE private.registration_rate_buckets
            SET window_started_at = v_now,
                attempt_count = 1,
                blocked_until = NULL,
                updated_at = v_now
            WHERE bucket_hash = p_bucket_hash;
            RETURN jsonb_build_object('allowed', true, 'retry_after_seconds', 0, 'attempt_count', 1);
        ELSE
            v_row.attempt_count := v_row.attempt_count + 1;
            IF v_row.attempt_count > p_max_attempts THEN
                -- Progressive exponential backoff: base * 2^(attempts - max - 1), capped at 60s
                v_cooldown := LEAST(60, p_cooldown_base_seconds * (2 ^ (v_row.attempt_count - p_max_attempts - 1)))::integer;
                UPDATE private.registration_rate_buckets
                SET attempt_count = v_row.attempt_count,
                    blocked_until = v_now + (v_cooldown * interval '1 second'),
                    updated_at = v_now
                WHERE bucket_hash = p_bucket_hash;
                RETURN jsonb_build_object('allowed', false, 'retry_after_seconds', v_cooldown, 'attempt_count', v_row.attempt_count);
            ELSE
                UPDATE private.registration_rate_buckets
                SET attempt_count = v_row.attempt_count,
                    updated_at = v_now
                WHERE bucket_hash = p_bucket_hash;
                RETURN jsonb_build_object('allowed', true, 'retry_after_seconds', 0, 'attempt_count', v_row.attempt_count);
            END IF;
        END IF;
    ELSE
        INSERT INTO private.registration_rate_buckets (bucket_hash, window_started_at, attempt_count, blocked_until, updated_at)
        VALUES (p_bucket_hash, v_now, 1, NULL, v_now);
        RETURN jsonb_build_object('allowed', true, 'retry_after_seconds', 0, 'attempt_count', 1);
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION private.consume_rate_bucket(bytea, integer, integer, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.consume_rate_bucket(bytea, integer, integer, integer) TO service_role;
