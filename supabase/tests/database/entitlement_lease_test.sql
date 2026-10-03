BEGIN;

SELECT plan(8);

SELECT ok(
    has_function_privilege('authenticated', 'public.get_feature_access()', 'EXECUTE'),
    'authenticated can read legacy feature access'
);
SELECT ok(
    NOT has_function_privilege('anon', 'public.get_feature_access()', 'EXECUTE'),
    'anonymous cannot read feature access'
);
SELECT ok(
    NOT has_function_privilege('public', 'public.get_feature_access()', 'EXECUTE'),
    'PUBLIC cannot read feature access'
);
SELECT ok(
    (SELECT prosecdef FROM pg_proc WHERE oid = 'public.get_feature_access()'::regprocedure),
    'legacy owner-scoped read remains SECURITY DEFINER'
);
SELECT ok(
    (SELECT proconfig @> ARRAY['search_path=""'] FROM pg_proc WHERE oid = 'public.get_feature_access()'::regprocedure),
    'legacy RPC pins an empty search_path'
);
SELECT ok(
    position('auth.uid()' IN pg_get_functiondef('public.get_feature_access()'::regprocedure)) > 0,
    'owner is still derived from the authenticated JWT'
);
SELECT ok(
    position('offline_valid_until' IN pg_get_functiondef('public.get_feature_access()'::regprocedure)) > 0
        AND position('NULL::timestamptz' IN pg_get_functiondef('public.get_feature_access()'::regprocedure)) > 0,
    'legacy response keeps the offline_valid_until key with a null value'
);
SELECT ok(
    position('now() + interval ''72 hours''' IN pg_get_functiondef('public.get_feature_access()'::regprocedure)) = 0,
    'legacy RPC cannot mint a rolling 72-hour lease'
);

SELECT * FROM finish();
ROLLBACK;
