---

description: "Task list for EP-CCO implementation"
---

# Tasks: EP-CCO Categorías, Subcategorías y Comercios

**Propagated**: 2026-10-08 — Added only the Sprint 5 work for HU-16/HU-17; completed S2 tasks remain historical.

**Scope history**: Phases 1–7 preserve the implemented S2 foundation and subsequent HU-14 type work, including the original HU-14/HU-15 exclusions. Phase 8 onward is the current S5 increment. HU-50 remains S8 and has no implementation tasks here.

**Input**: Design documents from `specs/003-ep-cco-categorias-comercios/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, and `quickstart.md`

**Tests**: Tests are included because the specification, quickstart, and constitution require domain, migration, synchronization, RLS, accessibility, and real-device evidence.

**Organization**: Tasks are grouped by user story after shared local-first, migration, and authorization prerequisites.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with unrelated tasks after its declared dependency is complete.
- **[USn]**: Maps the task to its user story.
- Every implementation task uses a repository-relative file path.

## Phase 1: Setup

**Purpose**: Establish the EP-CCO feature boundary and locate the existing financial and plan integration points.

- [X] T001 Create the `feature/categories` vertical package and Hilt module skeleton in `app/src/main/java/com/kipu/app/feature/categories/di/CategoriesModule.kt`.
- [X] T002 [P] Add an EP-CCO architecture-boundary test in `app/src/test/java/com/kipu/app/feature/categories/ArchitectureBoundaryTest.kt` that keeps domain independent of Android, Room, UI, and Supabase types.
- [X] T003 [P] Add the EP-CCO database-test entrypoint in `supabase/tests/database/category_merchant.test.sql`.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Build the shared model, persistence, authorization, and synchronization foundations. No user-story UI work starts before this phase is complete.

- [X] T004 Define typed category, category-presentation, merchant-catalog, movement-classification, and conflict models in `app/src/main/java/com/kipu/app/feature/categories/domain/model/CategoryModels.kt`.
- [X] T005 Define the owner-scoped repository ports and command/result types in `app/src/main/java/com/kipu/app/feature/categories/domain/CategoriesRepository.kt`.
- [X] T006 Add pure hierarchy, quota, eligibility, classification-exclusivity, and conflict-resolution rules in `app/src/main/java/com/kipu/app/feature/categories/domain/CategoryRules.kt`.
- [X] T007 [P] Add domain-rule coverage for two levels, cycles, active-root eligibility, five active custom roots, category/merchant independence, and provisional-text exclusivity in `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryRulesTest.kt`.
- [X] T008 Add category, presentation, merchant-catalog, category-conflict, and category-sync-outbox Room entities in `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryEntities.kt`.
- [X] T009 Extend movement persistence with nullable category, merchant, and provisional-merchant fields in `app/src/main/java/com/kipu/app/feature/accounts/data/local/FinancialMovementEntity.kt`.
- [X] T010 Add owner-scoped category, merchant-search, conflict, and atomic movement-classification DAO operations in `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryDao.kt` and `app/src/main/java/com/kipu/app/feature/categories/data/local/MerchantCatalogDao.kt`.
- [X] T011 Register EP-CCO entities and DAOs, implement the Room v3-to-v4 migration, and export schema v4 in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`, `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`, and `app/schemas/com.kipu.app.core.database.KipuDatabase/4.json`.
- [X] T012 [P] Add migration, owner-isolation, historical-null-preservation, and atomic-classification Room tests in `app/src/androidTest/java/com/kipu/app/feature/categories/data/local/CategoryDatabaseTest.kt`.
- [X] T013 Create the forward-only EP-CCO migration with the Supabase migration generator in `supabase/migrations/` for two-level category constraints, per-user presentations/lifecycle conflicts, movement classification, normalized merchant lookup, indexes, RLS/grants, and the active initial category catalog with Alimentación, Transporte, and Servicios.
- [X] T014 [P] Add pgTAP coverage for hierarchy, owner isolation, movement association independence, merchant read-only access, and migration preservation in `supabase/tests/database/category_merchant.test.sql`.
- [X] T015 Implement category DTOs, typed RPC client methods, and initial/refreshable merchant-catalog retrieval mappings with version metadata in `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoriesApi.kt` and `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`.
- [X] T016 Implement the offline-first repository that gates state through `SessionCoordinator`, commits Room changes before success, and maps remote outcomes in `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`.
- [X] T017 Implement the per-user category sync scheduler and worker with operation/hash receipts, retry handling, persisted presentation/lifecycle conflicts, initial merchant-catalog hydration, and refresh after reconnection in `app/src/main/java/com/kipu/app/feature/categories/data/sync/CategorySyncScheduler.kt` and `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`.
- [X] T018 Bind the repository, worker factory, and DAOs through Hilt in `app/src/main/java/com/kipu/app/feature/categories/di/CategoriesModule.kt` and `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`.
- [X] T019 [P] Add repository and worker tests for commit-before-success, duplicate receipt, retryable failure, `Protected`/`NoOwner`, presentation/lifecycle conflict persistence, catalog hydration, and stale-cache recovery in `app/src/test/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepositoryTest.kt` and `app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt`.

**Historical S2 checkpoint**: Room v4, the initial remote security boundary, owner-aware repository, and idempotent synchronization were delivered as the original S2 baseline.

---

## Phase 3: User Story 1 - Organizar categorías en dos niveles (Priority: P1) 🎯 MVP

**Goal**: Allow users to create, organize, activate, and inactivate custom roots and subcategories while preserving a strict two-level hierarchy and the Free limit.

**Independent Test**: A Free user can create a root and child, is blocked from a third level/cycle/sixth active custom root, and an inactive root blocks new assignments without removing history.

### Tests for User Story 1

- [X] T020 [P] [US1] Add use-case tests for create, inactivate, reactivate, hierarchy rejection, and Free-limit evaluation in `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryUseCasesTest.kt`.
- [X] T021 [P] [US1] Add RPC contract tests for category creation, lifecycle rejection, and explicit lifecycle-conflict outcomes in `supabase/tests/database/category_merchant.test.sql`.

### Implementation for User Story 1

- [X] T022 [P] [US1] Implement category observation and lifecycle use cases in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/ObserveCategories.kt` and `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/SetCategoryActive.kt`.
- [X] T023 [P] [US1] Implement custom-root and subcategory creation use cases with `Capability.CustomCategories` usage restricted to active custom roots in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/CreateCategory.kt`.
- [X] T024 [US1] Integrate category creation/reactivation with effective-plan access policy in `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`.
- [X] T025 [US1] Implement category list, form, lifecycle, quota, and one-shot error state in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt`.
- [X] T026 [US1] Implement the two-level category-management screen with create/edit, active/inactive, and Free-limit states in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`.
- [X] T027 [US1] Add the Categories route and connect the settings placeholder to it in `app/src/main/java/com/kipu/app/navigation/SettingsNavigation.kt`, `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`, and `app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt`.
- [X] T028 [P] [US1] Add Compose/navigation coverage for hierarchy display, disabled branch state, quota feedback, 48dp targets, and 200% text scaling in `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/CategoriesScreenTest.kt`.

**Checkpoint**: User Story 1 is independently usable and proves the hierarchy, lifecycle, and Free-cap requirements without relying on merchants or visual system-category customization.

---

## Phase 4: User Story 3 - Buscar y asignar comercios (Priority: P1)

**Goal**: Let users search the Kipu catalog and independently assign or clear a merchant while preserving category selection and supporting provisional text on no-result searches.

**Independent Test**: A categorized movement can receive, change, or clear a catalog merchant independently; normalized partial searches return catalog evidence only, and absent results retain optional provisional text without creating a merchant.

### Tests for User Story 3

- [X] T029 [P] [US3] Add normalized substring, accent, case, whitespace, no-result, and provisional-text use-case tests in `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantSearchUseCasesTest.kt`.
- [X] T030 [P] [US3] Add contract tests for catalog-only merchant reads, initial catalog availability, stale/unavailable catalog states, and movement-classification validation in `supabase/tests/database/category_merchant.test.sql`.

### Implementation for User Story 3

- [X] T031 [P] [US3] Implement merchant-catalog observation/search and movement-classification use cases in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/SearchMerchantCatalog.kt` and `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/UpdateMovementClassification.kt`.
- [X] T032 [US3] Implement normalized local catalog search, active-entry filtering, and independent movement classification writes in `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`.
- [X] T033 [US3] Implement the merchant picker state for query, results, empty result, chosen catalog merchant, provisional text, stale catalog, and unavailable catalog in `app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPickerViewModel.kt`.
- [X] T034 [US3] Implement the catalog search and provisional-text picker UI with stale/unavailable catalog states, without auto-assignment or tag controls in `app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt`.
- [X] T035 [US3] Create the `movement/edit/{movementId}` route and its independent category/merchant classification editor in `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/MovementClassificationEditor.kt`, `app/src/main/java/com/kipu/app/navigation/MovementNavigation.kt`, and `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`.
- [X] T036 [P] [US3] Add Compose coverage for partial search evidence, empty results, provisional text, independent clear actions, stale/unavailable catalog states, and non-color state communication in `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/MerchantPickerTest.kt`.

**Checkpoint**: User Story 3 can be demonstrated against the catalog with a pre-existing movement and category; no alias, rule, personal merchant, or automatic suggestion is introduced.

---

## Phase 5: User Story 2 - Personalizar catálogo de categorías (Priority: P2)

**Goal**: Let users edit name, icon, and color for any category, including predetermined categories, without changing category identity or historical movement links.

**Independent Test**: Editing a predetermined category's presentation leaves all linked movements tied to the same category identity, and concurrent offline presentation edits remain visible until the user selects a version.

### Tests for User Story 2

- [X] T037 [P] [US2] Add presentation-overlay and conflict-choice domain tests in `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryPresentationUseCasesTest.kt`.
- [X] T038 [P] [US2] Add database/RPC tests for presentation and lifecycle owner isolation, revision conflicts, duplicate resolutions, and historical movement preservation in `supabase/tests/database/category_merchant.test.sql`.

### Implementation for User Story 2

- [X] T039 [P] [US2] Implement presentation observation, update, and conflict-resolution use cases in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/UpdateCategoryPresentation.kt` and `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/ResolveCategoryConflict.kt`.
- [X] T040 [US2] Implement local system-category presentation overlays, expected-revision handling, and durable conflict records in `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`.
- [X] T041 [US2] Extend category UI state with presentation-edit and conflict-choice states in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt`.
- [X] T042 [US2] Implement the presentation editor and explicit two-version conflict-choice UI in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt` and `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryConflictScreen.kt`.
- [X] T043 [P] [US2] Add Compose coverage for system-category customization, preserved identity messaging, conflict choice, TalkBack semantics, and 200% text scaling in `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/CategoryPresentationTest.kt`.

**Checkpoint**: All category types are visually editable, historical links remain stable, and no concurrent presentation edit is silently discarded.

---

## Phase 6: Polish and Cross-Cutting Concerns

**Purpose**: Complete regression coverage, migration validation, accessibility, and release evidence across all stories.

- [X] T044 [P] Add S2 baseline regression coverage ensuring no category, merchant, movement form, or filter model introduces tags, aliases, original merchant text, or personal category preferences in `app/src/test/java/com/kipu/app/feature/categories/ScopeBoundaryTest.kt`.
- [X] T045 [P] Add cross-feature tests that category/merchant classification never changes movement amount, status, ledger effects, or account/card balances in `app/src/test/java/com/kipu/app/feature/accounts/MovementClassificationIntegrityTest.kt`.
- [X] T046 Add end-to-end offline/reconnect, process-death, retry, duplicate-receipt, catalog refresh, and two-device presentation/lifecycle conflict scenarios to `app/src/androidTest/java/com/kipu/app/feature/categories/EpCcoEndToEndTest.kt`.
- [X] T047 Run and record the Room v3-to-v4 migration and exported-schema review in `app/src/androidTest/java/com/kipu/app/core/database/KipuDatabaseSchemaTest.kt`.
- [X] T048 Run and record clean Supabase migration, pgTAP, RLS/grant, and advisor evidence in `supabase/tests/database/category_merchant.test.sql` and `specs/003-ep-cco-categorias-comercios/quickstart.md`.
- [X] T049 Run and record a timed acceptance evaluation for the 2-minute category-to-movement flow and 1-second cached-merchant-search threshold, plus real-device accessibility and offline validation, in `specs/003-ep-cco-categorias-comercios/quickstart.md`.
- [X] T050 Review requirement traceability, constitution compliance, and the reviewer-owned custom checklist in `specs/003-ep-cco-categorias-comercios/checklists/category-merchant.md` and `specs/003-ep-cco-categorias-comercios/checklists/requirements.md`.

---

## Historical S2 Dependencies and Execution Order (Phases 1–7)

### Phase Dependencies

- **Setup (Phase 1)** has no dependencies.
- **Foundational (Phase 2)** depends on Setup and blocks all user stories.
- **US1, US3, and US2** require Phase 2. Implement in priority order US1 -> US3 -> US2 for the smallest risk path; US3 and US2 may proceed in parallel after Phase 2 if they coordinate shared repository edits.
- **Polish (Phase 6)** requires all selected user-story phases.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2 and is the MVP. It owns custom category hierarchy and lifecycle.
- **US3 (P1)**: Starts after Phase 2 with a seeded/pre-existing movement; it reuses the shared movement-classification foundation but does not need the US1 management UI.
- **US2 (P2)**: Starts after Phase 2 and relies on the shared category/presentation model. It can follow US1 to reuse its category route.

### Parallel Opportunities

- T002, T003, and T007 can proceed independently once T001/T004 boundaries are available.
- T012, T014, and T019 can run in parallel with the corresponding migration, remote, and sync implementation once their interfaces are stable.
- Within US1: T020/T021 and T022/T023 can run in parallel.
- Within US3: T029/T030 and T031 can run in parallel.
- Within US2: T037/T038 and T039 can run in parallel.
- T044 and T045 can run in parallel during polish.

## Historical S2 Parallel Execution Examples

### User Story 1

```text
T020 Category use-case tests
T021 Category RPC contract tests
T022 Observe/lifecycle use cases
T023 Category creation use case
```

### User Story 3

```text
T029 Merchant-search use-case tests
T030 Merchant catalog and classification contract tests
T031 Merchant search and classification use cases
```

### User Story 2

```text
T037 Presentation and conflict domain tests
T038 Presentation RPC and persistence tests
T039 Presentation and conflict use cases
```

## Historical S2 Implementation Strategy (completed baseline)

### MVP First

1. Complete Phases 1 and 2, including Room v4, the remote migration, RLS, and synchronization safety.
2. Complete US1 and validate the hierarchy, lifecycle, Free quota, and history-preservation criteria independently.
3. Demonstrate the category-management increment before adding merchants or personal presentation conflicts.

### Incremental Delivery

1. Deliver US1 for custom category management.
2. Add US3 for catalog merchants and independent classification.
3. Add US2 for system-category presentation and explicit conflict resolution.
4. Complete cross-cutting validation and reviewer gates before release.

## Notes

- All tasks use strict checkbox, sequential-ID, optional-parallel, story-label, and exact-path formatting.
- `[P]` denotes only work that can avoid conflicting incomplete files after its documented prerequisite is satisfied.
- Phases 1–7 retain the original S2 boundary: aliases, original merchant text, and personal category preferences were excluded at that time. Phase 8 onward adds only the authorized S5 HU-16/HU-17 increment.

## Phase 7: HU-14 expense/income category types

**Purpose**: replace decorative category tabs with persisted, owner-safe type filters while preserving legacy classifications.

- [x] T051 Add `EXPENSE`, `INCOME`, and `GENERAL` to the category domain/local/remote model; existing untyped rows remain `GENERAL` in both tabs in `app/src/main/java/com/kipu/app/feature/categories/domain/model/CategoryModels.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryEntities.kt`, and `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`.
- [x] T052 Add Room 9→10 and forward-only Postgres migrations that default existing categories to `GENERAL`, preserve movement links, inherit a subcategory's root type, and validate new movement classification in `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt` and `supabase/migrations/20260923140000_category_types.sql`.
- [x] T053 Implement real Gastos/Ingresos filters, selected-tab root creation, inherited subcategory type, and type-filtered movement choices in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`, `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt`, and `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`.
- [x] T054 Add domain, repository payload, Room migration, movement validation, and pgTAP regression coverage in `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryRulesTest.kt`, `app/src/androidTest/java/com/kipu/app/feature/categories/data/local/CategoryTypeMigrationTest.kt`, and `supabase/tests/database/category_type_test.sql`.
- [x] T055 Run the updated Compose category screen tests and category-selector acceptance flow on a connected device/emulator; verified on connected physical device (Samsung SM-S926B, Android 16) with remote database and live account.

---

## Phase 8: Sprint 5 shared foundation for HU-16 and HU-17

**Purpose**: Extend the S2/S4 EP-CCO baseline with source-text preservation, private merchant rules/preferences, local-first persistence, and a verified remote authorization boundary. S5 work below is implementation; tests are authored and run before their corresponding production code.

### Tests first (TDD)

- [X] T056 [P] Add domain tests for stable IDs, owner/revision/tombstone invariants, source-text preservation, and private preference ownership in `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantRuleModelsTest.kt`.
- [X] T057 [P] Add Room v18→v19 migration and data-preservation tests for source merchant text, alias rules, preferences, revisions, and outbox state in `app/src/androidTest/java/com/kipu/app/core/database/MerchantRuleDatabaseTest.kt`.
- [X] T058 [P] Add pgTAP tests for owner isolation, active merchant/category references, no direct catalog DML, command idempotency, and effective RLS/grants for two users in `supabase/tests/database/merchant_rules_s5.test.sql`.

### Shared implementation

- [X] T059 Define the source-text, alias-rule, and merchant-category-preference domain models with stable IDs, owner, revision, and tombstone state in `app/src/main/java/com/kipu/app/feature/categories/domain/model/CategoryModels.kt` and `app/src/main/java/com/kipu/app/feature/accounts/data/local/FinancialMovementEntity.kt`.
- [X] T060 Add a shared text normalizer and exact-equality alias matcher that preserves punctuation and original text, plus explicit no-match and different-destination review outcomes in `app/src/main/java/com/kipu/app/feature/categories/domain/MerchantAliasRules.kt`.
- [X] T061 Add Room v18→v19 entities and a non-destructive migration for source merchant text, private alias rules, category preferences, revisions, and outbox commands; export schema 19 in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`, `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/local/MerchantRuleEntities.kt`, and `app/schemas/com.kipu.app.core.database.KipuDatabase/19.json`.
- [X] T062 Add forward-only Supabase migrations for the S5 alias/preference command contract, owner-scoped persistence, constraints, RPC receipts, and RLS/grants; preserve the Kipu catalog as read-only in `supabase/migrations/20261008161406_ep_cco_s5_alias_preference_foundation.sql`, `supabase/migrations/20261008161540_ep_cco_s5_alias_commands.sql`, and `supabase/migrations/20261008161631_ep_cco_s5_preference_commands.sql`.
- [X] T063 Add remote DTOs, typed API methods, repository ports, and outbox/worker mapping for alias, preference, and immutable source-text commands, using `auth.uid()`-derived ownership and idempotent operation receipts in `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoriesApi.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`, and `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`.
- [X] T064 Update the Supabase readiness procedure to compare live migration history with the local S4/October head and require approved migration application plus effective policy/grant review before S5 integration or release in `specs/003-ep-cco-categorias-comercios/quickstart.md`.

**Checkpoint**: Local v19 persistence and versioned server contracts are defined and testable. The linked/live Supabase project remains a release gate until its migration history is reconciled through the approved process; this task list does not authorize ad hoc remote writes.

---

## Phase 9: User Story 5 — HU-16 conservar texto original y reutilizar alias

**Goal**: Let a Premium user save an alias after confirming its canonical merchant, preserve the source text, and evaluate authorized signals by exact normalized equality.

**Independent Test**: A confirmed `IZIPAY*TAMBO` alias proposes Tambo for a later authorized signal with the same normalized text, leaves the original string unchanged, rejects prefix/substring-only matches, and requests human review if eligible rules resolve to different merchants.

### Tests for User Story 5

- [X] T065 [P] [US5] Add domain coverage for exact normalized equality, punctuation retention, no match, ambiguity review, source-text preservation, Premium rule creation through a verified bounded offline lease, lease expiry, and signal entitlement/consent gates in `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantAliasRulesTest.kt` and `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantRuleUseCasesTest.kt`.
- [X] T066 [P] [US5] Add use-case and RPC coverage for manual canonical confirmation, server-verified entitlement checks for new rules, owner-only edit/tombstone, revision conflicts, receipts, and catalog immutability in `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantRuleUseCasesTest.kt` and `supabase/tests/database/merchant_rules_s5.test.sql`.
- [X] T067 [P] [US5] Add Compose coverage for Premium gating, source/canonical separation, exact-match explanation, no-match and ambiguity review guidance, accessible controls, and manual-flow availability when signal processing is unauthorized in `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/MerchantAliasRulesScreenTest.kt`.

### Implementation for User Story 5

- [X] T068 [US5] Implement alias create/update/remove use cases and server RPCs; require verified Premium entitlement or a verified bounded offline lease locally only for creating a new rule, require manual canonical-merchant confirmation, and keep edits future-only in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/SaveMerchantAliasRule.kt`, `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/DeleteMerchantAliasRule.kt`, and `supabase/migrations/20261008161540_ep_cco_s5_alias_commands.sql`.
- [X] T069 [US5] Implement evaluation only for an authorized, provenance-bearing `CaptureCandidate` after current entitlement and consent checks; return no match or explicit review for different canonical destinations without priority tie-breaking in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/EvaluateMerchantAlias.kt` and `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`.
- [X] T070 [US5] Add a private alias-management screen that displays source pattern and canonical merchant separately, gates new-rule creation for Premium, explains no-match and ambiguity review, and never edits confirmed movements or the shared catalog in `app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantAliasRulesScreen.kt` and `app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantAliasRulesViewModel.kt`.

**Checkpoint**: HU-16 is independently demonstrable without a notification/OCR capture pipeline; only an already available signal that passes entitlement and consent checks can be evaluated.

---

## Phase 10: User Story 6 — HU-17 preferencia personal de categoría por comercio

**Goal**: Let each user choose an eligible category per merchant for future operations, ahead of a general suggestion, without a separate Premium gate or historical reclassification.

**Independent Test**: User A's eligible Tambo preference takes precedence on a compatible future operation for A, remains invisible to user B, and stops auto-applying when the category is inactive, plan-blocked, or incompatible; confirmed history stays unchanged.

### Tests for User Story 6

- [X] T071 [P] [US6] Add domain tests for owner isolation, preference-over-general precedence, future-only behavior, movement-type compatibility, active-root eligibility, and plan-blocked categories in `app/src/test/java/com/kipu/app/feature/categories/domain/MerchantCategoryPreferenceTest.kt`.
- [X] T072 [P] [US6] Add database/RPC coverage for one preference per owner/merchant, category reference validation, two-user RLS, idempotent updates/tombstones, and history preservation in `supabase/tests/database/merchant_rules_s5.test.sql`.
- [X] T073 [P] [US6] Add Compose coverage for eligible category presentation, inactive/ineligible recovery, no Premium gate, accessible selection, and future-only/history-preservation copy in `app/src/androidTest/java/com/kipu/app/feature/categories/presentation/MerchantCategoryPreferenceScreenTest.kt`; owner isolation is verified by T071 and T072.

### Implementation for User Story 6

- [X] T074 [US6] Implement save/remove/resolve preference use cases and server RPCs with no independent Premium gate and future-operation-only semantics in `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/SetMerchantCategoryPreference.kt`, `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/DeleteMerchantCategoryPreference.kt`, `app/src/main/java/com/kipu/app/feature/categories/domain/usecase/ResolvePreferredCategory.kt`, and `supabase/migrations/20261008161631_ep_cco_s5_preference_commands.sql`.
- [X] T075 [US6] Implement owner-scoped local persistence and sync for merchant-category preferences, validating active category/root, plan eligibility, and movement type before applying any suggestion in `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/local/MerchantRuleEntities.kt`, and `app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt`.
- [X] T076 [US6] Add preference controls to merchant settings with only eligible category choices, explicit inactive/ineligible recovery, and future-only explanatory text in `app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceScreen.kt` and `app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt`.

**Checkpoint**: HU-17 supplies a private, eligible, future-only preference that is separate from merchant catalog presentation and from general rule metadata.

---

## Phase 11: Sprint 5 regression and release evidence

**Purpose**: Update S2 boundary assertions to reflect the approved S5 increment while preserving S2 history and HU-50's S8 exclusion.

- [X] T077 [P] Update the S2 `ScopeBoundaryTest` so it allows HU-16/HU-17 source text, private aliases, and personal category preferences while continuing to prohibit tags and HU-50 learning-from-corrections behavior in `app/src/test/java/com/kipu/app/feature/categories/ScopeBoundaryTest.kt`.
- [X] T078 [P] Add cross-feature regression coverage proving alias/preference changes never alter movement amount, status, ledger effects, balances, or confirmed historical classification in `app/src/test/java/com/kipu/app/feature/accounts/MovementClassificationIntegrityTest.kt`.
- [X] T079 [P] Extend offline/reconnect and two-user acceptance coverage for raw-text retention, exact alias matching, ambiguity review, consent/entitlement gates, preference precedence, tombstones, and history preservation in `app/src/androidTest/java/com/kipu/app/feature/categories/EpCcoEndToEndTest.kt`.
- [X] T080 Record S5 test/device evidence and the Supabase live migration/RLS/grant readiness decision; do not mark release-ready while migration history remains behind the local S4/October head in `specs/003-ep-cco-categorias-comercios/quickstart.md`.
- [X] T081 Recheck HU-16/HU-17 FR/SC traceability, S2 historical boundaries, S8 HU-50 exclusion, constitution compliance, and reviewer-owned checklist ownership in `specs/003-ep-cco-categorias-comercios/spec.md`; preserve the reviewer-owned `checklists/requirements.md` unchanged.

### S5 dependencies

- Phase 8 blocks Phases 9–11. Complete tests T056–T058 before their implementation T059–T063; remote integration/release is additionally blocked until the Supabase migration/RLS gate is cleared.
- In Phase 9, tests T065–T067 precede implementation T068–T070. T069 depends on the entitlement/consent ports from T063.
- In Phase 10, tests T071–T073 precede implementation T074–T076. T075 depends on the category eligibility model from T059.
- Alias and preference work may proceed in parallel only in separate files; shared repository/worker edits must be coordinated and sequential.
- Phase 11 requires both user-story phases. HU-50 remains excluded from this dependency graph.

### S5 parallel opportunities

- T056–T058 and T065–T067 are independent test files and may be authored in parallel before implementation.
- T071–T073 are independent test files and may be authored in parallel before implementation.
- T076/T077 can proceed independently after the domain model settles; T078/T079 require both story flows.
