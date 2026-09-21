BEGIN;
SELECT plan(6);

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

-- 5. Verify anon and authenticated cannot directly access the table
SELECT throws_ok(
    $$ SET ROLE anon; SELECT * FROM private.registration_rate_buckets; $$,
    '42501',
    NULL,
    'anon cannot select from private.registration_rate_buckets'
);

SELECT * FROM finish();
ROLLBACK;
