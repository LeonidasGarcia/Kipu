BEGIN;

-- Quota rows are full snapshots, not an event log. Room may coalesce offline
-- edits, so any strictly newer revision replaces the previous snapshot even
-- when it advances the accepted head by more than one. Equal/older revisions
-- remain STALE. Receipt lookup and payload-hash conflict checks are unchanged.
-- The RPC is owned by this restricted executor role; grant it only for the
-- replacement and revoke the migration role's membership before commit.
GRANT plan_quota_selection_executor TO postgres;

CREATE OR REPLACE FUNCTION public.apply_plan_quota_selection(
    p_contract_version integer,
    p_operation_id uuid,
    p_feature_key text,
    p_selection_revision bigint,
    p_items jsonb
) RETURNS TABLE(contract_version smallint, operation_id uuid, result text, feature_key text,
                accepted_revision text, current_items jsonb, server_time text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    uid uuid := private.request_user_id();
    feature text := upper(p_feature_key);
    item jsonb;
    resource uuid;
    kind text;
    allowed integer;
    unique_resources integer;
    head bigint;
    known private.plan_quota_selection_receipts;
    canonical jsonb;
    hash bytea;
    outcome text;
    stamp timestamptz := clock_timestamp();
BEGIN
    IF uid IS NULL THEN RAISE insufficient_privilege; END IF;
    IF p_contract_version <> 1 OR p_selection_revision < 1 OR p_items IS NULL OR jsonb_typeof(p_items) <> 'array' THEN
        RAISE data_exception;
    END IF;
    allowed := CASE feature WHEN 'INSTRUMENTS' THEN 4 WHEN 'CUSTOM_CATEGORIES' THEN 5
        WHEN 'DEBTS' THEN 2 WHEN 'GOALS' THEN 2 WHEN 'BUDGETS' THEN 2 ELSE NULL END;
    IF allowed IS NULL THEN RAISE data_exception USING MESSAGE = 'Unknown quota group'; END IF;
    IF jsonb_array_length(p_items) > allowed THEN RAISE data_exception USING MESSAGE = 'Selection exceeds Free quota'; END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object('resource_id', resource_id, 'resource_type', resource_type) ORDER BY resource_id), '[]'::jsonb)
      INTO canonical
      FROM (
        SELECT DISTINCT (value->>'resource_id')::uuid AS resource_id, upper(value->>'resource_type') AS resource_type
        FROM jsonb_array_elements(p_items) AS rows(value)
        WHERE jsonb_typeof(value) = 'object'
          AND (SELECT count(*) FROM jsonb_object_keys(value)) = 2
          AND value ? 'resource_id' AND value ? 'resource_type'
      ) normalized;
    SELECT count(DISTINCT (value->>'resource_id')::uuid) INTO unique_resources
      FROM jsonb_array_elements(p_items) AS rows(value)
     WHERE jsonb_typeof(value) = 'object' AND value ? 'resource_id';
    IF jsonb_array_length(canonical) <> jsonb_array_length(p_items) OR unique_resources <> jsonb_array_length(p_items) THEN
        RAISE data_exception USING MESSAGE = 'Malformed or duplicate selection item';
    END IF;

    FOR item IN SELECT value FROM jsonb_array_elements(canonical) AS rows(value) LOOP
        resource := (item->>'resource_id')::uuid;
        kind := item->>'resource_type';
        IF NOT (
            (feature = 'INSTRUMENTS' AND kind = 'ACCOUNT' AND EXISTS (
                SELECT 1 FROM public.accounts a WHERE a.id = resource AND a.user_id = uid AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL
            )) OR
            (feature = 'INSTRUMENTS' AND kind = 'CARD' AND EXISTS (
                SELECT 1 FROM public.cards c WHERE c.id = resource AND c.user_id = uid AND NOT c.is_archived AND c.deleted_at IS NULL
            )) OR
            (feature = 'CUSTOM_CATEGORIES' AND kind = 'CATEGORY_ROOT' AND EXISTS (
                SELECT 1 FROM public.categories c WHERE c.id = resource AND c.user_id = uid AND c.parent_id IS NULL AND c.origin = 'CUSTOM' AND c.is_active AND c.deleted_at IS NULL
            )) OR
            (feature = 'DEBTS' AND kind = 'DEBT' AND EXISTS (
                SELECT 1 FROM public.debts d WHERE d.id = resource AND d.user_id = uid AND d.status = 'ACTIVE' AND d.deleted_at IS NULL
            )) OR
            (feature = 'GOALS' AND kind = 'GOAL' AND EXISTS (
                SELECT 1 FROM public.goals g WHERE g.id = resource AND g.user_id = uid AND g.status = 'ACTIVE' AND g.deleted_at IS NULL
            )) OR
            (feature = 'BUDGETS' AND kind = 'BUDGET' AND EXISTS (
                SELECT 1 FROM public.budgets b WHERE b.id = resource AND b.user_id = uid AND b.is_active AND b.deleted_at IS NULL
            ))
        ) THEN RAISE data_exception USING MESSAGE = 'Selection contains an ineligible resource'; END IF;
    END LOOP;

    hash := private.plan_quota_selection_hash(p_contract_version, p_operation_id, feature, p_selection_revision, canonical);
    INSERT INTO private.plan_quota_selection_heads(user_id, feature_key) VALUES (uid, feature) ON CONFLICT DO NOTHING;
    SELECT h.accepted_revision INTO head FROM private.plan_quota_selection_heads h WHERE h.user_id = uid AND h.feature_key = feature FOR UPDATE;
    SELECT * INTO known FROM private.plan_quota_selection_receipts r WHERE r.user_id = uid AND r.operation_id = p_operation_id;
    IF known.operation_id IS NOT NULL THEN
        outcome := CASE WHEN known.payload_hash = hash AND known.first_result = 'APPLIED' THEN 'DUPLICATE'
            WHEN known.payload_hash = hash THEN known.first_result ELSE 'CONFLICT' END;
    ELSE
        outcome := CASE WHEN p_selection_revision > head THEN 'APPLIED'
            ELSE 'STALE' END;
        IF outcome = 'APPLIED' THEN
            DELETE FROM private.plan_quota_selection_items i WHERE i.user_id = uid AND i.feature_key = feature;
            INSERT INTO private.plan_quota_selection_items(user_id, feature_key, resource_id, resource_type, created_at)
            SELECT uid, feature, (value->>'resource_id')::uuid, value->>'resource_type', stamp
            FROM jsonb_array_elements(canonical) AS rows(value);
            UPDATE private.plan_quota_selection_heads h SET accepted_revision = p_selection_revision, updated_at = stamp
             WHERE h.user_id = uid AND h.feature_key = feature;
            head := p_selection_revision;
        END IF;
        INSERT INTO private.plan_quota_selection_receipts(user_id, operation_id, feature_key, contract_version,
            selection_revision, payload_hash, first_result, accepted_revision_at_first_seen, first_seen_at)
        VALUES (uid, p_operation_id, feature, p_contract_version, p_selection_revision, hash, outcome, head, stamp);
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object('resource_id', i.resource_id, 'resource_type', i.resource_type) ORDER BY i.resource_id), '[]'::jsonb)
      INTO canonical FROM private.plan_quota_selection_items i WHERE i.user_id = uid AND i.feature_key = feature;
    RETURN QUERY SELECT 1::smallint, p_operation_id, outcome, feature, head::text, canonical,
        to_char(stamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
END;
$$;

REVOKE plan_quota_selection_executor FROM postgres;

COMMIT;
