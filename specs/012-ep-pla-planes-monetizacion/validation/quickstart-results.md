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

## Sprint 4 EP-PLA Execution — 2026-10-02

**Readiness**: Implementation and local automated checks are ready for review. Production offline Premium grants and Android instrumented acceptance remain blocked on release configuration and a device.

### Completed locally

- Refined HU-58/HU-59 requirements were implemented with a server-signed, owner/install/policy-bound ES256 grant, monotonic 72-hour lease evaluation, a Room v17→18 migration, shared capability checks, and the EP-MOV filter fallback/revalidation UI.
- `:app:testDebugUnitTest` passed: 371 tests, 0 failures. `:app:compileDebugAndroidTestKotlin` passed for the Room migration, movement access, and existing Android test sources.
- `deno test supabase/functions/verify-purchase/index_test.ts supabase/functions/verify-purchase/offline-entitlement-grant_test.ts` passed: 17 tests, 0 failures.
- The additive SQL migration ran against the local Docker Postgres and was recorded in local migration history. The new entitlement lease pgTAP file passed 8 assertions for ACL, owner check, pinned search path, null legacy lease, and no rolling timestamp. A transaction-scoped smoke test also observed `offline_valid_until = null` and rolled back its fixtures.
- `app/schemas/com.kipu.app.core.database.KipuDatabase/18.json` was generated. Both backup and device-transfer rules still exclude `kipu.db`, WAL, and SHM files.
- No remote deployment or signing material was changed.

### Acceptance traceability

| Sprint 4 criterion | Implementation and evidence | Status |
|---|---|---|
| SC-020 — Free core/basic filters and Free quotas remain available | FeatureAccessPolicy and MovementHistoryViewModel JVM tests | Automated PASS |
| SC-021 — Grant is signed and bound to verified purchase, owner, and installation | Edge signer and Android exact-byte signature/verifier tests | Automated PASS |
| SC-022 — 72-hour, commercial-end, Lifetime, boot-count, and monotonic boundaries | OfflineEntitlementLeasePolicy and evaluator integration tests | Automated PASS |
| SC-023 — Pending/legacy clients/missing secrets cannot obtain a grant | 17 verify-purchase Deno tests | Automated PASS |
| SC-024 — Premium expiry falls back to basic history and Free remains usable | Movement query/provider/ViewModel tests plus `MovementHistoryAccessIntegrationTest` (6 tests on Samsung SM-A165M, Android 16); category/account consumers use shared evaluator | JVM + device PASS |
| SC-025 — Additive Room and Postgres migrations preserve data and remove rolling lease | `MovementRoomMigrationTest` (7 tests on Samsung SM-A165M, Android 16); local `entitlement_lease_test.sql` (8 pgTAP assertions) and transaction-scoped function smoke check | Targeted Room/Postgres PASS; full migration-history replay pending |

**T112/T113 follow-up (2026-10-08)**: both validation tasks are complete. The 7 Room migration tests and 6 movement-access integration tests passed on the connected Samsung; the PostgreSQL migration and ACL/lease checks passed locally. FR-048–FR-055 and SC-020–SC-025 remain mapped in this table. Production signing-key configuration is documented below and remains required before release.

### Remaining gates

- Provide `ENTITLEMENT_GRANT_PRIVATE_JWK` and `ENTITLEMENT_GRANT_KEY_ID` as Supabase Edge secrets and matching public-key values through release build configuration. No secrets should be committed.
- Resolve the local baseline omission of `public.v_feature_access` and `public.user_devices` before claiming an end-to-end local baseline replay; the Sprint 4 migration leaves those pre-existing objects untouched. The targeted migration and regression checks passed with transaction-scoped baseline fixtures.
- Deploy and validate the Edge function and migration through the approved release process, then complete the independent release review.


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
- Gates: approved visual/refined FR-033–FR-039 and EP-PLA FR-056–FR-058 are implemented and checked. At this audit date, full Sprint 4 remained open on EP-MOV T110, EP-PLA device/database evidence, and release configuration. The 2026-10-08 follow-up closes EP-PLA T112/T113; EP-MOV T110 and production signing/provider/deployment acceptance remain open. Historical performance/SQL claims were not rerun or re-certified by this UI audit.

### Refine status and semantic cross-check

Both feature directories contain spec, plan, tasks, research, data-model and contracts. Refinement/propagation entries are current and no artifact-warning **STALE** marker remains. Requirement IDs and existing financial contracts are preserved. Source provenance is explicitly mapped to open T110; all new visual requirements have implementation tasks and evidence. Marker synchronization does not mean whole-sprint functional acceptance. The remaining high-priority finding is the pre-existing FR-017 source contract gap, with explicit task coverage and no inferred replacement.


## Sprint 5 HU-55 RTDN, restore, and reconciliation — 2026-10-08

**Readiness**: The isolated implementation and local automated checks are ready for review. Release acceptance remains **BLOCKED** on the missing live verifier endpoint (T118) and non-production/provider/scheduler acceptance (T128). No remote migration, Edge deployment, or Cron schedule was applied.

### Completed locally

- The authenticated `play-rtdn` Edge Function validates Google OIDC signature and claims before parsing Pub/Sub data, normalizes supported event types, hashes delivery identity and token, and routes known current-token events through the shared purchase verifier. Unknown owner or missing-token events remain `WAITING_FOR_TOKEN`; transient failures request Pub/Sub retry.
- The additive S5 migration adds internal forced-RLS event receipts and reconciliation jobs, per-token leases, server-only RPCs, atomic RTDN and restore persistence, and a bounded no-token sweep. Restore candidates use the same authenticated `verify-purchase` flow; the Kipu owner still comes from Supabase Auth. An owner conflict leaves the purchase and waiting work item attached to the existing owner.
- Purchase tokens remain request-scoped in memory. The database receives only SHA-256 token hashes and allowlisted billing-event fields. The restore marker is a client flow hint and cannot set or transfer the owner.
- `./gradlew testDebugUnitTest` passed: **511 tests, 0 failures**.
- `deno test --allow-env supabase/functions/verify-purchase supabase/functions/play-rtdn supabase/functions/reconcile-billing` passed: **37 tests, 0 failures**. Coverage includes current-state re-verification for out-of-order events and retry-only handling for an in-flight duplicate.
- The HU-55 pgTAP files passed: **78 assertions** across `billing_rtdn_reconciliation_test.sql` and `billing_reconciliation_sweep_test.sql`, including the additive upgrade fix for cross-account restore conflicts, preservation of the active receipt/lease during duplicate delivery, replacement-function ownership/search-path/ACL checks, atomic restore resume, no-token waiting, RLS/grants, privacy, lease recovery, out-of-order retry after lease release, and no entitlement mutation from the scheduler.
- The S5 and forward-fix migrations were applied manually to the local Docker database for these pgTAP runs. `git diff --check` passed.

### Remaining gates

- **T118 / P30**: P30 §4.3–4.4 and related flow rules were aligned in the owning `KipuApp` documentation working tree on 2026-10-08: complete tokens stay in request memory, hash-only jobs wait without a provider call or entitlement mutation, and waiting never extends the verified term. T118 remains open because the read-only remote catalog has `public.verify_play_purchase(jsonb)` and `public.v_feature_access`, but the Android verifier route `/functions/v1/verify-purchase/billing/verify` is not deployed: the only active Edge functions are `auth-access` and `plans`, and `plans` does not route that path. The remote `public.get_feature_access()` still returns `now() + interval '72 hours'`; the S4 migration `20261003042759` is absent. The last remote migration is `20260930035146`; no S5 migration/functions, `pg_cron`, `pg_net`, or billing/play scheduler secret were observed. `pg_cron`/`pg_net` are absent; Vault is installed but has no matching scheduling secret. No remote writes were made. The P30 working-tree change is outside this feature branch and remains uncommitted for its owning repository's review.
- A full local migration replay is still blocked before S5 by the pre-existing `20260928110000_credit_card_pull_projection.sql` dependency on missing `public.recurrence_occurrences`. The S5 migration was not recorded in local migration history through `db reset`; the targeted database tests ran against the manually applied S5 schema.
- No non-production Google Play/Pub/Sub target is available (confirmed 2026-10-08); no provider delivery, remote migration, function deployment, Vault secret, or Cron execution was performed. The 15-minute Cron recipe is versioned in `supabase/operations/billing-reconciliation-cron.sql` but remains unapplied until the verifier endpoint and non-production target are available and reviewed.
- **T128 remains open** until the external integration, migration-history reconciliation, and deployed function/scheduler evidence are complete. P30's no-token alignment is now present in its owning documentation working tree. T112/T113 validation tasks were completed on 2026-10-08; their separate full-replay and release-configuration limitations remain documented above.
