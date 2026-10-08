-- Sprint 3 follow-up: index uncovered foreign keys and make RLS auth checks initplan-friendly.
CREATE INDEX IF NOT EXISTS idx_ledger_entries_currency_code_fk ON internal.ledger_entries (currency_code);
CREATE INDEX IF NOT EXISTS idx_budgets_currency_code_fk ON public.budgets (currency_code);
CREATE INDEX IF NOT EXISTS idx_cards_currency_code_fk ON public.cards (currency_code);
CREATE INDEX IF NOT EXISTS idx_debt_events_installment_id_fk ON public.debt_events (installment_id);
CREATE INDEX IF NOT EXISTS idx_debt_events_transaction_id_fk ON public.debt_events (transaction_id);
CREATE INDEX IF NOT EXISTS idx_debts_currency_code_fk ON public.debts (currency_code);
CREATE INDEX IF NOT EXISTS idx_financial_movements_adjusts_movement_id_fk ON public.financial_movements (adjusts_movement_id);
CREATE INDEX IF NOT EXISTS idx_financial_movements_currency_fk ON public.financial_movements (currency);
CREATE INDEX IF NOT EXISTS idx_financial_movements_reverses_movement_id_fk ON public.financial_movements (reverses_movement_id);
CREATE INDEX IF NOT EXISTS idx_goal_events_transaction_id_fk ON public.goal_events (transaction_id);
CREATE INDEX IF NOT EXISTS idx_goals_currency_code_fk ON public.goals (currency_code);
CREATE INDEX IF NOT EXISTS idx_import_candidates_confirmed_transaction_id_fk ON public.import_candidates (confirmed_transaction_id);
CREATE INDEX IF NOT EXISTS idx_profiles_currency_code_fk ON public.profiles (currency_code);
CREATE INDEX IF NOT EXISTS idx_recurrence_occurrences_matched_transaction_id_fk ON public.recurrence_occurrences (matched_transaction_id);
CREATE INDEX IF NOT EXISTS idx_referential_rate_catalog_currency_fk ON public.referential_rate_catalog (currency);
CREATE INDEX IF NOT EXISTS idx_transactions_currency_code_fk ON public.transactions (currency_code);

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
