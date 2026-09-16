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
