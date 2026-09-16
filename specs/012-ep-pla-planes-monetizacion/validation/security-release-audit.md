# Security And Release Audit

**Date**: 2026-09-15
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
- Debug and release Android builds, lint, JVM tests, and all 30 plans instrumented tests pass on a Pixel 8 API 36 AVD.
- The unused Google Play Billing dependency was removed after APK inspection found it in the initial release artifact.
- A clean rebuilt release APK has no Billing permission, Billing component, Billing DEX class, fake entitlement, or test purchaser match.
- Production Android and Edge sources contain no service-role key or application logging call; Edge reads only the publishable/anon key and caller authorization header.
- Backup and device-transfer rules exclude `kipu.db`, `kipu.db-wal`, and `kipu.db-shm`.
- Migration drift tests fail safely without destructive replacement and preserve representative existing rows.

## Remaining Sign-Off

- Inspect the deployed Edge Function and signed distribution artifact in the target release environment.
- Obtain the constitution-required independent cross-review before release.
