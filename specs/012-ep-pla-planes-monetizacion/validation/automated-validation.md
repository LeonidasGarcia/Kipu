# Automated Validation

**Date**: 2026-09-15
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
| `npx --yes deno test supabase/functions/plans/index_test.ts` | PASS; 9 passed, 0 failed |
| `npx --yes @redocly/cli lint specs/012-ep-pla-planes-monetizacion/contracts/plans-selection.openapi.yaml` | PASS with one non-blocking `info.license` warning |
| `git diff --check` | PASS; only Git LF-to-CRLF notices |

## Not Executed

| Validation | Blocker |
|------------|---------|
| `supabase start`, `supabase db reset`, `supabase test db` | Docker is not installed/running; local PostgreSQL port 54322 refuses connections |
| Representative-row migration and drift execution | Requires the Docker-backed local Supabase database |

The pgTAP suites are present under `supabase/tests/database/`, but this report does not claim they passed without a PostgreSQL execution environment.
