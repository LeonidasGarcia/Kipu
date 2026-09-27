BEGIN;

SELECT plan(17);

INSERT INTO auth.users (id, email) VALUES
  ('61000000-0000-4000-8000-000000000001', 'notification-owner@example.test'),
  ('61000000-0000-4000-8000-000000000002', 'notification-other@example.test');

INSERT INTO public.app_notifications
  (id, user_id, title, body, notification_type, is_read, created_at)
VALUES
  ('62000000-0000-4000-8000-000000000001', '61000000-0000-4000-8000-000000000001', 'Owner notice', 'Body', 'SYSTEM', false, now()),
  ('62000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000002', 'Other notice', 'Body', 'SYSTEM', false, now());

SELECT has_column('public', 'app_notifications', 'deleted_at', 'notification soft-delete column exists');
SELECT ok(to_regclass('public.app_notifications_active_user_read_created_idx') IS NOT NULL, 'active/read ordering index exists');
SELECT ok(c.relrowsecurity AND c.relforcerowsecurity, 'notification RLS is enabled and forced')
  FROM pg_class c WHERE c.oid = 'public.app_notifications'::regclass;
SELECT ok(has_table_privilege('authenticated', 'public.app_notifications', 'SELECT'), 'authenticated can read owner-filtered notifications');
SELECT ok(has_column_privilege('authenticated', 'public.app_notifications', 'is_read', 'UPDATE'), 'authenticated can update read state');
SELECT ok(has_column_privilege('authenticated', 'public.app_notifications', 'deleted_at', 'UPDATE'), 'authenticated can update soft-delete state');
SELECT ok(NOT has_column_privilege('authenticated', 'public.app_notifications', 'title', 'UPDATE'), 'authenticated cannot update producer-owned title');
SELECT ok(NOT has_table_privilege('authenticated', 'public.app_notifications', 'INSERT'), 'authenticated cannot insert producer-owned notifications');
SELECT ok(NOT has_table_privilege('authenticated', 'public.app_notifications', 'DELETE'), 'authenticated cannot physically delete notifications');
SELECT ok(NOT has_table_privilege('anon', 'public.app_notifications', 'SELECT,INSERT,UPDATE,DELETE'), 'anonymous has no notification access');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
SELECT set_config('request.jwt.claim.sub', '61000000-0000-4000-8000-000000000001', true);

SELECT is((SELECT count(*)::integer FROM public.app_notifications), 1, 'owner sees only its own notification');
SELECT lives_ok(
  $$UPDATE public.app_notifications SET is_read = true
    WHERE id = '62000000-0000-4000-8000-000000000001'$$,
  'owner can mark its notification read'
);
SELECT ok((SELECT is_read FROM public.app_notifications WHERE id = '62000000-0000-4000-8000-000000000001'), 'owner read state is persisted');
WITH changed AS (
  UPDATE public.app_notifications SET is_read = true
  WHERE id = '62000000-0000-4000-8000-000000000002' RETURNING 1
)
SELECT is((SELECT count(*)::integer FROM changed), 0, 'cross-owner update affects no rows');
SELECT lives_ok(
  $$UPDATE public.app_notifications SET deleted_at = now()
    WHERE id = '62000000-0000-4000-8000-000000000001'$$,
  'owner can soft-delete its notification'
);
SELECT ok((SELECT deleted_at IS NOT NULL FROM public.app_notifications WHERE id = '62000000-0000-4000-8000-000000000001'), 'dismissal retains a remote tombstone');
SELECT is((SELECT count(*)::integer FROM public.app_notifications WHERE deleted_at IS NULL), 0, 'active notification query excludes soft-deleted rows');

SELECT * FROM finish();
ROLLBACK;
