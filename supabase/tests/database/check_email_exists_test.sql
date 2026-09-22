BEGIN;
SELECT plan(4);

-- 1. Verify function exists with expected signature
SELECT has_function(
    'public', 'check_email_exists',
    ARRAY['text'],
    'Function public.check_email_exists(text) exists'
);

-- 2. Verify function privileges: service_role only, not anon or authenticated
SELECT function_privs_are(
    'public', 'check_email_exists', ARRAY['text'], 'service_role',
    ARRAY['EXECUTE'],
    'service_role can execute public.check_email_exists'
);

-- 3. Verify anon cannot execute check_email_exists
SELECT throws_ok(
    $$ SET ROLE anon; SELECT public.check_email_exists('test@example.com'); $$,
    '42501',
    NULL,
    'anon cannot execute public.check_email_exists'
);

-- 4. Verify authenticated cannot execute check_email_exists
SELECT throws_ok(
    $$ SET ROLE authenticated; SELECT public.check_email_exists('test@example.com'); $$,
    '42501',
    NULL,
    'authenticated cannot execute public.check_email_exists'
);

SELECT * FROM finish();
ROLLBACK;
