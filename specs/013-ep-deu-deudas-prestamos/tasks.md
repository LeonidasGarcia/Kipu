---
description: "Dependency-ordered implementation tasks for EP-DEU Sprint 5"
---

# Tasks: EP-DEU — Deudas y Préstamos (Sprint 5)

**Input**: Design documents from `specs/013-ep-deu-deudas-prestamos/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, and `contracts/debt-command-contract.md`

**Tests**: Verification tasks are included because the project constitution requires domain, persistence, migration, sync, security/RLS, and applicable device-boundary validation. These are future implementation tasks; no tests were run during planning.

**Organization**: Tasks are grouped by HU-26 through HU-29. HU-26 and HU-27 follow shared foundations and can proceed in parallel. HU-28 depends on both opening flows; HU-29 depends on settlements.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can proceed in parallel with tasks that touch different files and have no unmet dependencies.
- **[Story]**: Maps a task to a user story in `spec.md`.
- All implementation paths are relative to the repository root unless identified as a canonical product-planning document.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the safe starting point for the debt feature and its migration work.

- [X] T001 Reconcile local and remote migration histories, debt constraints, and legacy event semantics; record the local-development GO, remote rollout NO-GO, and unresolved blockers in `specs/013-ep-deu-deudas-prestamos/research.md` without applying remote DDL.
- [X] T002 Create the `app/src/main/java/com/kipu/app/feature/debts/` package layout for domain, data, presentation, and DI boundaries described in `plan.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add compatible storage, shared command contracts, owner-safe sync, and financial calculation boundaries required by all stories.

**Checkpoint**: Do not begin user-story acceptance until the migration gate passes, Room can open from v18, and debt changes can be stored and synchronized without duplicate effects.

- [X] T003 [P] Add preflight and post-migration assertions against representative legacy rows, including ADJUSTMENT/FORGIVENESS semantics, installment uniqueness drift, and unchanged IDs, balances, ownership, and history, in `supabase/tests/database/debt_sprint5_migration_test.sql`.
- [X] T004 [P] Add pure domain and movement-accounting tests for signed principal deltas, legacy PAYMENT compatibility, zero-balance derivation, voided linked transactions, principal exclusion, and real-interest inclusion in `app/src/test/java/com/kipu/app/feature/debts/domain/DebtPrincipalCalculatorTest.kt` and `app/src/test/java/com/kipu/app/feature/movements/domain/DebtMovementAccountingTest.kt`.
- [X] T005 [P] Add Room v18-to-v19 upgrade and data-preservation coverage in `app/src/androidTest/java/com/kipu/app/core/database/DebtRoomMigrationTest.kt`.
- [X] T006 [P] Add database contract tests for owner isolation across debt/account/installment/transaction references, SECURITY DEFINER ownership checks, command idempotency, and concurrent Free quota enforcement in `supabase/tests/database/debt_sprint5_security_and_idempotency_test.sql`.
- [X] T007 Create the additive debt lifecycle migration in `supabase/migrations/20261008120000_s5_debt_lifecycle.sql` only after T001 confirms a safe migration path; add opening/reminder/event-delta fields, update the debt summary and category constraint, and preserve existing IDs, history, RLS, grants, and valid balances.
- [X] T008 Add `DebtEntity`, `DebtInstallmentEntity`, `DebtEventEntity`, their DAOs, indexes, and the v18-to-v19 registration in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt` and `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`.
- [X] T009 Define shared debt command IDs, request hashes, revision checks, owner-safe results, retry/conflict errors, and the pure signed-delta principal projection in `app/src/main/java/com/kipu/app/feature/debts/domain/model/DebtCommandModels.kt`, `app/src/main/java/com/kipu/app/feature/debts/domain/DebtCommandHasher.kt`, and `app/src/main/java/com/kipu/app/feature/debts/domain/DebtPrincipalCalculator.kt`.
- [X] T010 Implement atomic Room debt/event/outbox/receipt persistence and local change projection in `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt` and `app/src/main/java/com/kipu/app/feature/debts/data/local/DebtDao.kt`.
- [X] T011 Add debt DTOs, RPC calls, and paged debt change-feed application with explicit stale-revision conflicts in `app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt`, `app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtDtos.kt`, and `app/src/main/java/com/kipu/app/feature/debts/data/sync/SyncDebtChangesWorker.kt`.
- [X] T012 Extend movement and budget calculations so debt principal changes account cash but never counts as operating income/expense or expense consumption, while a separately recorded real-interest movement does, in `app/src/main/java/com/kipu/app/feature/movements/domain/MovementNetFlowCalculator.kt` and `app/src/main/java/com/kipu/app/feature/movements/domain/ExpenseConsumptionCalculator.kt`.
- [X] T013 Implement the shared owner-checked, idempotent `OPEN_DEBT` command framework, combined quota lock, receipt handling, and change-feed publication in `supabase/migrations/20261008120000_s5_debt_lifecycle.sql`; leave the type-specific cash branches to US1 and US2.
- [X] T014 Add the common obligation list/detail query path, repository boundary, and Hilt bindings in `app/src/main/java/com/kipu/app/feature/debts/domain/DebtRepository.kt`, `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`, and `app/src/main/java/com/kipu/app/feature/debts/di/DebtsModule.kt`.

---

## Phase 3: User Story 1 — Registrar y administrar una deuda propia (Priority: P1, HU-26)

**Goal**: Record new and historical PAYABLE obligations, preserve their financial meaning, and protect existing history and Free limits.

**Independent Test**: Create a new PAYABLE with cash received and a historical PAYABLE without cash movement; verify cash, liability, and operating income, edit descriptive fields, test conditional removal, and verify the combined two-obligation Free limit.

### Tests for User Story 1

- [X] T015 [P] [US1] Add PAYABLE opening, historical opening, and descriptive-edit domain tests in `app/src/test/java/com/kipu/app/feature/debts/domain/OpenPayableDebtTest.kt`.
- [X] T016 [P] [US1] Add server tests for PAYABLE cash/liability equality, no operating income, account ownership/currency validation, physical-delete guards, and combined Free quota in `supabase/tests/database/debt_payable_opening_test.sql`.
- [X] T017 [P] [US1] Add local-first repository tests for offline visibility, retry idempotency, and preservation of existing history in `app/src/androidTest/java/com/kipu/app/feature/debts/data/PayableDebtRepositoryTest.kt`.
- [X] T018 [P] [US1] Add Compose acceptance coverage for new/historical PAYABLE entry, descriptive edits, and blocked deletion when history exists in `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/PayableDebtFlowTest.kt`.

### Implementation for User Story 1

- [X] T019 [US1] Implement PAYABLE validation and new-versus-historical cash/liability behavior in `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenPayableDebt.kt` and the PAYABLE handler dispatched by `OPEN_DEBT` in `supabase/migrations/20261008130000_s5_open_payable_debt.sql`.
- [X] T020 [US1] Implement descriptive edits and conditional deletion that calls `DELETE_DEBT_IF_UNREFERENCED` and rejects any debt with financial history in `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/EditDebtDetails.kt`, `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/DeleteDebtIfUnreferenced.kt`, and `supabase/migrations/20261008140000_s5_debt_history_guard.sql`.
- [X] T021 [US1] Connect PAYABLE form state to the repository without placing financial calculations in Compose in `app/src/main/java/com/kipu/app/feature/debts/presentation/PayableDebtViewModel.kt` and `app/src/main/java/com/kipu/app/feature/debts/presentation/PayableDebtFormScreen.kt`.
- [X] T022 [US1] Render PAYABLE balance, opening basis, and non-destructive lifecycle actions from repository projections in `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtListScreen.kt` and `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtDetailScreen.kt`.

**Checkpoint**: New and historical PAYABLE openings must have correct cash/liability effects; edits and quota failures must leave financial history unchanged.

---

## Phase 4: User Story 2 — Registrar dinero prestado a otra persona (Priority: P1, HU-27)

**Goal**: Record new and historical RECEIVABLE obligations without duplicate cash deductions or operating expenses.

**Independent Test**: Lend from a same-currency active account and record an already-reflected historical loan; verify the first moves cash into receivables and the second creates no second cash deduction.

### Tests for User Story 2

- [X] T023 [P] [US2] Add RECEIVABLE opening and historical-opening domain tests in `app/src/test/java/com/kipu/app/feature/debts/domain/OpenReceivableDebtTest.kt`.
- [X] T024 [P] [US2] Add server tests for cash-to-receivable movement, no operating expense, invalid account rejection, and historical basis in `supabase/tests/database/debt_receivable_opening_test.sql`.
- [X] T025 [P] [US2] Add local-first repository retry and offline collection-balance preservation tests in `app/src/androidTest/java/com/kipu/app/feature/debts/data/ReceivableDebtRepositoryTest.kt`.
- [X] T026 [P] [US2] Add Compose acceptance coverage for account selection, currency validation, and new/historical RECEIVABLE entry in `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/ReceivableDebtFlowTest.kt`.

### Implementation for User Story 2

- [X] T027 [US2] Implement RECEIVABLE opening validation, same-currency source-account requirements, and the historical/new cash handler dispatched by `OPEN_DEBT` in `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenReceivableDebt.kt` and `supabase/migrations/20261008135000_s5_open_receivable_debt.sql`.
- [X] T028 [US2] Render RECEIVABLE cash direction, historical-opening basis, account selection, and currency errors in `app/src/main/java/com/kipu/app/feature/debts/presentation/ReceivableDebtViewModel.kt` and `app/src/main/java/com/kipu/app/feature/debts/presentation/ReceivableDebtFormScreen.kt`.
- [X] T029 [US2] Define the debt list, detail, and opening destinations for both obligation types in `app/src/main/java/com/kipu/app/navigation/DebtsNavigation.kt`.

**Checkpoint**: New RECEIVABLE openings reduce cash and increase receivables equally; historical openings do not move cash; neither creates operating expense.

---

## Phase 5: User Story 3 — Pagar o cobrar principal e interés por separado (Priority: P1, HU-28)

**Goal**: Record atomic principal settlements and separately account for real interest, with safe retries, corrections, and voids.

**Independent Test**: Apply PAYABLE and RECEIVABLE settlements with and without interest; verify cash changes by principal plus interest, principal changes by principal only, and void/correction restores the derived balance without duplicate effects.

### Tests for User Story 3

- [X] T030 [P] [US3] Add principal/interest direction, over-settlement, stale revision, and grouped void domain tests in `app/src/test/java/com/kipu/app/feature/debts/domain/SettleDebtTest.kt`.
- [X] T031 [P] [US3] Add RPC tests for atomic principal plus interest, owner/account/category/installment checks, idempotent retries, overpayment rejection, and void/revision recomputation in `supabase/tests/database/debt_settlement_test.sql`.
- [X] T032 [P] [US3] Add repository/outbox integration tests proving a retried settlement creates one event, one principal movement, and at most one interest movement in `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtSettlementSyncTest.kt`.
- [X] T033 [P] [US3] Add regression tests showing principal is absent from operating totals and budget consumption while real interest is included in `app/src/test/java/com/kipu/app/feature/movements/domain/DebtMovementAccountingTest.kt`.
- [X] T034 [P] [US3] Add Compose acceptance coverage for principal/interest entry, overpayment errors, and stale-command conflict display in `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtSettlementFlowTest.kt`.

### Implementation for User Story 3

- [X] T035 [US3] Implement principal and interest validation, current-balance checks, and direction-aware settlement planning in `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/SettleDebt.kt`.
- [X] T036 [US3] Implement server-side `SETTLE_DEBT` as one idempotent transaction for debt event, principal movement, optional categorized interest movement, ledger, installment allocation, receipt, and sync feed in `supabase/migrations/20261008150000_s5_settle_debt.sql`.
- [X] T037 [US3] Extend server and movement correction/void handling to invalidate the linked principal and interest pair together and recalculate debt, installment, and lifecycle projections in `supabase/migrations/20261008150000_s5_settle_debt.sql`, `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`, and `app/src/main/java/com/kipu/app/feature/movements/domain/VoidTransaction.kt`.
- [X] T038 [US3] Implement settlement entry and server conflict/retry states in `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtSettlementViewModel.kt` and `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtSettlementSheet.kt`.
- [X] T039 [US3] Show separate principal and interest amounts and their distinct operating effects in `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtDetailScreen.kt`.

**Checkpoint**: Principal-only operations never affect operating income/expense; interest is counted once; retries and grouped voids preserve a coherent ledger and derived debt state.

---

## Phase 6: User Story 4 — Planificar cuotas, recibir avisos y cerrar obligaciones (Priority: P2, HU-29)

**Goal**: Plan principal installments and debt reminders without implying payment, and close obligations only through a valid, auditable lifecycle action.

**Independent Test**: Create an exact-sum schedule, confirm no financial movement occurs, receive or suppress a debt-specific reminder based on platform permission, then settle, cancel, adjust, forgive, and reopen a balance after void as applicable.

### Tests for User Story 4

- [X] T040 [P] [US4] Add installment splitting/remainder and no-financial-effect domain tests in `app/src/test/java/com/kipu/app/feature/debts/domain/DebtScheduleTest.kt`.
- [X] T041 [P] [US4] Add server tests for schedule sum, installment ownership, auditable closure, active-quota release, cancellation, and reopening after void in `supabase/tests/database/debt_schedule_and_closure_test.sql`.
- [X] T042 [P] [US4] Add unique-work scheduling, replacement/cancellation, retry deduplication, and notification-permission behavior tests in `app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderSchedulerTest.kt`.
- [X] T043 [P] [US4] Add Compose/device acceptance coverage distinguishing planned installments from completed payments and verifying debt reminder destination in `app/src/androidTest/java/com/kipu/app/feature/debts/presentation/DebtScheduleAndClosureFlowTest.kt`.

### Implementation for User Story 4

- [X] T044 [US4] Implement exact-sum installment creation, revision validation, and server schedule replacement without ledger effects in `app/src/main/java/com/kipu/app/feature/debts/domain/usecase/SetDebtSchedule.kt` and `supabase/migrations/20261008160000_s5_debt_schedule.sql`.
- [X] T045 [US4] Implement idempotent debt-specific reminder scheduling and cancellation using stable debt/installment/due-date identity in `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderScheduler.kt` and `app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderWorker.kt`.
- [X] T046 [US4] Implement `CLOSE_DEBT` SETTLE/CANCEL/ADJUST/FORGIVE validation, history retention, and active-count transitions in `supabase/migrations/20261008170000_s5_debt_closure.sql`.
- [X] T047 [US4] Recompute installment, debt status, and pending reminder state after grouped settlement correction or void in `app/src/main/java/com/kipu/app/feature/debts/data/OfflineFirstDebtRepository.kt`.
- [X] T048 [US4] Implement schedule, reminder lead-time, cancellation, adjustment, forgiveness, and settlement controls in `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleViewModel.kt` and `app/src/main/java/com/kipu/app/feature/debts/presentation/DebtScheduleScreen.kt`.
- [X] T049 [US4] Register the debt navigation graph and reminder deep-link destinations in `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`.

**Checkpoint**: Schedules and reminders have no accounting effect; no non-zero derived balance is marked settled; cancellation preserves and displays history; voiding a settlement can restore an active balance.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Close cross-epic documentation and release-readiness gaps after the four stories pass their independent verification.

- [X] T050 Update `C:/Users/Alume/orca/KipuApp/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` with the approved debt entities, event semantics, movement links, RLS, quota locking, sync, and migration decisions; record the cross-repository review outcome in `specs/013-ep-deu-deudas-prestamos/research.md` because the canonical document is outside this isolated code worktree.
- [X] T051 [P] Verify every HU-26–HU-29 acceptance scenario, financial/regression check, migration compatibility result, retry/offline path, RLS boundary, notification-permission state, and accessibility path against `specs/013-ep-deu-deudas-prestamos/quickstart.md` and update the evidence references there.
- [X] T052 [P] Run a cross-review of the debt feature implementation and record findings and resolutions in `specs/013-ep-deu-deudas-prestamos/research.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 must establish a safe migration path; T002 creates the target package structure.
- **Foundational (Phase 2)**: Depends on Setup and blocks all four stories. T003–T006 verification coverage can be authored in parallel; migration and storage work starts only after T001.
- **User Stories**: HU-26 and HU-27 start after the foundation and may proceed in parallel. HU-28 starts only after both opening paths are integrated. HU-29 starts after HU-28 grouped settlement and correction behavior is available.
- **Polish**: Depends on the required stories and their verification passing.

### User Story Dependencies

- **US1 / HU-26 (P1)**: Foundational phase → PAYABLE flow. No dependency on HU-27 for opening; the server's combined quota check is shared.
- **US2 / HU-27 (P1)**: Foundational phase → RECEIVABLE flow. May run in parallel with US1 after shared opening/storage contracts exist.
- **US3 / HU-28 (P1)**: Depends on US1 and US2 because it settles either obligation type.
- **US4 / HU-29 (P2)**: Depends on US1 and US2 for both obligation types and US3 for payment allocation, reversal, and closure state.

### Parallel Opportunities

- T003–T006 can be authored in parallel after the file structure is known.
- US1 and US2 test/implementation tasks can proceed in parallel once T007–T014 establish common storage, sync, and opening command contracts.
- Within each story, independent domain, SQL, repository, and Compose tests can be authored in parallel before their implementations.
- Cross-review and quickstart evidence collation can proceed in parallel after all stories are complete.

## Parallel Example: User Stories 1 and 2

```text
Developer A: T015–T022 — PAYABLE opening and administration (HU-26)
Developer B: T023–T029 — RECEIVABLE opening (HU-27)
Both depend on T001–T014; finish both before HU-28 settlement work.
```

## Implementation Strategy

### MVP First (HU-26)

1. Complete Setup and Foundational tasks, especially migration reconciliation and verified storage/sync boundaries.
2. Complete HU-26 (US1) and its domain, database, repository, and UI verification.
3. Validate the new/historical PAYABLE acceptance cases independently.
4. Continue with HU-27, then HU-28, and HU-29 in dependency order.

### Incremental Delivery

1. Setup + Foundation → safe, testable local-first debt base.
2. HU-26 → new and historical payable obligations.
3. HU-27 → new and historical receivables.
4. HU-28 → principal/interest settlement and grouped correction.
5. HU-29 → installments, reminders, closure, and lifecycle restoration.
6. Polish → architecture documentation, cross-review, and verified acceptance evidence.

## Requirement Traceability

| Requirement | Planned coverage |
| --- | --- |
| FR-001–FR-005 | T013, T019, T023, T027–T029 |
| FR-006 | T015, T020, T022 |
| FR-007 | T004, T010, T030, T035 |
| FR-008–FR-011 | T030–T036 |
| FR-012–FR-013 | T006, T031–T037, T047 |
| FR-014–FR-017 | T040–T045, T048 |
| FR-018–FR-020 | T016, T041, T046, T047 |
| FR-021 | T006, T013, T016, T041, T046 |
| FR-022 | T010–T011, T017, T025, T032, T051 |
| FR-023–FR-024 | T006, T009, T011, T016, T024, T031, T034, T041 |
| FR-025 | T050–T051 |
| SC-001–SC-002 | T004, T015–T018, T023–T024, T030–T036, T051 |
| SC-003 | T040, T044, T051 |
| SC-004 | T006, T016, T024, T031, T034, T041 |
| SC-005 | T006, T032, T042 |
| SC-006 | T041, T046–T047 |
| SC-007 | T017, T025, T032, T051 |
| SC-008 | T043, T048–T049 |

## Notes

- Keep money in integer minor units with currency; never derive authoritative balances from editable stored totals.
- Free quota checks must be serialized by the server in the same transaction as opening.
- No task authorizes applying a migration to production or changing live user data; validate against representative local/dev data first.
- HU-24 movement allocation, HU-40 calendar, and HU-44 general reminder preferences/deduplication remain outside Sprint 5.
- Tasks describe implementation and future verification only; no tests were run while preparing this plan.
