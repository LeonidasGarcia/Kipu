---
description: "Implementation tasks for EP-APS Acceso, Perfil y Seguridad (HU-01 to HU-05)"
---

# Tasks: EP-APS - Acceso, Perfil y Seguridad

**Created**: 2026-09-20

**Input**: Design documents from `specs/001-ep-aps-acceso-perfil-seguridad/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Tests are required by the specification and Definition of Done (SC-001 through SC-016). Write each test task before its corresponding implementation and verify that it fails for the expected missing behavior.

**Organization**: The specification contains 5 user stories ordered by priority:
- User Story 1 (P1): HU-01 - Registrarse e ingresar de forma privada (`[US1]`)
- User Story 2 (P2): HU-02 - Recuperar acceso y controlar la sesión (`[US2]`)
- User Story 3 (P3): HU-04 - Configurar preferencias y privacidad de montos (`[US3]`)
- User Story 4 (P4): HU-05 - Comprender y controlar permisos opcionales (`[US4]`)
- User Story 5 (P5): HU-03 - Proteger el desbloqueo local con biometría (`[US5]`)

Setup (Phase 1) and Foundational (Phase 2) establish shared Android security/session/database infrastructure. Polish (Phase 8) covers cross-cutting validation and release audits.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its phase prerequisites because it changes different files and does not depend on an incomplete task in the same parallel group.
- **[Story]**: User Story label (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`) required for user story phase tasks only. Setup, Foundational, and Polish phases omit this label.
- Every checklist item includes the exact file path or paths it changes or validates.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure dependencies, version catalog, backend configuration, and architecture decision records for EP-APS.

- [X] T001 Add `postgrest-kt` Supabase library alias and ensure AndroidX Biometric 1.1.0, DataStore 1.2.1, and testing dependencies are configured in `gradle/libs.versions.toml`
- [X] T002 Apply `postgrest-kt` dependency in `app/build.gradle.kts`
- [X] T003 [P] Declare `auth-access` Edge Function with `verify_jwt = false` in `supabase/config.toml`
- [X] T004 [P] Record architecture decisions for session/lock/owner separation, monotonic lock, and `public.profiles` baseline in `docs/adr/ADR-001-acceso-perfil-seguridad.md`

**Checkpoint**: Gradle resolves all required dependencies, Supabase config declares the Edge Function, and the ADR is approved before implementation.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish core logging redaction, encrypted session storage, session coordinator contracts, Room migration 1→2 baseline, and backup rules.

**Critical**: Complete this phase before starting user story implementation or tests.

- [X] T005 [P] Implement `LogRedactor` and secure logger utility ensuring no secrets, passwords, tokens, or raw emails appear in logs in `app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt`
- [X] T006 [P] Implement `KeystoreEncryptedSessionStorage` using Android Keystore AES-GCM for secure session envelope storage in `app/src/main/java/com/kipu/app/core/security/KeystoreEncryptedSessionStorage.kt`
- [X] T007 [P] Define `LocalOwner` marker and `SessionCoordinator` interface separating remote session, local verified owner, and lock state in `app/src/main/java/com/kipu/app/core/session/LocalOwner.kt` and `app/src/main/java/com/kipu/app/core/session/SessionCoordinator.kt`
- [X] T008 Implement Room migration 1→2 creating `user_profiles`, `profile_preference_outbox`, `device_account_settings`, `installation_permission_state`, and `account_source_consent` while preserving all EP-PLA tables in `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- [X] T009 Register Room migration 1→2 and bump database version to 2 in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`
- [X] T010 [P] Exclude encrypted session storage, Room database, and local security preferences from cloud backup and device transfer in `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`

**Checkpoint**: Keystore encryption and redaction are ready, Room database supports version 2 without data loss, and backup rules prevent cross-account leakage.

---

## Phase 3: User Story 1 - Registrarse e ingresar de forma privada (Priority: P1)

**Goal**: Enable account creation and login via email/password, ensuring access only to the authenticated user's own space, neutral error handling, offline connection warnings, progressive rate limiting, and FR-051 existing-account notification.

**Independent Test**: Register a new account requiring email verification; verify registering an existing email returns `409 ACCOUNT_EXISTS` and offers sign-in without creating duplicate accounts; verify invalid credentials return neutral `401 AUTH_REJECTED`; verify first offline login explains network need and preserves form; verify rate limiting under burst requests.

### Tests for User Story 1

- [X] T011 [P] [US1] Write failing unit tests for email format, password policy (8-72 chars, letter, digit), and progressive cooldown in `app/src/test/java/com/kipu/app/feature/auth/domain/AuthValidationTest.kt`
- [X] T012 [P] [US1] Write failing ViewModel tests for login, registration, existing email disclosure (FR-051), password clearing from state, and network-needed state in `app/src/test/java/com/kipu/app/feature/auth/presentation/AuthViewModelTest.kt`
- [X] T013 [P] [US1] Write failing Ktor serialization and API contract tests for register and login endpoints matching OpenAPI spec in `app/src/test/java/com/kipu/app/feature/auth/data/remote/AuthApiTest.kt`
- [X] T014 [P] [US1] Write failing Compose tests for LoginScreen and RegisterScreen covering 48dp targets, error states, and TalkBack semantics in `app/src/androidTest/java/com/kipu/app/feature/auth/presentation/AuthScreensTest.kt`
- [X] T015 [P] [US1] Write failing Deno tests for `auth-access` Edge Function covering register (201, 409, 422), login (200, 401), rate limiting (429 with Retry-After), and secret-free logs in `supabase/functions/auth-access/index_test.ts`
- [X] T016 [P] [US1] Write failing pgTAP tests for registration rate bucket HMAC isolation, window progression, and retention cleanup in `supabase/tests/database/auth_rate_buckets_test.sql`

### Backend and Remote API for User Story 1

- [X] T017 [P] [US1] Create SQL migration for `private.registration_rate_buckets`, bucket consumption function with HMAC, and cleanup policy in `supabase/migrations/20260920170000_create_auth_rate_buckets.sql`
- [X] T018 [P] [US1] Implement `auth-access` Edge Function with register and login endpoints, CAPTCHA verification, HMAC rate limiting, and Supabase Auth delegation in `supabase/functions/auth-access/index.ts`

### Domain and Data for User Story 1

- [X] T019 [P] [US1] Implement domain models `AuthCredentials`, `AuthResult`, `AuthError`, and `CooldownState` in `app/src/main/java/com/kipu/app/feature/auth/domain/model/AuthModels.kt`
- [X] T020 [P] [US1] Implement `PasswordValidator` enforcing 8-72 characters, letter, and digit in `app/src/main/java/com/kipu/app/feature/auth/domain/PasswordValidator.kt`
- [X] T021 [US1] Implement `AuthRepository` interface and `SupabaseAuthRepository` with Ktor `AuthApi` and encrypted session import in `app/src/main/java/com/kipu/app/feature/auth/domain/AuthRepository.kt` and `app/src/main/java/com/kipu/app/feature/auth/data/SupabaseAuthRepository.kt`

### Presentation and Navigation for User Story 1

- [X] T022 [P] [US1] Add auth UI strings for registration, login, neutral errors, password rules, cooldown, and existing-account prompt in `app/src/main/res/values/strings.xml`
- [X] T023 [US1] Implement `AuthUiState`, `AuthViewModel`, `LoginScreen`, `RegisterScreen`, and auth navigation routes in `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthUiState.kt`, `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt`, `app/src/main/java/com/kipu/app/feature/auth/presentation/LoginScreen.kt`, `app/src/main/java/com/kipu/app/feature/auth/presentation/RegisterScreen.kt`, and `app/src/main/java/com/kipu/app/navigation/AuthNavigation.kt`

**Checkpoint**: User Story 1 delivers complete registration and login flows with rate limiting and privacy safeguards.

---

## Phase 4: User Story 2 - Recuperar acceso y controlar la sesión (Priority: P2)

**Goal**: Provide account recovery via temporary single-use link with neutral observable responses, persistent session restoration with owner preservation, and safe sign-out with pending changes warning.

**Independent Test**: Recovery request for existing and non-existing email returns identical neutral 202 response and normalized duration; opening valid reset link allows setting new password once; opening expired/used/altered link fails safely; app reopen maintains same owner session; sign out with pending changes warns user and isolates local data upon confirmation.

### Tests for User Story 2

- [X] T024 [P] [US2] Write failing unit tests for recovery timing normalization, single-use link token handling, and pending changes counting across outboxes in `app/src/test/java/com/kipu/app/feature/auth/domain/RecoveryAndSignOutTest.kt`
- [X] T025 [P] [US2] Write failing ViewModel tests for RecoveryViewModel, ResetPasswordViewModel, and SignOutDialog in `app/src/test/java/com/kipu/app/feature/auth/presentation/RecoveryViewModelTest.kt`
- [X] T026 [P] [US2] Write failing unit tests for deep link validation (`/auth/confirm`, `/auth/recovery`) verifying scheme, host, path, single-use consumption, and replay rejection in `app/src/test/java/com/kipu/app/navigation/AuthDeepLinkHandlerTest.kt`
- [X] T027 [P] [US2] Write failing Deno tests for `POST /functions/v1/auth-access/recovery` checking identical 202 status and normalized response duration for existing vs non-existing emails in `supabase/functions/auth-access/recovery_test.ts`
- [X] T028 [P] [US2] Write failing Android instrumented tests for sign-out warning dialog when pending outbox rows exist and multi-user data isolation in `app/src/androidTest/java/com/kipu/app/feature/auth/presentation/SignOutFlowTest.kt`

### Backend and Remote API for User Story 2

- [X] T029 [US2] Implement recovery endpoint in `supabase/functions/auth-access/index.ts` with normalized timing floor, jitter, and rate limiting

### Domain and Data for User Story 2

- [X] T030 [P] [US2] Implement recovery domain use cases `RequestPasswordRecovery` and `CompletePasswordReset` in `app/src/main/java/com/kipu/app/feature/auth/domain/RecoveryUseCases.kt`
- [X] T031 [P] [US2] Implement `PendingChangesRepository` aggregating pending rows from EP-PLA and EP-APS outboxes in `app/src/main/java/com/kipu/app/core/session/PendingChangesRepository.kt` and `app/src/main/java/com/kipu/app/core/session/PendingChangesRepositoryImpl.kt`
- [X] T032 [US2] Implement session restoration, owner validation, and secure sign-out logic in `app/src/main/java/com/kipu/app/feature/auth/data/SupabaseAuthRepository.kt` and `app/src/main/java/com/kipu/app/core/session/SessionCoordinator.kt`

### Presentation and Navigation for User Story 2

- [X] T033 [P] [US2] Add recovery, reset password, and sign-out pending changes strings in `app/src/main/res/values/strings.xml`
- [X] T034 [P] [US2] Implement `RecoveryUiState`, `RecoveryViewModel`, `RecoveryScreen`, and `ResetPasswordScreen` in `app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryUiState.kt`, `app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryViewModel.kt`, `app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryScreen.kt`, and `app/src/main/java/com/kipu/app/feature/auth/presentation/ResetPasswordScreen.kt`
- [X] T035 [US2] Implement `SignOutDialog` with pending changes count, deep-link intent handling in `app/src/main/java/com/kipu/app/feature/auth/presentation/SignOutDialog.kt`, `app/src/main/java/com/kipu/app/navigation/AuthDeepLinkHandler.kt`, and `app/src/main/AndroidManifest.xml`

**Checkpoint**: User Story 2 delivers access recovery, session continuity, and safe sign-out with pending outbox warnings.

---

## Phase 5: User Story 3 - Configurar preferencias y privacidad de montos (Priority: P3)

**Goal**: Allow user to configure currency, month start (1..28), theme, and balance masking; persist locally first and sync via optimistic revision without modifying financial amounts.

**Independent Test**: Changing preferences persists locally offline; balance mask toggles visual amounts without altering stored values; currency change preserves existing transaction values; month start outside 1..28 is rejected; remote sync increments revision; concurrent edits detect revision mismatch.

### Tests for User Story 3

- [X] T036 [P] [US3] Write failing domain unit tests for `ProfilePreferences`, month_start validation (1..28), and balance masking in `app/src/test/java/com/kipu/app/feature/settings/domain/ProfilePreferencesTest.kt`
- [X] T037 [P] [US3] Write failing ViewModel tests for `SettingsViewModel` verifying local edit, validation feedback, and balance mask toggling in `app/src/test/java/com/kipu/app/feature/settings/presentation/SettingsViewModelTest.kt`
- [X] T038 [P] [US3] Write failing Room tests for `UserProfileCacheDao` and `ProfilePreferenceOutboxDao` atomic commits, conflict detection, and user isolation in `app/src/androidTest/java/com/kipu/app/feature/settings/data/local/ProfileLocalDataTest.kt`
- [X] T039 [P] [US3] Write failing WorkManager tests for `SyncProfilePreferencesWorker` handling SYNCED, CONFLICT, and WAITING_FOR_AUTH in `app/src/androidTest/java/com/kipu/app/feature/settings/data/sync/SyncProfilePreferencesWorkerTest.kt`
- [X] T040 [P] [US3] Write failing pgTAP tests for `public.profiles` RLS, `update_profile_preferences` RPC, receipt idempotency, collision detection, and stale revision rejection in `supabase/tests/database/profile_preferences_test.sql`

### Backend and Remote API for User Story 3

- [X] T041 [P] [US3] Create SQL migration for `public.profiles` baseline, revision column, `private.profile_preference_receipts`, `update_profile_preferences` RPC, and `ensure_profile` RPC in `supabase/migrations/20260920171000_create_profiles_and_preferences.sql`

### Domain and Local Data for User Story 3

- [X] T042 [P] [US3] Implement domain models `UserProfile`, `ProfilePreferenceDelta`, `ThemeMode`, and `SyncState` in `app/src/main/java/com/kipu/app/feature/settings/domain/model/ProfileModels.kt`
- [X] T043 [P] [US3] Implement Room entities `UserProfileCacheEntity` and `ProfilePreferenceOutboxEntity` in `app/src/main/java/com/kipu/app/feature/settings/data/local/UserProfileCacheEntity.kt` and `app/src/main/java/com/kipu/app/feature/settings/data/local/ProfilePreferenceOutboxEntity.kt`
- [X] T044 [US3] Implement Room DAO `ProfilePreferencesDao` with atomic transaction methods in `app/src/main/java/com/kipu/app/feature/settings/data/local/ProfilePreferencesDao.kt`
- [X] T045 [US3] Implement `ProfilePreferencesRepository` interface and `OfflineFirstProfilePreferencesRepository` with PostgREST client in `app/src/main/java/com/kipu/app/feature/settings/domain/ProfilePreferencesRepository.kt` and `app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt`
- [X] T046 [US3] Implement `SyncProfilePreferencesWorker` and `ProfileSyncScheduler` for background outbox drain in `app/src/main/java/com/kipu/app/feature/settings/data/sync/SyncProfilePreferencesWorker.kt` and `app/src/main/java/com/kipu/app/feature/settings/data/sync/ProfileSyncScheduler.kt`

### Presentation and UI for User Story 3

- [X] T047 [US3] Implement `SettingsUiState`, `SettingsViewModel`, `ProfileSettingsScreen`, and `BalanceMaskToggle` in `app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsUiState.kt`, `app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsViewModel.kt`, `app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt`, and `app/src/main/java/com/kipu/app/feature/settings/presentation/BalanceMaskToggle.kt`

**Checkpoint**: User Story 3 delivers offline-first preferences, balance masking, and optimistic revision sync.

---

## Phase 6: User Story 4 - Comprender y controlar permisos opcionales (Priority: P4)

**Goal**: Provide clear contextual explanations and controls for optional permissions (own notifications vs other app notification content), separating Android installation-level grants from account-level consent and capability authorization, ensuring manual core remains available upon rejection or revocation.

**Independent Test**: Before platform permission request, explanation rationale is displayed; rejecting permission keeps manual core operational; Free user attempting capture source sees Premium required notice with zero content processing; revoking permission stops future signal processing immediately; external revocation detected on foreground.

### Tests for User Story 4

- [X] T048 [P] [US4] Write failing unit tests for permission evaluation rules (device grant + account consent + capability + source available) in `app/src/test/java/com/kipu/app/feature/settings/domain/PermissionSourcePolicyTest.kt`
- [X] T049 [P] [US4] Write failing ViewModel tests for `PermissionsViewModel` covering rationale display, consent grant/denial, Free tier restriction, and revocation in `app/src/test/java/com/kipu/app/feature/settings/presentation/PermissionsViewModelTest.kt`
- [X] T050 [P] [US4] Write failing Room tests for `InstallationPermissionStateDao` (installation-wide) and `AccountSourceConsentDao` (per-account) in `app/src/androidTest/java/com/kipu/app/feature/settings/data/local/PermissionDataTest.kt`
- [X] T051 [P] [US4] Write failing Compose tests for `PermissionsScreen` verifying TalkBack announcements, rationale dialogs, and non-color state indicators in `app/src/androidTest/java/com/kipu/app/feature/settings/presentation/PermissionsScreenTest.kt`

### Domain and Data for User Story 4

- [X] T052 [P] [US4] Implement domain models `PermissionSource`, `DeviceAuthorization`, `AccountConsent`, `CapabilityState`, and `ProcessingAuthorization` in `app/src/main/java/com/kipu/app/feature/settings/domain/model/PermissionModels.kt`
- [X] T053 [P] [US4] Implement Room entities `InstallationPermissionStateEntity` and `AccountSourceConsentEntity` and their DAO `PermissionConsentDao` in `app/src/main/java/com/kipu/app/feature/settings/data/local/InstallationPermissionStateEntity.kt`, `app/src/main/java/com/kipu/app/feature/settings/data/local/AccountSourceConsentEntity.kt`, and `app/src/main/java/com/kipu/app/feature/settings/data/local/PermissionConsentDao.kt`
- [X] T054 [US4] Implement `PermissionSourceGateway` and `AndroidPermissionSourceGateway` querying Android NotificationManager and managing platform intents in `app/src/main/java/com/kipu/app/feature/settings/domain/PermissionSourceGateway.kt` and `app/src/main/java/com/kipu/app/feature/settings/data/AndroidPermissionSourceGateway.kt`

### Presentation and UI for User Story 4

- [X] T055 [P] [US4] Add permissions and consent rationale strings in `app/src/main/res/values/strings.xml`
- [X] T056 [US4] Implement `PermissionsUiState`, `PermissionsViewModel`, and `PermissionsScreen` with contextual rationale dialogs in `app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsUiState.kt`, `app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsViewModel.kt`, and `app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsScreen.kt`

**Checkpoint**: User Story 4 delivers permission explanation, consent tracking, and dual-authorization gates without background capture.

---

## Phase 7: User Story 5 - Proteger el desbloqueo local con biometría (Priority: P5)

**Goal**: Enable optional local biometric/device-credential unlocking to protect access to financial balances without replacing remote session, enforcing lock at startup and after 60 continuous seconds in background.

**Independent Test**: Enabling requires successful local authentication prompt; app restart locks UI behind opaque gate; background for 59s does not lock, background >= 60s locks UI; prompt cancellation/failure retains opaque gate and allows retry; devices without biometrics offer device credential or explain limitation; prompt success does not generate or refresh remote session.

### Tests for User Story 5

- [X] T057 [P] [US5] Write failing unit tests for `LocalLockCoordinator` state transitions, monotonic clock 60s timeout, and background lifecycle events in `app/src/test/java/com/kipu/app/core/security/LocalLockTest.kt`
- [X] T058 [P] [US5] Write failing ViewModel tests for `BiometricSettingsViewModel` and `LockScreenViewModel` in `app/src/test/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModelTest.kt`
- [X] T059 [P] [US5] Write failing Room tests for `DeviceAccountSettingsDao` per-account storage and exclusion from backup in `app/src/androidTest/java/com/kipu/app/feature/settings/data/local/DeviceAccountSettingsTest.kt`
- [X] T060 [P] [US5] Write failing Compose tests for `LockScreenOverlay` verifying opaque cover, accessibility semantics hiding balances, and retry actions in `app/src/androidTest/java/com/kipu/app/core/security/LocalLockScreenTest.kt`

### Domain and Data for User Story 5

- [X] T061 [P] [US5] Implement domain models `LocalLockState`, `LockReason`, and `LocalAuthenticatorCapability` in `app/src/main/java/com/kipu/app/core/security/model/LocalProtectionModels.kt`
- [X] T062 [P] [US5] Implement Room entity `DeviceAccountSettingsEntity` and `DeviceAccountSettingsDao` in `app/src/main/java/com/kipu/app/feature/settings/data/local/DeviceAccountSettingsEntity.kt` and `app/src/main/java/com/kipu/app/feature/settings/data/local/DeviceAccountSettingsDao.kt`
- [X] T063 [US5] Implement `LocalAuthenticatorGateway` and `AndroidXBiometricGateway` wrapping AndroidX `BiometricPrompt` and `BiometricManager` in `app/src/main/java/com/kipu/app/core/security/LocalAuthenticatorGateway.kt` and `app/src/main/java/com/kipu/app/core/security/AndroidXBiometricGateway.kt`

### Runtime Lock Gate and UI for User Story 5

- [X] T064 [US5] Implement `LocalLockCoordinator` managing monotonic background timer, lock state, and lifecycle observation in `app/src/main/java/com/kipu/app/core/security/LocalLockCoordinator.kt`
- [X] T065 [US5] Implement `BiometricSettingsUiState`, `BiometricSettingsViewModel`, `BiometricSettingsScreen`, and `LockScreenOverlay` in `app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsUiState.kt`, `app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt`, `app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsScreen.kt`, and `app/src/main/java/com/kipu/app/core/security/LockScreenOverlay.kt`

**Checkpoint**: User Story 5 delivers local biometric/credential protection, 60s background timeout, and opaque privacy cover.

---

## Phase 8: Polish and Cross-Cutting Validation

**Purpose**: Generate durable evidence for migration safety, architecture boundaries, performance, accessibility, security, and release readiness.

- [X] T066 Generate and review Room v2 schema produced by KSP, then commit the verified artifact in `app/schemas/com.kipu.app.core.database.KipuDatabase/2.json`
- [X] T067 [P] Implement and execute architecture boundary test ensuring no financial mutations, no plain-text credentials in logs, and layer separation in `app/src/test/java/com/kipu/app/feature/auth/AuthFeatureBoundaryTest.kt`
- [X] T068 Run full automated validation suite (JVM tests, Room migration tests, pgTAP, Deno, lint, assembleDebug) and document results in `specs/001-ep-aps-acceso-perfil-seguridad/validation/automated-validation.md`
- [X] T069 [P] Execute device validation matrix (biometric prompt, 60s background timer, process kill, offline manual core) and document evidence in `specs/001-ep-aps-acceso-perfil-seguridad/validation/device-matrix.md`
- [X] T070 [P] Execute TalkBack, 200% font scale, and usability/comprehension protocols (SC-002, SC-011, SC-012) and record in `specs/001-ep-aps-acceso-perfil-seguridad/validation/accessibility-and-comprehension.md`
- [X] T071 Audit release artifacts, APK secrets, Supabase advisors, and RLS policies, recording in `specs/001-ep-aps-acceso-perfil-seguridad/validation/security-release-audit.md`
- [X] T072 Execute quickstart validation scenarios and document results in `specs/001-ep-aps-acceso-perfil-seguridad/validation/quickstart-results.md`

---

## Dependencies and Execution Order

### Phase Dependencies

```
Phase 1: Setup ──► Phase 2: Foundational ──► Phase 3: User Story 1 (P1)
                                                    │
                      ┌─────────────────────────────┼─────────────────────────────┐
                      ▼                             ▼                             ▼
         Phase 4: User Story 2 (P2)    Phase 5: User Story 3 (P3)    Phase 6: User Story 4 (P4)
                      │                             │                             │
                      └─────────────────────────────┼─────────────────────────────┘
                                                    ▼
                                       Phase 7: User Story 5 (P5)
                                                    │
                                                    ▼
                                       Phase 8: Polish & Validation
```

- **Phase 1 (Setup)**: Starts immediately. T002 depends on T001; T003 and T004 can proceed in parallel.
- **Phase 2 (Foundational)**: Depends on Phase 1 completion. T009 depends on T008; T005, T006, T007, T010 can proceed in parallel.
- **Phase 3 (User Story 1)**: Depends on Phase 2 completion. Blocking prerequisite for all subsequent user stories.
- **Phase 4 (User Story 2)**: Depends on Phase 3 (US1) completion for authenticated session foundation.
- **Phase 5 (User Story 3)**: Depends on Phase 3 (US1) completion for user identity and authenticated session.
- **Phase 6 (User Story 4)**: Depends on Phase 3 (US1) completion for user account context.
- **Phase 7 (User Story 5)**: Depends on Phase 3 (US1) for session context and Phase 2 (Foundational) for Keystore/security primitives.
- **Phase 8 (Polish)**: Depends on all user story phases completion.

### Critical Path

`T001 -> T002 -> T008 -> T009 -> T011-T016 (Tests) -> T017-T018 (Backend) -> T019-T021 (Domain/Data) -> T023 (UI) -> T036-T040 (US3 Tests) -> T041 (US3 Migration) -> T043-T046 (US3 Sync) -> T066 -> T068`

---

## Parallel Execution Opportunities

### Within Phase 1 (Setup)
- `T003` (Edge Function config) and `T004` (ADR) can execute in parallel with `T001`-`T002` (Gradle/dependencies).

### Within Phase 2 (Foundational)
- `T005` (LogRedactor), `T006` (KeystoreEncryptedSessionStorage), `T007` (LocalOwner/SessionCoordinator), and `T010` (Backup rules) can all execute in parallel before `T008`-`T009` (Room migration).

### Within Phase 3 (User Story 1)
- Tests `T011`, `T012`, `T013`, `T014`, `T015`, `T016` can all be written concurrently across domain, presentation, Ktor, Compose, Deno, and pgTAP.
- Backend tasks `T017` (SQL migration) and `T018` (Edge Function) can run concurrently.
- Domain tasks `T019` (models), `T020` (validator), and `T022` (strings) can run concurrently.

### Within Phase 4 (User Story 2)
- Tests `T024`, `T025`, `T026`, `T027`, `T028` can run concurrently.
- Domain use cases `T030`, pending changes repository `T031`, and strings `T033` can run concurrently.

### Within Phase 5 (User Story 3)
- Tests `T036`, `T037`, `T038`, `T039`, `T040` can run concurrently.
- SQL migration `T041`, models `T042`, Room entities `T043` can run concurrently.

### Within Phase 6 (User Story 4)
- Tests `T048`, `T049`, `T050`, `T051` can run concurrently.
- Domain models `T052`, Room entities `T053`, and strings `T055` can run concurrently.

### Within Phase 7 (User Story 5)
- Tests `T057`, `T058`, `T059`, `T060` can run concurrently.
- Domain models `T061` and Room entity `T062` can run concurrently.

### Within Phase 8 (Polish)
- `T067` (boundary test), `T069` (device matrix), and `T070` (accessibility) can run concurrently.

---

## Implementation Strategy

### MVP Scope (User Story 1)

The minimum viable product delivering immediate, independent value is **User Story 1** (HU-01: Registrarse e ingresar de forma privada):
- Phase 1 (Setup: T001-T004)
- Phase 2 (Foundational: T005-T010)
- Phase 3 (US1: T011-T023)

With US1 complete:
1. Users can register with email/password and see email confirmation notice.
2. Existing email registrations are safely flagged per FR-051.
3. Users can sign in and access only their own isolated space.
4. Offline login explains network need without exposing passwords in logs.
5. Progressive rate limiting prevents brute-force abuse.

### Incremental Delivery Order

1. **Increment 1 (MVP)**: Phase 1 + Phase 2 + Phase 3 (US1) -> Core authentication and session boundary.
2. **Increment 2**: Phase 4 (US2) -> Password recovery, deep linking, and safe sign-out with pending changes warning.
3. **Increment 3**: Phase 5 (US3) -> Offline-first profile preferences, balance masking, and revision-based synchronization.
4. **Increment 4**: Phase 6 (US4) -> Contextual permission explanations, consent tracking, and dual-authorization gates.
5. **Increment 5**: Phase 7 (US5) -> Biometric/device-credential local protection with 60s background timer and opaque screen cover.
6. **Increment 6**: Phase 8 (Polish) -> Room schema commit, boundary tests, pgTAP/Deno validation, TalkBack/200% font audit, security audit, and quickstart sign-off.

## Ajustes visuales de perfil (2026-09-21)

- [X] Reorganizar Ajustes según las cinco secciones de la referencia Stitch y conservar los controles de preferencias existentes.
- [X] Mover Permisos y automatización a Notificaciones y alertas y mantener Biometría en Seguridad y copias de seguridad.
- [X] Mostrar las funciones de otras épicas como pendientes, sin datos ficticios ni acciones destructivas.
- [ ] Verificar visualmente en un dispositivo los temas claro y oscuro, el texto ampliado y los controles de PEN/USD y día 1–28.
