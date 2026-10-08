BEGIN;

ALTER TYPE public.operation_kind ADD VALUE IF NOT EXISTS 'DEBT_AMORTIZATION';

ALTER TABLE public.debt_installments
    DROP CONSTRAINT IF EXISTS obligation_installments_status_check;
DO $installment_status$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conrelid = 'public.debt_installments'::regclass
          AND conname = 'debt_installments_status_valid'
    ) THEN
        ALTER TABLE public.debt_installments
            ADD CONSTRAINT debt_installments_status_valid
            CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'CANCELLED')) NOT VALID;
    END IF;
END;
$installment_status$;
ALTER TABLE public.debt_installments VALIDATE CONSTRAINT debt_installments_status_valid;

ALTER TABLE public.debt_events ADD COLUMN IF NOT EXISTS interest_transaction_id uuid;
DO $interest_transaction_owner$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = 'debt_events_interest_transaction_owner_fkey') THEN
        ALTER TABLE public.debt_events
            ADD CONSTRAINT debt_events_interest_transaction_owner_fkey
            FOREIGN KEY (user_id, interest_transaction_id)
            REFERENCES public.transactions(user_id, id) ON DELETE RESTRICT NOT VALID;
    END IF;
END;
$interest_transaction_owner$;
ALTER TABLE public.debt_events VALIDATE CONSTRAINT debt_events_interest_transaction_owner_fkey;
CREATE INDEX IF NOT EXISTS idx_debt_events_user_interest_transaction
    ON public.debt_events(user_id, interest_transaction_id);

CREATE OR REPLACE FUNCTION public.settle_debt_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $settle_debt$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id uuid := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_account_id uuid := NULLIF(p_payload->>'account_id', '')::uuid;
    v_category_id uuid := NULLIF(p_payload->>'interest_category_id', '')::uuid;
    v_installment_id uuid := NULLIF(p_payload->>'installment_id', '')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_principal bigint := NULLIF(p_payload->>'principal_minor', '')::bigint;
    v_interest bigint := COALESCE(NULLIF(p_payload->>'interest_minor', '')::bigint, 0);
    v_occurred_at timestamptz := NULLIF(p_payload->>'occurred_at', '')::timestamptz;
    v_request_hash text := lower(NULLIF(p_payload->>'request_hash', ''));
    v_payload jsonb := p_payload - 'request_hash' - 'user_id';
    v_receipt internal.command_receipts%ROWTYPE;
    v_debt public.debts%ROWTYPE;
    v_account public.accounts%ROWTYPE;
    v_category public.categories%ROWTYPE;
    v_installment public.debt_installments%ROWTYPE;
    v_event_id uuid;
    v_principal_transaction_id uuid;
    v_interest_transaction_id uuid;
    v_transaction_type public.transaction_type;
    v_signed_principal bigint;
    v_signed_interest bigint;
    v_remaining numeric;
    v_remaining_after numeric;
    v_installment_paid numeric;
    v_installment_remaining numeric;
    v_installment_paid_after numeric;
    v_revision bigint;
    v_now timestamptz := clock_timestamp();
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF jsonb_typeof(p_payload) <> 'object'
       OR NOT p_payload ?& ARRAY['contract_version','operation_id','debt_id','expected_revision',
           'account_id','principal_minor','interest_minor','occurred_at','request_hash']
       OR EXISTS (
           SELECT 1 FROM jsonb_object_keys(p_payload) AS keys(key)
           WHERE key NOT IN ('contract_version','operation_id','request_hash','user_id','debt_id',
               'expected_revision','account_id','principal_minor','interest_minor','interest_category_id',
               'installment_id','occurred_at','notes')
       ) THEN
        RAISE EXCEPTION 'INVALID_SETTLEMENT_COMMAND' USING ERRCODE = '22023';
    END IF;
    IF COALESCE((p_payload->>'contract_version')::integer, 0) <> 1
       OR v_operation_id IS NULL OR v_debt_id IS NULL OR v_account_id IS NULL
       OR v_expected_revision IS NULL OR v_expected_revision < 1
       OR v_principal IS NULL OR v_principal NOT BETWEEN 1 AND 99999999999999
       OR v_interest < 0 OR v_interest > 99999999999999
       OR v_occurred_at IS NULL
       OR v_request_hash IS NULL OR v_request_hash !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'INVALID_SETTLEMENT_COMMAND' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'SETTLE_DEBT' OR v_receipt.request_hash <> v_request_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_payload THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status', 'DUPLICATE');
    END IF;

    SELECT * INTO v_debt FROM public.debts
    WHERE id = v_debt_id AND user_id = v_user_id AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'DEBT_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_debt.revision <> v_expected_revision THEN
        RETURN jsonb_build_object('status', 'CONFLICT', 'debt_id', v_debt_id,
            'current_revision', v_debt.revision, 'error', jsonb_build_object('code', 'DEBT_REVISION_CONFLICT'));
    END IF;
    IF v_debt.status <> 'ACTIVE' THEN RAISE EXCEPTION 'DEBT_NOT_ACTIVE' USING ERRCODE = 'P0001'; END IF;

    SELECT * INTO v_account FROM public.accounts WHERE id = v_account_id FOR KEY SHARE;
    IF NOT FOUND OR v_account.user_id <> v_user_id THEN
        RAISE EXCEPTION 'ACCOUNT_NOT_OWNED' USING ERRCODE = '42501';
    END IF;
    IF v_account.deleted_at IS NOT NULL OR v_account.is_archived THEN
        RAISE EXCEPTION 'ACCOUNT_NOT_ELIGIBLE' USING ERRCODE = '22023';
    END IF;
    IF v_account.currency_code::text <> v_debt.currency_code::text THEN
        RAISE EXCEPTION 'ACCOUNT_CURRENCY_MISMATCH' USING ERRCODE = '22023';
    END IF;

    IF v_interest > 0 AND v_debt.obligation_type = 'PAYABLE' THEN
        IF v_category_id IS NULL THEN RAISE EXCEPTION 'INVALID_INTEREST_CATEGORY' USING ERRCODE = '22023'; END IF;
        SELECT * INTO v_category FROM public.categories WHERE id = v_category_id FOR KEY SHARE;
        IF NOT FOUND OR (v_category.user_id IS NOT NULL AND v_category.user_id <> v_user_id)
           OR NOT v_category.is_active OR v_category.deleted_at IS NOT NULL
           OR v_category.category_type NOT IN ('GENERAL', 'EXPENSE') THEN
            RAISE EXCEPTION 'INVALID_INTEREST_CATEGORY' USING ERRCODE = '22023';
        END IF;
    ELSIF v_interest > 0 AND v_category_id IS NOT NULL THEN
        SELECT * INTO v_category FROM public.categories WHERE id = v_category_id FOR KEY SHARE;
        IF NOT FOUND OR (v_category.user_id IS NOT NULL AND v_category.user_id <> v_user_id)
           OR NOT v_category.is_active OR v_category.deleted_at IS NOT NULL
           OR v_category.category_type NOT IN ('GENERAL', 'INCOME') THEN
            RAISE EXCEPTION 'INVALID_INTEREST_CATEGORY' USING ERRCODE = '22023';
        END IF;
    END IF;

    SELECT GREATEST(0::numeric, v_debt.total_minor::numeric + COALESCE(sum(
        CASE
            WHEN e.principal_delta_minor IS NOT NULL THEN e.principal_delta_minor::numeric
            WHEN e.event_type = 'PAYMENT' THEN -e.amount_minor::numeric
            ELSE 0::numeric
        END
    ), 0::numeric)) INTO v_remaining
    FROM public.debt_events e
    LEFT JOIN public.transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
    WHERE e.user_id = v_user_id AND e.debt_id = v_debt_id
      AND (e.transaction_id IS NULL OR t.status <> 'VOIDED');
    IF v_principal::numeric > v_remaining THEN
        RAISE EXCEPTION 'PRINCIPAL_EXCEEDS_REMAINING' USING ERRCODE = 'P0001';
    END IF;
    v_remaining_after := v_remaining - v_principal::numeric;

    IF v_installment_id IS NOT NULL THEN
        SELECT * INTO v_installment FROM public.debt_installments
        WHERE id = v_installment_id AND user_id = v_user_id AND debt_id = v_debt_id
          AND deleted_at IS NULL FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'INSTALLMENT_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
        SELECT COALESCE(sum(
            CASE WHEN e.principal_delta_minor IS NULL THEN e.amount_minor::numeric
                 ELSE GREATEST(0::numeric, -e.principal_delta_minor::numeric) END
        ), 0::numeric) INTO v_installment_paid
        FROM public.debt_events e
        LEFT JOIN public.transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.user_id = v_user_id AND e.debt_id = v_debt_id
          AND e.installment_id = v_installment_id AND e.event_type = 'PAYMENT'
          AND (e.transaction_id IS NULL OR t.status <> 'VOIDED');
        v_installment_remaining := GREATEST(0::numeric, v_installment.amount_minor::numeric - v_installment_paid);
        IF v_principal::numeric > v_installment_remaining THEN
            RAISE EXCEPTION 'INSTALLMENT_PRINCIPAL_EXCEEDED' USING ERRCODE = 'P0001';
        END IF;
        v_installment_paid_after := v_installment_paid + v_principal::numeric;
    END IF;

    v_transaction_type := CASE WHEN v_debt.obligation_type = 'PAYABLE' THEN 'EXPENSE'::public.transaction_type
                               ELSE 'INCOME'::public.transaction_type END;
    v_signed_principal := CASE WHEN v_debt.obligation_type = 'PAYABLE' THEN -v_principal ELSE v_principal END;
    v_signed_interest := CASE WHEN v_debt.obligation_type = 'PAYABLE' THEN -v_interest ELSE v_interest END;
    v_principal_transaction_id := v_operation_id;
    v_interest_transaction_id := CASE WHEN v_interest > 0
        THEN md5('kipu:debt-interest:' || v_operation_id::text)::uuid ELSE NULL END;
    v_event_id := md5('kipu:debt-settlement-event:' || v_operation_id::text)::uuid;

    INSERT INTO public.transactions (
        id, user_id, account_id, category_id, transaction_type, operation_kind,
        amount_minor, currency_code, occurred_at, notes, status, revision, created_at, updated_at
    ) VALUES (
        v_principal_transaction_id, v_user_id, v_account_id, NULL, v_transaction_type, 'DEBT_PAYMENT',
        v_principal, v_debt.currency_code, v_occurred_at,
        COALESCE(NULLIF(btrim(p_payload->>'notes'), ''),
            CASE WHEN v_debt.obligation_type = 'PAYABLE' THEN 'Pago de deuda: ' ELSE 'Cobro de deuda: ' END || v_debt.counterparty_name),
        'CONFIRMED', 1, v_now, v_now
    );
    INSERT INTO internal.ledger_entries (
        transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
    ) VALUES (
        v_principal_transaction_id, v_user_id, v_account_id, v_signed_principal,
        v_debt.currency_code, 'PRIMARY', v_occurred_at
    );

    IF v_interest_transaction_id IS NOT NULL THEN
        INSERT INTO public.transactions (
            id, user_id, account_id, category_id, transaction_type, operation_kind,
            amount_minor, currency_code, occurred_at, notes, status, revision, created_at, updated_at
        ) VALUES (
            v_interest_transaction_id, v_user_id, v_account_id, v_category_id, v_transaction_type,
            'DEBT_AMORTIZATION', v_interest, v_debt.currency_code, v_occurred_at,
            COALESCE(NULLIF(btrim(p_payload->>'notes'), ''), 'Interés de deuda: ' || v_debt.counterparty_name),
            'CONFIRMED', 1, v_now, v_now
        );
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES (
            v_interest_transaction_id, v_user_id, v_account_id, v_signed_interest,
            v_debt.currency_code, 'PRIMARY', v_occurred_at
        );
        INSERT INTO public.transaction_links (
            user_id, source_transaction_id, target_transaction_id, link_type, metadata
        ) VALUES (
            v_user_id, v_principal_transaction_id, v_interest_transaction_id, 'DEBT_AMORTIZATION',
            jsonb_build_object('debt_id', v_debt_id, 'event_id', v_event_id, 'group', 'SETTLEMENT')
        );
    END IF;

    INSERT INTO public.debt_events (
        id, user_id, debt_id, transaction_id, interest_transaction_id, installment_id, event_type, amount_minor,
        principal_delta_minor, occurred_at, created_at
    ) VALUES (
        v_event_id, v_user_id, v_debt_id, v_principal_transaction_id, v_interest_transaction_id, v_installment_id,
        'PAYMENT', v_principal, -v_principal, v_occurred_at, v_now
    );

    v_revision := v_debt.revision + 1;
    UPDATE public.debts
    SET status = CASE WHEN v_remaining_after = 0 THEN 'SETTLED' ELSE 'ACTIVE' END,
        revision = v_revision, updated_at = v_now
    WHERE id = v_debt_id AND user_id = v_user_id;
    IF v_installment_id IS NOT NULL THEN
        UPDATE public.debt_installments
        SET status = CASE WHEN v_installment_paid_after >= v_installment.amount_minor THEN 'PAID'
                          WHEN v_installment_paid_after > 0 THEN 'PARTIAL' ELSE 'PENDING' END,
            revision = revision + 1, updated_at = v_now
        WHERE id = v_installment_id AND user_id = v_user_id;
        INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
        SELECT v_user_id, 'DEBT_INSTALLMENT', i.id, i.revision, 'UPSERT', to_jsonb(i)
        FROM public.debt_installments i WHERE i.id = v_installment_id AND i.user_id = v_user_id;
    END IF;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'TRANSACTION', t.id, t.revision, 'UPSERT', to_jsonb(t)
    FROM public.transactions t
    WHERE t.user_id = v_user_id AND t.id IN (v_principal_transaction_id, v_interest_transaction_id);
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'DEBT_EVENT', v_event_id, 1, 'UPSERT',
        (SELECT to_jsonb(e) FROM public.debt_events e WHERE e.id = v_event_id AND e.user_id = v_user_id));
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT', d.id, d.revision, 'UPSERT', to_jsonb(d)
    FROM public.debts d WHERE d.id = v_debt_id AND d.user_id = v_user_id;

    v_response := jsonb_build_object(
        'status', 'APPLIED', 'result', 'SETTLED', 'operation_id', v_operation_id,
        'event_id', v_event_id, 'debt_id', v_debt_id, 'revision', v_revision,
        'remaining_minor', v_remaining_after, 'principal_minor', v_principal,
        'interest_minor', v_interest, 'cash_delta_minor', v_signed_principal + v_signed_interest,
        'principal_transaction_id', v_principal_transaction_id,
        'interest_transaction_id', v_interest_transaction_id
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status, command_payload
    ) VALUES (
        v_user_id, v_operation_id::text, 'SETTLE_DEBT', v_request_hash, v_response, 'APPLIED', v_payload
    );
    RETURN v_response;
EXCEPTION
    WHEN numeric_value_out_of_range OR invalid_text_representation OR invalid_datetime_format THEN
        RAISE EXCEPTION 'INVALID_SETTLEMENT_COMMAND' USING ERRCODE = '22023';
    WHEN check_violation OR foreign_key_violation THEN
        RAISE EXCEPTION 'INVALID_SETTLEMENT_REFERENCE' USING ERRCODE = '22023';
END;
$settle_debt$;

REVOKE ALL ON FUNCTION public.settle_debt_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_debt_v1(jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.void_debt_settlement_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $void_debt_settlement$
DECLARE
    v_user_id uuid := auth.uid();
    v_key uuid := NULLIF(p_command->>'idempotency_key', '')::uuid;
    v_selected_id uuid := NULLIF(p_command->>'transaction_id', '')::uuid;
    v_expected bigint := NULLIF(p_command->>'expected_revision', '')::bigint;
    v_hash text := internal.movement_revision_hash_v1(p_command);
    v_existing internal.command_receipts%ROWTYPE;
    v_selected public.transactions%ROWTYPE;
    v_principal_id uuid;
    v_interest_id uuid;
    v_debt_id uuid;
    v_installment_id uuid;
    v_principal public.transactions%ROWTYPE;
    v_interest public.transactions%ROWTYPE;
    v_tx public.transactions%ROWTYPE;
    v_snapshot_id uuid;
    v_command_id uuid;
    v_before jsonb;
    v_after jsonb;
    v_effect record;
    v_entry_id uuid;
    v_ordinal integer;
    v_effects jsonb := '[]'::jsonb;
    v_transaction_effects jsonb;
    v_selected_after jsonb;
    v_selected_revision bigint;
    v_debt public.debts%ROWTYPE;
    v_remaining numeric;
    v_paid numeric;
    v_now timestamptz := clock_timestamp();
    v_receipt_id uuid := extensions.gen_random_uuid();
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','AUTH_REQUIRED')); END IF;
    IF v_key IS NULL OR v_selected_id IS NULL OR v_expected IS NULL OR v_hash IS NULL
       OR p_command->>'request_hash' <> v_hash THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user_id::text || ':' || v_key::text, 0));
    SELECT * INTO v_existing FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_key::text FOR UPDATE;
    IF FOUND THEN
        IF v_existing.command_type <> 'VOID_TRANSACTION' OR v_existing.request_hash <> v_hash THEN
            RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','IDEMPOTENCY_KEY_REUSE'));
        END IF;
        RETURN jsonb_set(v_existing.response_payload, '{status}', '"DUPLICATE"'::jsonb);
    END IF;

    SELECT * INTO v_selected FROM public.transactions
    WHERE id = v_selected_id AND user_id = v_user_id FOR UPDATE;
    IF NOT FOUND OR v_selected.operation_kind::text NOT IN ('DEBT_PAYMENT','DEBT_AMORTIZATION') THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_DEBT_SETTLEMENT'));
    END IF;
    IF v_selected.revision <> v_expected THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_selected_id,
            'current_revision',v_selected.revision,'error',jsonb_build_object('code','REVISION_CONFLICT'));
    END IF;

    IF v_selected.operation_kind::text = 'DEBT_PAYMENT' THEN
        v_principal_id := v_selected.id;
        SELECT target_transaction_id INTO v_interest_id FROM public.transaction_links
        WHERE user_id = v_user_id AND source_transaction_id = v_principal_id
          AND link_type::text = 'DEBT_AMORTIZATION';
    ELSE
        v_interest_id := v_selected.id;
        SELECT source_transaction_id INTO v_principal_id FROM public.transaction_links
        WHERE user_id = v_user_id AND target_transaction_id = v_interest_id
          AND link_type::text = 'DEBT_AMORTIZATION';
    END IF;
    IF v_principal_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','SETTLEMENT_LINK_REQUIRED'));
    END IF;
    SELECT * INTO v_principal FROM public.transactions WHERE id = v_principal_id AND user_id = v_user_id FOR UPDATE;
    IF NOT FOUND OR v_principal.operation_kind::text <> 'DEBT_PAYMENT' OR v_principal.card_id IS NOT NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_PRINCIPAL_MOVEMENT'));
    END IF;
    IF v_interest_id IS NOT NULL THEN
        SELECT * INTO v_interest FROM public.transactions WHERE id = v_interest_id AND user_id = v_user_id FOR UPDATE;
        IF NOT FOUND OR v_interest.operation_kind::text <> 'DEBT_AMORTIZATION'
           OR v_interest.account_id <> v_principal.account_id
           OR v_interest.currency_code <> v_principal.currency_code
           OR v_interest.transaction_type <> v_principal.transaction_type OR v_interest.card_id IS NOT NULL THEN
            RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_INTEREST_MOVEMENT'));
        END IF;
    END IF;
    IF v_principal.status = 'VOIDED' AND (v_interest_id IS NULL OR v_interest.status = 'VOIDED') THEN
        v_response := jsonb_build_object('status','APPLIED','result','ALREADY_VOIDED',
            'transaction_id',v_selected_id,'group_transaction_ids',jsonb_build_array(v_principal_id,v_interest_id),
            'resulting_revision',v_selected.revision,'snapshot',to_jsonb(v_selected),'ledger_effects','[]'::jsonb);
        INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status,contract_version,command_payload)
        VALUES(v_receipt_id,v_user_id,v_key::text,'VOID_TRANSACTION',v_hash,v_response,'APPLIED',1,p_command);
        RETURN v_response;
    END IF;
    IF v_principal.status NOT IN ('CONFIRMED','REVISED') OR (v_interest_id IS NOT NULL AND v_interest.status NOT IN ('CONFIRMED','REVISED')) THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','SETTLEMENT_NOT_ACTIVE'));
    END IF;

    SELECT e.debt_id, e.installment_id INTO v_debt_id, v_installment_id
    FROM public.debt_events e
    WHERE e.user_id = v_user_id AND e.transaction_id = v_principal_id AND e.event_type = 'PAYMENT';
    IF v_debt_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','SETTLEMENT_EVENT_NOT_FOUND'));
    END IF;
    SELECT * INTO v_debt FROM public.debts WHERE id = v_debt_id AND user_id = v_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','DEBT_NOT_FOUND')); END IF;

    PERFORM internal.ensure_movement_revision_baseline(v_user_id, v_principal_id);
    IF v_interest_id IS NOT NULL THEN PERFORM internal.ensure_movement_revision_baseline(v_user_id, v_interest_id); END IF;
    PERFORM set_config('kipu.movement_revision_apply','on',true);

    FOR v_tx IN
        SELECT t.* FROM public.transactions t
        WHERE t.user_id = v_user_id AND t.id IN (v_principal_id, v_interest_id)
        ORDER BY t.id FOR UPDATE
    LOOP
        v_command_id := CASE WHEN v_tx.id = v_principal_id THEN v_key
            ELSE md5('kipu:debt-settlement-void:' || v_key::text || ':' || v_tx.id::text)::uuid END;
        v_snapshot_id := extensions.gen_random_uuid();
        v_before := to_jsonb(v_tx) || jsonb_build_object('destination_account_id', NULL);
        v_after := v_before || jsonb_build_object('status','VOIDED');
        INSERT INTO public.transaction_revisions(
            id,transaction_id,user_id,revision_number,previous_payload,new_payload,change_reason,
            command_id,command_type,base_revision,local_revision,provenance
        ) VALUES (
            v_snapshot_id,v_tx.id,v_user_id,v_tx.revision+1,v_before,v_after,p_command->>'reason',
            v_command_id,'VOID_TRANSACTION',v_tx.revision,v_tx.revision+1,'REMOTE_COMMAND'
        );
        v_ordinal := 0;
        v_transaction_effects := '[]'::jsonb;
        FOR v_effect IN
            SELECT e.command_id,e.effect_ordinal,le.id AS ledger_entry_id,le.account_id,
                le.entry_role::text AS entry_role,le.signed_amount_minor,le.currency_code::text AS currency_code
            FROM internal.movement_ledger_effects e
            JOIN internal.ledger_entries le ON le.user_id=e.user_id AND le.id=e.ledger_entry_id
            WHERE e.user_id=v_user_id AND e.transaction_id=v_tx.id AND le.entry_role::text <> 'REVERSAL'
              AND NOT EXISTS (
                  SELECT 1 FROM internal.movement_ledger_effects r
                  WHERE r.user_id=e.user_id AND r.reverses_command_id=e.command_id
                    AND r.reverses_effect_ordinal=e.effect_ordinal
              )
            ORDER BY le.account_id,le.id
        LOOP
            INSERT INTO internal.ledger_entries(
                transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
            ) VALUES (
                v_tx.id,v_user_id,v_effect.account_id,-v_effect.signed_amount_minor,
                v_effect.currency_code,'REVERSAL',v_now
            ) RETURNING id INTO v_entry_id;
            INSERT INTO internal.movement_ledger_effects(
                user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id,
                reverses_command_id,reverses_effect_ordinal
            ) VALUES (
                v_user_id,v_command_id,v_ordinal,v_tx.id,v_snapshot_id,v_entry_id,
                v_effect.command_id,v_effect.effect_ordinal
            );
            INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
            VALUES(v_user_id,v_entry_id,v_command_id,v_ordinal);
            v_transaction_effects := v_transaction_effects || jsonb_build_array(jsonb_build_object(
                'ledger_entry_id',v_entry_id,'command_id',v_command_id,'effect_ordinal',v_ordinal,
                'reverses',jsonb_build_array(v_effect.command_id,v_effect.effect_ordinal)
            ));
            v_ordinal := v_ordinal + 1;
        END LOOP;
        IF v_ordinal = 0 THEN RAISE EXCEPTION 'SETTLEMENT_FINANCIAL_EVIDENCE_REQUIRED' USING ERRCODE = 'P0001'; END IF;
        v_effects := v_effects || v_transaction_effects;

        UPDATE public.transactions SET status='VOIDED',revision=v_tx.revision+1,updated_at=v_now
        WHERE id=v_tx.id AND user_id=v_user_id;
        INSERT INTO public.movement_official_revisions(
            user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at
        ) VALUES(v_user_id,v_tx.id,v_tx.revision+1,v_snapshot_id,v_snapshot_id,v_now);
        INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
        VALUES(v_user_id,'TRANSACTION',v_tx.id,v_tx.revision+1,'UPSERT',
            v_after || jsonb_build_object('id',v_tx.id,'ledger_effects',v_transaction_effects));
        IF v_tx.id = v_selected_id THEN
            v_selected_after := v_after;
            v_selected_revision := v_tx.revision + 1;
        END IF;
    END LOOP;

    SELECT GREATEST(0::numeric, v_debt.total_minor::numeric + COALESCE(sum(
        CASE WHEN e.principal_delta_minor IS NOT NULL THEN e.principal_delta_minor::numeric
             WHEN e.event_type = 'PAYMENT' THEN -e.amount_minor::numeric ELSE 0::numeric END
    ), 0::numeric)) INTO v_remaining
    FROM public.debt_events e
    LEFT JOIN public.transactions t ON t.user_id=e.user_id AND t.id=e.transaction_id
    WHERE e.user_id=v_user_id AND e.debt_id=v_debt_id
      AND (e.transaction_id IS NULL OR t.status <> 'VOIDED');
    UPDATE public.debts
    SET status=CASE WHEN v_remaining=0 THEN 'SETTLED' ELSE 'ACTIVE' END,
        revision=revision+1,updated_at=v_now
    WHERE id=v_debt_id AND user_id=v_user_id
    RETURNING * INTO v_debt;
    IF v_installment_id IS NOT NULL THEN
        SELECT COALESCE(sum(CASE WHEN e.principal_delta_minor IS NULL THEN e.amount_minor::numeric
                ELSE GREATEST(0::numeric,-e.principal_delta_minor::numeric) END),0::numeric)
        INTO v_paid
        FROM public.debt_events e LEFT JOIN public.transactions t ON t.user_id=e.user_id AND t.id=e.transaction_id
        WHERE e.user_id=v_user_id AND e.debt_id=v_debt_id AND e.installment_id=v_installment_id
          AND e.event_type='PAYMENT' AND (e.transaction_id IS NULL OR t.status <> 'VOIDED');
        UPDATE public.debt_installments i
        SET status=CASE WHEN v_paid>=i.amount_minor THEN 'PAID' WHEN v_paid>0 THEN 'PARTIAL' ELSE 'PENDING' END,
            revision=revision+1,updated_at=v_now
        WHERE i.id=v_installment_id AND i.user_id=v_user_id;
        INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
        SELECT v_user_id,'DEBT_INSTALLMENT',i.id,i.revision,'UPSERT',to_jsonb(i)
        FROM public.debt_installments i WHERE i.id=v_installment_id AND i.user_id=v_user_id;
    END IF;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'DEBT',v_debt.id,v_debt.revision,'UPSERT',to_jsonb(v_debt));

    v_response := jsonb_build_object(
        'status','APPLIED','result','VOIDED','transaction_id',v_selected_id,
        'group_transaction_ids',jsonb_build_array(v_principal_id,v_interest_id),
        'debt_id',v_debt_id,'remaining_minor',v_remaining,'resulting_revision',v_selected_revision,
        'server_updated_at',v_now,'snapshot',v_selected_after,'ledger_effects',v_effects
    );
    INSERT INTO internal.command_receipts(
        id,user_id,idempotency_key,command_type,request_hash,response_payload,status,contract_version,command_payload
    ) VALUES(v_receipt_id,v_user_id,v_key::text,'VOID_TRANSACTION',v_hash,v_response,'APPLIED',1,p_command);
    RETURN v_response;
EXCEPTION WHEN others THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','SETTLEMENT_VOID_FAILED','retryable',false));
END;
$void_debt_settlement$;

REVOKE ALL ON FUNCTION public.void_debt_settlement_v1(jsonb) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.void_debt_settlement_v1(jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.void_transaction_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $void_transaction$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_kind text;
BEGIN
    SELECT operation_kind::text INTO v_operation_kind FROM public.transactions
    WHERE id = NULLIF(p_command->>'transaction_id','')::uuid AND user_id = v_user_id;
    IF v_operation_kind IN ('DEBT_PAYMENT','DEBT_AMORTIZATION') THEN
        RETURN public.void_debt_settlement_v1(p_command);
    END IF;
    RETURN internal.apply_movement_revision_v1(p_command,'VOID_TRANSACTION');
END;
$void_transaction$;
REVOKE ALL ON FUNCTION public.void_transaction_v1(jsonb) FROM PUBLIC, anon, service_role;
GRANT EXECUTE ON FUNCTION public.void_transaction_v1(jsonb) TO authenticated;

COMMIT;
