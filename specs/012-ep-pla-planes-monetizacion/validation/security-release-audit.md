> **Revalidated**: 2026-09-16 after the Pantalla 1B corrective implementation.

# Security And Release Audit

**Date**: 2026-09-16
**Status**: Local source, database, Edge, and release-artifact audit passed; external deployment and human cross-review remain release gates.

## Verified

- Plans production sources contain no `BillingClient`, `com.android.billing`, billing-flow call, service-role key, or application logging call.
- The Edge Function uses only the publishable/anon key and forwards the caller authorization header to PostgreSQL RPCs.
- Plan intent values have no conversion to `EffectiveEntitlement` and are excluded from `FeatureAccessRequest`.
- Room is configured without destructive migration fallback in production.
- `kipu.db`, `kipu.db-wal`, and `kipu.db-shm` are excluded from cloud backup and device transfer.
- PostgreSQL source enables and forces RLS for all four user-owned tables, revokes direct authenticated/anonymous DML, and uses NOLOGIN executor roles.
- All 39 RLS/grant assertions pass, including cross-account denial and revoked anonymous access to private SECURITY DEFINER helpers.
- Edge Function type checking, lint, and nine contract tests pass.
- Debug and release Android builds, lint, JVM tests, and all 31 plans instrumented tests pass on a Pixel 8 API 36 AVD.
- Both visual actions still call the same entitlement-neutral `ConfirmPlanSelection`; the Free action supplies `FREE`, while Annual/Monthly eligibility mapping and Lifetime `PREMIUM_INTENT` remain unchanged.
- The unused Google Play Billing dependency was removed after APK inspection found it in the initial release artifact.
- A clean rebuilt release APK has no Billing permission, Billing component, Billing DEX class, fake entitlement, or test purchaser match.
- Production Android and Edge sources contain no service-role key or application logging call; Edge reads only the publishable/anon key and caller authorization header.
- Backup and device-transfer rules exclude `kipu.db`, `kipu.db-wal`, and `kipu.db-shm`.
- Migration drift tests fail safely without destructive replacement and preserve representative existing rows.

## Sprint 3 Billing Security Review — 2026-09-26

**Status**: Source, migration/RLS, Edge fake-provider, and local database checks passed. Google Play Console and license-account checks remain external release gates.

### Verified in source and local tests

- Play Billing SDK calls are isolated to `PlayBillingGateway`; the Android app contains no Supabase service-role or Google service-account secret. Purchase tokens stay in memory only, redact their `toString`, and are sent only in the authenticated verifier request.
- The Edge Function requires a bearer JWT and revalidates it through Supabase Auth. It rejects caller-supplied owner IDs, validates the active catalog product, and derives the purchase owner only from the authenticated user response.
- Google purchase tokens are SHA-256 hashed over their exact UTF-8 bytes before persistence. A global unique hash plus owner-checked upsert rejects account reassignment. No raw token is stored in purchase/event rows, sent to the database writer, echoed, or logged.
- `PENDING` persists with null entitlement, returns no new grant, and never starts acknowledgement. Verified purchases are committed before the server acknowledgement call. A five-minute acknowledgement lease prevents concurrent duplicate acknowledgement attempts; Lifetime has no consume operation.
- Effective access is computed from verified purchases. A canceled subscription retains access only while its verified term is still current; account hold, pause, expiry, and revocation do not grant that purchase.
- `internal.billing_events` is unexposed, forced-RLS, allowlist-sanitized, append-only, and retained with restrictive user-deletion references. Purchase rows are readable only by their JWT owner; client writes are revoked. The server executor and catalog administration grants are separately scoped.
- Evidence: all 20 local pgTAP suites passed (355 assertions); 13 Deno Edge tests passed; 244 JVM tests passed; Android instrumented test sources compile.

### Not verified against external providers

- No Play Console product/base-plan/trial IDs, internal test track, license tester, Android Publisher service-account credential, emulator, or attached Android device is available. No real provider lookup/acknowledgement or instrumented test run is claimed.
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (or the documented email/private-key pair), `GOOGLE_PLAY_PACKAGE_NAME`, and the Supabase Edge server configuration must be supplied in the target test environment before T088/T081 can pass.
- `assembleRelease` completed on 2026-09-26 and produced `app/build/outputs/apk/release/app-release-unsigned.apk` (20,412,606 bytes; SHA-256 `41E830A92A51750E4F3F70255060E9D52EF9989A6C299379410DC186DB4D531F`). A binary scan for service-role/Google service-account key markers, private-key markers, payment test secrets, and the test purchase-token marker found no matches. The artifact is unsigned and local; this does not claim a deployed Edge Function or published build.
- The source audit found that the server RPC store could fall back to the service-role key for auth/catalog API-key configuration. That fallback was removed; the store now fails closed unless an anon or publishable key is configured. A Deno regression test covers this condition.

T094 source, local database, Edge boundary, and unsigned release APK review is complete. T068, T081, T088, and final T095 remain unchecked until their external evidence is available.

## Remaining Sign-Off

- Inspect the deployed Edge Function and signed distribution artifact in the target release environment.
- Obtain the constitution-required independent cross-review before release.

## Sprint 4 Offline Entitlement Security Review — 2026-10-02

**Status**: Signed-grant enforcement and local database checks pass. Android device execution, release signing-key provisioning, and external deployment remain release gates.

### Verified in source and local checks

- The Edge verifier issues an ES256 grant only after authenticated Google Play verification, durable purchase persistence, and an effective Premium result. The grant binds owner, P-256 installation-key thumbprint, policy version, server verification time, commercial end, and `notAfter = min(serverVerifiedAt + 72h, entitlementEnd)`; Lifetime remains capped at 72 hours.
- `ENTITLEMENT_GRANT_PRIVATE_JWK` and `ENTITLEMENT_GRANT_KEY_ID` are read only as Edge Function environment secrets. Android receives only `OFFLINE_GRANT_KEY_ID` and `OFFLINE_GRANT_PUBLIC_KEY_X509_BASE64`, supplied through untracked local properties or environment configuration. No production signing values are stored in this repository.
- Android verifies the exact signed payload and owner/install/policy claims, then evaluates the lease with `SystemClock.elapsedRealtime()` plus same-boot `Settings.Global.BOOT_COUNT`. The entitlement path has no civil-clock input; missing boot continuity, owner/install mismatch, malformed or unsigned legacy cache, and the exact expiry boundary deny Premium and require reconnection.
- Category/instrument quota checks and the EP-MOV advanced-filter gate consume the shared evaluator. Free registration, basic history search/type/date filters, and local outbox paths remain available after Premium denial; over-limit resources and financial history are retained.
- Room v17→18 is additive. Schema 18 is generated, a v17 row-preservation migration test is present and compiles, and cloud-backup/device-transfer rules continue to exclude the complete `kipu.db` files.
- The local Supabase migration is applied and recorded as `20261003042759`. The new `entitlement_lease_test.sql` passes 8 pgTAP assertions: `authenticated` can execute `get_feature_access()` while `anon` and `PUBLIC` cannot; the function keeps SECURITY DEFINER, owner-from-`auth.uid()`, and an empty `search_path`; its legacy offline field is null and it has no rolling 72-hour calculation. A transaction-scoped function smoke test also passed and rolled back temporary fixture objects.
- Automated evidence: 371 Android JVM tests pass; the full Android instrumented-test source set compiles; 17 Deno verify-purchase tests pass; `git diff --check` passes.

### Environment and release limits

- The local Postgres container is missing the pre-existing `public.v_feature_access` view and `public.user_devices` relation even though the current production database has the view. The migration does not create or alter those baseline objects. Its local behavior was smoke-tested with transaction-only fixtures; no fixture objects or rows were retained.
- No emulator, AVD, or attached Android device is available, so the new Room migration and movement access instrumented tests were compiled but not executed.
- The production ES256 private key and matching Android public verification configuration have not been provisioned. Until the approved environment supplies both sides, new offline Premium grants are unavailable and the safe behavior is Kipu Free after any already-valid lease expires.
- No remote Supabase migration, Edge deployment, release signing, or publication was performed.


## Sprint 5 RTDN and reconciliation security review — 2026-10-08

**Status**: Isolated source and local automated checks passed. Remote configuration and non-production Play/Pub/Sub acceptance remain open release gates.

### Verified in source and local checks

- `play-rtdn` validates the Google OIDC JWT signature using Google JWKS and checks issuer, audience, service-account email, email verification, and time claims before decoding the Pub/Sub envelope. Package identity and supported notification types are validated before persistence.
- RTDN delivery identity and purchase tokens are hashed. The Edge function passes a current token only in memory to the shared Google Play verifier. Receipts, jobs, sanitized billing events, logs, and RPC bodies do not contain the raw purchase token or raw notification payload.
- The authenticated restore path supplies current device tokens to the same verifier. Supabase Auth remains the owner source; the client restore marker only selects an atomic server persistence path. A token bound to another account returns a conflict without reassigning its purchase or binding a waiting receipt/job to the caller.
- Internal receipt/job tables enable and force RLS, deny client table access, use server-executor policies, and expose only narrowly granted service-role functions. The migration validates existing S3 schema and global token-hash uniqueness, and fails visibly on duplicate hashes or conflicting index definitions.
- `reconcile-billing` requires a dedicated scheduler secret with a minimum length, limits the batch size, returns generic failures, and only reclaims leases/classifies hash-only jobs. It imports no Google Play provider and makes no provider call or entitlement change.
- Local checks passed: **35 Deno tests**, **61 pgTAP assertions**, and **452 Android JVM tests**. The database assertions include restore resume and owner-conflict behavior.

### Not verified against external systems

- P30 §4.3–4.4 remains unaligned in its owning documentation repository; the approved no-token behavior is recorded here and in the feature contract pending that review.
- The remote Supabase baseline observed on 2026-10-08 has not received the S5 migration or Edge functions, and scheduling extensions/secrets are not configured. No remote write or deployment was made.
- The local full migration reset fails at the pre-existing missing `public.recurrence_occurrences` dependency. The S5 migration was manually applied only to the local Docker database for targeted pgTAP runs; its migration-history replay is not certified.
- No real Google Play verification, Pub/Sub push, Vault read, Cron invocation, or deployed-function acceptance was performed. `T118` and `T128` therefore remain unchecked.
