-- S4 movement revisions: append-only identity, atomic correction and void RPCs.
ALTER TABLE public.transaction_revisions
    ALTER COLUMN previous_payload DROP NOT NULL,
    ADD COLUMN command_id uuid,
    ADD COLUMN command_type text NOT NULL DEFAULT 'LEGACY_IMPORT',
    ADD COLUMN base_revision bigint,
    ADD COLUMN local_revision bigint,
    ADD COLUMN provenance text NOT NULL DEFAULT 'REMOTE_SNAPSHOT';

UPDATE public.transaction_revisions
SET local_revision=revision_number,
    base_revision=GREATEST(revision_number-1,0)
WHERE local_revision IS NULL OR base_revision IS NULL;

ALTER TABLE public.transaction_revisions
    ALTER COLUMN local_revision SET NOT NULL,
    ALTER COLUMN base_revision SET NOT NULL;

CREATE UNIQUE INDEX transaction_revisions_owner_transaction_revision_uq
    ON public.transaction_revisions(user_id,transaction_id,revision_number);
CREATE UNIQUE INDEX transaction_revisions_owner_transaction_id_uq
    ON public.transaction_revisions(user_id,transaction_id,id);
CREATE UNIQUE INDEX transaction_revisions_owner_command_uq
    ON public.transaction_revisions(user_id,command_id) WHERE command_id IS NOT NULL;
CREATE UNIQUE INDEX ledger_entries_owner_id_uq
    ON internal.ledger_entries(user_id,id);

ALTER TABLE public.transaction_revisions
    ADD CONSTRAINT transaction_revisions_owner_transaction_fk
    FOREIGN KEY(user_id,transaction_id) REFERENCES public.transactions(user_id,id) ON DELETE CASCADE;

CREATE TABLE public.movement_official_revisions (
    user_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    official_revision bigint NOT NULL CHECK (official_revision > 0),
    revision_id uuid NOT NULL,
    official_revision_id uuid NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,transaction_id,official_revision),
    UNIQUE(user_id,official_revision_id),
    FOREIGN KEY(user_id,transaction_id,revision_id)
        REFERENCES public.transaction_revisions(user_id,transaction_id,id) ON DELETE RESTRICT
);

-- A transaction without an S2 revision row receives a factual server baseline.
INSERT INTO public.transaction_revisions(
    transaction_id,user_id,revision_number,previous_payload,new_payload,change_reason,
    command_id,command_type,base_revision,local_revision,provenance
)
SELECT tx.id,tx.user_id,tx.revision,NULL,
       to_jsonb(tx) || jsonb_build_object('destination_account_id',(
           SELECT le.account_id FROM internal.ledger_entries le
           WHERE le.user_id=tx.user_id AND le.transaction_id=tx.id AND le.entry_role='DESTINATION'
           ORDER BY le.id LIMIT 1)),
       NULL,NULL,'MIGRATION_BASELINE',GREATEST(tx.revision-1,0),tx.revision,'MIGRATION_BASELINE'
FROM public.transactions tx
WHERE NOT EXISTS (
    SELECT 1 FROM public.transaction_revisions tr
    WHERE tr.user_id=tx.user_id AND tr.transaction_id=tx.id AND tr.revision_number=tx.revision
);

INSERT INTO public.movement_official_revisions(
    user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at
)
SELECT user_id,transaction_id,revision_number,id,id,created_at
FROM public.transaction_revisions
ON CONFLICT(user_id,transaction_id,official_revision) DO NOTHING;

CREATE TABLE internal.movement_ledger_effects (
    user_id uuid NOT NULL,
    command_id uuid NOT NULL,
    effect_ordinal integer NOT NULL CHECK (effect_ordinal >= 0),
    transaction_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    ledger_entry_id uuid NOT NULL,
    reverses_command_id uuid,
    reverses_effect_ordinal integer,
    PRIMARY KEY(user_id,command_id,effect_ordinal),
    UNIQUE(user_id,ledger_entry_id),
    CHECK ((reverses_command_id IS NULL)=(reverses_effect_ordinal IS NULL)),
    FOREIGN KEY(user_id,transaction_id,revision_id)
        REFERENCES public.transaction_revisions(user_id,transaction_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(user_id,ledger_entry_id)
        REFERENCES internal.ledger_entries(user_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(user_id,reverses_command_id,reverses_effect_ordinal)
        REFERENCES internal.movement_ledger_effects(user_id,command_id,effect_ordinal) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX movement_ledger_effects_reverse_once_uq
    ON internal.movement_ledger_effects(user_id,reverses_command_id,reverses_effect_ordinal)
    WHERE reverses_command_id IS NOT NULL;

CREATE TABLE internal.movement_ledger_aliases (
    user_id uuid NOT NULL,
    physical_entry_id uuid NOT NULL,
    command_id uuid NOT NULL,
    effect_ordinal integer NOT NULL,
    PRIMARY KEY(user_id,physical_entry_id),
    FOREIGN KEY(user_id,physical_entry_id)
        REFERENCES internal.ledger_entries(user_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(user_id,command_id,effect_ordinal)
        REFERENCES internal.movement_ledger_effects(user_id,command_id,effect_ordinal) ON DELETE RESTRICT
);

-- Stable synthetic identities connect legacy physical rows without inventing S2 commands.
INSERT INTO internal.movement_ledger_effects(
    user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id
)
SELECT le.user_id,md5('kipu-ledger-baseline:'||le.id::text)::uuid,0,
       le.transaction_id,tr.id,le.id
FROM internal.ledger_entries le
JOIN public.transactions tx ON tx.id=le.transaction_id AND tx.user_id=le.user_id
JOIN public.transaction_revisions tr
  ON tr.user_id=tx.user_id AND tr.transaction_id=tx.id AND tr.revision_number=tx.revision
ON CONFLICT(user_id,command_id,effect_ordinal) DO NOTHING;

INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
SELECT user_id,ledger_entry_id,command_id,effect_ordinal
FROM internal.movement_ledger_effects
ON CONFLICT(user_id,physical_entry_id) DO NOTHING;

ALTER TABLE public.transaction_revisions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS transaction_revisions_own ON public.transaction_revisions;
CREATE POLICY transaction_revisions_own ON public.transaction_revisions
    FOR SELECT TO authenticated USING (auth.uid()=user_id);
REVOKE ALL ON public.transaction_revisions FROM PUBLIC,anon,authenticated,service_role;
GRANT SELECT ON public.transaction_revisions TO authenticated;

ALTER TABLE public.movement_official_revisions ENABLE ROW LEVEL SECURITY;
CREATE POLICY movement_official_revisions_own ON public.movement_official_revisions
    FOR SELECT TO authenticated USING (auth.uid()=user_id);
REVOKE ALL ON public.movement_official_revisions FROM PUBLIC,anon,authenticated,service_role;
GRANT SELECT ON public.movement_official_revisions TO authenticated;
REVOKE ALL ON internal.movement_ledger_effects FROM PUBLIC,anon,authenticated,service_role;
REVOKE ALL ON internal.movement_ledger_aliases FROM PUBLIC,anon,authenticated,service_role;
REVOKE DELETE ON internal.ledger_entries FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION internal.reject_movement_evidence_mutation()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $function$
BEGIN
    RAISE EXCEPTION 'Movement financial evidence is append-only' USING ERRCODE='55000';
END;
$function$;
REVOKE ALL ON FUNCTION internal.reject_movement_evidence_mutation() FROM PUBLIC,anon,authenticated,service_role;

CREATE TRIGGER transaction_revisions_append_only
    BEFORE UPDATE OR DELETE ON public.transaction_revisions
    FOR EACH ROW EXECUTE FUNCTION internal.reject_movement_evidence_mutation();
CREATE TRIGGER movement_official_revisions_append_only
    BEFORE UPDATE OR DELETE ON public.movement_official_revisions
    FOR EACH ROW EXECUTE FUNCTION internal.reject_movement_evidence_mutation();
CREATE TRIGGER movement_ledger_effects_append_only
    BEFORE UPDATE OR DELETE ON internal.movement_ledger_effects
    FOR EACH ROW EXECUTE FUNCTION internal.reject_movement_evidence_mutation();
CREATE TRIGGER movement_ledger_aliases_append_only
    BEFORE UPDATE OR DELETE ON internal.movement_ledger_aliases
    FOR EACH ROW EXECUTE FUNCTION internal.reject_movement_evidence_mutation();

CREATE FUNCTION internal.movement_instrument_locked_v1(p_user_id uuid,p_account_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $function$
    SELECT EXISTS (
        SELECT 1
        FROM public.accounts target
        WHERE target.id=p_account_id AND target.user_id=p_user_id
          AND target.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY')
          AND NOT target.is_archived AND target.deleted_at IS NULL
          AND (SELECT count(*) FROM public.accounts a
               WHERE a.user_id=p_user_id
                 AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY')
                 AND NOT a.is_archived AND a.deleted_at IS NULL)
              + (SELECT count(*) FROM public.cards c
                 WHERE c.user_id=p_user_id AND NOT c.is_archived AND c.deleted_at IS NULL) > 4
          AND NOT EXISTS (
              SELECT 1 FROM private.plan_quota_selection_items i
              WHERE i.user_id=p_user_id AND i.feature_key='INSTRUMENTS'
                AND i.resource_id=p_account_id
          )
    );
$function$;
REVOKE ALL ON FUNCTION internal.movement_instrument_locked_v1(uuid,uuid) FROM PUBLIC,anon,authenticated,service_role;

-- Revision compensation preserves unchanged historical resources; new selections
-- are checked by the RPC. Ordinary ledger writes still honor the plan lock.
CREATE FUNCTION internal.enforce_ledger_entry_plan_quota_s4()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
BEGIN
    IF current_setting('kipu.movement_revision_apply',true) IS DISTINCT FROM 'on'
       AND EXISTS (
           SELECT 1
           FROM public.accounts target
           WHERE target.id=NEW.account_id AND target.user_id=NEW.user_id
             AND target.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY')
             AND NOT target.is_archived AND target.deleted_at IS NULL
             AND (SELECT count(*) FROM public.accounts a
                  WHERE a.user_id=NEW.user_id
                    AND a.account_type::text NOT IN ('CASH','GOALS_VIRTUAL','CREDIT_LIABILITY')
                    AND NOT a.is_archived AND a.deleted_at IS NULL)
                 + (SELECT count(*) FROM public.cards c
                    WHERE c.user_id=NEW.user_id AND NOT c.is_archived AND c.deleted_at IS NULL) > 4
             AND NOT EXISTS (
                 SELECT 1 FROM private.plan_quota_selection_items i
                 WHERE i.user_id=NEW.user_id AND i.feature_key='INSTRUMENTS'
                   AND i.resource_id=NEW.account_id
             )
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='ledger instrument is locked by the Free plan selection';
    END IF;
    RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION internal.enforce_ledger_entry_plan_quota_s4() FROM PUBLIC,anon,authenticated,service_role;
DROP TRIGGER IF EXISTS enforce_ledger_entry_plan_quota ON internal.ledger_entries;
CREATE TRIGGER enforce_ledger_entry_plan_quota
    BEFORE INSERT ON internal.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION internal.enforce_ledger_entry_plan_quota_s4();

CREATE OR REPLACE FUNCTION internal.movement_revision_hash_v1(p_command jsonb)
RETURNS text LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path = '' AS $function$
DECLARE
    v_type text;
    v_body jsonb;
    v_time timestamptz;
    v_millis numeric;
    v_time_canonical text;
    v_canonical text;
    v_prefix text;
    v_payload_canonical text;
BEGIN
    v_type := p_command->>'command_type';
    IF v_type NOT IN ('REVISE_TRANSACTION','VOID_TRANSACTION') THEN RETURN NULL; END IF;
    IF v_type='REVISE_TRANSACTION' THEN
        v_body := p_command->'revised_payload';
        IF jsonb_typeof(v_body)<>'object' THEN RETURN NULL; END IF;
        v_time := (v_body->>'occurred_at')::timestamptz;
        v_millis := extract(epoch FROM v_time)*1000;
        IF v_millis<>trunc(v_millis) THEN RETURN NULL; END IF;
        v_time_canonical := to_char(v_time AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS') ||
            CASE WHEN mod(v_millis::bigint,1000)=0 THEN 'Z'
                 ELSE '.'||to_char(v_time AT TIME ZONE 'UTC','MS')||'Z' END;
        v_payload_canonical := array_to_json(ARRAY[
                upper(v_body->>'type'),upper(v_body->>'operation_kind'),
                v_body->>'amount_minor',upper(v_body->>'currency_code'),
                CASE WHEN v_body->>'source_account_id' IS NULL THEN NULL ELSE lower(v_body->>'source_account_id') END,
                CASE WHEN v_body->>'destination_account_id' IS NULL THEN NULL ELSE lower(v_body->>'destination_account_id') END,
                CASE WHEN v_body->>'category_id' IS NULL THEN NULL ELSE lower(v_body->>'category_id') END,
                CASE WHEN v_body->>'merchant_id' IS NULL THEN NULL ELSE lower(v_body->>'merchant_id') END,
                v_body->>'merchant_provisional_text',v_time_canonical,v_body->>'note'
            ]::text[])::text;
        v_prefix := array_to_json(ARRAY[
            'MOV_REVISION_V1','REVISE',lower(p_command->>'idempotency_key'),
            lower(p_command->>'transaction_id'),p_command->>'expected_revision',
            CASE WHEN p_command->>'depends_on_command_id' IS NULL THEN NULL ELSE lower(p_command->>'depends_on_command_id') END,
            p_command->>'reason'
        ]::text[])::text;
        v_canonical := left(v_prefix,length(v_prefix)-1)||','||v_payload_canonical||']';
    ELSE
        v_canonical := array_to_json(ARRAY[
            'MOV_REVISION_V1','VOID',lower(p_command->>'idempotency_key'),
            lower(p_command->>'transaction_id'),p_command->>'expected_revision',
            CASE WHEN p_command->>'depends_on_command_id' IS NULL THEN NULL ELSE lower(p_command->>'depends_on_command_id') END,
            p_command->>'reason',NULL
        ]::text[])::text;
    END IF;
    RETURN encode(extensions.digest(convert_to(v_canonical,'UTF8'),'sha256'),'hex');
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$function$;
REVOKE ALL ON FUNCTION internal.movement_revision_hash_v1(jsonb) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION internal.ensure_movement_revision_baseline(p_user_id uuid,p_transaction_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $function$
DECLARE
    v_tx public.transactions%ROWTYPE;
    v_revision_id uuid;
    v_payload jsonb;
    v_entry record;
    v_command_id uuid;
BEGIN
    SELECT * INTO v_tx FROM public.transactions
    WHERE id=p_transaction_id AND user_id=p_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT id INTO v_revision_id FROM public.transaction_revisions
    WHERE user_id=p_user_id AND transaction_id=p_transaction_id AND revision_number=v_tx.revision;
    IF v_revision_id IS NULL THEN
        v_payload := to_jsonb(v_tx) || jsonb_build_object('destination_account_id',(
            SELECT le.account_id FROM internal.ledger_entries le
            WHERE le.user_id=p_user_id AND le.transaction_id=p_transaction_id AND le.entry_role='DESTINATION'
            ORDER BY le.id LIMIT 1));
        INSERT INTO public.transaction_revisions(
            transaction_id,user_id,revision_number,previous_payload,new_payload,command_type,
            base_revision,local_revision,provenance
        ) VALUES (
            p_transaction_id,p_user_id,v_tx.revision,NULL,v_payload,'MIGRATION_BASELINE',
            GREATEST(v_tx.revision-1,0),v_tx.revision,'MIGRATION_BASELINE'
        ) RETURNING id INTO v_revision_id;
    END IF;

    INSERT INTO public.movement_official_revisions(
        user_id,transaction_id,official_revision,revision_id,official_revision_id
    ) VALUES(p_user_id,p_transaction_id,v_tx.revision,v_revision_id,v_revision_id)
    ON CONFLICT(user_id,transaction_id,official_revision) DO NOTHING;

    FOR v_entry IN
        SELECT le.id FROM internal.ledger_entries le
        WHERE le.user_id=p_user_id AND le.transaction_id=p_transaction_id
        ORDER BY le.id
    LOOP
        v_command_id := md5('kipu-ledger-baseline:'||v_entry.id::text)::uuid;
        INSERT INTO internal.movement_ledger_effects(
            user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id
        ) VALUES(p_user_id,v_command_id,0,p_transaction_id,v_revision_id,v_entry.id)
        ON CONFLICT DO NOTHING;
        INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
        SELECT p_user_id,v_entry.id,e.command_id,e.effect_ordinal
        FROM internal.movement_ledger_effects e
        WHERE e.user_id=p_user_id AND e.ledger_entry_id=v_entry.id
        ON CONFLICT(user_id,physical_entry_id) DO NOTHING;
    END LOOP;
    RETURN v_revision_id;
END;
$function$;
REVOKE ALL ON FUNCTION internal.ensure_movement_revision_baseline(uuid,uuid) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION internal.apply_movement_revision_v1(p_command jsonb,p_expected_type text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_key uuid;
    v_tx_id uuid;
    v_dependency uuid;
    v_expected bigint;
    v_hash text;
    v_existing internal.command_receipts%ROWTYPE;
    v_tx public.transactions%ROWTYPE;
    v_snapshot_id uuid;
    v_receipt_id uuid;
    v_result jsonb;
    v_before jsonb;
    v_after jsonb;
    v_active jsonb;
    v_active_signature jsonb;
    v_desired_signature jsonb;
    v_destination uuid;
    v_new_source uuid;
    v_new_destination uuid;
    v_category uuid;
    v_merchant uuid;
    v_type text;
    v_amount bigint;
    v_currency text;
    v_time timestamptz;
    v_note text;
    v_merchant_text text;
    v_body jsonb;
    v_effect record;
    v_entry_id uuid;
    v_ordinal integer := 0;
    v_count integer;
    v_effects jsonb := '[]'::jsonb;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','AUTH_REQUIRED','retryable',false));
    END IF;
    IF jsonb_typeof(p_command)<>'object' OR
       NOT p_command ?& ARRAY['contract_version','command_type','idempotency_key','transaction_id',
           'expected_revision','depends_on_command_id','reason','request_hash','revised_payload'] OR
       EXISTS(SELECT 1 FROM jsonb_object_keys(p_command) AS keys(key)
              WHERE key NOT IN ('contract_version','command_type','idempotency_key','transaction_id',
                  'expected_revision','depends_on_command_id','reason','request_hash','revised_payload')) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END IF;
    IF jsonb_typeof(p_command->'contract_version')<>'number' OR
       jsonb_typeof(p_command->'command_type')<>'string' OR
       jsonb_typeof(p_command->'idempotency_key')<>'string' OR
       jsonb_typeof(p_command->'transaction_id')<>'string' OR
       jsonb_typeof(p_command->'expected_revision')<>'number' OR
       jsonb_typeof(p_command->'request_hash')<>'string' OR
       jsonb_typeof(p_command->'depends_on_command_id') NOT IN ('string','null') OR
       jsonb_typeof(p_command->'reason') NOT IN ('string','null') OR
       p_command->>'contract_version'<>'1' OR p_command->>'command_type'<>p_expected_type OR
       (p_expected_type='REVISE_TRANSACTION' AND jsonb_typeof(p_command->'revised_payload')<>'object') OR
       (p_expected_type='VOID_TRANSACTION' AND p_command->'revised_payload' IS DISTINCT FROM 'null'::jsonb) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END IF;
    IF COALESCE(p_command->>'idempotency_key','') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' OR
       COALESCE(p_command->>'transaction_id','') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' OR
       COALESCE(p_command->>'expected_revision','') !~ '^[1-9][0-9]*$' OR
       COALESCE(p_command->>'request_hash','') !~ '^[0-9a-f]{64}$' OR
       (p_command->>'depends_on_command_id' IS NOT NULL AND
        p_command->>'depends_on_command_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND','retryable',false));
    END IF;
    v_key := (p_command->>'idempotency_key')::uuid;
    v_tx_id := (p_command->>'transaction_id')::uuid;
    v_expected := (p_command->>'expected_revision')::bigint;
    v_dependency := NULLIF(p_command->>'depends_on_command_id','')::uuid;
    v_hash := internal.movement_revision_hash_v1(p_command);
    IF v_hash IS NULL OR v_hash<>p_command->>'request_hash' THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','REQUEST_HASH_MISMATCH','retryable',false));
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text||':'||v_key::text,0));
    SELECT * INTO v_existing FROM internal.command_receipts
    WHERE user_id=v_user_id AND idempotency_key=v_key::text;
    IF FOUND THEN
        IF v_existing.command_type<>p_expected_type OR v_existing.request_hash<>v_hash THEN
            RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','IDEMPOTENCY_KEY_REUSE','retryable',false));
        END IF;
        RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
    END IF;
    IF v_dependency IS NOT NULL AND NOT EXISTS(
        SELECT 1 FROM internal.command_receipts r
        WHERE r.user_id=v_user_id AND r.idempotency_key=v_dependency::text AND r.status='APPLIED'
    ) THEN
        RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','COMMAND_DEPENDENCY_PENDING','retryable',true));
    END IF;

    SELECT * INTO v_tx FROM public.transactions
    WHERE id=v_tx_id AND user_id=v_user_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_FOUND','retryable',false));
    END IF;
    IF v_tx.revision<>v_expected THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx.id,
            'current_revision',v_tx.revision,'current_payload',to_jsonb(v_tx),
            'error',jsonb_build_object('code','REVISION_CONFLICT','retryable',false));
    END IF;
    IF v_tx.status='VOIDED' THEN
        IF p_expected_type='REVISE_TRANSACTION' THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','ALREADY_VOIDED_FOR_EDIT','retryable',false));
        END IF;
        v_receipt_id := extensions.gen_random_uuid();
        v_result := jsonb_build_object('status','APPLIED','result','ALREADY_VOIDED',
            'transaction_id',v_tx.id,'receipt_id',v_receipt_id,
            'resulting_revision',v_tx.revision,'server_updated_at',v_tx.updated_at,
            'snapshot',to_jsonb(v_tx),'ledger_effects','[]'::jsonb);
        INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
        VALUES(v_receipt_id,v_user_id,v_key::text,p_expected_type,v_hash,v_result,'APPLIED');
        UPDATE internal.command_receipts SET contract_version=1,command_payload=p_command WHERE id=v_receipt_id;
        RETURN v_result;
    END IF;

    IF v_tx.status NOT IN ('CONFIRMED','REVISED') OR v_tx.deleted_at IS NOT NULL OR
       v_tx.operation_kind::text<>'STANDARD' OR v_tx.card_id IS NOT NULL OR v_tx.installment_count<>1 OR
       v_tx.refund_of_transaction_id IS NOT NULL OR
       EXISTS(SELECT 1 FROM public.credit_installments ci WHERE ci.user_id=v_user_id AND ci.transaction_id=v_tx.id) OR
       EXISTS(SELECT 1 FROM public.credit_payment_allocations ca WHERE ca.user_id=v_user_id AND ca.payment_transaction_id=v_tx.id) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','OPERATION_SPECIALIZED','retryable',false));
    END IF;

    v_snapshot_id := internal.ensure_movement_revision_baseline(v_user_id,v_tx.id);
    IF v_snapshot_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','FINANCIAL_EVIDENCE_REQUIRED','retryable',false));
    END IF;
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'command_id',e.command_id,'effect_ordinal',e.effect_ordinal,'ledger_entry_id',le.id,
        'account_id',le.account_id,'role',le.entry_role::text,'signed_amount_minor',le.signed_amount_minor,
        'currency_code',le.currency_code::text
    ) ORDER BY le.entry_role::text,le.account_id,le.id),'[]'::jsonb)
    INTO v_active
    FROM internal.movement_ledger_effects e
    JOIN internal.ledger_entries le ON le.user_id=e.user_id AND le.id=e.ledger_entry_id
    WHERE e.user_id=v_user_id AND e.transaction_id=v_tx.id AND le.entry_role::text<>'REVERSAL'
      AND NOT EXISTS(
          SELECT 1 FROM internal.movement_ledger_effects r
          WHERE r.user_id=e.user_id AND r.reverses_command_id=e.command_id
            AND r.reverses_effect_ordinal=e.effect_ordinal
      );
    v_count := jsonb_array_length(v_active);
    IF v_tx.transaction_type::text='TRANSFER' THEN
        IF v_count<>2 OR
           (SELECT count(*) FROM jsonb_array_elements(v_active) e WHERE e->>'role'='SOURCE'
                AND (e->>'account_id')::uuid=v_tx.account_id AND (e->>'signed_amount_minor')::bigint=-v_tx.amount_minor
                AND e->>'currency_code'=v_tx.currency_code::text)<>1 OR
           (SELECT count(*) FROM jsonb_array_elements(v_active) e WHERE e->>'role'='DESTINATION'
                AND (e->>'account_id')::uuid<>v_tx.account_id AND (e->>'signed_amount_minor')::bigint=v_tx.amount_minor
                AND e->>'currency_code'=v_tx.currency_code::text)<>1 THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','FINANCIAL_EVIDENCE_REQUIRED','retryable',false));
        END IF;
        SELECT (e->>'account_id')::uuid INTO v_destination
        FROM jsonb_array_elements(v_active) e WHERE e->>'role'='DESTINATION';
    ELSE
        IF v_count<>1 OR (v_active->0->>'role')<>'PRIMARY' OR
           (v_active->0->>'account_id')::uuid<>v_tx.account_id OR
           (v_active->0->>'signed_amount_minor')::bigint<>
             (CASE WHEN v_tx.transaction_type::text='EXPENSE' THEN -v_tx.amount_minor ELSE v_tx.amount_minor END) OR
           v_active->0->>'currency_code'<>v_tx.currency_code::text THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','FINANCIAL_EVIDENCE_REQUIRED','retryable',false));
        END IF;
        v_destination := NULL;
    END IF;

    IF p_expected_type='REVISE_TRANSACTION' THEN
        v_body := p_command->'revised_payload';
        IF NOT v_body ?& ARRAY[
            'type','operation_kind','amount_minor','currency_code','source_account_id',
            'destination_account_id','category_id','merchant_id','merchant_provisional_text',
            'occurred_at','note'
        ] OR EXISTS(SELECT 1 FROM jsonb_object_keys(v_body) AS keys(key) WHERE key NOT IN(
            'type','operation_kind','amount_minor','currency_code','source_account_id','destination_account_id',
            'category_id','merchant_id','merchant_provisional_text','occurred_at','note')) OR
           jsonb_typeof(v_body->'type')<>'string' OR
           jsonb_typeof(v_body->'operation_kind')<>'string' OR
           jsonb_typeof(v_body->'amount_minor')<>'number' OR
           jsonb_typeof(v_body->'currency_code')<>'string' OR
           jsonb_typeof(v_body->'source_account_id')<>'string' OR
           jsonb_typeof(v_body->'destination_account_id') NOT IN ('string','null') OR
           jsonb_typeof(v_body->'category_id') NOT IN ('string','null') OR
           jsonb_typeof(v_body->'merchant_id') NOT IN ('string','null') OR
           jsonb_typeof(v_body->'merchant_provisional_text') NOT IN ('string','null') OR
           jsonb_typeof(v_body->'occurred_at')<>'string' OR
           jsonb_typeof(v_body->'note') NOT IN ('string','null') OR
           COALESCE(v_body->>'amount_minor','') !~ '^(0|[1-9][0-9]*)$' OR
           COALESCE(v_body->>'occurred_at','') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{3})?Z$' OR
           COALESCE(v_body->>'source_account_id','') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' OR
           EXISTS(SELECT 1 FROM unnest(ARRAY['destination_account_id','category_id','merchant_id']) f(name)
                  WHERE v_body->>f.name IS NOT NULL AND v_body->>f.name !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_PAYLOAD','retryable',false));
        END IF;
        v_type := upper(v_body->>'type');
        v_amount := (v_body->>'amount_minor')::bigint;
        v_currency := upper(v_body->>'currency_code');
        v_new_source := (v_body->>'source_account_id')::uuid;
        v_new_destination := NULLIF(v_body->>'destination_account_id','')::uuid;
        v_category := NULLIF(v_body->>'category_id','')::uuid;
        v_merchant := NULLIF(v_body->>'merchant_id','')::uuid;
        v_merchant_text := v_body->>'merchant_provisional_text';
        v_time := (v_body->>'occurred_at')::timestamptz;
        v_note := v_body->>'note';
        IF v_type<>v_tx.transaction_type::text OR
           upper(COALESCE(v_body->>'operation_kind',''))<>v_tx.operation_kind::text OR
           v_currency<>v_tx.currency_code::text OR v_amount<=0 OR v_new_source IS NULL OR
           ((v_type='TRANSFER')<>(v_new_destination IS NOT NULL)) OR
           (v_new_destination IS NOT NULL AND v_new_destination=v_new_source) OR
           (v_type<>'TRANSFER' AND v_new_destination IS NOT NULL) OR
           (v_merchant IS NOT NULL AND v_merchant_text IS NOT NULL) OR
           (v_time IS NULL) THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_PAYLOAD','retryable',false));
        END IF;
        IF NOT EXISTS(SELECT 1 FROM public.accounts a WHERE a.id=v_new_source AND a.user_id=v_user_id
            AND a.currency_code=v_currency AND (v_new_source=v_tx.account_id OR
                (NOT a.is_archived AND a.deleted_at IS NULL AND
                 NOT internal.movement_instrument_locked_v1(v_user_id,a.id)))) THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
        END IF;
        IF v_new_destination IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.accounts a
            WHERE a.id=v_new_destination AND a.user_id=v_user_id AND a.currency_code=v_currency
              AND (v_new_destination=v_destination OR (NOT a.is_archived AND a.deleted_at IS NULL
                   AND NOT internal.movement_instrument_locked_v1(v_user_id,a.id)))) THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
        END IF;
        IF (v_type='EXPENSE' AND v_category IS NULL) OR (v_category IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.categories c
            WHERE c.id=v_category AND (c.user_id IS NULL OR c.user_id=v_user_id)
              AND (v_category=v_tx.category_id OR (c.is_active AND c.deleted_at IS NULL))
              AND c.category_type IN ('GENERAL',v_type))) THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
        END IF;
        IF v_merchant IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.merchant_services m
            WHERE m.id=v_merchant AND (m.user_id IS NULL OR m.user_id=v_user_id)
              AND (v_merchant=v_tx.merchant_service_id OR (m.is_active AND m.deleted_at IS NULL))) THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
        END IF;
        IF v_type='TRANSFER' AND v_category IS NOT NULL THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
        END IF;
        v_after := jsonb_build_object(
            'type',v_type,'operation_kind',v_tx.operation_kind::text,'amount_minor',v_amount,
            'currency_code',v_currency,'source_account_id',v_new_source,'destination_account_id',v_new_destination,
            'category_id',v_category,'merchant_id',v_merchant,'merchant_provisional_text',v_merchant_text,
            'occurred_at',to_char(v_time AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
            'note',v_note,'status','REVISED'
        );
    ELSE
        v_new_source := v_tx.account_id;
        v_new_destination := v_destination;
        v_after := jsonb_build_object(
            'type',v_tx.transaction_type::text,'operation_kind',v_tx.operation_kind::text,
            'amount_minor',v_tx.amount_minor,'currency_code',v_tx.currency_code::text,
            'source_account_id',v_tx.account_id,'destination_account_id',v_destination,
            'category_id',v_tx.category_id,'merchant_id',v_tx.merchant_service_id,
            'merchant_provisional_text',v_tx.merchant_provisional_text,'occurred_at',v_tx.occurred_at,
            'note',v_tx.notes,'status','VOIDED'
        );
    END IF;

    v_before := jsonb_build_object(
        'type',v_tx.transaction_type::text,'operation_kind',v_tx.operation_kind::text,
        'amount_minor',v_tx.amount_minor,'currency_code',v_tx.currency_code::text,
        'source_account_id',v_tx.account_id,'destination_account_id',v_destination,
        'category_id',v_tx.category_id,'merchant_id',v_tx.merchant_service_id,
        'merchant_provisional_text',v_tx.merchant_provisional_text,'occurred_at',v_tx.occurred_at,
        'note',v_tx.notes,'status',v_tx.status
    );
    SELECT COALESCE(jsonb_agg(jsonb_build_array(
        e->>'account_id',e->>'role',e->>'signed_amount_minor',e->>'currency_code'
    ) ORDER BY e->>'role',e->>'account_id'),'[]'::jsonb)
    INTO v_active_signature FROM jsonb_array_elements(v_active) e;

    IF p_expected_type='VOID_TRANSACTION' THEN
        v_desired_signature := '[]'::jsonb;
    ELSIF v_type='TRANSFER' THEN
        v_desired_signature := jsonb_build_array(
            jsonb_build_array(v_new_source::text,'SOURCE',(-v_amount)::text,v_currency),
            jsonb_build_array(v_new_destination::text,'DESTINATION',v_amount::text,v_currency));
    ELSIF v_type='EXPENSE' THEN
        v_desired_signature := jsonb_build_array(
            jsonb_build_array(v_new_source::text,'PRIMARY',(-v_amount)::text,v_currency));
    ELSE
        v_desired_signature := jsonb_build_array(
            jsonb_build_array(v_new_source::text,'PRIMARY',v_amount::text,v_currency));
    END IF;
    SELECT COALESCE(jsonb_agg(value ORDER BY value->>1,value->>0),'[]'::jsonb)
    INTO v_desired_signature FROM jsonb_array_elements(v_desired_signature) value;

    v_snapshot_id := extensions.gen_random_uuid();
    v_receipt_id := extensions.gen_random_uuid();
    INSERT INTO public.transaction_revisions(
        id,transaction_id,user_id,revision_number,previous_payload,new_payload,change_reason,
        command_id,command_type,base_revision,local_revision,provenance
    ) VALUES (
        v_snapshot_id,v_tx.id,v_user_id,v_tx.revision+1,v_before,v_after,p_command->>'reason',
        v_key,p_expected_type,v_tx.revision,v_tx.revision+1,'REMOTE_COMMAND'
    );

    IF v_active_signature IS DISTINCT FROM v_desired_signature THEN
        PERFORM set_config('kipu.movement_revision_apply','on',true);
        FOR v_effect IN SELECT value FROM jsonb_array_elements(v_active) LOOP
            INSERT INTO internal.ledger_entries(
                transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
            ) VALUES (
                v_tx.id,v_user_id,(v_effect.value->>'account_id')::uuid,
                -((v_effect.value->>'signed_amount_minor')::bigint),(v_effect.value->>'currency_code'),
                'REVERSAL',v_now
            ) RETURNING id INTO v_entry_id;
            INSERT INTO internal.movement_ledger_effects(
                user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id,
                reverses_command_id,reverses_effect_ordinal
            ) VALUES (
                v_user_id,v_key,v_ordinal,v_tx.id,v_snapshot_id,v_entry_id,
                (v_effect.value->>'command_id')::uuid,(v_effect.value->>'effect_ordinal')::integer
            );
            INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
            VALUES(v_user_id,v_entry_id,v_key,v_ordinal);
            v_effects := v_effects || jsonb_build_array(jsonb_build_object(
                'ledger_entry_id',v_entry_id,'command_id',v_key,'effect_ordinal',v_ordinal,
                'reverses',jsonb_build_array(v_effect.value->>'command_id',v_effect.value->>'effect_ordinal')));
            v_ordinal := v_ordinal+1;
        END LOOP;

        IF p_expected_type='REVISE_TRANSACTION' THEN
            IF v_type='TRANSFER' THEN
                FOR v_effect IN SELECT * FROM (VALUES
                    (v_new_source,-v_amount::bigint,'SOURCE'::text),
                    (v_new_destination,v_amount::bigint,'DESTINATION'::text)
                ) x(account_id,signed_amount,role) LOOP
                    INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at)
                    VALUES(v_tx.id,v_user_id,v_effect.account_id,v_effect.signed_amount,v_currency,v_effect.role::internal.entry_role,v_time)
                    RETURNING id INTO v_entry_id;
                    INSERT INTO internal.movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id)
                    VALUES(v_user_id,v_key,v_ordinal,v_tx.id,v_snapshot_id,v_entry_id);
                    INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
                    VALUES(v_user_id,v_entry_id,v_key,v_ordinal);
                    v_effects := v_effects || jsonb_build_array(jsonb_build_object(
                        'ledger_entry_id',v_entry_id,'command_id',v_key,'effect_ordinal',v_ordinal));
                    v_ordinal := v_ordinal+1;
                END LOOP;
            ELSE
                INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at)
                VALUES(v_tx.id,v_user_id,v_new_source,
                    CASE WHEN v_type='EXPENSE' THEN -v_amount ELSE v_amount END,
                    v_currency,'PRIMARY',v_time)
                RETURNING id INTO v_entry_id;
                INSERT INTO internal.movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id)
                VALUES(v_user_id,v_key,v_ordinal,v_tx.id,v_snapshot_id,v_entry_id);
                INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
                VALUES(v_user_id,v_entry_id,v_key,v_ordinal);
                v_effects := v_effects || jsonb_build_array(jsonb_build_object(
                    'ledger_entry_id',v_entry_id,'command_id',v_key,'effect_ordinal',v_ordinal));
            END IF;
        END IF;
    END IF;

    UPDATE public.transactions SET
        account_id=v_new_source,
        amount_minor=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_amount ELSE v_tx.amount_minor END,
        category_id=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_category ELSE v_tx.category_id END,
        merchant_service_id=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_merchant ELSE v_tx.merchant_service_id END,
        merchant_provisional_text=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_merchant_text ELSE v_tx.merchant_provisional_text END,
        occurred_at=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_time ELSE v_tx.occurred_at END,
        notes=CASE WHEN p_expected_type='REVISE_TRANSACTION' THEN v_note ELSE v_tx.notes END,
        status=CASE WHEN p_expected_type='VOID_TRANSACTION' THEN 'VOIDED' ELSE 'REVISED' END,
        revision=v_tx.revision+1,updated_at=v_now
    WHERE id=v_tx.id AND user_id=v_user_id;

    INSERT INTO public.movement_official_revisions(
        user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at
    ) VALUES(v_user_id,v_tx.id,v_tx.revision+1,v_snapshot_id,v_snapshot_id,v_now);

    v_result := jsonb_build_object(
        'status','APPLIED','result',CASE WHEN p_expected_type='VOID_TRANSACTION' THEN 'VOIDED' ELSE 'REVISED' END,
        'transaction_id',v_tx.id,'receipt_id',v_receipt_id,'resulting_revision',v_tx.revision+1,
        'server_updated_at',v_now,'snapshot',v_after,'ledger_effects',v_effects
    );
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx.id,v_tx.revision+1,'UPSERT',
        v_after || jsonb_build_object('id',v_tx.id,'ledger_effects',v_effects));

    INSERT INTO internal.command_receipts(
        id,user_id,idempotency_key,command_type,request_hash,response_payload,status,contract_version,command_payload
    ) VALUES(v_receipt_id,v_user_id,v_key::text,p_expected_type,v_hash,v_result,'APPLIED',1,p_command);
    RETURN v_result;
EXCEPTION
    WHEN numeric_value_out_of_range OR invalid_text_representation OR invalid_datetime_format THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_PAYLOAD','retryable',false));
    WHEN check_violation OR foreign_key_violation THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_REFERENCE','retryable',false));
END;
$function$;
REVOKE ALL ON FUNCTION internal.apply_movement_revision_v1(jsonb,text) FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION public.revise_transaction_v1(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
BEGIN
    RETURN internal.apply_movement_revision_v1(p_command,'REVISE_TRANSACTION');
END;
$function$;
CREATE OR REPLACE FUNCTION public.void_transaction_v1(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
BEGIN
    RETURN internal.apply_movement_revision_v1(p_command,'VOID_TRANSACTION');
END;
$function$;
REVOKE ALL ON FUNCTION public.revise_transaction_v1(jsonb) FROM PUBLIC,anon,service_role;
REVOKE ALL ON FUNCTION public.void_transaction_v1(jsonb) FROM PUBLIC,anon,service_role;
GRANT EXECUTE ON FUNCTION public.revise_transaction_v1(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.void_transaction_v1(jsonb) TO authenticated;
