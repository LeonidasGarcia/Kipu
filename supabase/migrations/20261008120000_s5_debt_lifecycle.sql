BEGIN;

-- Preflight history before changing the projections or adding restrictive references.
DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.debt_installments
        GROUP BY debt_id, installment_number HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'DEBT_INSTALLMENT_SEQUENCE_DUPLICATE: reconcile duplicate installment numbers before migration'
            USING ERRCODE = '23505';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.debt_events e
        JOIN public.debts d ON d.id = e.debt_id
        WHERE e.user_id <> d.user_id
    ) OR EXISTS (
        SELECT 1 FROM public.debt_installments i
        JOIN public.debts d ON d.id = i.debt_id
        WHERE i.user_id <> d.user_id
    ) THEN
        RAISE EXCEPTION 'DEBT_OWNER_MISMATCH: reconcile cross-owner debt references before migration'
            USING ERRCODE = '23503';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.debt_events e
        JOIN public.transactions t ON t.id = e.transaction_id
        WHERE e.user_id <> t.user_id
    ) THEN
        RAISE EXCEPTION 'DEBT_TRANSACTION_OWNER_MISMATCH: reconcile linked movement ownership before migration'
            USING ERRCODE = '23503';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.debt_events e
        JOIN public.debt_installments i ON i.id = e.installment_id
        WHERE e.user_id <> i.user_id OR e.debt_id <> i.debt_id
    ) THEN
        RAISE EXCEPTION 'DEBT_INSTALLMENT_OWNER_MISMATCH: reconcile linked installment ownership before migration'
            USING ERRCODE = '23503';
    END IF;
END;
$preflight$;

ALTER TABLE public.debts
    ADD COLUMN IF NOT EXISTS opened_on date,
    ADD COLUMN IF NOT EXISTS opening_mode text NOT NULL DEFAULT 'HISTORICAL',
    ADD COLUMN IF NOT EXISTS reminder_lead_days smallint;

UPDATE public.debts d
SET opening_mode = CASE
    WHEN EXISTS (
        SELECT 1 FROM public.debt_events e
        WHERE e.user_id = d.user_id AND e.debt_id = d.id
          AND e.event_type = 'DISBURSEMENT' AND e.transaction_id IS NOT NULL
    ) THEN 'NEW_CASH_FLOW'
    ELSE 'HISTORICAL'
END;

DO $opening_mode_constraint$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conrelid = 'public.debts'::regclass AND conname = 'debts_opening_mode_valid'
    ) THEN
        ALTER TABLE public.debts ADD CONSTRAINT debts_opening_mode_valid
            CHECK (opening_mode IN ('NEW_CASH_FLOW', 'HISTORICAL')) NOT VALID;
    END IF;
END;
$opening_mode_constraint$;

UPDATE public.debts
SET opened_on = COALESCE(opened_on, created_at::date)
WHERE opened_on IS NULL;

ALTER TABLE public.debts
    ALTER COLUMN opened_on SET DEFAULT CURRENT_DATE,
    ALTER COLUMN opened_on SET NOT NULL;

DO $reminder_constraint$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
        WHERE conrelid = 'public.debts'::regclass AND conname = 'debts_reminder_lead_days_range'
    ) THEN
        ALTER TABLE public.debts
            ADD CONSTRAINT debts_reminder_lead_days_range
            CHECK (reminder_lead_days IS NULL OR reminder_lead_days BETWEEN 0 AND 365) NOT VALID;
    END IF;
END;
$reminder_constraint$;

ALTER TABLE public.debt_events
    ADD COLUMN IF NOT EXISTS principal_delta_minor bigint;

-- Ambiguous legacy adjustment and forgiveness magnitudes have no documented direction.
-- Stop atomically until the owner explicitly reconciles those rows.
DO $event_semantics$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.debt_events
        WHERE event_type IN ('ADJUSTMENT', 'FORGIVENESS') AND principal_delta_minor IS NULL
    ) THEN
        RAISE EXCEPTION 'DEBT_EVENT_SEMANTICS_UNRESOLVED: set explicit principal deltas for legacy ADJUSTMENT/FORGIVENESS rows before retrying'
            USING ERRCODE = 'P0001';
    END IF;
END;
$event_semantics$;

CREATE UNIQUE INDEX IF NOT EXISTS debts_user_id_id_uq
    ON public.debts (user_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS transactions_user_id_id_uq
    ON public.transactions (user_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS debt_installments_user_debt_id_uq
    ON public.debt_installments (user_id, debt_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS debt_installments_debt_number_unique
    ON public.debt_installments (debt_id, installment_number);

DO $owner_constraints$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = 'debt_installments_debt_owner_fkey') THEN
        ALTER TABLE public.debt_installments
            ADD CONSTRAINT debt_installments_debt_owner_fkey
            FOREIGN KEY (user_id, debt_id) REFERENCES public.debts (user_id, id)
            ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = 'debt_events_debt_owner_fkey') THEN
        ALTER TABLE public.debt_events
            ADD CONSTRAINT debt_events_debt_owner_fkey
            FOREIGN KEY (user_id, debt_id) REFERENCES public.debts (user_id, id)
            ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = 'debt_events_transaction_owner_fkey') THEN
        ALTER TABLE public.debt_events
            ADD CONSTRAINT debt_events_transaction_owner_fkey
            FOREIGN KEY (user_id, transaction_id) REFERENCES public.transactions (user_id, id)
            ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = 'debt_events_installment_owner_fkey') THEN
        ALTER TABLE public.debt_events
            ADD CONSTRAINT debt_events_installment_owner_fkey
            FOREIGN KEY (user_id, debt_id, installment_id)
            REFERENCES public.debt_installments (user_id, debt_id, id)
            ON DELETE RESTRICT NOT VALID;
    END IF;
END;
$owner_constraints$;

ALTER TABLE public.debt_installments VALIDATE CONSTRAINT debt_installments_debt_owner_fkey;
ALTER TABLE public.debt_events VALIDATE CONSTRAINT debt_events_debt_owner_fkey;
ALTER TABLE public.debt_events VALIDATE CONSTRAINT debt_events_transaction_owner_fkey;
ALTER TABLE public.debt_events VALIDATE CONSTRAINT debt_events_installment_owner_fkey;
ALTER TABLE public.debts VALIDATE CONSTRAINT debts_opening_mode_valid;

CREATE INDEX IF NOT EXISTS idx_debt_events_user_debt ON public.debt_events (user_id, debt_id, occurred_at, id);
CREATE INDEX IF NOT EXISTS idx_debt_events_user_transaction ON public.debt_events (user_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_debt_events_user_installment ON public.debt_events (user_id, debt_id, installment_id);

ALTER TABLE public.transactions DROP CONSTRAINT IF EXISTS transactions_expense_requires_category_v1;
ALTER TABLE public.transactions
    ADD CONSTRAINT transactions_expense_requires_category_v1
    CHECK (transaction_type <> 'EXPENSE' OR category_id IS NOT NULL OR operation_kind IN ('DEBT_DISBURSEMENT', 'DEBT_PAYMENT')) NOT VALID;

CREATE OR REPLACE VIEW public.v_debt_summary
WITH (security_invoker = true)
AS
SELECT
    d.id AS debt_id,
    d.user_id,
    d.obligation_type AS debt_type,
    d.counterparty_name,
    d.total_minor,
    d.currency_code,
    d.due_date,
    d.status,
    COALESCE(sum(
        CASE
            WHEN t.status = 'VOIDED' OR de.event_type <> 'PAYMENT' THEN 0::numeric
            WHEN de.principal_delta_minor IS NULL THEN de.amount_minor::numeric
            ELSE GREATEST(0::numeric, -de.principal_delta_minor::numeric)
        END
    ), 0::numeric) AS total_paid_minor,
    GREATEST(
        0::numeric,
        d.total_minor::numeric + COALESCE(sum(
            CASE
                WHEN t.status = 'VOIDED' THEN 0::numeric
                WHEN de.principal_delta_minor IS NOT NULL THEN de.principal_delta_minor::numeric
                WHEN de.event_type = 'PAYMENT' THEN -de.amount_minor::numeric
                ELSE 0::numeric
            END
        ), 0::numeric)
    ) AS remaining_minor
FROM public.debts d
LEFT JOIN public.debt_events de ON de.debt_id = d.id AND de.user_id = d.user_id
LEFT JOIN public.transactions t ON t.id = de.transaction_id AND t.user_id = de.user_id
WHERE d.deleted_at IS NULL
GROUP BY d.id, d.user_id, d.obligation_type, d.counterparty_name, d.total_minor,
    d.currency_code, d.due_date, d.status;

GRANT SELECT ON public.v_debt_summary TO authenticated;

CREATE OR REPLACE FUNCTION public.open_debt_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $open_debt$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid;
    v_debt_id uuid;
    v_obligation_type text;
    v_request_hash text;
    v_server_hash text;
    v_canonical jsonb;
    v_receipt internal.command_receipts%ROWTYPE;
    v_response jsonb;
    v_is_pro boolean;
    v_active_count integer;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '28000';
    END IF;
    IF COALESCE((p_payload->>'contract_version')::integer, 0) <> 1 THEN
        RAISE EXCEPTION 'Unsupported debt command contract version' USING ERRCODE = '22023';
    END IF;

    v_operation_id := NULLIF(p_payload->>'operation_id', '')::uuid;
    v_debt_id := NULLIF(p_payload->>'debt_id', '')::uuid;
    v_obligation_type := upper(NULLIF(btrim(p_payload->>'obligation_type'), ''));
    v_request_hash := lower(NULLIF(p_payload->>'request_hash', ''));
    IF v_operation_id IS NULL OR v_debt_id IS NULL OR v_obligation_type NOT IN ('PAYABLE', 'RECEIVABLE') THEN
        RAISE EXCEPTION 'Debt command identity and obligation type are required' USING ERRCODE = '22023';
    END IF;
    IF v_request_hash IS NULL OR v_request_hash !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'A lowercase SHA-256 request hash is required' USING ERRCODE = '22023';
    END IF;

    v_canonical := p_payload - 'request_hash' - 'user_id';
    -- The hash is checked for shape and replayed together with the canonical JSON
    -- payload stored in the shared receipt ledger. Payload equality remains the
    -- authority, so a client cannot reuse a key with a different request body.
    v_server_hash := v_request_hash;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('kipu:debt-command:' || v_user_id::text || ':' || v_operation_id::text, 0)
    );
    SELECT * INTO v_receipt
    FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text
    FOR UPDATE;
    IF FOUND THEN
        IF v_receipt.command_type <> 'OPEN_DEBT' OR v_receipt.request_hash <> v_server_hash
           OR v_receipt.command_payload IS DISTINCT FROM v_canonical THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = 'P0001';
        END IF;
        RETURN v_receipt.response_payload || jsonb_build_object('status', 'DUPLICATE');
    END IF;

    v_is_pro := private.is_user_pro(v_user_id);
    IF NOT v_is_pro THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('kipu:debt-quota:' || v_user_id::text, 0)
        );
        SELECT count(*)::integer INTO v_active_count
        FROM public.debts
        WHERE user_id = v_user_id AND status = 'ACTIVE' AND deleted_at IS NULL;
        IF v_active_count >= 2 THEN
            RAISE EXCEPTION 'FREE_DEBT_QUOTA_EXCEEDED' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    IF v_obligation_type = 'PAYABLE' THEN
        v_response := private.open_payable_debt_v1(p_payload);
    ELSE
        v_response := private.open_receivable_debt_v1(p_payload);
    END IF;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT', d.id, d.revision, 'UPSERT', to_jsonb(d)
    FROM public.debts d WHERE d.user_id = v_user_id AND d.id = v_debt_id;

    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'DEBT_EVENT', e.id, 1, 'UPSERT', to_jsonb(e)
    FROM public.debt_events e WHERE e.user_id = v_user_id AND e.debt_id = v_debt_id
    ORDER BY e.created_at DESC, e.id DESC LIMIT 1;

    v_response := v_response || jsonb_build_object(
        'status', 'APPLIED',
        'operation_id', v_operation_id,
        'request_hash', v_server_hash
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status, command_payload
    ) VALUES (
        v_user_id, v_operation_id::text, 'OPEN_DEBT', v_server_hash, v_response, 'APPLIED', v_canonical
    );
    RETURN v_response;
END;
$open_debt$;

REVOKE ALL ON FUNCTION public.open_debt_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.open_debt_v1(jsonb) TO authenticated;

COMMIT;
