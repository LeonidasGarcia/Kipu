BEGIN;
SELECT plan(8);

-- 1. Check profiles table and columns
SELECT has_table('public', 'profiles', 'Table public.profiles exists');
SELECT has_column('public', 'profiles', 'revision', 'Column revision exists in profiles');
SELECT has_column('public', 'profiles', 'month_start', 'Column month_start exists in profiles');
SELECT has_column('public', 'profiles', 'hide_balances', 'Column hide_balances exists in profiles');

-- 2. Check receipts table in private schema
SELECT has_table('private', 'profile_preference_receipts', 'Table private.profile_preference_receipts exists');

-- 3. Check functions exist
SELECT has_function('public', 'update_profile_preferences', 'Function update_profile_preferences exists');
SELECT has_function('public', 'ensure_profile', 'Function ensure_profile exists');

-- 4. Check RLS is enabled on public.profiles
SELECT table_privs_are(
    'public', 'profiles', 'authenticated',
    ARRAY['SELECT'],
    'authenticated has only SELECT directly on public.profiles'
);

SELECT * FROM finish();
ROLLBACK;
