# Quickstart Validation: EP-CTA - Cuentas y Tarjetas

**Date**: 2026-09-21  
**Plan**: [plan.md](plan.md)  
**Data model**: [data-model.md](data-model.md)

This guide defines the runnable evidence required after implementation. It does not imply the feature is already implemented.

## Prerequisites

- JDK 17 and Android SDK for compile/target configuration in `app/build.gradle.kts`.
- Android emulator or device API 24+; at least one real device for release evidence.
- Docker-compatible runtime and Supabase CLI for local PostgreSQL 17.
- `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` supplied through local properties or environment only.
- No `service_role` or server secret in Android configuration.
- Checked-in financial baseline and alignment migrations available; clean PostgreSQL 17 reconstruction is required before any Sprint 3 migration.

## 1. Static Artifact Validation

Confirm the required planning artifacts:

```bash
test -f specs/002-ep-cta-cuentas-tarjetas/plan.md
test -f specs/002-ep-cta-cuentas-tarjetas/research.md
test -f specs/002-ep-cta-cuentas-tarjetas/data-model.md
test -f specs/002-ep-cta-cuentas-tarjetas/contracts/remote-api.openapi.yaml
test -f specs/002-ep-cta-cuentas-tarjetas/contracts/sync-contract.md
test -f specs/002-ep-cta-cuentas-tarjetas/contracts/security-boundary.md
test -f specs/002-ep-cta-cuentas-tarjetas/contracts/ui-contract.md
```

Expected: every command exits zero and the documents contain no `NEEDS CLARIFICATION` markers.

## 2. Supabase Baseline Gate

Discover CLI commands from the installed version before use:

```bash
supabase --version
supabase db --help
supabase test --help
```

Then validate reconstruction:

```bash
supabase start
supabase db reset
supabase test db
```

Expected:

- A clean PostgreSQL 17 instance applies every checked-in migration in order.
- `accounts`, `cards`, financial movements/transactions, ledger entries, command receipts and sync changes exist from versioned migrations.
- No migration depends on an object absent from repository history.
- pgTAP suites pass.

If reset fails or the rebuilt schema cannot be reconciled with the approved model, stop. Do not add `IF NOT EXISTS`, edit applied history or apply Sprint 3 changes directly to the linked project.

## 3. Android Build and Unit Tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Expected: build succeeds with no architecture-boundary, financial-domain, privacy or lint failures.

Focused domain command after tests exist:

```bash
./gradlew :app:testDebugUnitTest --tests "com.kipu.app.feature.accounts.*"
```

Minimum unit evidence:

- `Money` rejects currency mismatch, overflow and out-of-range amounts.
- Zero opening is valid; negative liquid opening is rejected.
- Debit requires compatible account and creates no balance.
- Credit line is excluded from liquid assets.
- Four-digit validator accepts only exactly four ASCII digits.
- Fifth quota-consuming Free instrument is denied; Cash remains exempt.
- Account appearance edits cannot mutate opening facts.
- Opening amount/date correction posts a linked reversal plus replacement adjustment and reproduces both current and historical-period balances.
- Effective day handles 29/30/31 in common/leap years.
- Package-boundary test keeps domain framework-free.

## 4. Room Migration and Atomicity

Run instrumented tests:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.kipu.app.feature.accounts
```

Run core migration tests as part of the full suite:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected Room evidence:

1. The registered migration chain from schema 1 through schema 10 preserves representative rows and relationships.
2. Full `1 -> 10` path succeeds and validates exported schema 10 against the checked-in database.
3. The planned `10 -> 11` migration preserves every schema-10 row and adds only the Sprint 3 card-rate/installment projections; verify round-trips for Long minor-unit fields, owner isolation and foreign keys.
4. Account, one opening movement and outbox commit together.
5. Injected failure rolls back all three.
6. Retry with same operation/payload creates no duplicate; changed payload conflicts.
7. Two concurrent fifth-instrument attempts cannot both commit.
8. Every DAO query is owner-scoped.
9. Debit link rejects wrong owner, currency, type or archived account.
10. Balance equals posted movement sum after restart.
11. Archiving an account blocks new operations and hides it from the default list without changing current or historical money totals.

## 5. Synchronization Worker

Worker tests use WorkManager Test and Ktor/PostgREST fakes.

Expected matrix:

- `APPLIED` and `DUPLICATE` become `SYNCED` once.
- `OPERATION_COLLISION` and revision `CONFLICT` preserve local evidence and require user action.
- Network/timeout/429/5xx returns identical payload to `PENDING` with backoff.
- Missing/mismatched session becomes `WAITING_FOR_AUTH` and sends no request.
- Expired lease recovers after simulated process death.
- Startup/session restoration schedules pending work after a commit/enqueue interruption.
- Logout pending count includes plans, profile and EP-CTA via `PendingChangesSource` aggregation.
- Offline create -> edit -> archive for one aggregate syncs in predecessor order without a false revision conflict.
- A second device/fresh database pulls `MOVEMENT` changes and reproduces each balance exactly before advancing its cursor.
- `SessionCoordinator.localAccess` switches DAO flows by owner; `Protected` and `NoOwner` expose no sensitive rows, while the worker obtains remote credentials separately.
- Push responses never advance the pull cursor; only a fully committed pull page does.

## 6. PostgreSQL Security and Financial Commands

Required test files should cover schema, migrations, commands, idempotency, sync, privacy, ledger and RLS.

Run:

```bash
supabase test db
```

When supported by the installed CLI, run database lint/advisors using commands discovered via `--help`; otherwise use the project advisor integration.

Expected:

- `anon` and `PUBLIC` have no financial access.
- User A cannot read or mutate User B accounts/cards/children.
- UPDATE policies include both `USING` and `WITH CHECK` where direct UPDATE is intentionally granted; financial DML remains revoked from clients.
- Typed RPC derives owner from JWT and rejects any unknown/user-id field.
- Same operation/hash returns `DUPLICATE`; different hash returns `OPERATION_COLLISION` without effects.
- Account RPC atomically writes account, opening, receipt and sync event.
- Account command accepts the client-generated opening movement ID; retry/pull preserves that exact identity.
- Opening-correction RPC atomically appends the linked reversal/replacement pair, receipt and both movement sync changes.
- Quota validation is concurrency-safe.
- Archive preserves ledger/history and creates sync change.
- Unused card delete emits tombstone; a card with dependencies cannot delete.
- Public views run as invoker and private/internal schemas are not exposed.
- No unresolved EP-CTA security advisor finding remains.

## 7. Compose and Navigation

Compose tests must prove:

- Dashboard contains separate semantic headings for real money and credit.
- PEN/USD totals remain separate.
- Debit reference does not repeat its account as another asset.
- Masked `MoneyText` has no raw amount in merged or unmerged semantics.
- Account/debit/credit form modes expose only applicable fields.
- Changing mode clears incompatible state.
- More-than-four-digit paste never appears in UI state/tree.
- PAN/CVV-like values entered through alias or issuer never enter state, Room, outbox or requests.
- Duplicate visible card identity requires warning, acknowledgement and distinct alias.
- Presets expose selection, valid contrast and 48dp targets.
- Banco de la Nacion uses approved fallback until a token update exists.
- Local save navigates once without waiting for network.
- Routes contain only internal IDs/mode, never financial or card display data.
- Compact and list-detail layouts work at 200% font scale.

Sprint 3 capture evidence, before enabling any source:

- Every candidate retains source kind/reference and a confidence assessment, is deduplicated across sources, and has no financial effect before review.
- Credit-card candidates require explicit confirmation even at high confidence; uncertain matching does not choose an instrument.
- OCR/raw notification content remains local-only, is absent from logs/telemetry/sync, and extraction failure leaves manual entry available.

## 8. End-to-End Sprint 2 Scenarios

### A. Offline Account Creation

1. Authenticate once, then disable connectivity.
2. Create a PEN bank account with an opening amount.
3. Confirm it appears immediately under real money.
4. Force-stop/reopen the app.
5. Confirm the account and exact balance remain.
6. Restore connectivity and wait for permitted background work.
7. Confirm one remote account, one opening effect and one receipt.
8. On a fresh second installation, pull changes and confirm the same balance is reconstructed from the opening movement.

### B. Debit Without Duplication

1. Create an active savings account.
2. Register a debit card linked to it.
3. Confirm Free quota consumed two slots.
4. Confirm dashboard liquid total is unchanged and displayed once.
5. Archive account and verify new debit operations are disabled while history/link remain.

### C. Credit Separation and Privacy

1. Register credit card with issuer, network, last four, currency, line and days.
2. Confirm line does not change PEN/USD liquid totals.
3. Try pasting 13-19 digits and verify rejection without state/log persistence.
4. Verify no CVV field exists.
5. Register matching issuer/network/last4 with distinct alias after warning.

### D. Quota and Lifecycle

1. Reach four active computable instruments.
2. Verify fifth create/reactivate is blocked without data loss.
3. Add Cash and verify it is exempt.
4. Archive one instrument and verify a slot becomes available.
5. Verify a created account cannot hard-delete because opening history exists.
6. Archive an account with nonzero balance and verify the total does not change until an explicit financial movement changes it.
7. Reactivate it and verify the preserved balance remains available for later settlement.

### E. Two-User Isolation

1. Create instruments for User A, including pending offline sync.
2. Sign out and authenticate User B.
3. Verify A data is hidden and A outbox is not dispatched.
4. Attempt controlled B access to A identifiers locally/remotely; expect no data disclosure.
5. Reauthenticate A and verify pending work resumes only for A.

## 9. Real-Device Release Evidence

Validate on representative low/medium capability phones and a tablet/foldable profile:

- TalkBack reading order and masked semantics.
- Switch access/external keyboard focus.
- 200% text, display scaling, landscape and IME behavior.
- Doze, battery restriction, force-stop and reconnect recovery.
- App switcher/screenshot behavior contains no unmasked test financial data.
- UI remains responsive under 100 instruments and 10,000 movements fixture.

## 10. Closed Sprint 2 Baseline

Sprint 2 is closed. Retain these checks as regression gates whenever Sprint 3 touches shared code, schema or ledger behavior:

- All RF-C01/C02/C03/C05/C06 scenarios remain green.
- Financial/domain, Room migration, worker, Compose, navigation and pgTAP evidence remains available.
- Clean Supabase reset stays reproducible.
- Cross-user, duplicate-effect, history-loss and PAN/CVV leakage tests remain green.
- Review changes for architecture, financial integrity, privacy and requirement traceability.
- Sprint 3 behavior is planned separately in Section 11 and is not part of the closed Sprint 2 increment.

## 11. Sprint 3 EP-CTA Validation (HU-09..HU-13)

Run these only after the Sprint 2 gates, external blockers HU-18/HU-19/HU-23, the clean Supabase baseline and the canonical command contracts are verified. They are validation scenarios for the planned increment; they do not imply Sprint 3 is implemented yet.

### A. Credit separation and line metrics

1. Register a credit card with line PEN 1,000.00, then confirm PEN 100.00 of debt. Verify liquid money does not change, used credit is PEN 100.00, available credit is PEN 900.00, and utilization is 10%.
2. Verify zero line yields available credit zero and utilization `No disponible`.
3. Reduce the line below existing debt. Verify debt remains unchanged, available credit stays zero and utilization can exceed 100%.
4. Verify preferred closing/due days 29, 30 and 31 remain stored while February and other short months use the effective final day.

### B. Purchase and installment schedule

1. Confirm a PEN 100.00 credit purchase in three installments. Verify exactly one PEN 100.00 expense and liability increase, and installment principal values are PEN 33.34, PEN 33.33 and PEN 33.33.
2. Verify installment principal sums to the confirmed principal for counts 1..36, including totals smaller than the count and values at monetary bounds.
3. Verify a purchase on closing_day belongs to that cycle; a purchase after closing_day belongs to the next cycle. The first installment is due on the next due_day after the selected cycle close; later installments are monthly with independent short-month clamping.
4. Simulate a nonzero TEA using the French fixed-payment formula. Verify the method/rate/source, dates, rounded total and explicit estimate warning; verify the simulation alone changes neither ledger, expense nor debt.
5. Retry the same confirmed purchase operation and payload. Verify it creates no extra expense, liability effect, installment or threshold event. Reject the same operation identity with a changed payload.

### C. Card payment without duplicate expense

1. Start with PEN 300.00 in an active bank account and PEN 100.00 card debt. Pay PEN 100.00.
2. Verify bank balance becomes PEN 200.00, card liability becomes zero, credit payment allocations total PEN 100.00, and operational expense does not increase.
3. Verify insufficient funds, wrong currency, cross-user source/card, payment above debt and retry are rejected or deduplicated with no partial effects.

### D. Thresholds and notification integration

1. Apply a committed operation that moves utilization from below 50% to at least 100%. Verify exactly one event for each threshold 50%, 80% and 100%.
2. Apply further operations while above each threshold; verify no duplicate crossing. Reduce utilization below one threshold and cross it again; verify one new event for that threshold.
3. Deny OS notification permission. Verify the in-app event remains available through the shared HU-42/app_notifications contract.

### E. Regression and required gates

- Run unit/domain, Room migration and DAO, repository/worker, Compose/navigation, and database/pgTAP suites for affected flows.
- Verify owner-isolated RLS, operation idempotency, rollback atomicity, and no duplicate old/new RPC path.
- Run the project's discovered Android build and lint tasks plus Supabase reset/database tests. Confirm all Sprint 1/Sprint 2 ledger, account, card, privacy, sync and navigation regressions remain green.
- Do not close EP-CTA while the canonical-baseline reconciliation or HU-18/HU-19/HU-23 prerequisite evidence is unresolved.
