---

description: "Task list for EP-CCO implementation"
---

# Tasks: EP-CCO Categorías, Subcategorías y Comercios

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

**Checkpoint**: Room v4, the remote security boundary, owner-aware repository, and idempotent synchronization are ready for story increments.

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

- [X] T044 [P] Add regression coverage ensuring no category, merchant, movement form, or filter model introduces tags, aliases, original merchant text, or personal category preferences in `app/src/test/java/com/kipu/app/feature/categories/ScopeBoundaryTest.kt`.
- [X] T045 [P] Add cross-feature tests that category/merchant classification never changes movement amount, status, ledger effects, or account/card balances in `app/src/test/java/com/kipu/app/feature/accounts/MovementClassificationIntegrityTest.kt`.
- [X] T046 Add end-to-end offline/reconnect, process-death, retry, duplicate-receipt, catalog refresh, and two-device presentation/lifecycle conflict scenarios to `app/src/androidTest/java/com/kipu/app/feature/categories/EpCcoEndToEndTest.kt`.
- [X] T047 Run and record the Room v3-to-v4 migration and exported-schema review in `app/src/androidTest/java/com/kipu/app/core/database/KipuDatabaseSchemaTest.kt`.
- [X] T048 Run and record clean Supabase migration, pgTAP, RLS/grant, and advisor evidence in `supabase/tests/database/category_merchant.test.sql` and `specs/003-ep-cco-categorias-comercios/quickstart.md`.
- [X] T049 Run and record a timed acceptance evaluation for the 2-minute category-to-movement flow and 1-second cached-merchant-search threshold, plus real-device accessibility and offline validation, in `specs/003-ep-cco-categorias-comercios/quickstart.md`.
- [X] T050 Review requirement traceability, constitution compliance, and the reviewer-owned custom checklist in `specs/003-ep-cco-categorias-comercios/checklists/category-merchant.md` and `specs/003-ep-cco-categorias-comercios/checklists/requirements.md`.

---

## Dependencies and Execution Order

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

## Parallel Execution Examples

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

## Implementation Strategy

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
- Do not introduce Sprint 5 aliases, original merchant text, or personal category preferences while executing these tasks.

## Phase 7: HU-14 expense/income category types

**Purpose**: replace decorative category tabs with persisted, owner-safe type filters while preserving legacy classifications.

- [x] T051 Add `EXPENSE`, `INCOME`, and `GENERAL` to the category domain/local/remote model; existing untyped rows remain `GENERAL` in both tabs in `app/src/main/java/com/kipu/app/feature/categories/domain/model/CategoryModels.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/local/CategoryEntities.kt`, and `app/src/main/java/com/kipu/app/feature/categories/data/remote/CategoryDtos.kt`.
- [x] T052 Add Room 9→10 and forward-only Postgres migrations that default existing categories to `GENERAL`, preserve movement links, inherit a subcategory's root type, and validate new movement classification in `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt` and `supabase/migrations/20260923140000_category_types.sql`.
- [x] T053 Implement real Gastos/Ingresos filters, selected-tab root creation, inherited subcategory type, and type-filtered movement choices in `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt`, `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt`, and `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`.
- [x] T054 Add domain, repository payload, Room migration, movement validation, and pgTAP regression coverage in `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryRulesTest.kt`, `app/src/androidTest/java/com/kipu/app/feature/categories/data/local/CategoryTypeMigrationTest.kt`, and `supabase/tests/database/category_type_test.sql`.
- [x] T055 Run the updated Compose category screen tests and category-selector acceptance flow on a connected device/emulator; verified on connected physical device (Samsung SM-S926B, Android 16) with remote database and live account.
