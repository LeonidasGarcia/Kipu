# S1/S2 remote backend readiness

**Inspected:** 2026-09-24, read-only through the Kipu Supabase MCP. No remote DDL, data writes, or function deployments were performed.

## Compatibility with `fix/s1-s2-stabilization`

The remote project is **not yet compatible with the current Android branch**. Its migration history ends at `20260921010242 create_auth_rate_buckets`; the local branch has additional CTA, CCO, MOV, quota, and validation migrations through `20260923160000`.

Read-only catalog checks confirmed:

- `public.financial_movements` is absent, while the current account/card client and local CTA migrations use it.
- `public.category_presentations` is absent, while the current category migration and data model use it for names, icons, colors, and presentation revisions.
- `private.plan_quota_selection_heads`, `private.plan_quota_selection_items`, and `private.plan_quota_selection_receipts` are absent.
- `public.create_category_v1(jsonb)`, `public.create_account_v1(jsonb)`, `public.register_card_v1(jsonb)`, `public.register_transaction_v1(jsonb)`, and `public.apply_plan_quota_selection(integer,uuid,text,bigint,jsonb)` are absent. The server has the older `public.register_transaction(p_command jsonb)` and `public.sync_pull(p_after_sequence bigint,p_limit_count integer)` functions instead.
- Remote `public.categories` is also missing three branch-required columns: `origin`, `remote_revision`, and `category_type`. It currently has the older `is_system`, `revision`, `icon_key`, and `color_argb` representation. `public.merchant_services` is missing `is_active`.
- The deployed `plans` Edge Function is version 1 and routes eligibility and plan intent selection only; it does not implement `/quota-selection`.

The documentation also needs propagation before Sprint 3: `specs/004-ep-mov-movimientos-ledger/team-questions.md` records the Sprint 2 decision **one transaction with two balanced ledger entries** for a transfer, while `Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` still describes linked source/destination transactions in `transaction_links`.

Postgres logs in the inspected window also contain repeated `42703` failures for `categories.origin` and `merchant_services.is_active`, and `42501 permission denied for schema auth` in `public.ensure_profile()`. These reproduce the schema/function drift; they are not hypothetical gaps.

## Security and performance advisors

- **ERROR:** four public views use `SECURITY DEFINER`: `v_credit_summary`, `v_goal_summary`, `v_debt_summary`, `v_obligation_summary`. [Remediation](https://supabase.com/docs/guides/database/database-linter?lint=0010_security_definer_view)
- **WARN:** 15 `SECURITY DEFINER` functions are executable by `anon` and 17 by `authenticated`, including `register_transaction`, `sync_pull`, and `select_plan_capacity`. Review grants and authenticated boundaries. [Anon remediation](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable) · [Authenticated remediation](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable)
- **WARN:** seven functions have mutable `search_path`, including `register_transaction`. [Remediation](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable)
- **WARN:** leaked-password protection is disabled in Auth. [Remediation](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
- **INFO:** `internal.billing_events`, `internal.command_receipts`, and `private.registration_rate_buckets` have RLS enabled without policies; confirm they are intentionally inaccessible via client roles. Also review 11 unindexed foreign keys and the duplicate permissive `SELECT` policies on `public.profiles`. The 67 unused-index notices are not grounds for deletion on a low-activity database. [RLS remediation](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy) · [FK remediation](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys) · [policy remediation](https://supabase.com/docs/guides/database/database-linter?lint=0006_multiple_permissive_policies)

## Rollout gate

Local migrations and local pgTAP passing do not make this remote database compatible. Before using the current app against it, reconcile the remote history/schema, prepare and validate an ordered forward-only migration set in a disposable staging environment, test ownership/RLS/grants and Edge routes, and then obtain explicit approval for any remote rollout. This file records findings only; it does not authorize production changes.
