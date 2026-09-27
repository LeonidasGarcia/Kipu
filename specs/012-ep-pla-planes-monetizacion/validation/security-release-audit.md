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
