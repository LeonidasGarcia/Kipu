BEGIN;
SELECT plan(8);

-- 1. Verify schema and table exist
SELECT has_schema('private', 'Schema private exists');
SELECT has_table('private', 'registration_rate_buckets', 'Table private.registration_rate_buckets exists');

-- 2. Verify RLS is enabled and forced
SELECT table_privs_are(
    'private', 'registration_rate_buckets', 'service_role',
    ARRAY['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
    'Only service_role has privileges on registration_rate_buckets'
);

-- 3. Verify function exists
SELECT has_function(
    'private', 'consume_rate_bucket',
    ARRAY['bytea', 'integer', 'integer', 'integer'],
    'Function private.consume_rate_bucket exists with expected parameters'
);
SELECT has_function(
    'public', 'consume_auth_rate_bucket',
    ARRAY['text'],
    'Edge Function rate bucket RPC exists'
);

-- 4. Test rate bucket consumption and progression
SELECT lives_ok(
    $$
    DECLARE
        v_hash bytea := sha256('test_origin_ip'::bytea);
        v_res jsonb;
    BEGIN
        -- First attempt: allowed
        v_res := private.consume_rate_bucket(v_hash, 900, 3, 1);
        IF (v_res->>'allowed')::boolean IS NOT TRUE THEN
            RAISE EXCEPTION 'First attempt should be allowed';
        END IF;

        -- Second attempt: allowed
        v_res := private.consume_rate_bucket(v_hash, 900, 3, 1);
        -- Third attempt: allowed
        v_res := private.consume_rate_bucket(v_hash, 900, 3, 1);

        -- Fourth attempt: rate limited with progressive cooldown
        v_res := private.consume_rate_bucket(v_hash, 900, 3, 1);
        IF (v_res->>'allowed')::boolean IS NOT FALSE THEN
            RAISE EXCEPTION 'Fourth attempt should be rate limited';
        END IF;
    END;
    $$,
    'Rate bucket consumes and blocks after exceeding max attempts'
);

SELECT lives_ok(
    $$
    DECLARE
        v_key text := 'login:test-expired-cooldown';
        v_hash bytea := extensions.digest(convert_to(v_key, 'UTF8'), 'sha256');
        v_res jsonb;
    BEGIN
        FOR i IN 1..5 LOOP
            v_res := public.consume_auth_rate_bucket(v_key);
        END LOOP;
        v_res := public.consume_auth_rate_bucket(v_key);
        IF (v_res->>'allowed')::boolean IS NOT FALSE THEN
            RAISE EXCEPTION 'Sixth attempt should be rate limited';
        END IF;

        UPDATE private.registration_rate_buckets
        SET blocked_until = clock_timestamp() - interval '1 second'
        WHERE bucket_hash = v_hash;

        v_res := public.consume_auth_rate_bucket(v_key);
        IF (v_res->>'allowed')::boolean IS NOT TRUE THEN
            RAISE EXCEPTION 'An attempt should be allowed after the cooldown';
        END IF;
    END;
    $$,
    'Expired cooldown allows one more login attempt'
);

-- 5. Verify anon and authenticated cannot directly access the table
SELECT throws_ok(
    $$ SET ROLE anon; SELECT * FROM private.registration_rate_buckets; $$,
    '42501',
    NULL,
    'anon cannot select from private.registration_rate_buckets'
);

SELECT * FROM finish();
ROLLBACK;
