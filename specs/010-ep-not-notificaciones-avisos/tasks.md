# Tasks: EP-NOT — Centro de Notificaciones y Avisos (HU-42)

**Input**: Design documents in `/specs/010-ep-not-notificaciones-avisos/`.
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, and `contracts/notifications-rest.md`.
**Tests**: Included because HU-42, its plan, and the user prompt explicitly require JVM, Room, Compose, and Supabase verification. Write tests before the implementation they cover.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files and no dependency on an unfinished task.
- **[Story]**: Maps to a user story from `spec.md`; setup and foundational tasks have no story label.
- Every task names concrete file paths.

## Phase 1: Setup — Boundary Tests

**Purpose**: Establish failing database and persistence boundary tests before implementation.

- [X] T001 [P] Add pgTAP coverage for owner SELECT/UPDATE, cross-user isolation, anon denial, column privileges, soft-delete column/index, and physical-delete denial (FR-007, FR-009) in `supabase/tests/database/notifications_center_test.sql`.
- [X] T002 [P] Add Room migration 11→12 and account-scoped DAO schema tests (FR-009, FR-010) in `app/src/androidTest/java/com/kipu/app/feature/notifications/data/local/NotificationsDatabaseTest.kt`.

---

## Phase 2: Foundational — Shared Data and Domain

**Purpose**: Build the account-scoped storage, migration, domain types, and remote boundary required by all stories.

- [X] T003 Add a forward-only Supabase migration for nullable `deleted_at`, active/unread ordering index, and least-privilege authenticated column UPDATE grants (FR-007, FR-008, FR-009) in `supabase/migrations/20260928000000_ep_not_notification_center_hu42.sql`.
- [X] T004 [P] Add `AppNotificationEntity.kt` (including `NotificationSyncOutboxEntity`) and `AppNotificationDao.kt` (including `NotificationSyncOutboxDao`) with account-scoped queries (FR-001, FR-006–FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/data/local/`.
- [X] T005 Register both notification entities and DAO accessors in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`; add `MIGRATION_11_12` in `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`; register it in `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`.
- [X] T006 [P] Add type/reference normalization and category unit tests for canonical values, the historic credit alias, case variants, and unknown values (FR-002, FR-004) in `app/src/test/java/com/kipu/app/feature/notifications/domain/NotificationTypeTest.kt`.
- [X] T007 Implement `Notification.kt`, `NotificationsRepository.kt`, and `NotificationType.kt` with alert/reminder classification and action results (FR-001, FR-002, FR-004–FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/domain/`.
- [X] T008 [P] Add authenticated PostgREST DTO/API contract tests for owner filters, ordering, allowed PATCH fields, failure mapping, and legacy payload decoding in `app/src/test/java/com/kipu/app/feature/notifications/data/remote/NotificationsApiTest.kt`.
- [X] T009 Implement authenticated GET/PATCH API, DTOs, and tolerant unknown-field decoding in `app/src/main/java/com/kipu/app/feature/notifications/data/remote/`.
- [X] T010 Bind repository and provide feature DAOs/API in `app/src/main/java/com/kipu/app/feature/notifications/di/NotificationsModule.kt`.

**Checkpoint**: Supabase/Room boundaries, migrations, account-scoped domain types, and the API contract are ready.

---

## Phase 3: User Story 1 — Consultar alertas y recordatorios (Priority: P1, MVP)

**Goal**: Show the active account's latest notices, distinguish alerts from future reminders, and filter the list.

**Independent Test**: Seed account-owned Room/remote notices, open PNOT, verify newest-first alert/reminder presentation and all three filters, including the exact empty-state copy.

### Tests for User Story 1

- [X] T011 [P] [US1] Add repository tests for cache-first reads, remote refresh, pending-command merge protection, tombstone exclusion, and user scoping (FR-001, FR-009, FR-010, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepositoryTest.kt`.
- [X] T012 [P] [US1] Add ViewModel tests for newest-first order, Todos/Alertas/Recordatorios filters, empty state, and account switch (FR-001–FR-003, FR-009, SC-004) in `app/src/test/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModelTest.kt`.
- [X] T013 [P] [US1] Add Compose tests for alert/reminder distinction, all filters, structured `event_payload.due_date` plus body fallback, exact empty copy, and local first-render within 2 seconds (FR-001–FR-003, SC-001) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreenTest.kt`; test dashboard bell routing to PNOT and never P19 in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationNavigationTest.kt` (FR-013).

### Implementation for User Story 1

- [X] T014 [US1] Implement local-first repository refresh/upsert while preserving outbox-pending values (FR-001, FR-009, FR-010) in `app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt`.
- [X] T015 [US1] Implement cache observation, refresh, category filters, and error-safe UI state (FR-001–FR-003, FR-009) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModel.kt`.
- [X] T016 [US1] Build the notification center list, category chips, semantic alert/reminder rows, structured expected-date text with body fallback, loading and exact empty state using Kipu tokens (FR-001–FR-003, FR-011, FR-012) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt` and `NotificationDueDate.kt`.
- [X] T017 [US1] Add the PNOT route and authenticated center destination (FR-013) in `app/src/main/java/com/kipu/app/navigation/NotificationsNavigation.kt` and `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`.

**Checkpoint**: PNOT reads the active owner's local snapshot and displays/filter notices without requiring the later mutation or badge story.

---

## Phase 4: User Story 2 — Abrir el objeto relacionado con seguridad (Priority: P1)

**Goal**: Open the exact registered card destination and handle missing or unsupported targets without crashing or leaving PNOT unexpectedly.

**Independent Test**: Resolve an existing owner-scoped card reference to card detail; resolve a missing card or unregistered budget/debt/goal reference to a friendly message while keeping the center visible and notice state unchanged.

### Tests for User Story 2

- [X] T018 [P] [US2] Add destination resolver tests for card route, deleted card, unknown type, unregistered budget/debt/goal route, and case normalization (FR-004, FR-005, SC-003) in `app/src/test/java/com/kipu/app/feature/notifications/domain/NotificationDestinationResolverTest.kt`.
- [X] T019 [P] [US2] Add navigation/screen tests proving unresolved links keep PNOT visible and emit the friendly unavailable message without marking read (FR-005, SC-003) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationDestinationTest.kt` and `app/src/test/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModelTest.kt`.

### Implementation for User Story 2

- [X] T020 [US2] Implement the destination resolver using `CardDao.getById` and registered-route availability for owner-scoped resolution (FR-004, FR-005) in `app/src/main/java/com/kipu/app/feature/notifications/data/RegisteredNotificationDestinationResolver.kt`.
- [X] T021 [US2] Connect notice selection to existing card detail navigation and snackbar fallback without auto-marking unavailable notices read (FR-004, FR-005) in `app/src/main/java/com/kipu/app/navigation/NotificationsNavigation.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt`.

**Checkpoint**: Existing card links navigate safely; absent and future destinations remain in PNOT with a friendly explanation.

---

## Phase 5: User Story 3 — Mantener organizado el centro (Priority: P2)

**Goal**: Mark one/all notices as read, soft-delete notices offline-first, synchronize safely, and show the active owner's unread badge.

**Independent Test**: Read/dismiss offline and verify the Room list/count changes immediately; reconnect and verify the remote row updates without physical deletion; switch accounts and verify isolation.

### Tests for User Story 3

- [X] T022 [P] [US3] Add DAO/repository tests for atomic read/dismiss plus outbox coalescing, read-all active-row scope, badge count, and account isolation (FR-006–FR-010, SC-002, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/NotificationMutationTest.kt`.
- [X] T023 [P] [US3] Add worker tests for verified-owner match, retry behavior, idempotent patches, and clearing only successful outbox rows (FR-009, FR-010, SC-004, SC-005) in `app/src/test/java/com/kipu/app/feature/notifications/data/sync/SyncNotificationsWorkerTest.kt`.
- [X] T024 [P] [US3] Add Compose tests for mark-one/read-all, archive collapse, unread badge semantics/99+, 48dp touch targets, and reduced-motion final state (FR-006–FR-008, FR-011, FR-012, SC-002, SC-005, SC-006) in `app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt`.

### Implementation for User Story 3

- [X] T025 [US3] Implement transactional read/read-all/dismiss mutations and outbox coalescing in `app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt` and `app/src/main/java/com/kipu/app/feature/notifications/data/local/AppNotificationDao.kt` (which also defines `NotificationSyncOutboxDao`) (FR-006, FR-007, FR-009, FR-010, SC-005).
- [X] T026 [US3] Implement per-user WorkManager scheduling and verified-owner retry worker in `app/src/main/java/com/kipu/app/feature/notifications/data/sync/NotificationSyncScheduler.kt` and `app/src/main/java/com/kipu/app/feature/notifications/data/sync/SyncNotificationsWorker.kt` (FR-009, FR-010, SC-005).
- [X] T027 [US3] Add mark-one/read-all/archive actions and user-visible mutation feedback (FR-006, FR-007) to `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationsViewModel.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt`.
- [X] T028 [US3] Add reduced-motion-aware entry/archive transitions and semantic unread badge component (FR-008, FR-011, FR-012) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationMotion.kt` and `app/src/main/java/com/kipu/app/feature/notifications/presentation/UnreadNotificationBadge.kt`.
- [X] T029 [US3] Observe unread Room count from the dashboard and add a 48dp bell badge action routing to PNOT (FR-008, FR-009, FR-013, SC-002, SC-004) in `app/src/main/java/com/kipu/app/navigation/AccountsNavigation.kt` and `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`.

**Checkpoint**: Read, read-all, dismiss, badge, account isolation, and retry synchronization work online and offline.

---

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Validate migration history, accessibility, route safety, tests, and quickstart acceptance.

- [X] T030 [P] Review Kipu color/typography tokens, 48dp targets, contrast, content descriptions, focus behavior, and reduced-motion behavior (FR-011, FR-012, SC-006) in `app/src/main/java/com/kipu/app/feature/notifications/presentation/` and `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`.
- [X] T031 Run `supabase test db --local` and resolve migration/RLS privilege regressions documented in `supabase/tests/database/notifications_center_test.sql`.
- [X] T032 Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and, when a device/emulator is available, `./gradlew.bat :app:connectedDebugAndroidTest`; record actual test results in `specs/010-ep-not-notificaciones-avisos/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Tests can be authored immediately; they define the required boundaries.
- **Foundational (Phase 2)**: Depends on Setup tests and blocks all story implementation.
- **US1 (Phase 3)**: Depends on Foundation. Delivers the independent center/read/filter increment.
- **US2 (Phase 4)**: Depends on Foundation and the US1 list selection/route surface.
- **US3 (Phase 5)**: Depends on Foundation and US1 observation/UI; navigation (US2) is not required for read/dismiss.
- **Polish (Phase 6)**: Depends on desired story phases being complete.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; independent MVP.
- **US2 (P1)**: Starts after Phase 2; integrates with the US1 notification row and center route.
- **US3 (P2)**: Starts after Phase 2; integrates with the US1 Room observations and dashboard; can proceed independently of US2.

### Parallel Opportunities

- T001, T002, T004, T006, and T008 touch separate test/model/storage files and can be authored in parallel.
- T011, T012, and T013 are separate US1 test files and can be authored in parallel.
- T018 and T019 are separate US2 test files and can be authored in parallel.
- T022, T023, and T024 are separate US3 test files and can be authored in parallel.
- After Foundation, US2 and US3 can be developed in parallel if shared navigation/screen files are coordinated; tasks that edit the same files remain sequential.

## Implementation Strategy

1. Complete migration and Room/API/domain foundations.
2. Deliver US1 as the MVP and validate it independently.
3. Add safe deep links (US2) and local-first management/badge sync (US3).
4. Run cross-cutting database, JVM, build, and available Compose tests from Phase 6.

## Notes

- Keep raw historical notification type/reference values unchanged; normalize only at the domain boundary.
- A missing target does not auto-mark a notice read.
- No commit or push is part of this task list; those actions require separate user confirmation.

## Phase 7: Convergence

- [ ] T033 CRITICAL: Execute `./gradlew.bat :app:connectedDebugAndroidTest` on a representative Android emulator/device, resolve any failures, and record the runtime results in `specs/010-ep-not-notificaciones-avisos/quickstart.md` for platform-dependent navigation, badge, accessibility, and reduced-motion behavior per Constitution IX (missing).