BEGIN;

CREATE SCHEMA IF NOT EXISTS private;
DO $$ BEGIN
    CREATE ROLE plan_quota_selection_executor NOLOGIN;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
GRANT plan_quota_selection_executor TO postgres;

CREATE TABLE private.plan_quota_selection_heads (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    feature_key text NOT NULL CHECK (feature_key IN ('INSTRUMENTS','CUSTOM_CATEGORIES','DEBTS','GOALS','BUDGETS')),
    accepted_revision bigint NOT NULL DEFAULT 0 CHECK (accepted_revision >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, feature_key)
);

CREATE TABLE private.plan_quota_selection_items (
    user_id uuid NOT NULL,
    feature_key text NOT NULL,
    resource_id uuid NOT NULL,
    resource_type text NOT NULL CHECK (resource_type IN ('ACCOUNT','CARD','CATEGORY_ROOT','DEBT','GOAL','BUDGET')),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, feature_key, resource_id),
    FOREIGN KEY (user_id, feature_key) REFERENCES private.plan_quota_selection_heads(user_id, feature_key) ON DELETE CASCADE
);

CREATE TABLE private.plan_quota_selection_receipts (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation_id uuid NOT NULL,
    feature_key text NOT NULL CHECK (feature_key IN ('INSTRUMENTS','CUSTOM_CATEGORIES','DEBTS','GOALS','BUDGETS')),
    contract_version smallint NOT NULL CHECK (contract_version = 1),
    selection_revision bigint NOT NULL CHECK (selection_revision > 0),
    payload_hash bytea NOT NULL CHECK (octet_length(payload_hash) = 32),
    first_result text NOT NULL CHECK (first_result IN ('APPLIED','STALE','CONFLICT')),
    accepted_revision_at_first_seen bigint NOT NULL,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, operation_id)
);

CREATE INDEX plan_quota_receipts_user_revision
    ON private.plan_quota_selection_receipts(user_id, feature_key, selection_revision);

ALTER TABLE private.plan_quota_selection_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.plan_quota_selection_heads FORCE ROW LEVEL SECURITY;
ALTER TABLE private.plan_quota_selection_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.plan_quota_selection_items FORCE ROW LEVEL SECURITY;
ALTER TABLE private.plan_quota_selection_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.plan_quota_selection_receipts FORCE ROW LEVEL SECURITY;

GRANT USAGE ON SCHEMA public, private TO plan_quota_selection_executor;
GRANT USAGE ON SCHEMA extensions TO plan_quota_selection_executor;
GRANT EXECUTE ON FUNCTION extensions.digest(bytea, text) TO plan_quota_selection_executor;
GRANT EXECUTE ON FUNCTION private.request_user_id() TO plan_quota_selection_executor;
GRANT SELECT ON public.accounts, public.cards, public.categories, public.debts, public.goals, public.budgets TO plan_quota_selection_executor;
GRANT SELECT, INSERT, UPDATE, DELETE ON private.plan_quota_selection_heads, private.plan_quota_selection_items TO plan_quota_selection_executor;
GRANT SELECT, INSERT ON private.plan_quota_selection_receipts TO plan_quota_selection_executor;

CREATE POLICY quota_heads_owner ON private.plan_quota_selection_heads TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id) WITH CHECK (private.request_user_id() = user_id);
CREATE POLICY quota_items_owner ON private.plan_quota_selection_items TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id) WITH CHECK (private.request_user_id() = user_id);
CREATE POLICY quota_receipts_owner ON private.plan_quota_selection_receipts TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id) WITH CHECK (private.request_user_id() = user_id);

CREATE POLICY quota_accounts_owner_read ON public.accounts FOR SELECT TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id);
CREATE POLICY quota_cards_owner_read ON public.cards FOR SELECT TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id);
CREATE POLICY quota_categories_owner_read ON public.categories FOR SELECT TO plan_quota_selection_executor
    USING (user_id IS NULL OR private.request_user_id() = user_id);
CREATE POLICY quota_debts_owner_read ON public.debts FOR SELECT TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id);
CREATE POLICY quota_goals_owner_read ON public.goals FOR SELECT TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id);
CREATE POLICY quota_budgets_owner_read ON public.budgets FOR SELECT TO plan_quota_selection_executor
    USING (private.request_user_id() = user_id);

REVOKE ALL ON private.plan_quota_selection_heads, private.plan_quota_selection_items, private.plan_quota_selection_receipts FROM anon, authenticated;

CREATE FUNCTION private.plan_quota_selection_hash(
    p_contract integer,
    p_operation uuid,
    p_feature text,
    p_revision bigint,
    p_items jsonb
) RETURNS bytea
LANGUAGE sql IMMUTABLE SECURITY DEFINER SET search_path = '' AS $$
    SELECT extensions.digest(convert_to(
        p_contract::text || chr(10) || lower(p_operation::text) || chr(10) || upper(p_feature) || chr(10) ||
        p_revision::text || chr(10) || p_items::text,
        'UTF8'
    ), 'sha256')
$$;

CREATE FUNCTION public.apply_plan_quota_selection(
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
        outcome := CASE WHEN p_selection_revision = head + 1 THEN 'APPLIED'
            WHEN p_selection_revision <= head THEN 'STALE' ELSE 'CONFLICT' END;
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

CREATE FUNCTION private.is_plan_quota_resource_locked(p_user_id uuid, p_feature_key text, p_resource_id uuid)
RETURNS boolean
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    feature text := upper(p_feature_key);
    target_id uuid := p_resource_id;
    active_count integer;
    allowed integer;
    selected boolean;
    resource_active boolean;
    root_id uuid;
BEGIN
    IF feature = 'INSTRUMENTS' THEN
        SELECT EXISTS (SELECT 1 FROM public.accounts a WHERE a.id=target_id AND a.user_id=p_user_id AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL)
            INTO resource_active;
        IF NOT resource_active THEN
            SELECT EXISTS (SELECT 1 FROM public.cards c WHERE c.id=target_id AND c.user_id=p_user_id AND NOT c.is_archived AND c.deleted_at IS NULL)
                INTO resource_active;
        END IF;
        SELECT (SELECT count(*) FROM public.accounts a WHERE a.user_id=p_user_id AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY') AND NOT a.is_archived AND a.deleted_at IS NULL)
             + (SELECT count(*) FROM public.cards c WHERE c.user_id=p_user_id AND NOT c.is_archived AND c.deleted_at IS NULL)
          INTO active_count;
        allowed := 4;
        IF EXISTS (SELECT 1 FROM public.accounts a WHERE a.id=target_id AND a.user_id=p_user_id AND a.account_type::text='CASH') THEN RETURN false; END IF;
    ELSIF feature = 'CUSTOM_CATEGORIES' THEN
        SELECT COALESCE(c.parent_id,c.id) INTO root_id FROM public.categories c WHERE c.id=target_id AND (c.user_id=p_user_id OR c.user_id IS NULL);
        IF root_id IS NULL THEN RETURN false; END IF;
        SELECT EXISTS (SELECT 1 FROM public.categories c WHERE c.id=root_id AND c.user_id=p_user_id AND c.parent_id IS NULL AND c.origin='CUSTOM' AND c.is_active AND c.deleted_at IS NULL)
            INTO resource_active;
        SELECT count(*) INTO active_count FROM public.categories c WHERE c.user_id=p_user_id AND c.parent_id IS NULL AND c.origin='CUSTOM' AND c.is_active AND c.deleted_at IS NULL;
        allowed := 5;
        target_id := root_id;
    ELSE
        RETURN false;
    END IF;
    IF NOT resource_active OR active_count <= allowed THEN RETURN false; END IF;
    SELECT EXISTS (SELECT 1 FROM private.plan_quota_selection_items i WHERE i.user_id=p_user_id AND i.feature_key=feature AND i.resource_id=target_id)
      INTO selected;
    RETURN NOT selected;
END;
$$;

CREATE FUNCTION private.enforce_transaction_plan_quota()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
    IF NEW.account_id IS NOT NULL AND private.is_plan_quota_resource_locked(NEW.user_id,'INSTRUMENTS',NEW.account_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='source instrument is locked by the Free plan selection';
    END IF;
    IF NEW.category_id IS NOT NULL AND private.is_plan_quota_resource_locked(NEW.user_id,'CUSTOM_CATEGORIES',NEW.category_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='category is locked by the Free plan selection';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION private.enforce_ledger_entry_plan_quota()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
    IF private.is_plan_quota_resource_locked(NEW.user_id,'INSTRUMENTS',NEW.account_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='ledger instrument is locked by the Free plan selection';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION private.enforce_movement_plan_quota()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
    IF NEW.account_id IS NOT NULL AND private.is_plan_quota_resource_locked(NEW.user_id,'INSTRUMENTS',NEW.account_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='source instrument is locked by the Free plan selection';
    END IF;
    IF NEW.card_id IS NOT NULL AND private.is_plan_quota_resource_locked(NEW.user_id,'INSTRUMENTS',NEW.card_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='card is locked by the Free plan selection';
    END IF;
    IF NEW.category_id IS NOT NULL AND private.is_plan_quota_resource_locked(NEW.user_id,'CUSTOM_CATEGORIES',NEW.category_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='category is locked by the Free plan selection';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER enforce_transaction_plan_quota BEFORE INSERT ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION private.enforce_transaction_plan_quota();
CREATE TRIGGER enforce_ledger_entry_plan_quota BEFORE INSERT ON internal.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION private.enforce_ledger_entry_plan_quota();
CREATE TRIGGER enforce_movement_plan_quota BEFORE INSERT ON public.financial_movements
    FOR EACH ROW EXECUTE FUNCTION private.enforce_movement_plan_quota();

GRANT EXECUTE ON FUNCTION private.is_plan_quota_resource_locked(uuid,text,uuid) TO plan_quota_selection_executor;
GRANT CREATE ON SCHEMA private TO plan_quota_selection_executor;
ALTER FUNCTION private.is_plan_quota_resource_locked(uuid,text,uuid) OWNER TO plan_quota_selection_executor;
ALTER FUNCTION private.enforce_transaction_plan_quota() OWNER TO plan_quota_selection_executor;
ALTER FUNCTION private.enforce_ledger_entry_plan_quota() OWNER TO plan_quota_selection_executor;
ALTER FUNCTION private.enforce_movement_plan_quota() OWNER TO plan_quota_selection_executor;
REVOKE CREATE ON SCHEMA private FROM plan_quota_selection_executor;

REVOKE ALL ON FUNCTION public.apply_plan_quota_selection(integer, uuid, text, bigint, jsonb) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.apply_plan_quota_selection(integer, uuid, text, bigint, jsonb) TO authenticated;
REVOKE ALL ON FUNCTION private.plan_quota_selection_hash(integer, uuid, text, bigint, jsonb) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.plan_quota_selection_hash(integer, uuid, text, bigint, jsonb) TO plan_quota_selection_executor;
REVOKE ALL ON FUNCTION private.is_plan_quota_resource_locked(uuid,text,uuid), private.enforce_transaction_plan_quota(), private.enforce_ledger_entry_plan_quota(), private.enforce_movement_plan_quota() FROM public, anon, authenticated;
GRANT plan_quota_selection_executor TO postgres;
GRANT CREATE ON SCHEMA public TO plan_quota_selection_executor;
GRANT CREATE ON SCHEMA private TO plan_quota_selection_executor;
ALTER FUNCTION public.apply_plan_quota_selection(integer, uuid, text, bigint, jsonb) OWNER TO plan_quota_selection_executor;
ALTER FUNCTION private.plan_quota_selection_hash(integer, uuid, text, bigint, jsonb) OWNER TO plan_quota_selection_executor;
REVOKE CREATE ON SCHEMA public FROM plan_quota_selection_executor;
REVOKE CREATE ON SCHEMA private FROM plan_quota_selection_executor;
REVOKE plan_quota_selection_executor FROM postgres;

COMMIT;
