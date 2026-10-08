-- Sprint 3 follow-up: index uncovered foreign keys and make RLS auth checks initplan-friendly.
DO $foreign_key_indexes$
DECLARE
    v_index record;
    v_relation regclass;
BEGIN
    FOR v_index IN
        SELECT * FROM (VALUES
            ('idx_ledger_entries_currency_code_fk', 'internal.ledger_entries', 'currency_code'),
            ('idx_budgets_currency_code_fk', 'public.budgets', 'currency_code'),
            ('idx_cards_currency_code_fk', 'public.cards', 'currency_code'),
            ('idx_debt_events_installment_id_fk', 'public.debt_events', 'installment_id'),
            ('idx_debt_events_transaction_id_fk', 'public.debt_events', 'transaction_id'),
            ('idx_debts_currency_code_fk', 'public.debts', 'currency_code'),
            ('idx_financial_movements_adjusts_movement_id_fk', 'public.financial_movements', 'adjusts_movement_id'),
            ('idx_financial_movements_currency_fk', 'public.financial_movements', 'currency'),
            ('idx_financial_movements_reverses_movement_id_fk', 'public.financial_movements', 'reverses_movement_id'),
            ('idx_goal_events_transaction_id_fk', 'public.goal_events', 'transaction_id'),
            ('idx_goals_currency_code_fk', 'public.goals', 'currency_code'),
            ('idx_import_candidates_confirmed_transaction_id_fk', 'public.import_candidates', 'confirmed_transaction_id'),
            ('idx_profiles_currency_code_fk', 'public.profiles', 'currency_code'),
            ('idx_recurrence_occurrences_matched_transaction_id_fk', 'public.recurrence_occurrences', 'matched_transaction_id'),
            ('idx_referential_rate_catalog_currency_fk', 'public.referential_rate_catalog', 'currency'),
            ('idx_transactions_currency_code_fk', 'public.transactions', 'currency_code')
        ) AS requested(index_name, relation_name, column_name)
    LOOP
        v_relation := pg_catalog.to_regclass(v_index.relation_name);
        IF v_relation IS NOT NULL AND EXISTS (
            SELECT 1 FROM pg_catalog.pg_attribute AS a
            WHERE a.attrelid = v_relation AND a.attname = v_index.column_name
              AND a.attnum > 0 AND NOT a.attisdropped
        ) THEN
            EXECUTE pg_catalog.format(
                'CREATE INDEX IF NOT EXISTS %I ON %s (%I)',
                v_index.index_name, v_relation, v_index.column_name
            );
        END IF;
    END LOOP;
END;
$foreign_key_indexes$;

ALTER POLICY billing_purchases_owner_read
ON public.billing_purchases
USING ((SELECT auth.uid()) IS NOT NULL AND user_id = (SELECT auth.uid()));

ALTER POLICY financial_movements_own
ON public.financial_movements
USING ((SELECT auth.uid()) = user_id)
WITH CHECK ((SELECT auth.uid()) = user_id);

ALTER POLICY manage_own_card_personal_teas
ON public.card_personal_teas
USING ((SELECT auth.uid()) = user_id)
WITH CHECK ((SELECT auth.uid()) = user_id);

ALTER POLICY p_category_presentations_all
ON public.category_presentations
USING (user_id = (SELECT auth.uid()))
WITH CHECK (user_id = (SELECT auth.uid()));

ALTER POLICY p_category_conflicts_all
ON public.category_conflicts
USING (user_id = (SELECT auth.uid()))
WITH CHECK (user_id = (SELECT auth.uid()));
