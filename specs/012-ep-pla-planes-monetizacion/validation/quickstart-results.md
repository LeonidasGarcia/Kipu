> **Revalidated**: 2026-09-16 after the Pantalla 1B corrective implementation.

# Quickstart Results

**Date**: 2026-09-16
**Readiness**: Not ready for Sprint Review.

## Completed

- Static, KSP, dependency, debug, lint, JVM, release, OpenAPI, and Edge Function checks pass.
- Room, Worker, route, and Compose instrumented suites pass: 31 tests on Pixel 8 API 36, including Annual default, static Trial, non-selectable Free, two CTA and 200% scroll.
- Room schema v1 is generated under `app/schemas/`.
- Architecture checks confirm the plans feature has no Billing or entitlement-conversion path.
- A clean release build and APK inspection confirm Google Play Billing is not packaged.
- The migration and all four pgTAP suites pass in a disposable PostgreSQL 15 compatibility container: 102 assertions, including representative data, drift, RLS, grants, hashing, and selection semantics.

## Blocked Scenarios

- HU-01 and Pantalla 1C are not implemented, so the official onboarding navigation flow cannot be wired or demonstrated.
- The 40-confirmation/30-operation physical-device matrix remains unexecuted; blockers and required measurements are recorded in `device-matrix.md`.
- The full Supabase PostgreSQL 17 local stack cannot start on this host because its image fails with `exec /bin/sh: exec format error`; `supabase functions serve` also remains unexecuted after optional service downloads exhausted Docker memory. PostgreSQL 17 remains the configured target.
- TalkBack for all eight flows and the 20-participant commercial-comprehension protocol remain unexecuted; blockers and required evidence are recorded in `accessibility-and-comprehension.md`.

The feature must not be declared Sprint Review ready until these scenarios are executed and their evidence is attached.

## Sprint 3 Billing Verification — 2026-09-26

**Readiness**: Implementation and local automated checks are ready for review. End-to-end Sprint 3 acceptance remains BLOCKED on approved Play Console configuration and a test device/account.

### Completed locally

- Billing Library 9.1.0 is pinned and isolated in the Play gateway. Monthly, Annual, Lifetime, localized offers, retry/restore flow, status UI, and Kipu-tokenized access cache are implemented.
- Authenticated `POST /billing/verify`, Google Android Publisher adapters, owner-bound SHA-256 persistence, acknowledgement leasing, and sanitized append-only verification events are implemented.
- The additive migration applied successfully to local Supabase. All 20 local database suites passed (355 assertions), including the 36 billing lifecycle/RLS assertions.
- Edge verification passed: `deno fmt --check`, `deno check`, and 13 Deno tests, including a fail-closed regression for missing anon/publishable API-key configuration.
- Android JVM suite passed: 244 tests. `compileDebugAndroidTestKotlin` passed for the Compose purchase screen tests.
- `assembleRelease` passed and produced a local unsigned release APK; the sensitive-marker scan passed. The unsigned artifact is not suitable for distribution until signed through the release process.
- Instrumented Compose tests were not executed: this host has no emulator/AVD or attached device.

### Acceptance traceability

| Sprint 3 criterion | Implementation and evidence | Status |
|---|---|---|
| SC-014 — callback, pending, invalid/foreign token, provider outage never grant Premium | T072, T075–T077, T082–T087; JVM and Edge tests | Automated PASS |
| SC-015 — localized Play prices, no invented payable price, empty catalog | T074, T078, T080; Play ProductDetails source and Compose tests | Code/compile PASS; Play track blocked |
| SC-016 — idempotent verification/acknowledgement, Lifetime never consumed | T070–T071, T082–T087; pgTAP and Deno retry/lease tests | Automated PASS |
| SC-017 — all purchase lifecycle states and Lifetime precedence | T089–T093; JVM policy and Compose disclosure tests | Code/compile PASS; instrumented run blocked |
| SC-018 — cancellation keeps the paid term; expiry/revocation preserve user history | T091–T094; effective-access policy, server projection, FK/RLS pgTAP checks | Automated PASS |
| SC-019 — 48dp actions, reduced motion, trial date, management link, stable loading/status | T078–T080; Compose tests compile | Code/compile PASS; instrumented run blocked |

### Remaining blockers

- **T068/T081**: approved Play Console product/base-plan/trial IDs, internal test-track access, and a license tester have not been provided. No candidate IDs were treated as production configuration.
- **T088**: no Google Play license-test account or Android Publisher service-account configuration is available, so no real Google lookup or acknowledgement was attempted.
- **T080/T093 device execution**: Compose tests compile but cannot run on this host without an emulator/device.
- **T095** remains unchecked because its required T081/T088 provider acceptance evidence and instrumented device evidence are still unavailable; this file records local regression evidence and all remaining limitations.
