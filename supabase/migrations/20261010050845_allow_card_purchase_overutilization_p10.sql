-- P10 records the full purchase principal even when it exceeds the card's
-- available line. Card projections clamp available credit to zero separately.
-- Preserve the canonical registration function and change only its obsolete
-- hard rejection, failing closed if the deployed definition has drifted.
DO $migration$
DECLARE
    v_function regprocedure := 'internal.register_transaction_pre_hash_v2(jsonb)'::regprocedure;
    v_definition text;
    v_old_check text := $old$
    IF v_credit_limit IS NOT NULL AND v_before_debt + v_amount > v_credit_limit THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_LIMIT_EXCEEDED','field','amount_minor','retryable',false));
    END IF;
$old$;
BEGIN
    v_definition := pg_catalog.pg_get_functiondef(v_function);

    IF pg_catalog.strpos(v_definition, v_old_check) = 0
       OR pg_catalog.length(v_definition) - pg_catalog.length(pg_catalog.replace(v_definition, v_old_check, '')) <> pg_catalog.length(v_old_check) THEN
        RAISE EXCEPTION 'P10 migration expected exactly one credit-limit rejection in %', v_function;
    END IF;

    v_definition := pg_catalog.replace(
        v_definition,
        v_old_check,
        '    -- P10: accept overutilization and preserve the complete purchase principal.' || E'\n'
    );
    EXECUTE v_definition;
END;
$migration$;
