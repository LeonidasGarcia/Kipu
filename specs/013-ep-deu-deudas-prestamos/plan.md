# Implementation Plan: EP-DEU — Deudas y Préstamos (Sprint 5)

**Branch**: `[013-ep-deu-deudas-prestamos]` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

**Input**: Sprint 5 stories HU-26, HU-27, HU-28, HU-29 from Product Backlog V4.2.

## Summary

Deliver the end-to-end debt lifecycle for own debts and loans given: opening (new or historical), principal/interest settlements, optional schedules, due reminders, correction, and closure. Use the existing Kotlin/Room local-first and Supabase/Postgres architecture. Extend the legacy debt tables and payment command rather than creating parallel records. Enforce owner, plan quota, balance, revision, and idempotency checks at the server boundary. Keep principal out of operating results; count real interest once.

Sprint scope is 29 points. HU-24 movement allocation, HU-40 calendar, and HU-44 generalized reminder settings/deduplication remain later work. The Sprint 5 cross-epic total remains 52 points, including EP-CCO HU-16/17 and EP-PLA HU-55.

## Technical Context

**Language/Version**: Kotlin using the repository's Android Gradle configuration; SQL/PostgreSQL migrations; TypeScript only where an existing Edge Function is the correct boundary.

**Primary Dependencies**: Jetpack Compose, Room, Kotlin Coroutines, Hilt, WorkManager, Supabase Auth/PostgREST, Ktor, PostgreSQL. No additional library is assumed.

**Storage**: Room local database plus Supabase public debt tables, transactions, ledger entries, receipts, and sync feed.

**Testing**: Kotlin/JUnit domain and repository tests, Room migration tests, Android Compose/WorkManager tests, and Supabase pgTAP/database tests. Tests are specified as implementation work; none were run while drafting the plan.

**Target Platform**: Native Android app (API 24+; project target 36) with Supabase Postgres.

**Project Type**: Native Android application with backend database/RPC.

**Performance Goals**: Local validation and persistence should feel immediate; target p95 ≤300 ms for local command confirmation on representative hardware. Remote synchronization is asynchronous and retry-safe; it never blocks the local financial confirmation.

**Constraints**:
- Integer minor units and currency for every authoritative amount.
- Local Room operation, projection, receipt, and outbox persist atomically.
- Server serializes changes by debt and validates each referenced owner/account/currency/revision.
- Principal and interest have separate financial semantics; no duplicated cash, income, expense, or budget use.
- Free admits at most two active PAYABLE+RECEIVABLE obligations combined; reducing plan access never deletes or hides history.
- Existing migration drift must be resolved before S5 database rollout.
- A debt reminder does not create or confirm a payment. The app remains usable without notification permission.

**Scale/Scope**: Four HUs; 29 story points; one personal user account with multiple obligations, each with optional installments and linked settlements.

## Constitution Check

**Pre-design gate — PASS**

- Financial integrity: principal movements, interest, schedules, adjustments, voids, and closures are specified with auditable effects. Balance is derived from events.
- Local-first: Room confirmation and outbox precede asynchronous sync; retries do not duplicate effects.
- Security/privacy: owner checks apply to debt, account, installment, transaction, category, and event references; database RPCs cannot rely on RLS alone.
- Freemium: the combined Free limit is enforced on creation; existing financial history remains intact after a plan change.
- Android boundaries: domain rules remain outside Compose and provider/database models stay behind data adapters.
- Traceability: all delivery tasks map to HU-26–HU-29; no story is silently added from future sprints.

**Post-design gate — PASS WITH IMPLEMENTATION GATES**

Implementation may start after the team reconciles remote/local migration history and confirms the debt event backfill. Before accepting HU-28, the operating-flow calculators and expense rules must prove principal exclusion and real-interest inclusion. Before accepting HU-29, cancellation/reversal must recompute balance, installment, reminder, and status.

## Research Decisions

See [research.md](research.md). Key decisions:

1. Use `feature/debts` and the current movements offline-first pattern.
2. Add a Room v18→v19 migration and additive Supabase migration; do not recreate legacy tables.
3. Use one idempotent server command for each composite financial action. Owner, quota, currency, balance, revision, and linked entity checks happen server-side.
4. Represent principal with DEBT_DISBURSEMENT/DEBT_PAYMENT movements excluded from operating totals. If a real interest amount exists, create a separate standard interest movement and link it to the principal movement with DEBT_AMORTIZATION; confirm both with the debt event atomically.
5. Extend debt events with signed principal delta for new adjustments. Keep compatibility for legacy events and stop migration if ambiguous historical data cannot be reconciled.
6. Deliver only debt-specific reminders in S5. The generic reminder policy and cross-object dedupe stay with HU-44 in S6.

## Scope and Sequencing

| Story | S5 priority | Blocking prerequisites | Partial/related work |
| --- | --- | --- | --- |
| HU-26 PAYABLE creation/administration | P1 | HU-07, HU-19, HU-57 | HU-28, HU-29 |
| HU-27 RECEIVABLE creation/administration | P1 | HU-07, HU-19, HU-57 | HU-28, HU-29 |
| HU-28 principal payments/collections | P1 | HU-18, HU-19, HU-26, HU-27 | HU-24, HU-29 |
| HU-29 installments/due/closure | P2 | HU-26, HU-27, HU-28 | HU-42 partial; HU-40/HU-44 related |

Build order: schema and local-first foundation → HU-26 and HU-27 opening flows → HU-28 settlements → HU-29 schedules/reminders/closure. HU-26 and HU-27 may be implemented in parallel after shared foundations; HU-28 requires both. HU-29 requires HU-28.

## Database Readiness

The remote project already has `debts`, `debt_installments`, `debt_events`, `v_debt_summary`, RLS, and `record_debt_payment`. The Android project does not have debt entities or screens. Current server objects are legacy foundations, not acceptance evidence:

- The summary ignores ADJUSTMENT/FORGIVENESS and events whose linked transactions are VOIDED.
- The current payment RPC does not prevent overpayment, validate all account/installment references, record interest separately, or enforce expected revision; its EXPENSE insert also conflicts with the category-required constraint.
- Authenticated table policies and cascading foreign keys do not by themselves preserve audit history or protect multi-table money operations.
- The local migration folder includes S4/S5 migrations dated 2026-10-02 through 2026-10-07, while the remote migration catalog stops at 2026-09-30. Remote and local constraint state also differs for installment sequence uniqueness.

Gate: reconcile migration history and structure first; validate data-preserving forward migration in a local/dev database; do not apply S5 DDL until the prerequisite migrations and old event semantics are understood.

## Project Structure

### Documentation

```text
specs/013-ep-deu-deudas-prestamos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/debt-command-contract.md
└── tasks.md
```

### Source Code

```text
app/src/main/java/com/kipu/app/
├── core/database/KipuDatabase.kt
├── core/database/RoomMigrations.kt
├── feature/debts/
│   ├── domain/model/
│   ├── domain/usecase/
│   ├── domain/DebtRepository.kt
│   ├── data/local/
│   ├── data/remote/
│   ├── data/sync/
│   ├── presentation/
│   └── di/
├── feature/movements/domain/
├── feature/notifications/
└── navigation/

app/src/test/java/com/kipu/app/feature/debts/
app/src/androidTest/java/com/kipu/app/feature/debts/
supabase/migrations/
supabase/tests/
```

**Structure Decision**: Add a cohesive `feature/debts` module matching current `feature/movements` and `feature/accounts` boundaries. Keep pure principal/interest/balance rules in domain; Room, PostgREST/RPC and WorkManager in data; Compose screens in presentation. Extend movement flow calculators and navigation only at their existing boundaries. No new app module or third-party package is needed.

## Delivery Notes

- The existing `public.record_debt_payment(jsonb)` is not safe to reuse unchanged. Replace/extend it behind the idempotent command path and add commands for opening, schedule management, closure, and conditional delete.
- Keep parent principal payment and optional interest as one grouped, atomic user action. A void/correction applies to both movements and the principal event.
- Update the project-level Architecture and Data V4.2 debt model after approving the new opening date, reminder setting, signed event delta, transaction grouping, constraints, RLS, and sync shape.
- HU-29's S5 reminder should use a stable local unique-work identity and debt destination; HU-44 will own reminder preferences across object types and cross-device deduplication.
- The Android Studio account is available for a later acceptance pass. This plan uses isolated test data and does not create transactions or cards.

## Complexity Tracking

No constitution exception is proposed. The feature reuses the existing app/database boundary; its required new complexity is the complete debt domain module and financial sync contract.
