> **Revalidated**: 2026-09-16 after the Pantalla 1B corrective implementation.

# Automated Validation

**Date**: 2026-09-16
**Branch**: `001-planes-monetizacion-freemium`

## Passed

| Command | Result |
|---------|--------|
| `python3 -X utf8 .specify/scripts/python/check_prerequisites.py --json --require-tasks --include-tasks` | PASS; feature directory and required artifacts resolved |
| `.\gradlew.bat :app:kspDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:check :app:assembleRelease` | PASS; 113 tasks evaluated |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.kipu.app.feature.plans.*" :app:compileDebugAndroidTestKotlin` | PASS after strict DTO and boundary-test hardening |
| `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.kipu.app.feature.plans"` | PASS on Pixel 8 API 36 AVD; 30 passed, 0 failed |
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` | PASS; dependency graph resolved |
| `npx --yes deno lint supabase/functions/plans/index.ts supabase/functions/plans/index_test.ts` | PASS |
| `npx --yes deno check supabase/functions/plans/index.ts supabase/functions/plans/index_test.ts` | PASS |
| `npx --yes deno test supabase/functions/plans/index_test.ts` | PASS; 9 passed, 0 failed |
| `npx --yes @redocly/cli lint specs/012-ep-pla-planes-monetizacion/contracts/plans-selection.openapi.yaml` | PASS with one non-blocking `info.license` warning |
| `.\gradlew.bat :app:clean :app:check :app:assembleRelease` | PASS from a clean app build; 95 tasks executed |
| `.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath` | PASS; release graph resolves without Google Play Billing |
| Clean migration via `psql --set ON_ERROR_STOP=1` in `public.ecr.aws/supabase/postgres:15.8.1.085` | PASS without migration warnings; project target remains PostgreSQL 17 |
| `plans_migration_test.sql` | PASS; 24/24, including representative-row preservation and safe drift failure |
| `plans_selection_test.sql` | PASS; 30/30, including APPLIED/DUPLICATE/STALE/CONFLICT and 3-2-1 delivery |
| `plans_rls_test.sql` | PASS; 39/39, including forced RLS, cross-account denial, private helper ACLs, and writer isolation |
| `plans_hash_test.sql` | PASS; 9/9, including the canonical SHA-256 golden vector |
| `apkanalyzer` release manifest and DEX inspection | PASS; no Billing permission, component, package/class, test purchaser, or fake entitlement artifact |
| `git diff --check` | PASS; only Git LF-to-CRLF notices |
| `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | PASS after T057-T059; complete JVM suite, lint and debug artifact |
| `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.kipu.app.feature.plans"` | PASS on Pixel 8 API 36 AVD; 31 passed, 0 failed, including refined 1B order/copy/two-CTA/200% checks |
| `.\gradlew.bat :app:assembleRelease` | PASS after T059; release compile and lint-vital completed |

## Environment Limitation

| Validation | Blocker |
|------------|---------|
| Full `supabase start` / PostgreSQL 17 local stack | The Supabase PostgreSQL 17 image fails on this host with `exec /bin/sh: exec format error`; PostgreSQL 15 was used only as a compatibility execution environment. The project target in `supabase/config.toml` remains 17. |
| `supabase functions serve plans` against the full local stack | The CLI exhausted available Docker memory while downloading optional services. Deno type checking, lint, and nine router/RPC-adapter tests passed independently. |

The disposable PostgreSQL container was recreated from scratch before the final migration run. All four pgTAP files completed with 102 passing assertions and no `not ok` result.

## Sprint 4 EP-PLA — 2026-10-02

| Command or check | Result |
|---|---|
| `.\gradlew.bat :app:testDebugUnitTest` | PASS; 371 tests, 0 failures, including signed-grant evaluator and history-gate coverage |
| `.\gradlew.bat :app:compileDebugAndroidTestKotlin` | PASS; Room v17→18 and movement-access instrumentation sources compile |
| `deno test supabase/functions/verify-purchase/index_test.ts supabase/functions/verify-purchase/offline-entitlement-grant_test.ts` | PASS; 17 tests, 0 failures |
| Local Docker Postgres migration `20261003042759_sprint_4_disable_rolling_feature_access_lease` | PASS; function replaced and migration version recorded |
| Local `has_function_privilege` check | PASS; authenticated=true, anon=false, PUBLIC=false |
| `supabase/tests/database/entitlement_lease_test.sql` | PASS; 8 pgTAP assertions for ACL, owner check, empty search path, null legacy lease, and no rolling timestamp |
| Local transaction-only RPC smoke test | PASS; `offline_valid_until` is JSON null; temporary view/table fixture rolled back |
| `git diff --check` | PASS |

### Not executed on this host

| Validation | Limitation |
|---|---|
| `connectedDebugAndroidTest` | No attached device or configured AVD is available. |
| Full local Postgres baseline replay | Local DB lacks the pre-existing `public.v_feature_access` view and `public.user_devices`; production has the view. Sprint 4 left baseline objects unchanged and used only a rolled-back fixture for the function smoke test. |
| Production offline-grant acceptance | Edge signing secret and release public-key configuration have not been provisioned; no remote deployment was requested. |


## UI/UX S4 refinement — validation 2026-10-03

- Skills applied: `ui-ux-pro-max`, `compose-animations`, Android Kotlin and Spec Kit refine/update/propagate/implement. Focused UX/Compose skill searches informed decimal keyboards, persistent labels, 48 dp targets, theme semantics and reduced motion.
- Full JVM regression: **383 tests, 0 failures, 0 errors** (`:app:testDebugUnitTest`). Draft tests cover decimal/currency parsing, invalid ranges and DST-inclusive dates; history tests cover apply/removal, owner reset, query retry and duplicate restore requests. Billing recovery tests cover no purchases, pending, failure and bounded timeout.
- Debug application and AndroidTest APKs assemble successfully. Android deprecation warnings remain for existing test-rule/Hilt APIs; no compilation errors.
- Instrumentation: **39 distinct checks passed** on the booted `Pixel_10` emulator: 38 in the combined UI/editor/history-access/Room run plus the integrated history route check, including light/dark rendering. The final UI refinement suite is rerun against the latest APK; command and summaries are retained in the local `.backups/` validation logs. These are selected instrumentation suites, not the entire AndroidTest catalog.
- Privacy assertions inspect unmerged semantics for detail, editor/conflict, Before/After and void amounts. Reduced motion and outgoing/incoming AnimatedContent identities are exercised; a draft survives UI saved-state restoration without applying or granting access.
- The history route opens VOIDED read-only detail, blocks financial actions there, retains parked filters and renders contextual recovery. Search/filter controls and results share one scroll container so enlarged text does not permanently displace history. Filter access is visible beside search; applied chips remove individual selections. Audit reads are owner-scoped and sorted by revision.
- Visual evidence uses synthetic fixture data only: `validation/ui/history-route-light.png`, `history-route-dark.png`, `history-large-font.png` in EP-MOV. Light, dark and 1.6× text captures were inspected. This is visual inspection plus automated semantic coverage; it does not certify a complete manual TalkBack traversal on a physical device.
- The first physical Samsung attempt encountered a locked/dozing screen; its failures are not acceptance evidence. Canonical-category/UUID/setup fixtures were corrected without relaxing production invariants; old assertions were updated to current copy and the unmerged badge semantics tree.
- Database evidence here is local Room instrumentation. This UI refinement adds no ledger/Postgres migration and performs no remote deployment or real purchase.
- Gates: approved visual/refined FR-033–FR-039 and EP-PLA FR-056–FR-058 are implemented and checked. **Full Sprint 4 is still open**: EP-MOV T110 preserves the required source filter whose current data contract is absent; EP-PLA T112/T113 preserve PostgreSQL baseline and production signing/provider/release acceptance. Historical performance/SQL claims were not rerun or re-certified by this UI audit.

### Refine status and semantic cross-check

Both feature directories contain spec, plan, tasks, research, data-model and contracts. Refinement/propagation entries are current and no artifact-warning **STALE** marker remains. Requirement IDs and existing financial contracts are preserved. Source provenance is explicitly mapped to open T110; all new visual requirements have implementation tasks and evidence. Marker synchronization does not mean whole-sprint functional acceptance. The remaining high-priority finding is the pre-existing FR-017 source contract gap, with explicit task coverage and no inferred replacement.
