# Security And Release Audit

**Date**: 2026-09-15
**Status**: Partial; database and device execution remain blocked.

## Verified

- Plans production sources contain no `BillingClient`, `com.android.billing`, billing-flow call, service-role key, or application logging call.
- The Edge Function uses only the publishable/anon key and forwards the caller authorization header to PostgreSQL RPCs.
- Plan intent values have no conversion to `EffectiveEntitlement` and are excluded from `FeatureAccessRequest`.
- Room is configured without destructive migration fallback in production.
- `kipu.db`, `kipu.db-wal`, and `kipu.db-shm` are excluded from cloud backup and device transfer.
- PostgreSQL source enables and forces RLS for all four user-owned tables, revokes direct authenticated/anonymous DML, and uses NOLOGIN executor roles.
- Edge Function type checking, lint, and nine contract tests pass.
- Debug and release Android builds, lint, JVM tests, and all 30 plans instrumented tests pass on a Pixel 8 API 36 AVD.

## Remaining Sign-Off

- Execute pgTAP cross-account and grant tests against local Supabase after Docker becomes available.
- Inspect deployed Edge Function and release distribution artifacts in the target release environment.
