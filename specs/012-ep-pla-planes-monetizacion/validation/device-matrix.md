> **Revalidated**: 2026-09-16 for emulator automation; physical-device matrices remain pending.

# Device Matrix

**Date**: 2026-09-16
**Status**: BLOCKED; no physical Android device is attached and the integrated HU-01/Pantalla 1C host is unavailable.

## Automated Evidence

- The plans instrumented suite passed 31/31 tests on the Pixel 8 API 36 AVD.
- Room tests cover atomic confirmation, rollback, monotonic revisions, operation identity, leases, and account isolation.
- Worker tests cover APPLIED, DUPLICATE, STALE, CONFLICT, authentication waiting, retry, timeout, and lease recovery.
- PostgreSQL tests cover 3-2-1 delivery and immutable operation replay.

Automated tests do not substitute for the real-device timing and scheduler evidence required by SC-005, SC-006, and SC-010.

## SC-005 Confirmation Matrix

| Required Runs | Executed | Passing | Result |
|---------------|----------|---------|--------|
| 40 | 0 | 0 | BLOCKED |

Each run must record device/API, selection, online/offline state, process state, local commit duration, navigation duration, and whether both complete within two seconds.

## SC-006/SC-010 Synchronization Matrix

| Required Runs | Executed | Reconciled Within Target | Result |
|---------------|----------|--------------------------|--------|
| 30 | 0 | 0 | BLOCKED |

The matrix must include stable connectivity, network recovery, 3-2-1 delivery, ambiguous timeout, restart, process death, Doze, battery restriction, force-stop, and reopen. It must distinguish operating-system deferral from an application failure and verify same-user session isolation.

## Blockers

- `adb devices` reports only the Pixel 8 API 36 emulator; no physical device is attached.
- A `Pixel_8` AVD exists, but the plan and constitution require representative real-device validation for Android scheduler behavior.
- T048 remains blocked because production `LoginScreen` and `BiometricConfigScreen` destinations do not exist; therefore the required integrated navigation timing cannot be measured without creating prohibited placeholders.

T052 must remain unchecked until the 40/30 matrices are executed and raw measurements are attached.

## Sprint 3 — Google Play Billing matrix

**Date**: 2026-09-26
**Status**: BLOCKED; source, JVM, Edge, and database checks pass, but this host has no Android emulator/device or configured Play internal test track.

| Scenario | Device/track run | Provider lookup and Kipu result | Status |
|---|---|---|---|
| Monthly subscription | Not run | No approved Play product/base-plan ID or license tester | BLOCKED |
| Annual subscription and eligible trial | Not run | No approved base-plan/trial-offer ID or license tester | BLOCKED |
| Lifetime one-time purchase | Not run | No approved in-app product ID or license tester | BLOCKED |
| User cancels the Play sheet | Not run | No Play test track/device | BLOCKED |
| Product unavailable | Automated empty-catalog path is covered; Play track not run | No Play test track/device | BLOCKED |
| Pending cash payment | Edge fake and JVM/UI paths covered; Play provider not run | No license tester | BLOCKED |

## Automated Sprint 3 Evidence

- `testDebugUnitTest`: 244 JVM tests passed, including Billing product mapping, callback gating, purchase lifecycle, access aggregation, and ViewModel states.
- `compileDebugAndroidTestKotlin`: passed for the purchase Compose suite; instrumented execution was not possible because `adb devices` listed no attached device and no `emulator` executable is available on `PATH` on this host.
- The local Supabase database migration and all database suites passed: 20 files, 355 pgTAP assertions.
- Edge Function checks passed: 13 Deno tests, `deno check`, and `deno fmt --check`.

## External Blockers

- Play Console has not supplied approved product IDs, recurring base-plan IDs, trial-offer IDs, internal-track entry, or a license-test account. Candidate identifiers in `research.md` are explicitly unapproved and are not seeded into the catalog.
- No emulator or physical Android device is available for the instrumented Compose/device run.
- The monthly, annual, Lifetime, cancelled-sheet, unavailable-product, and real server-lookup/acknowledgement evidence must be recorded here before T081 can be checked.
