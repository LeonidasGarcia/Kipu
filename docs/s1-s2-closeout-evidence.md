# Sprint 1/2 implementation evidence — 2026-09-24

**Code branch:** `fix/s1-s2-stabilization`
**Scope confirmed:** S1/S2 corrections and UI consistency. P15, the global five-destination bar, notification center, planning, calendar, reports, and Import Inbox remain in their scheduled later sprints. Category tabs are implemented as real Gastos/Ingresos filters; legacy categories stay `GENERAL` and appear in both.

## Implemented in this branch

- HU-07: dashboard account/card rows open detail; accounts can edit presentation, correct opening balance with auditable adjustment, archive, and reactivate. Card forms reject oversized last-four pastes and validate credit fields inline.
- HU-14/HU-18: categories have persisted `EXPENSE`/`INCOME`/`GENERAL` type, legacy-safe Room/Postgres migrations, inherited subcategory type, type-aware movement choices, and no category on transfers. Movement registration has a date picker and reports local-pending versus synchronized status after save.
- HU-57: selection sync checks session ownership, uses deterministic operation identity, retries transient failures, rebases stale full snapshots, and does not return success for terminal API failures. The SQL contract accepts newer full snapshots even when local revisions coalesced offline; equal/older snapshots remain stale.
- UI: account/card previews, theme-driven history and movement form colors, consistent 16/12/24dp shapes, shared brief motion timings, animated selection/type transitions, and 48dp category actions. Category root and subcategory edit/archive icons now align horizontally with their labels; root status appears beneath the title. The history screen no longer routes its notification button to permissions before HU-42; financial amounts do not animate.

## Automated verification

| Check | Result |
|---|---|
| `.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin --rerun-tasks --no-parallel` | **BUILD SUCCESSFUL** |
| `pnpm exec supabase migration up --local` | Applied forward-only local migrations `20260923140000` and `20260923160000`; no reset performed. |
| `pnpm exec supabase test db --local` | **16 files, 252 assertions, PASS**, including 100 identical retries with one transaction, receipt, and ledger effect. Four harmless notices report optional `pg_cron`/`pg_net` grants unavailable in the local stack. |
| `deno test supabase/functions/auth-access/ supabase/functions/plans/` | **19 passed, 0 failed** |
| `connectedLabAndroidTest -PisolatedAndroidTests=true` | **111 tests passed on Samsung SM-A165M / Android 16**, using isolated package `com.kipu.app.lab`; no existing `com.kipu.app` data was replaced. |
| `testDebugUnitTest compileDebugAndroidTestKotlin --no-parallel` after category layout correction | **BUILD SUCCESSFUL** |
| `connectedLabAndroidTest -PisolatedAndroidTests=true --no-parallel` after category alignment and movement-dialog tests | **117 tests passed on Samsung SM-S926B / Android 16**, using isolated package `com.kipu.app.lab`; includes geometry checks for category icons, Gastos/Ingresos fields, and duplicate warning actions. |
| `testDebugUnitTest compileDebugAndroidTestKotlin --no-parallel` after adding the 10,000-row history test | **BUILD SUCCESSFUL** |
| `ManualEntryAcceptanceTest.preparedExpenseCanBeEnteredAndAcceptedWithinTenSeconds` | **PASS on Samsung SM-S926B / Android 16** with prepared account/category data. |
| `connectedLabAndroidTest -PisolatedAndroidTests=true --no-parallel` after manual-entry acceptance test | **119 tests passed on Samsung SM-S926B / Android 16**, using isolated package `com.kipu.app.lab`; includes the 10,000-row lazy-history and category-action alignment checks. |
| `git diff --check` | No whitespace errors. |

## Acceptance still open

- The 119-test isolated suite passes on the Samsung SM-S926B, but does not verify a full authenticated offline→remote-sync→second-device walkthrough or TalkBack acceptance.
- T047's 10,000-row lazy-history performance assertion now runs successfully; database-level pagination remains unverified because the current repository observes the full transaction list.
- T052 PostgreSQL historical backfill remains open until EP-CTA/EP-CCO reviews one deterministic transaction identity: Room 5→6 currently prefixes legacy IDs with `legacy:`, while the PostgreSQL source IDs are UUIDs. T050 full offline/restart/second-device walkthrough and T057 named cross-review also remain open. T055's 100-retry pgTAP stress test passes locally; T056's prepared manual-entry acceptance passes on device.
- No live sign-in with supplied credentials was performed: the remote backend currently has missing current-branch migrations/RPCs and catalog-column mismatches. Credentials were not stored in source or test fixtures.
- `specs/004-ep-mov-movimientos-ledger/review-record.md` still requires named cross-reviewers. The PostgreSQL historical backfill and database-level history pagination remain unchecked in `specs/004/tasks.md`.
- The remote Kipu database and `plans` Edge Function remain incompatible with this branch and were not modified. See [`s1-s2-remote-backend-readiness.md`](s1-s2-remote-backend-readiness.md) for read-only MCP evidence and release gates.
- HU-57 categories/instruments have UI; future debt/goal/budget screens remain deferred. A verified Premium authority is still required before the server can remove Free locks.

**Status:** local unit tests, 252 pgTAP assertions, and 119 Android instrumented tests are green. Sprint 1/2 are not certified closed until the historical backfill and pagination decisions, full offline/second-device acceptance, cross-review, and remote migration/function rollout are completed in an approved staging/production process.
