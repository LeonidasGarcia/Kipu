BEGIN;

ALTER TABLE public.app_notifications
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE INDEX IF NOT EXISTS app_notifications_active_user_read_created_idx
    ON public.app_notifications (user_id, is_read, created_at DESC)
    WHERE deleted_at IS NULL;

-- Keep notice content and producer metadata immutable to authenticated clients.
-- The existing owner RLS policy further scopes these two state columns to auth.uid().
GRANT UPDATE (is_read, deleted_at)
    ON TABLE public.app_notifications
    TO authenticated;

COMMIT;
