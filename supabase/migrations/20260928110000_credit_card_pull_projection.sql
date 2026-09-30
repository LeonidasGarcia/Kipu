BEGIN;

-- Persist card currency and issuer so a fresh-device CARD projection can be
-- validated without treating the linked credit-liability account as cash.
ALTER TABLE public.cards ADD COLUMN IF NOT EXISTS currency_code char(3);
ALTER TABLE public.cards ADD COLUMN IF NOT EXISTS issuer text;

-- Preserve legacy values where present; infer currency only from a linked
-- account or one unambiguous confirmed transaction currency. Never default a
-- credit card to the profile currency.
UPDATE public.cards c
SET currency_code = NULLIF(to_jsonb(c)->>'currency', '')::char(3)
WHERE c.currency_code IS NULL AND NULLIF(to_jsonb(c)->>'currency', '') IS NOT NULL;

UPDATE public.cards c
SET currency_code = a.currency_code
FROM public.accounts a
WHERE c.currency_code IS NULL AND c.account_id = a.id AND c.user_id = a.user_id;

UPDATE public.cards c
SET currency_code = known.currency_code::char(3)
FROM (
    SELECT t.user_id, t.card_id, min(t.currency_code::text) AS currency_code
    FROM public.transactions t
    WHERE t.card_id IS NOT NULL AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
    GROUP BY t.user_id, t.card_id
    HAVING count(DISTINCT t.currency_code) = 1
) known
WHERE c.currency_code IS NULL AND c.user_id = known.user_id AND c.id = known.card_id;

UPDATE public.cards c
SET issuer = COALESCE(NULLIF(btrim(c.issuer), ''), NULLIF(btrim(cp.institution_name), ''), NULLIF(btrim(cp.institution_code), ''), 'Desconocido')
FROM public.credit_products cp
WHERE c.product_id = cp.id AND (c.issuer IS NULL OR btrim(c.issuer) = '');

UPDATE public.cards SET issuer = 'Desconocido' WHERE issuer IS NULL OR btrim(issuer) = '';

-- Do not guess a credit card's currency from the profile currency. If historic
-- data has no linked account or unambiguous confirmed transaction, stop the
-- migration and require an explicit data repair before enabling fresh-device pull.
DO $$
DECLARE
    v_missing_currency_count integer;
    v_invalid_currency_count integer;
    v_invalid_last4_count integer;
    v_invalid_credit_terms_count integer;
BEGIN
    SELECT count(*) INTO v_missing_currency_count
    FROM public.cards
    WHERE currency_code IS NULL;

    IF v_missing_currency_count > 0 THEN
        RAISE EXCEPTION 'CARD_CURRENCY_BACKFILL_REQUIRED: % card rows have no authoritative currency',
            v_missing_currency_count;
    END IF;

    SELECT count(*) INTO v_invalid_currency_count
    FROM public.cards
    WHERE currency_code NOT IN ('PEN', 'USD');
    IF v_invalid_currency_count > 0 THEN
        RAISE EXCEPTION 'CARD_CURRENCY_REPAIR_REQUIRED: % cards use a currency unsupported by the Android domain',
            v_invalid_currency_count;
    END IF;

    SELECT count(*) INTO v_invalid_last4_count
    FROM public.cards
    WHERE last4 IS NULL OR btrim(last4::text) !~ '^[0-9]{4}$';
    IF v_invalid_last4_count > 0 THEN
        RAISE EXCEPTION 'CARD_LAST4_REPAIR_REQUIRED: % cards do not have four numeric last digits',
            v_invalid_last4_count;
    END IF;

    SELECT count(*) INTO v_invalid_credit_terms_count
    FROM public.cards
    WHERE (is_credit AND (
               credit_limit_minor IS NULL OR credit_limit_minor < 0
               OR closing_day IS NULL OR closing_day NOT BETWEEN 1 AND 31
               OR due_day IS NULL OR due_day NOT BETWEEN 1 AND 31
           ))
       OR (NOT is_credit AND (
               account_id IS NULL OR credit_limit_minor IS NOT NULL
               OR closing_day IS NOT NULL OR due_day IS NOT NULL
           ));
    IF v_invalid_credit_terms_count > 0 THEN
        RAISE EXCEPTION 'CARD_TERMS_REPAIR_REQUIRED: % cards have incomplete or incompatible credit fields',
            v_invalid_credit_terms_count;
    END IF;
END;
$$;

-- Match java.util.UUID.nameUUIDFromBytes so client and server derive the same
-- owner-scoped liability-account identity for a card.
CREATE OR REPLACE FUNCTION internal.credit_liability_identity_v1(
    p_kind text,
    p_user_id uuid,
    p_card_id uuid
)
RETURNS uuid
LANGUAGE plpgsql
IMMUTABLE
STRICT
SET search_path = ''
AS $$
DECLARE
    v_bytes bytea;
BEGIN
    IF p_kind NOT IN ('account', 'create-operation') THEN
        RAISE EXCEPTION 'INVALID_LIABILITY_ID_KIND';
    END IF;
    v_bytes := extensions.digest(
        convert_to('kipu:credit-liability:' || p_kind || ':v1:' || lower(p_user_id::text) || ':' || lower(p_card_id::text), 'UTF8'),
        'md5'
    );
    v_bytes := set_byte(v_bytes, 6, (get_byte(v_bytes, 6) & 15) | 48);
    v_bytes := set_byte(v_bytes, 8, (get_byte(v_bytes, 8) & 63) | 128);
    RETURN (
        encode(substring(v_bytes FROM 1 FOR 4), 'hex') || '-' ||
        encode(substring(v_bytes FROM 5 FOR 2), 'hex') || '-' ||
        encode(substring(v_bytes FROM 7 FOR 2), 'hex') || '-' ||
        encode(substring(v_bytes FROM 9 FOR 2), 'hex') || '-' ||
        encode(substring(v_bytes FROM 11 FOR 6), 'hex')
    )::uuid;
END;
$$;
REVOKE ALL ON FUNCTION internal.credit_liability_identity_v1(text, uuid, uuid) FROM PUBLIC, anon, authenticated, service_role;

-- Credit cards own a hidden CREDIT_LIABILITY account. Legacy links to liquid
-- payment accounts are preserved as accounts; only the card link is redirected.
INSERT INTO public.accounts (
    id, user_id, creation_operation_id, name, account_type, currency_code,
    initial_balance_minor_units, opened_at, is_archived, revision
)
SELECT internal.credit_liability_identity_v1('account', c.user_id, c.id),
       c.user_id,
       internal.credit_liability_identity_v1('create-operation', c.user_id, c.id),
       'Pasivo tarjeta •••• ' || c.last4::text,
       'CREDIT_LIABILITY'::public.account_type,
       c.currency_code,
       0,
       c.created_at,
       false,
       1
FROM public.cards c
WHERE c.is_credit
  AND NOT EXISTS (
      SELECT 1 FROM public.accounts a
      WHERE a.id = c.account_id AND a.user_id = c.user_id
        AND a.account_type = 'CREDIT_LIABILITY' AND a.currency_code = c.currency_code
  )
ON CONFLICT (id) DO NOTHING;

UPDATE public.cards c
SET account_id = internal.credit_liability_identity_v1('account', c.user_id, c.id)
WHERE c.is_credit
  AND NOT EXISTS (
      SELECT 1 FROM public.accounts a
      WHERE a.id = c.account_id AND a.user_id = c.user_id
        AND a.account_type = 'CREDIT_LIABILITY' AND a.currency_code = c.currency_code
  );

DO $$
DECLARE v_invalid_credit_accounts integer;
BEGIN
    SELECT count(*) INTO v_invalid_credit_accounts
    FROM public.cards c
    LEFT JOIN public.accounts a ON a.id = c.account_id AND a.user_id = c.user_id
    WHERE c.is_credit AND (
        a.id IS NULL OR a.account_type <> 'CREDIT_LIABILITY'
        OR a.currency_code <> c.currency_code OR a.is_archived
        OR a.initial_balance_minor_units <> 0
    );
    IF v_invalid_credit_accounts > 0 THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_ACCOUNT_REPAIR_REQUIRED: % credit cards have an invalid linked liability account',
            v_invalid_credit_accounts;
    END IF;
END;
$$;

-- Publish internal liability accounts so another device can apply them before
-- later card/transaction events. Pull also embeds the account ID in CARD rows
-- and the client can safely seed it for older event ordering.
INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
SELECT a.user_id, 'ACCOUNT', a.id, a.revision, 'UPSERT', jsonb_build_object(
    'id', a.id, 'creation_operation_id', a.creation_operation_id,
    'alias', a.name, 'type', a.account_type, 'currency', a.currency_code,
    'initial_balance_minor_units', a.initial_balance_minor_units,
    'opened_at', a.opened_at, 'is_archived', a.is_archived
)
FROM public.accounts a
WHERE a.account_type = 'CREDIT_LIABILITY'
  AND EXISTS (SELECT 1 FROM public.cards c WHERE c.user_id = a.user_id AND c.account_id = a.id AND c.is_credit)
  AND NOT EXISTS (
      SELECT 1 FROM internal.sync_changes sc
      WHERE sc.user_id = a.user_id AND sc.entity_type = 'ACCOUNT' AND sc.entity_id = a.id
  );

ALTER TABLE public.cards ALTER COLUMN currency_code SET NOT NULL;
ALTER TABLE public.cards ALTER COLUMN issuer SET NOT NULL;
ALTER TABLE public.cards ALTER COLUMN last4 SET NOT NULL;
ALTER TABLE public.cards ALTER COLUMN account_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cards'::regclass
          AND conname = 'cards_currency_code_fkey'
    ) THEN
        ALTER TABLE public.cards
            ADD CONSTRAINT cards_currency_code_fkey
            FOREIGN KEY (currency_code) REFERENCES public.currencies(code) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cards'::regclass
          AND conname = 'cards_issuer_length_check'
    ) THEN
        ALTER TABLE public.cards
            ADD CONSTRAINT cards_issuer_length_check
            CHECK (length(btrim(issuer)) BETWEEN 1 AND 80) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cards'::regclass
          AND conname = 'cards_currency_code_supported_check'
    ) THEN
        ALTER TABLE public.cards
            ADD CONSTRAINT cards_currency_code_supported_check
            CHECK (currency_code IN ('PEN', 'USD')) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cards'::regclass
          AND conname = 'cards_last4_digits_check'
    ) THEN
        ALTER TABLE public.cards
            ADD CONSTRAINT cards_last4_digits_check
            CHECK (btrim(last4::text) ~ '^[0-9]{4}$') NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cards'::regclass
          AND conname = 'cards_type_link_and_credit_terms_check'
    ) THEN
        ALTER TABLE public.cards
            ADD CONSTRAINT cards_type_link_and_credit_terms_check
            CHECK (
                (is_credit AND account_id IS NOT NULL
                    AND credit_limit_minor IS NOT NULL AND credit_limit_minor >= 0
                    AND closing_day IS NOT NULL AND due_day IS NOT NULL
                    AND closing_day BETWEEN 1 AND 31 AND due_day BETWEEN 1 AND 31)
                OR
                (NOT is_credit AND account_id IS NOT NULL
                    AND credit_limit_minor IS NULL AND closing_day IS NULL AND due_day IS NULL)
            ) NOT VALID;
    END IF;
END;
$$;

ALTER TABLE public.cards VALIDATE CONSTRAINT cards_currency_code_fkey;
ALTER TABLE public.cards VALIDATE CONSTRAINT cards_issuer_length_check;
ALTER TABLE public.cards VALIDATE CONSTRAINT cards_currency_code_supported_check;
ALTER TABLE public.cards VALIDATE CONSTRAINT cards_last4_digits_check;
ALTER TABLE public.cards VALIDATE CONSTRAINT cards_type_link_and_credit_terms_check;

CREATE OR REPLACE FUNCTION internal.card_sync_projection_v1(p_card public.cards)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT jsonb_build_object(
        'id', p_card.id,
        'creation_operation_id', p_card.creation_operation_id,
        'account_id', p_card.account_id,
        'alias', NULLIF(btrim(p_card.alias), ''),
        'type', CASE WHEN p_card.is_credit THEN 'CREDIT' ELSE 'DEBIT' END,
        'currency', NULLIF(btrim(p_card.currency_code::text), ''),
        'network', COALESCE(NULLIF(btrim(p_card.network), ''), 'OTHER'),
        'issuer', COALESCE(NULLIF(btrim(p_card.issuer), ''), 'Desconocido'),
        'last_four_digits', btrim(p_card.last4::text),
        'credit_limit_minor_units', p_card.credit_limit_minor,
        'billing_day', p_card.closing_day,
        'due_day', p_card.due_day,
        'preset_id', p_card.preset_id,
        'style_preset_id', p_card.style_preset_id,
        'color', p_card.color,
        'icon', p_card.icon,
        'is_archived', p_card.is_archived,
        'personal_tea_bps', p_card.personal_tea_bps,
        'created_at', p_card.created_at
    )
$$;

-- Contract guard applies to new/changed writes while allowing existing legacy
-- rows to be inspected and repaired without a destructive backfill.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.transactions'::regclass
          AND conname = 'transactions_expense_requires_category_v1'
    ) THEN
        ALTER TABLE public.transactions
            ADD CONSTRAINT transactions_expense_requires_category_v1
            CHECK (transaction_type <> 'EXPENSE' OR category_id IS NOT NULL) NOT VALID;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION internal.emit_card_sync_projection_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_operation text;
BEGIN
    IF ROW(NEW.alias, NEW.network, NEW.issuer, NEW.last4, NEW.currency_code,
           NEW.account_id, NEW.credit_limit_minor, NEW.closing_day, NEW.due_day, NEW.preset_id,
           NEW.style_preset_id, NEW.color, NEW.icon, NEW.is_archived)
       IS NOT DISTINCT FROM
       ROW(OLD.alias, OLD.network, OLD.issuer, OLD.last4, OLD.currency_code,
           OLD.account_id, OLD.credit_limit_minor, OLD.closing_day, OLD.due_day, OLD.preset_id,
           OLD.style_preset_id, OLD.color, OLD.icon, OLD.is_archived) THEN
        RETURN NEW;
    END IF;

    v_operation := CASE WHEN NEW.is_archived THEN 'ARCHIVE' ELSE 'UPSERT' END;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (NEW.user_id, 'CARD', NEW.id, NEW.revision, v_operation,
            internal.card_sync_projection_v1(NEW));
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS cards_emit_sync_projection_v1 ON public.cards;
CREATE TRIGGER cards_emit_sync_projection_v1
AFTER UPDATE OF account_id, alias, network, issuer, last4, currency_code, credit_limit_minor,
    closing_day, due_day, preset_id, style_preset_id, color, icon, is_archived
ON public.cards
FOR EACH ROW EXECUTE FUNCTION internal.emit_card_sync_projection_v1();

CREATE OR REPLACE FUNCTION public.register_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_alias text;
    v_type text;
    v_currency bpchar(3);
    v_network text;
    v_issuer text;
    v_last_four text;
    v_credit_limit bigint;
    v_billing_day integer;
    v_due_day integer;
    v_preset_id text;
    v_style_preset_id text;
    v_color text;
    v_icon text;
    v_request_hash text;
    v_client_request_hash text;
    v_existing_receipt record;
    v_computable_count integer;
    v_linked_account record;
    v_expected_liability_account_id uuid;
    v_response jsonb;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_operation_id := (p_command->>'operation_id')::uuid;
    v_card_id := (p_command->>'card_id')::uuid;
    v_account_id := NULLIF(p_command->>'account_id', '')::uuid;
    v_alias := NULLIF(trim(p_command->>'alias'), '');
    v_type := upper(p_command->>'type');
    v_currency := NULLIF(trim(p_command->>'currency'), '')::bpchar(3);
    v_network := upper(p_command->>'network');
    v_issuer := NULLIF(trim(p_command->>'issuer'), '');
    v_last_four := trim(p_command->>'last_four_digits');
    v_credit_limit := (p_command->>'credit_limit_minor_units')::bigint;
    v_billing_day := (p_command->>'billing_day')::integer;
    v_due_day := (p_command->>'due_day')::integer;
    v_preset_id := p_command->>'preset_id';
    v_style_preset_id := p_command->>'style_preset_id';
    v_color := p_command->>'color';
    v_icon := p_command->>'icon';
    v_client_request_hash := p_command->>'payload_hash';

    IF v_card_id IS NULL OR v_operation_id IS NULL OR v_client_request_hash IS NULL
       OR v_issuer IS NULL OR length(v_issuer) > 80 OR v_alias IS NOT NULL AND length(v_alias) > 80
       OR v_currency IS NULL OR length(v_last_four) <> 4
       OR v_last_four !~ '^[0-9]{4}$' THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Required card fields are missing or invalid';
    END IF;

    -- Serialize duplicate submissions and compute the receipt identity from
    -- the complete normalized request, never from the client-provided digest.
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id::text || ':' || v_operation_id::text, 0)
    );
    v_request_hash := pg_catalog.encode(
        extensions.digest(convert_to((p_command - 'payload_hash')::text, 'UTF8'), 'sha256'),
        'hex'
    );

    SELECT * INTO v_existing_receipt FROM internal.command_receipts
    WHERE user_id = v_user_id AND idempotency_key = v_operation_id::text;
    IF FOUND THEN
        -- Accept the previous client-hash format for in-flight commands created
        -- before this migration; all new receipts store the server hash above.
        IF v_existing_receipt.request_hash IN (v_request_hash, v_client_request_hash) THEN
            RETURN v_existing_receipt.response_payload;
        END IF;
        RAISE EXCEPTION 'OPERATION_COLLISION: Operation ID reused with differing payload';
    END IF;

    SELECT ((SELECT count(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')) +
            (SELECT count(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false))
    INTO v_computable_count;
    IF v_computable_count >= 4 THEN RAISE EXCEPTION 'FREE_LIMIT_REACHED: Maximum 4 active instruments reached under Free tier'; END IF;

    IF v_type = 'DEBIT' THEN
        IF v_account_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: Debit card requires linked account_id'; END IF;
        SELECT * INTO v_linked_account FROM public.accounts
        WHERE id = v_account_id AND user_id = v_user_id AND is_archived = false;
        IF NOT FOUND THEN RAISE EXCEPTION 'ACCOUNT_NOT_FOUND: Linked account must exist and be active'; END IF;
        IF v_linked_account.account_type = 'CREDIT_LIABILITY' THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Debit card cannot link to a credit-liability account';
        END IF;
        v_currency := v_linked_account.currency_code;
    ELSIF v_type = 'CREDIT' THEN
        v_expected_liability_account_id := internal.credit_liability_identity_v1('account', v_user_id, v_card_id);
        IF v_account_id IS DISTINCT FROM v_expected_liability_account_id
           OR v_credit_limit IS NULL OR v_credit_limit < 0
           OR v_billing_day IS NULL OR v_billing_day NOT BETWEEN 1 AND 31
           OR v_due_day IS NULL OR v_due_day NOT BETWEEN 1 AND 31 THEN
            RAISE EXCEPTION 'INVALID_REQUEST: Credit card fields are invalid';
        END IF;
        SELECT * INTO v_linked_account FROM public.accounts
        WHERE id = v_account_id AND user_id = v_user_id;
        IF FOUND THEN
            IF v_linked_account.account_type <> 'CREDIT_LIABILITY'
               OR v_linked_account.currency_code <> v_currency OR v_linked_account.is_archived THEN
                RAISE EXCEPTION 'INVALID_REQUEST: Credit card liability account is incompatible';
            END IF;
        ELSE
            INSERT INTO public.accounts (
                id, user_id, creation_operation_id, name, account_type, currency_code,
                initial_balance_minor_units, opened_at, is_archived, revision
            ) VALUES (
                v_account_id, v_user_id,
                internal.credit_liability_identity_v1('create-operation', v_user_id, v_card_id),
                'Pasivo tarjeta •••• ' || v_last_four, 'CREDIT_LIABILITY', v_currency,
                0, clock_timestamp(), false, 1
            );
        END IF;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown card type %', v_type;
    END IF;
    IF v_currency NOT IN ('PEN', 'USD') OR NOT EXISTS (SELECT 1 FROM public.currencies WHERE code = v_currency) THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Unsupported card currency';
    END IF;

    INSERT INTO public.cards (
        id, user_id, creation_operation_id, account_id, network, alias, last4,
        is_credit, credit_limit_minor, closing_day, due_day, currency_code, issuer,
        preset_id, style_preset_id, color, icon, is_archived, revision
    ) VALUES (
        v_card_id, v_user_id, v_operation_id, v_account_id, v_network,
        COALESCE(v_alias, v_issuer), v_last_four, (v_type = 'CREDIT'), v_credit_limit,
        v_billing_day, v_due_day, v_currency, v_issuer, v_preset_id,
        v_style_preset_id, v_color, v_icon, false, 1
    );

    IF v_type = 'CREDIT' THEN
        INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
        SELECT a.user_id, 'ACCOUNT', a.id, a.revision, 'UPSERT', jsonb_build_object(
            'id', a.id, 'creation_operation_id', a.creation_operation_id,
            'alias', a.name, 'type', a.account_type, 'currency', a.currency_code,
            'initial_balance_minor_units', a.initial_balance_minor_units,
            'opened_at', a.opened_at, 'is_archived', a.is_archived
        )
        FROM public.accounts a WHERE a.id = v_account_id AND a.user_id = v_user_id
          AND NOT EXISTS (
              SELECT 1 FROM internal.sync_changes sc
              WHERE sc.user_id = v_user_id AND sc.entity_type = 'ACCOUNT' AND sc.entity_id = v_account_id
          );
    END IF;

    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    SELECT v_user_id, 'CARD', c.id, c.revision, 'UPSERT', internal.card_sync_projection_v1(c)
    FROM public.cards c WHERE c.id = v_card_id AND c.user_id = v_user_id;

    v_response := jsonb_build_object('status', 'APPLIED', 'card_id', v_card_id, 'revision', 1);
    INSERT INTO internal.command_receipts(user_id, idempotency_key, command_type, request_hash, response_payload, status)
    VALUES (v_user_id, v_operation_id::text, 'REGISTER_CARD', v_request_hash, v_response, 'APPLIED');
    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.register_card_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_card_v1(jsonb) TO authenticated;

-- The legacy delete guard inspected only financial_movements. Canonical
-- transactions are now the financial history, so include both stores before
-- physically removing a card and publishing its tombstone.
CREATE OR REPLACE FUNCTION public.delete_unused_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_card_id uuid;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_card_id := (p_command->>'card_id')::uuid;
    IF v_card_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: card_id is required'; END IF;

    IF EXISTS (
        SELECT 1 FROM public.financial_movements
        WHERE user_id = v_user_id AND card_id = v_card_id
    ) OR EXISTS (
        SELECT 1 FROM public.transactions
        WHERE user_id = v_user_id AND card_id = v_card_id
    ) THEN
        RAISE EXCEPTION 'CARD_HAS_HISTORY: Cannot physically delete card with transaction history. Archive instead.';
    END IF;

    DELETE FROM public.cards WHERE id = v_card_id AND user_id = v_user_id;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, 0, 'DELETE', jsonb_build_object('id', v_card_id));

    RETURN jsonb_build_object('status', 'APPLIED', 'card_id', v_card_id, 'deleted', true);
END;
$$;

REVOKE ALL ON FUNCTION public.delete_unused_card_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_unused_card_v1(jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION internal.credit_transaction_sync_projection_v1(p_transaction public.transactions)
RETURNS jsonb
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
    SELECT jsonb_build_object(
        'id', p_transaction.id,
        'type', p_transaction.transaction_type,
        'operation_kind', p_transaction.operation_kind,
        'amount_minor', p_transaction.amount_minor,
        'currency_code', p_transaction.currency_code,
        'source_account_id', CASE WHEN p_transaction.operation_kind = 'CARD_PAYMENT' THEN p_transaction.account_id ELSE NULL END,
        'destination_account_id', NULL,
        'category_id', p_transaction.category_id,
        'merchant_id', p_transaction.merchant_service_id,
        'merchant_provisional_text', p_transaction.merchant_provisional_text,
        'card_id', p_transaction.card_id,
        'installment_count', p_transaction.installment_count,
        'occurred_at', p_transaction.occurred_at,
        'note', p_transaction.notes,
        'status', 'ACTIVE',
        'ledger_entries', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', le.id, 'account_id', le.account_id, 'role', le.entry_role,
                'signed_amount_minor', le.signed_amount_minor,
                'currency_code', le.currency_code, 'created_at', le.created_at
            ) ORDER BY le.created_at, le.id)
            FROM internal.ledger_entries le
            WHERE le.user_id = p_transaction.user_id AND le.transaction_id = p_transaction.id
        ), '[]'::jsonb),
        'installments', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', ci.id, 'installment_number', ci.installment_number,
                'due_date', ci.due_date, 'principal_minor', ci.principal_minor,
                'interest_minor', ci.interest_minor
            ) ORDER BY ci.installment_number, ci.id)
            FROM public.credit_installments ci
            WHERE ci.user_id = p_transaction.user_id AND ci.transaction_id = p_transaction.id AND ci.deleted_at IS NULL
        ), '[]'::jsonb),
        'allocations', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'installment_id', cpa.installment_id, 'amount_minor', cpa.allocated_minor
            ) ORDER BY cpa.created_at, cpa.id)
            FROM public.credit_payment_allocations cpa
            WHERE cpa.user_id = p_transaction.user_id AND cpa.payment_transaction_id = p_transaction.id
        ), '[]'::jsonb)
    )
$$;
REVOKE ALL ON FUNCTION internal.credit_transaction_sync_projection_v1(public.transactions) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.pull_financial_changes_v1(
    contract_version integer,
    after_sequence bigint,
    "limit" integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_changes jsonb;
    v_next_sequence bigint;
    v_has_more boolean := false;
    v_limit integer := LEAST(GREATEST(COALESCE("limit", 100), 1), 500);
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    IF COALESCE(contract_version, 1) <> 1 OR after_sequence < 0 THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Unsupported pull cursor or contract version';
    END IF;

    -- CARD change rows are snapshots for a mutable aggregate. When older
    -- schema revisions lack newly required fields, return the current owner
    -- projection together with its matching current revision; sequence/order
    -- remain the cursor contract, not a historical card audit log.
    SELECT jsonb_agg(jsonb_build_object(
        'sequence', sequence,
        'entity_type', entity_type,
        'entity_id', entity_id,
        'revision', revision,
        'operation', operation,
        'payload', payload,
        'created_at', created_at
    ) ORDER BY sequence), COALESCE(MAX(sequence), after_sequence)
    INTO v_changes, v_next_sequence
    FROM (
        SELECT sc.sequence, sc.entity_type, sc.entity_id,
               CASE WHEN sc.entity_type = 'CARD' AND c.id IS NOT NULL THEN c.revision ELSE sc.revision END AS revision,
               CASE
                   WHEN sc.entity_type = 'CARD' AND c.id IS NULL THEN 'DELETE'
                   ELSE sc.operation
               END AS operation,
                CASE
                    WHEN sc.entity_type = 'CARD' AND c.id IS NULL
                        THEN jsonb_build_object('id', sc.entity_id)
                    WHEN sc.entity_type = 'CARD' AND sc.operation IN ('UPSERT', 'ARCHIVE') AND c.id IS NOT NULL
                        THEN internal.card_sync_projection_v1(c)
                    WHEN sc.entity_type = 'TRANSACTION' AND sc.operation = 'UPSERT'
                         AND t.id IS NOT NULL AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
                         AND t.operation_kind IN ('CARD_PURCHASE', 'CARD_PAYMENT')
                        THEN jsonb_build_object(
                            'id', t.id,
                            'type', t.transaction_type,
                            'operation_kind', t.operation_kind,
                            'amount_minor', t.amount_minor,
                            'currency_code', t.currency_code,
                            'source_account_id', CASE WHEN t.operation_kind = 'CARD_PAYMENT' THEN t.account_id ELSE NULL END,
                            'destination_account_id', NULL,
                            'category_id', t.category_id,
                            'merchant_id', t.merchant_service_id,
                            'merchant_provisional_text', t.merchant_provisional_text,
                            'card_id', t.card_id,
                            'installment_count', t.installment_count,
                            'occurred_at', t.occurred_at,
                            'note', t.notes,
                            'status', 'ACTIVE',
                            'ledger_entries', COALESCE((
                                SELECT jsonb_agg(jsonb_build_object(
                                    'id', le.id, 'account_id', le.account_id, 'role', le.entry_role,
                                    'signed_amount_minor', le.signed_amount_minor,
                                    'currency_code', le.currency_code, 'created_at', le.created_at
                                ) ORDER BY le.created_at, le.id)
                                FROM internal.ledger_entries le
                                WHERE le.user_id = t.user_id AND le.transaction_id = t.id
                            ), '[]'::jsonb),
                            'installments', COALESCE((
                                SELECT jsonb_agg(jsonb_build_object(
                                    'id', ci.id, 'installment_number', ci.installment_number,
                                    'due_date', ci.due_date, 'principal_minor', ci.principal_minor,
                                    'interest_minor', ci.interest_minor
                                ) ORDER BY ci.installment_number, ci.id)
                                FROM public.credit_installments ci
                                WHERE ci.user_id = t.user_id AND ci.transaction_id = t.id AND ci.deleted_at IS NULL
                            ), '[]'::jsonb),
                            'allocations', COALESCE((
                                SELECT jsonb_agg(jsonb_build_object(
                                    'installment_id', cpa.installment_id, 'amount_minor', cpa.allocated_minor
                                ) ORDER BY cpa.created_at, cpa.id)
                                FROM public.credit_payment_allocations cpa
                                WHERE cpa.user_id = t.user_id AND cpa.payment_transaction_id = t.id
                            ), '[]'::jsonb)
                        )
                    ELSE sc.payload
                END AS payload,
                COALESCE(to_jsonb(sc)->>'created_at', to_jsonb(sc)->>'occurred_at') AS created_at
        FROM internal.sync_changes sc
        LEFT JOIN public.cards c ON c.user_id = sc.user_id AND c.id = sc.entity_id
        LEFT JOIN public.transactions t ON t.user_id = sc.user_id AND t.id = sc.entity_id
        WHERE sc.user_id = v_user_id AND sc.sequence > after_sequence
        ORDER BY sc.sequence ASC
        LIMIT v_limit
    ) changes;

    SELECT EXISTS (
        SELECT 1 FROM internal.sync_changes sc
        WHERE sc.user_id = v_user_id AND sc.sequence > v_next_sequence
    ) INTO v_has_more;

    RETURN jsonb_build_object(
        'changes', COALESCE(v_changes, '[]'::jsonb),
        'next_sequence', v_next_sequence,
        'has_more', v_has_more
    );
END;
$$;

REVOKE ALL ON FUNCTION public.pull_financial_changes_v1(integer, bigint, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pull_financial_changes_v1(integer, bigint, integer) TO authenticated;

-- Refuse a ledger backfill if posted purchases, payments, schedules or existing
-- liability rows disagree. This keeps a migration from silently changing debt.
DO $$
DECLARE
    v_invalid_purchase_count integer;
    v_invalid_payment_count integer;
    v_invalid_liability_count integer;
BEGIN
    SELECT count(*) INTO v_invalid_purchase_count
    FROM public.transactions t
    WHERE t.operation_kind = 'CARD_PURCHASE' AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
      AND COALESCE((SELECT sum(ci.principal_minor) FROM public.credit_installments ci
                    WHERE ci.user_id = t.user_id AND ci.transaction_id = t.id AND ci.deleted_at IS NULL), 0) <> t.amount_minor;
    IF v_invalid_purchase_count > 0 THEN
        RAISE EXCEPTION 'CREDIT_PURCHASE_SCHEDULE_REPAIR_REQUIRED: % confirmed purchases do not reconcile to installments',
            v_invalid_purchase_count;
    END IF;

    SELECT count(*) INTO v_invalid_payment_count
    FROM public.transactions t
    WHERE t.operation_kind = 'CARD_PAYMENT' AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
      AND (
          COALESCE((SELECT sum(cpa.allocated_minor) FROM public.credit_payment_allocations cpa
                    WHERE cpa.user_id = t.user_id AND cpa.payment_transaction_id = t.id), 0) <> t.amount_minor
          OR COALESCE((SELECT sum(le.signed_amount_minor) FROM internal.ledger_entries le
                       WHERE le.user_id = t.user_id AND le.transaction_id = t.id
                         AND le.account_id = t.account_id AND le.entry_role = 'SOURCE'), 0) <> -t.amount_minor
      );
    IF v_invalid_payment_count > 0 THEN
        RAISE EXCEPTION 'CREDIT_PAYMENT_REPAIR_REQUIRED: % confirmed payments do not reconcile to cash and allocations',
            v_invalid_payment_count;
    END IF;

    SELECT count(*) INTO v_invalid_liability_count
    FROM (
        SELECT t.user_id, t.id
        FROM public.transactions t
        JOIN public.cards c ON c.user_id = t.user_id AND c.id = t.card_id
        JOIN internal.ledger_entries le ON le.user_id = t.user_id AND le.transaction_id = t.id
        WHERE t.operation_kind IN ('CARD_PURCHASE', 'CARD_PAYMENT')
          AND le.entry_role = 'LIABILITY'
          AND (le.account_id <> c.account_id OR le.currency_code <> t.currency_code
               OR le.signed_amount_minor <> CASE WHEN t.operation_kind = 'CARD_PURCHASE' THEN -t.amount_minor ELSE t.amount_minor END)
        UNION ALL
        SELECT t.user_id, t.id
        FROM public.transactions t
        JOIN internal.ledger_entries le ON le.user_id = t.user_id AND le.transaction_id = t.id
        WHERE t.operation_kind IN ('CARD_PURCHASE', 'CARD_PAYMENT') AND le.entry_role = 'LIABILITY'
        GROUP BY t.user_id, t.id HAVING count(*) > 1
    ) invalid_rows;
    IF v_invalid_liability_count > 0 THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_LEDGER_REPAIR_REQUIRED: % transactions have conflicting liability entries',
            v_invalid_liability_count;
    END IF;
END;
$$;

INSERT INTO internal.ledger_entries (
    transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
)
SELECT t.id, t.user_id, c.account_id, -t.amount_minor, t.currency_code, 'LIABILITY', t.occurred_at
FROM public.transactions t
JOIN public.cards c ON c.user_id = t.user_id AND c.id = t.card_id
WHERE t.operation_kind = 'CARD_PURCHASE' AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM internal.ledger_entries le
      WHERE le.user_id = t.user_id AND le.transaction_id = t.id AND le.entry_role = 'LIABILITY'
  );

INSERT INTO internal.ledger_entries (
    transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
)
SELECT t.id, t.user_id, c.account_id, t.amount_minor, t.currency_code, 'LIABILITY', t.occurred_at
FROM public.transactions t
JOIN public.cards c ON c.user_id = t.user_id AND c.id = t.card_id
WHERE t.operation_kind = 'CARD_PAYMENT' AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM internal.ledger_entries le
      WHERE le.user_id = t.user_id AND le.transaction_id = t.id AND le.entry_role = 'LIABILITY'
  );

-- Republish complete snapshots after backfill. Old change rows are also projected
-- from current canonical rows by pull_financial_changes_v1 above.
INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
SELECT t.user_id, 'TRANSACTION', t.id, t.revision, 'UPSERT', internal.credit_transaction_sync_projection_v1(t)
FROM public.transactions t
WHERE t.operation_kind IN ('CARD_PURCHASE', 'CARD_PAYMENT')
  AND t.status = 'CONFIRMED' AND t.deleted_at IS NULL;

ALTER FUNCTION public.register_transaction_v1(jsonb) RENAME TO register_transaction_v1_pre_liability_s3;
REVOKE ALL ON FUNCTION public.register_transaction_v1_pre_liability_s3(jsonb) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.register_transaction_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_operation_kind text := upper(COALESCE(p_command->'transaction'->>'operation_kind', 'STANDARD'));
    v_response jsonb;
    v_transaction_id uuid;
    v_card_id uuid;
    v_amount bigint;
    v_currency bpchar(3);
    v_signed_amount bigint;
    v_linked_account_id uuid;
    v_linked_account_type public.account_type;
    v_linked_currency bpchar(3);
    v_transaction public.transactions;
    v_existing_account_id uuid;
    v_existing_amount bigint;
    v_existing_currency bpchar(3);
    v_existing_count integer;
BEGIN
    v_response := public.register_transaction_v1_pre_liability_s3(p_command);
    IF v_operation_kind <> 'CARD_PURCHASE'
       OR COALESCE(v_response->>'status', '') NOT IN ('APPLIED', 'DUPLICATE') THEN
        RETURN v_response;
    END IF;
    IF v_user_id IS NULL THEN RETURN v_response; END IF;

    v_transaction_id := COALESCE(NULLIF(v_response->>'transaction_id', '')::uuid, NULLIF(v_tx->>'id', '')::uuid);
    v_card_id := NULLIF(v_tx->>'card_id', '')::uuid;
    SELECT t.* INTO v_transaction
    FROM public.transactions t
    WHERE t.user_id = v_user_id AND t.id = v_transaction_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_ACCOUNT_INVALID: Cannot post credit purchase liability';
    END IF;
    SELECT c.account_id, a.account_type, a.currency_code
    INTO v_linked_account_id, v_linked_account_type, v_linked_currency
    FROM public.cards c
    JOIN public.accounts a ON a.user_id = c.user_id AND a.id = c.account_id
    WHERE c.user_id = v_user_id AND c.id = v_card_id
    FOR UPDATE OF c;
    IF NOT FOUND OR v_transaction.status <> 'CONFIRMED'
       OR v_linked_account_type <> 'CREDIT_LIABILITY'
       OR v_linked_currency <> v_transaction.currency_code THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_ACCOUNT_INVALID: Cannot post credit purchase liability';
    END IF;
    v_signed_amount := -v_transaction.amount_minor;

    SELECT count(*) INTO v_existing_count
    FROM internal.ledger_entries le
    WHERE le.user_id = v_user_id AND le.transaction_id = v_transaction_id AND le.entry_role = 'LIABILITY';
    IF v_existing_count > 1 THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_LEDGER_CONFLICT: Multiple purchase liability rows exist';
    END IF;
    IF v_existing_count = 1 THEN
        SELECT le.account_id, le.signed_amount_minor, le.currency_code
        INTO v_existing_account_id, v_existing_amount, v_existing_currency
        FROM internal.ledger_entries le
        WHERE le.user_id = v_user_id AND le.transaction_id = v_transaction_id AND le.entry_role = 'LIABILITY'
        LIMIT 1;
    END IF;
    IF v_existing_count = 1 AND (
        v_existing_account_id <> v_linked_account_id OR v_existing_amount <> v_signed_amount
        OR v_existing_currency <> v_transaction.currency_code
    ) THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_LEDGER_CONFLICT: Existing purchase liability differs';
    END IF;
    IF v_existing_count = 0 THEN
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES (
            v_transaction_id, v_user_id, v_linked_account_id, v_signed_amount,
            v_transaction.currency_code, 'LIABILITY', v_transaction.occurred_at
        );
    END IF;
    UPDATE internal.sync_changes sc
    SET payload = internal.credit_transaction_sync_projection_v1(v_transaction)
    WHERE sc.user_id = v_user_id AND sc.entity_type = 'TRANSACTION' AND sc.entity_id = v_transaction_id
      AND sc.sequence = (SELECT max(latest.sequence) FROM internal.sync_changes latest
                         WHERE latest.user_id = v_user_id AND latest.entity_type = 'TRANSACTION'
                           AND latest.entity_id = v_transaction_id);
    RETURN v_response;
END;
$$;
REVOKE ALL ON FUNCTION public.register_transaction_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_transaction_v1(jsonb) TO authenticated, service_role;

ALTER FUNCTION public.allocate_credit_payment_v1(jsonb) RENAME TO allocate_credit_payment_v1_pre_liability;
REVOKE ALL ON FUNCTION public.allocate_credit_payment_v1_pre_liability(jsonb) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.allocate_credit_payment_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_response jsonb;
    v_transaction_id uuid;
    v_card_id uuid;
    v_amount bigint;
    v_linked_account_id uuid;
    v_transaction public.transactions;
    v_card_currency bpchar(3);
    v_linked_account_type public.account_type;
    v_linked_currency bpchar(3);
    v_source_sum bigint;
    v_existing_account_id uuid;
    v_existing_amount bigint;
    v_existing_currency bpchar(3);
    v_existing_count integer;
BEGIN
    v_response := public.allocate_credit_payment_v1_pre_liability(p_command);
    IF COALESCE(v_response->>'status', '') NOT IN ('APPLIED', 'DUPLICATE') THEN
        RETURN v_response;
    END IF;
    IF v_user_id IS NULL THEN RETURN v_response; END IF;

    v_transaction_id := COALESCE(NULLIF(v_response->>'transaction_id', '')::uuid, NULLIF(v_tx->>'id', '')::uuid);
    v_card_id := NULLIF(v_tx->>'card_id', '')::uuid;
    v_amount := (v_tx->>'amount_minor')::bigint;
    SELECT t.* INTO v_transaction
    FROM public.transactions t
    WHERE t.user_id = v_user_id AND t.id = v_transaction_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_ACCOUNT_INVALID: Cannot post credit-card payment liability';
    END IF;
    SELECT c.account_id, c.currency_code, a.account_type, a.currency_code
    INTO v_linked_account_id, v_card_currency, v_linked_account_type, v_linked_currency
    FROM public.cards c
    JOIN public.accounts a ON a.user_id = c.user_id AND a.id = c.account_id
    WHERE c.user_id = v_user_id AND c.id = v_card_id
    FOR UPDATE OF c;
    IF NOT FOUND OR v_transaction.status <> 'CONFIRMED'
       OR v_transaction.amount_minor <> v_amount OR v_card_currency <> v_transaction.currency_code
       OR v_linked_account_type <> 'CREDIT_LIABILITY' OR v_linked_currency <> v_transaction.currency_code THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_ACCOUNT_INVALID: Cannot post credit-card payment liability';
    END IF;

    SELECT COALESCE(sum(le.signed_amount_minor), 0)::bigint INTO v_source_sum
    FROM internal.ledger_entries le
    WHERE le.user_id = v_user_id AND le.transaction_id = v_transaction_id
      AND le.account_id = v_transaction.account_id AND le.entry_role = 'SOURCE';
    IF v_transaction.account_id IS NULL OR v_source_sum <> -v_amount THEN
        RAISE EXCEPTION 'CARD_PAYMENT_SOURCE_LEDGER_INVALID: Cash posting does not match payment';
    END IF;

    SELECT count(*) INTO v_existing_count
    FROM internal.ledger_entries le
    WHERE le.user_id = v_user_id AND le.transaction_id = v_transaction_id AND le.entry_role = 'LIABILITY';
    IF v_existing_count > 1 THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_LEDGER_CONFLICT: Multiple payment liability rows exist';
    END IF;
    IF v_existing_count = 1 THEN
        SELECT le.account_id, le.signed_amount_minor, le.currency_code
        INTO v_existing_account_id, v_existing_amount, v_existing_currency
        FROM internal.ledger_entries le
        WHERE le.user_id = v_user_id AND le.transaction_id = v_transaction_id AND le.entry_role = 'LIABILITY'
        LIMIT 1;
    END IF;
    IF v_existing_count = 1 AND (
        v_existing_account_id <> v_linked_account_id OR v_existing_amount <> v_amount
        OR v_existing_currency <> v_transaction.currency_code
    ) THEN
        RAISE EXCEPTION 'CREDIT_LIABILITY_LEDGER_CONFLICT: Existing payment liability differs';
    END IF;
    IF v_existing_count = 0 THEN
        INSERT INTO internal.ledger_entries (
            transaction_id, user_id, account_id, signed_amount_minor, currency_code, entry_role, occurred_at
        ) VALUES (
            v_transaction_id, v_user_id, v_linked_account_id, v_amount,
            v_transaction.currency_code, 'LIABILITY', v_transaction.occurred_at
        );
    END IF;
    UPDATE internal.sync_changes sc
    SET payload = internal.credit_transaction_sync_projection_v1(v_transaction)
    WHERE sc.user_id = v_user_id AND sc.entity_type = 'TRANSACTION' AND sc.entity_id = v_transaction_id
      AND sc.sequence = (SELECT max(latest.sequence) FROM internal.sync_changes latest
                         WHERE latest.user_id = v_user_id AND latest.entity_type = 'TRANSACTION'
                           AND latest.entity_id = v_transaction_id);
    RETURN v_response;
END;
$$;
REVOKE ALL ON FUNCTION public.allocate_credit_payment_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.allocate_credit_payment_v1(jsonb) TO authenticated, service_role;

COMMIT;
