CREATE OR REPLACE FUNCTION public.resolve_category_conflict_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_conflict_id uuid := (p_payload->>'conflict_id')::uuid;
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_chosen_version text := p_payload->>'chosen_version';
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF v_operation_id IS NULL OR v_chosen_version IS NULL OR v_chosen_version NOT IN ('LOCAL', 'REMOTE') THEN
        RAISE EXCEPTION 'Invalid conflict resolution request' USING ERRCODE = '22023';
    END IF;

    UPDATE public.category_conflicts
    SET status = 'RESOLVED',
        resolution_operation_id = v_operation_id,
        updated_at = now()
    WHERE id = v_conflict_id AND user_id = v_user_id AND status = 'OPEN';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Open conflict not found' USING ERRCODE = 'P0002';
    END IF;

    RETURN jsonb_build_object('success', true, 'status', 'RESOLVED');
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_category_conflict_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.resolve_category_conflict_v1(jsonb) TO authenticated;
