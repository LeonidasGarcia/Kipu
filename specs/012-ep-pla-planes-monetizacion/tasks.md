---

description: "EP-PLA S1/S2 historical tasks, S3/S4/S4.5 increments, and Sprint 5 HU-55 tasks"
---

# Tasks: EP-PLA - Planes, Limites y Monetizacion Freemium

**Propagated**: 2026-10-08 — Added dependency-ordered HU-55 tasks for authenticated RTDN, existing restore, ephemeral tokens, and safe hash-only waiting; preserved S1–S4.5 history and open S4 gates.

**Propagated**: 2026-10-02 — Added dependency-ordered Sprint 4 tasks for HU-58/HU-59 while retaining completed S1/S2/S3 history.

**Propagated**: 2026-09-15 — Updated from spec.md refinement (tokens del design system Stitch adoptados como normativos; T042 actualizada y T056 añadida).

**Propagated**: 2026-09-15 — Updated from spec.md refinement (cleanup sin cambios funcionales: eliminadas las referencias residuales al sistema de diseño previo; ninguna tarea afectada, T042/T056 ya apuntan a `docs/stitch-design-system.md`).

**Propagated**: 2026-09-16 — Updated from spec.md refinement (fidelidad de Pantalla 1B Stitch; tareas correctivas T057-T060 para pruebas, copy/documentación, implementación y revalidación).

**Propagated**: 2026-09-26 — Added dependency-ordered S3 tasks for HU-53/HU-54/HU-56, including pre-migration model/API artifacts and the onboarding Trial versus Play-backed offer boundary; earlier completed S1/S2 tasks remain historical.

**Propagated**: 2026-09-26 — Updated HU-54/UI coverage to distinguish Google Play payment `PENDING`, Billing verification `RETRYABLE`, and HU-52 selection-sync outbox `PENDING`.

**Input**: Design documents from `specs/012-ep-pla-planes-monetizacion/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `.specify/memory/constitution.md`

**Tests**: Tests are required by the specification and Definition of Done. Write each test task before its corresponding implementation and verify that it fails for the expected missing behavior.

**Organization**: S1/S2 tasks remain completed history. S3 setup and story tasks preserve the HU-53/HU-54/HU-56 blockers; S4 and S4.5 remain appended with T112/T113 open. S5 contains only HU-55 as one story, reuses the existing verifier/restore path, and keeps the live-baseline and P30 documentation-alignment gates explicit.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its phase prerequisites because it changes different files and does not depend on an incomplete task in the same parallel group.
- Historical S1 `[US1]` continues to mean HU-52; S2 tasks keep their `[HU-57]` labels.
- **S3-local story labels**: `[US1]` = HU-53, `[US2]` = HU-54, and `[US3]` = HU-56 within the S3 extension below.
- Every checklist item includes the exact file path or paths it changes or validates.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure the existing single-module Android project and establish versioned backend/documentation roots.

- [X] T001 Add Room plugin 2.8.5, WorkManager 2.11.2, AndroidX Hilt Work/compiler 1.4.0, Ktor JSON/mock, coroutines-test, navigation-testing, Hilt testing, Kotlin Test, and Room androidTest aliases in `gradle/libs.versions.toml`
- [X] T002 Apply the Room plugin, configure schema export to `app/schemas`, expose non-secret Supabase URL/publishable-key BuildConfig values, and add all production/test dependencies from T001 in `build.gradle.kts` and `app/build.gradle.kts`
- [X] T003 [P] Initialize local Supabase project settings for migrations, database tests, and the `plans` Edge Function in `supabase/config.toml`
- [X] T004 [P] Record and obtain architecture approval for intent-versus-entitlement separation, monotonic revisions, canonical hashing, and conflict handling in `docs/adr/ADR-012-freemium-intent.md`

**Checkpoint**: Gradle resolves the required libraries, Supabase has a versioned project root, and the ADR is approved before backend implementation.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish application, authentication boundary, network, timestamp, and backup infrastructure required by the story.

**Critical**: Complete this phase before starting US1 implementation or tests.

- [X] T005 Configure `@HiltAndroidApp`, `HiltWorkerFactory`, WorkManager `Configuration.Provider`, and manifest registration in `app/src/main/java/com/kipu/app/KipuApplication.kt` and `app/src/main/AndroidManifest.xml`
- [X] T006 [P] Define the HU-01-owned current-user/session-token contract without implementing a second authentication flow in `app/src/main/java/com/kipu/app/core/network/AuthenticatedSessionProvider.kt`
- [X] T007 Implement Supabase Auth session adaptation plus configured Ktor/Supabase clients using only BuildConfig URL and publishable key in `app/src/main/java/com/kipu/app/core/network/SupabaseNetworkModule.kt` and `app/src/main/java/com/kipu/app/core/network/SupabaseAuthenticatedSessionProvider.kt`
- [X] T008 [P] Implement canonical UUID and lossless `Instant` to epoch-microseconds Room converters in `app/src/main/java/com/kipu/app/core/database/DatabaseConverters.kt`
- [X] T009 [P] Exclude the Room database, WAL/SHM files, outbox, and access cache from cloud backup and device transfer in `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`

**Checkpoint**: Hilt can construct process-restorable workers, network code can obtain only the active HU-01 session, and local sync/access state cannot cross accounts through backup.

---

## Phase 3: User Story 1 - Elegir Free o conocer Premium sin riesgo (Priority: P1)

**Goal**: Let a newly authenticated person inspect the Stitch-faithful Free/Trial presentation, confirm the preselected or chosen Premium option, or continue through the dedicated Free CTA; then continue onboarding after an atomic local commit even offline and synchronize an entitlement-neutral preference without payment, data loss, or cross-account access.

**Independent Test**: With an authenticated test account and a host callback standing in for Pantalla 1C, verify Free as a non-selectable information card with its dedicated CTA, static Trial copy, Annual preselection, Monthly/Lifetime selection, eligible/ineligible/unknown intent mapping, abandonment, restart/recovery, duplicate/stale/conflict/error responses, accessibility, and cross-account RLS. The story passes only when either CTA advances after local confirmation, without network, Billing, charges or Premium grants.

### Tests for User Story 1

- [X] T010 [P] [US1] Write failing domain tests for Free core, all five Free thresholds, Premium-only denial, non-destructive over-limit data, deterministic decisions, forbidden Billing/layer imports, and absence of intent-to-entitlement converters in `app/src/test/java/com/kipu/app/feature/plans/domain/FeatureAccessPolicyTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/PlanFeatureBoundaryTest.kt`
- [X] T011 [P] [US1] Write failing mapping/use-case tests for Free, eligible Monthly/Annual, ineligible/unknown Premium, Lifetime, stable operation identity, and no intent-to-entitlement conversion in `app/src/test/java/com/kipu/app/feature/plans/domain/ConfirmPlanSelectionTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/domain/PlanSelectionMappingTest.kt`
- [X] T012 [P] [US1] Write failing ViewModel tests for initial Free selection, option changes, eligibility copy state, abandonment with zero repository writes, double-submit coalescing, safe errors, and one-shot confirmation after repository success in `app/src/test/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModelTest.kt`
- [X] T013 [P] [US1] Write failing serialization tests for eligibility variants, request/response enums, string BIGINT revisions, RFC3339 microseconds, fixed Free limits, and error retryability in `app/src/test/java/com/kipu/app/feature/plans/data/remote/PlanSelectionDtosTest.kt`
- [X] T014 [P] [US1] Write failing Room tests for atomic preference/revision/outbox commit, rollback, lookup-before-mutation idempotency, local conflict, monotonic revisions, lease recovery, reconciliation, and account isolation in `app/src/androidTest/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDaoTest.kt`
- [X] T015 [P] [US1] Write failing clean-schema and representative-row persistence tests for database v1 without destructive migration in `app/src/androidTest/java/com/kipu/app/feature/plans/data/local/KipuDatabaseSchemaTest.kt`
- [X] T016 [P] [US1] Write failing WorkManager/Ktor-mock tests for network constraints, per-user unique work, APPLIED/DUPLICATE/STALE/CONFLICT, auth waiting, terminal validation/forbidden errors, retry/backoff, ambiguous timeout, and lease recovery in `app/src/androidTest/java/com/kipu/app/feature/plans/data/sync/SyncPlanSelectionWorkerTest.kt`
- [X] T017 [P] [US1] Write failing Compose tests for four options, five limits, conditional Trial wording, exact prices, renewal frequency, cancellation conditions, tabular figures, selection semantics, 48dp targets, 200% font scroll, one CTA, and absence of bars/payment/promotional copy in `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreenTest.kt`
- [X] T018 [P] [US1] Write failing route tests proving lifecycle-aware state collection, exit/back abandonment with no preference/outbox write, and exactly one host callback after local commit including offline confirmation in `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanSelectionRouteTest.kt`
- [X] T019 [P] [US1] Write failing pgTAP tests for APPLIED/DUPLICATE/STALE/CONFLICT classification, 3-2-1 delivery, atomic writes, retention, fixed Free limits, migration preservation of representative existing rows, and safe failure on unexpected schema drift in `supabase/tests/database/plans_selection_test.sql` and `supabase/tests/database/plans_migration_test.sql`
- [X] T020 [P] [US1] Write failing pgTAP tests for forced RLS on all four user-owned tables, cross-user denial through the executor, revoked direct DML/EXECUTE, anonymous denial, and the isolated server-only eligibility writer in `supabase/tests/database/plans_rls_test.sql`
- [X] T021 [P] [US1] Write failing SQL tests for hash normalization, JSON-order independence, operation payload conflicts, and the SHA-256 golden vector in `supabase/tests/database/plans_hash_test.sql`
- [X] T022 [P] [US1] Write failing Edge Function contract tests for `GET /plans/eligibility`, `POST /plans/selection`, JWT/media/method validation, status-specific error bodies, Retry-After, and zero billing/entitlement effects in `supabase/functions/plans/index_test.ts`

### Domain and Local Data for User Story 1

- [X] T023 [P] [US1] Implement entitlement-neutral `PlanSelection` and integer-PEN `CommercialOption` models with Monthly/Annual/Lifetime mapping rules in `app/src/main/java/com/kipu/app/feature/plans/domain/model/PlanSelection.kt` and `app/src/main/java/com/kipu/app/feature/plans/domain/model/CommercialOption.kt`
- [X] T024 [P] [US1] Implement verified/expiring `TrialEligibilitySnapshot` and versioned five-value `FreePlanLimits` models in `app/src/main/java/com/kipu/app/feature/plans/domain/model/TrialEligibilitySnapshot.kt` and `app/src/main/java/com/kipu/app/feature/plans/domain/model/FreePlanLimits.kt`
- [X] T025 [P] [US1] Implement capability classification, separately verified effective entitlement, access request, and access decision types in `app/src/main/java/com/kipu/app/feature/plans/domain/model/Capability.kt`, `app/src/main/java/com/kipu/app/feature/plans/domain/model/EffectiveEntitlement.kt`, `app/src/main/java/com/kipu/app/feature/plans/domain/model/FeatureAccessRequest.kt`, and `app/src/main/java/com/kipu/app/feature/plans/domain/model/FeatureAccessDecision.kt`
- [X] T026 [US1] Implement the pure deterministic policy from `contracts/feature-access-policy.md` without accepting plan preferences or commercial intent in `app/src/main/java/com/kipu/app/feature/plans/domain/FeatureAccessPolicy.kt`
- [X] T027 [US1] Define local-first confirmation, preference observation, eligibility loading, and explicit sync-state contracts plus option-to-intent use-case mapping in `app/src/main/java/com/kipu/app/feature/plans/domain/PlanPreferencesRepository.kt` and `app/src/main/java/com/kipu/app/feature/plans/domain/ConfirmPlanSelection.kt`
- [X] T028 [P] [US1] Implement Room preference and per-user revision-state entities with the constraints from `data-model.md` in `app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesEntity.kt` and `app/src/main/java/com/kipu/app/feature/plans/data/local/PlanSelectionSyncStateEntity.kt`
- [X] T029 [P] [US1] Implement immutable outbox/lease/error state and Free-only access-cache entities without any intent-to-Premium write path in `app/src/main/java/com/kipu/app/feature/plans/data/local/SyncOutboxEntity.kt` and `app/src/main/java/com/kipu/app/feature/plans/data/local/FeatureAccessCacheEntity.kt`
- [X] T030 [US1] Implement `@Transaction` lookup-before-mutation confirmation, monotonic revision allocation, leases, retries, terminal reconciliation, and per-user queries in `app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDao.kt`
- [X] T031 [US1] Register all four entities, converters, DAO, schema export, and explicit non-destructive v1 database configuration in `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`
- [X] T032 [US1] Provide the Room database, DAO, dispatcher/clock dependencies, and database filename through Hilt in `app/src/main/java/com/kipu/app/core/di/CoreModule.kt`

### Remote Contract and Backend for User Story 1

- [X] T033 [P] [US1] Implement kotlinx-serialization DTOs and strict domain mappings matching `contracts/plans-selection.openapi.yaml`, including eligibility and error variants, in `app/src/main/java/com/kipu/app/feature/plans/data/remote/PlanSelectionDtos.kt`
- [X] T034 [US1] Implement authenticated Ktor calls to `/functions/v1/plans/eligibility` and `/functions/v1/plans/selection` with media validation, Retry-After, and safe error mapping in `app/src/main/java/com/kipu/app/feature/plans/data/remote/PlanSelectionApi.kt`
- [X] T035 [P] [US1] Create schemas, all four user-owned tables, constraints/indexes/cascades, dedicated NOLOGIN executor, forced RLS policies, minimum grants, eligibility writer boundary, canonical SHA-256 v1, and atomic selection/eligibility RPCs in `supabase/migrations/20260915000000_create_plan_selection.sql`
- [X] T036 [P] [US1] Implement the `plans` Edge Function router with forwarded user JWT, GET eligibility, POST selection validation, RPC delegation, status-specific safe errors, and no service secret/billing/entitlement access in `supabase/functions/plans/index.ts`

### Synchronization and Repository for User Story 1

- [X] T037 [P] [US1] Implement per-user unique one-shot work, network constraints, periodic safety-net work, and pending-outbox rescheduling APIs in `app/src/main/java/com/kipu/app/feature/plans/data/sync/PlanSyncScheduler.kt`
- [X] T038 [US1] Implement the injected `@HiltWorker` drain loop with same-user session checks, immutable payload retries, leases, backoff, Retry-After, and result reconciliation in `app/src/main/java/com/kipu/app/feature/plans/data/sync/SyncPlanSelectionWorker.kt`
- [X] T039 [US1] Implement atomic local confirmation before scheduling/navigation, conservative eligibility fallback, observations, and no `FeatureAccessCacheEntity` mutation in `app/src/main/java/com/kipu/app/feature/plans/data/OfflineFirstPlanPreferencesRepository.kt`
- [X] T040 [US1] Bind repository, API, policy, scheduler, and worker-facing dependencies without Billing imports in `app/src/main/java/com/kipu/app/feature/plans/di/PlansModule.kt`

### Presentation and Navigation for User Story 1

- [X] T041 [P] [US1] Add approved Free, Trial, renewal, cancellation, Lifetime, eligibility, confirmation, and accessibility strings without promotional/filler/payment language in `app/src/main/res/values/strings.xml`
- [X] T042 [P] [US1] Replace template purple/dynamic styling with the Stitch design system tokens registered in `docs/stitch-design-system.md` (paleta tonal: primary `#0F766E`, pressed `#005C55`, background `#F7F9FB`, surface `#FFFFFF`, onSurface `#191C1E`, radios 16dp/12dp, Inter) in `app/src/main/java/com/kipu/app/ui/theme/Color.kt`, `app/src/main/java/com/kipu/app/ui/theme/Theme.kt`, and `app/src/main/java/com/kipu/app/ui/theme/Type.kt`
- [X] T043 [P] [US1] Implement immutable UI state, option chips, eligibility-aware commercial disclosure, loading/error state, and one-shot confirmation event types in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionUiState.kt`
- [X] T044 [US1] Implement `@HiltViewModel` state reduction, eligibility loading, selection mapping, double-submit protection, and event emission only after local commit in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModel.kt`
- [X] T045 [US1] Implement stateless scrollable `FreePlanCard`, `PremiumTrialCard`, option chips, and `ConfirmPlanButton` with exact prices, five limits, conditional copy, tabular figures, 48dp semantics, and no top/bottom bars in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreen.kt`
- [X] T056 [P] [US1] Adoptar los tokens del design system Stitch (paleta tonal `#0F766E`/`#005C55`/`#006A63`/`#F7F9FB`/`#FFFFFF`/`#191C1E`/`#A8ECE5`/`#216963`, radios 16dp/12dp) e Inter empaquetada en `res/font` en el tema global, re-estilar la Pantalla 1B según el layout Stitch conservando el copy aprobado y los invariantes (5 límites Free, CTA único, sin barras/slogans/filler, orden `MONTHLY`->`ANNUAL`->`LIFETIME`, `tnum`, 48dp), eliminar `docs/DESIGN.md`, crear `docs/stitch-design-system.md`, actualizar `research.md` y verificar compile/tests/lint/grep `DESIGN.md`=0 en `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreen.kt` y `docs/` (depends on T042, T045)
- [X] T046 [US1] Implement lifecycle-aware ViewModel collection and consume each confirmation event once through a host-owned `onConfirmed` callback in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionRoute.kt`
- [X] T047 [US1] Add a reusable plan-selection navigation destination that accepts an explicit host continuation and never owns a `NavController` in the ViewModel/screen in `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`

**Refinement note (2026-09-16)**: T012, T017, T041, T043-T045 and T056 remain completed historical baselines, but their Free preselection, eligibility-conditioned copy, five-row Free content, single CTA, promotional-copy prohibition and Monthly-first assumptions are superseded by FR-002..009, FR-020, FR-023, FR-026/027 and SC-002/004/008. T057-T060 provide the current acceptance evidence and implementation.

### Pantalla 1B Stitch Fidelity Refinement

- [X] T057 [P] [US1] Rewrite ViewModel, Compose screen, and route tests for Annual preselection, three selectable Premium options in `ANNUAL`->`MONTHLY`->`LIFETIME` order, non-selectable Free card, static Trial copy for all eligibility states, six `check_circle` Free rows, approved badges/icons/price suffixes/info/footer, two 48dp CTAs, Free/Premium commit callbacks, `tnum`, no bars, and 200% scroll in `app/src/test/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModelTest.kt`, `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreenTest.kt`, and `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanSelectionRouteTest.kt`
- [X] T058 [P] [US1] Replace the superseded eligibility/prohibition copy with the exact approved Pantalla 1B strings and document its component/icon mapping and product-decision rationale in `app/src/main/res/values/strings.xml`, `docs/stitch-design-system.md`, `specs/012-ep-pla-planes-monetizacion/research.md`, and `specs/012-ep-pla-planes-monetizacion/quickstart.md`
- [X] T059 [US1] Implement the refined screen contract: default `ANNUAL`, keep eligibility only for intent mapping, render Free as informational, render static Trial and Premium cards in Stitch order, add badges/icons/suffixes/literal info/footer, confirm Premium through the primary CTA, and commit `FREE` through the secondary CTA before emitting the shared navigation event in `app/src/main/java/com/kipu/app/feature/plans/domain/model/CommercialOption.kt`, `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionUiState.kt`, `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModel.kt`, `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreen.kt`, and `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionRoute.kt` (depends on T057, T058)
- [X] T060 [US1] Run JVM tests, Compose instrumentation, lint, debug/release builds, `git diff --check`, and visual/accessibility assertions for SC-002/SC-004/SC-008; reconcile stale validation evidence and verify no Billing/entitlement behavior changed in `specs/012-ep-pla-planes-monetizacion/validation/automated-validation.md`, `specs/012-ep-pla-planes-monetizacion/validation/accessibility-and-comprehension.md`, and `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md` (depends on T059)

- [X] T048 [US1] After HU-01 and Pantalla 1C provide real destinations, wire the real post-registration callback to the plan destination and its local-commit callback to Biometria, remove plan selection from the back stack, and reschedule pending work on restored sessions without creating placeholder auth/biometric screens in `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt` and `app/src/main/java/com/kipu/app/MainActivity.kt`

**Checkpoint**: US1 is independently functional with a host callback; integrated onboarding additionally requires the external HU-01/Pantalla 1C dependency identified in T048.

---

## Phase 4: Polish and Cross-Cutting Validation

**Purpose**: Generate durable evidence for migration safety, architecture boundaries, performance, accessibility, security, and release readiness.

- [X] T049 Generate and review the Room v1 schema produced by KSP, then commit the verified artifact in `app/schemas/com.kipu.app.core.database.KipuDatabase/1.json`
- [X] T050 [P] Run and harden the architecture boundary test authored in T010 against the completed source tree, covering Billing imports, domain dependencies, intent conversion, and plans-layer secrets in `app/src/test/java/com/kipu/app/feature/plans/PlanFeatureBoundaryTest.kt`
- [X] T051 Run KSP, debug/release builds, dependency inspection, JVM/instrumented tests, lint, clean and representative-data Supabase migration/drift tests, and all backend tests after T049-T050; record command results and migration evidence in `specs/012-ep-pla-planes-monetizacion/validation/automated-validation.md`
- [X] T052 [P] Execute the 40-confirmation performance matrix and 30-operation synchronization matrix, including restart, 3-2-1 delivery, timeout, Doze, battery restriction, force-stop, and reopen; record SC-005/SC-006/SC-010 evidence in `specs/012-ep-pla-planes-monetizacion/validation/device-matrix.md`
- [X] T053 [P] Validate all eight critical flows with TalkBack and 200% font scale and run the 20-participant commercial-comprehension protocol; record SC-008/SC-009 results in `specs/012-ep-pla-planes-monetizacion/validation/accessibility-and-comprehension.md`
- [X] T054 Audit release artifacts, logs, grants, backup output, Android sources, and Edge Function bundles for fake entitlements, Billing calls, service-role secrets, sensitive data, destructive migrations, and cross-account access; record sign-off in `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md`
- [X] T055 Run every scenario and release command in `specs/012-ep-pla-planes-monetizacion/quickstart.md` and record final cross-review/Sprint Review readiness in `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 - Setup**: Starts immediately. T002 depends on T001; T003 and T004 can proceed in parallel with dependency setup.
- **Phase 2 - Foundational**: Depends on T001-T002. T005, T006, T008, and T009 can proceed in parallel; T007 depends on T006 and the BuildConfig/dependencies from T002.
- **Phase 3 - US1 tests**: Depends on Phase 2. T010-T022 may be authored in parallel and must fail for the expected missing behavior before implementation.
- **Phase 3 - Domain/local**: T023-T025 can proceed in parallel; T026 depends on T024-T025; T027 depends on T023-T024; T028-T029 depend on T023; T030 depends on T028-T029; T031 depends on T008 and T028-T030; T032 depends on T031.
- **Phase 3 - Remote/backend**: T033 depends on T023-T024; T034 depends on T007 and T033. T035 depends on the approved T004 ADR, while T036 depends on T003 and the OpenAPI contract; T035-T036 can otherwise proceed in parallel with Android domain/local work.
- **Phase 3 - Sync/repository**: T037 depends on T005 and the local models; T038 depends on T030, T034, and T037; T039 depends on T027, T030, T034, and T037; T040 depends on T026, T032, and T034-T039.
- **Phase 3 - Presentation**: T041-T043 can proceed in parallel after domain models exist; T044 depends on T027, T039, and T043; T045 depends on T041-T043; T046 depends on T044-T045; T047 depends on T046; T056 depends on T042 and T045 (re-estilado según design system Stitch tras tokens y pantalla).
- **Phase 3 - Stitch fidelity refinement**: T057 and T058 can run in parallel; T059 depends on both; T060 depends on T059. This wave supersedes the affected presentation assumptions of T012, T017, T041, T043-T045 and T056 without invalidating their historical completion evidence.
- **Integrated onboarding gate**: T048 additionally depends on T060 plus external HU-01 and Pantalla 1C production code. Do not satisfy it with fake destinations; all other US1 behavior remains independently testable through the shared host callback.
- **Phase 4 - Polish**: T049-T050 remain complete; T052 and T053 must run after T060 against the refined UI; T051/T054 evidence is refreshed by T060 where affected; T055 runs after T048-T054 and T060 and remains the final release gate.

### User Story Dependency Graph

```text
Setup -> Foundational -> US1 baseline -> T057/T058 -> T059 -> T060 -> Polish
                                          |                         |
                                          +-> test/docs in parallel +-> Independent host-callback demo
                                                                    +-> T048 integrated demo (requires HU-01 + Pantalla 1C)
```

- **US1 (P1)** is the only story and has no dependency on another EP-PLA story.
- **MVP scope** is Setup + Foundational + all US1 tasks except the externally blocked T048, demonstrated with the explicit host callback and local/test Supabase environment.
- **Integrated Sprint 1 delivery** adds T048 only when HU-01 and Pantalla 1C are available.

### Within User Story 1

- Author and run failing tests T010-T022 before their implementation targets.
- Implement pure models/policy before persistence, transport, repository, ViewModel, and UI.
- Commit Room state before scheduling work or emitting navigation.
- Deploy/test the SQL migration before relying on the Edge Function, then validate Android retries against the complete backend.
- Never use preference, local clock, installation state, or outbox state as Premium authority.
- For the 2026-09-16 refinement, author T057 before T059; T057 and T058 may proceed in parallel, then T059 and T060 execute sequentially.

## Parallel Execution Examples

### Domain and Contract Tests

```text
Task T010: FeatureAccessPolicy JVM tests
Task T013: Serialization/DTO JVM tests
Task T019: Selection RPC pgTAP tests
Task T020: RLS/grants pgTAP tests
Task T021: Canonical hash pgTAP tests
Task T022: Edge Function contract tests
```

### Independent Models and Infrastructure

```text
Task T023: PlanSelection and CommercialOption
Task T024: TrialEligibilitySnapshot and FreePlanLimits
Task T025: Capability and access decision types
Task T028: Preference and revision Room entities
Task T029: Outbox and access-cache Room entities
Task T035: Supabase migration/RPC
Task T036: Edge Function
```

### UI Preparation

```text
Task T041: Product/accessibility strings
Task T042: Kipu theme tokens (design system Stitch)
Task T043: UI state and events
```

### Stitch Fidelity Refinement

```text
Task T057: Refined ViewModel/Compose/route tests
Task T058: Approved copy and design/research/quickstart documentation
Task T059: Refined UI state, screen and two-CTA implementation
Task T060: Automated, accessibility and security revalidation
```

## Implementation Strategy

### MVP First

1. Complete Phase 1 and Phase 2.
2. Write T010-T022 and verify expected failures.
3. Complete domain and local persistence through T032.
4. Complete backend, synchronization, repository, and UI through T047.
5. Stop and validate US1 independently with a real authenticated test session and explicit host callback.

### Incremental Delivery

1. Deliver pure domain policy and mapping with JVM evidence.
2. Add atomic Room confirmation and prove offline navigation durability.
3. Add SQL/RLS/RPC plus Edge Function and prove idempotent cross-account-safe synchronization.
4. Add the accessible Stitch-aligned Compose experience.
5. Apply T057-T060 to align that experience with the refined Pantalla 1B contract.
6. Integrate with real HU-01/Pantalla 1C when available, then complete Phase 4 evidence.

### Parallel Team Strategy

1. Complete Gradle/session/Hilt foundations together.
2. After the tests are authored, split work across Android domain/local, Supabase backend, and Compose presentation.
3. Join at repository/worker integration, then run the shared release matrix.

## Notes

- `[P]` tasks operate on distinct files but still require their stated phase/dependency prerequisites.
- No task may introduce a fake LoginScreen, BiometricConfigScreen, entitlement, purchaser, billing state, or server secret to bypass an external dependency.
- `TRIAL_INTENT` and `PREMIUM_INTENT` remain commercial preferences and must never write `feature_access_cache` or authorize Premium.
- Completed outbox rows may be compacted only after accepted-revision persistence; unresolved conflict/error rows remain diagnosable without sensitive data.
- Commit after each task or cohesive task group; do not combine unrelated setup, backend, and UI changes in one commit.

## Sprint 2 extension — HU-57 Cupos, selección y downgrade

These tasks add the S2 HU-57 increment without changing the HU-52 S1 story boundary above.

- [x] T061 [HU-57] Persist per-user/group selection snapshots atomically and rebase stale full snapshots while preserving latest local items in `app/src/main/java/com/kipu/app/feature/plans/data/local/PlanQuotaSelectionDao.kt` and `app/src/androidTest/java/com/kipu/app/feature/plans/data/local/PlanQuotaSelectionDaoTest.kt`.
- [x] T062 [HU-57] Implement owner-checked WorkManager sync, deterministic operation identity, transient retry, and stale/conflict reconciliation in `app/src/main/java/com/kipu/app/feature/plans/data/sync/SyncPlanQuotaWorker.kt` and `app/src/androidTest/java/com/kipu/app/feature/plans/data/sync/SyncPlanQuotaWorkerTest.kt`.
- [x] T063 [HU-57] Implement authenticated Edge route, resource/group validation, full-snapshot revision semantics, owner RLS, and idempotent receipts in `supabase/functions/plans/index.ts`, `supabase/migrations/20260923130000_plan_quota_selections.sql`, and `supabase/migrations/20260923160000_plan_quota_forward_snapshot_revisions.sql`.
- [x] T064 [HU-57] Verify snapshot gaps, delayed stale devices, payload conflicts, Free resource exclusions, owner isolation, and guards on transaction/accounting writes in `supabase/tests/database/plan_quota_selection_test.sql` and `supabase/functions/plans/index_test.ts`.
- [x] T065 [HU-57] Execute the offline/two-device conflict and remote reconciliation acceptance. Verified offline synchronization, snapshot rebase, and remote reconciliation on device suite and connected physical device (SM-S926B).
- [x] T066 [HU-57] Reconcile/deploy the tested migration and Edge Function set to the intended remote environment after the remote schema drift is resolved and explicitly approved. Remote migrations (20260915 to 20260923) and plans Edge Function v2 deployed and verified via MCP Kipu.

## Sprint 3 extension — HU-53 Compra, HU-54 Verificación y HU-56 Ciclo de vida (21 pts)

Las tareas S1/S2 anteriores se conservan como registro histórico. En este bloque, los IDs de historia son locales a Sprint 3: `[US1]` HU-53, `[US2]` HU-54 y `[US3]` HU-56. HU-53 y HU-54 pueden avanzar en paralelo con el contrato y fixtures; la compra integrada solo se acepta cuando ambos flujos pasan juntos. HU-56 requiere la proyección verificada de HU-54.

### Phase S3.1: Setup compartido y persistencia de Billing

- [x] T067 [P] Add the stable Google Play Billing Library 9.1.0 version alias and app dependency without changing existing versions in `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [x] T068 [P] Record approved Play Console product IDs, recurring base-plan IDs, trial-offer IDs, test-account prerequisites, and test-track entry criteria in `specs/012-ep-pla-planes-monetizacion/research.md`
- [x] T069 [P] Reconcile the S3 billing entities and lifecycle fields in `specs/012-ep-pla-planes-monetizacion/data-model.md` and add the authenticated `POST /billing/verify` request/response contract, including normalized outcomes and token privacy, in `specs/012-ep-pla-planes-monetizacion/contracts/verify-purchase.openapi.yaml`
- [x] T070 Add pgTAP coverage for `PRO_LIFETIME`, `REVOKED`, unique purchase-token hash, owner-only purchase reads, denied client writes, and append-only private verification events in `supabase/tests/database/billing_purchase_lifecycle_test.sql` (depends on T069)
- [x] T071 Run `supabase migration new billing_purchase_verification_s3`, then create the additive migration for `public.billing_products`, `public.billing_purchases`, and `internal.billing_events`; include `PRO_LIFETIME`/`REVOKED` constraints, hash uniqueness, indexes, RLS, grants, and server-only write access in the CLI-generated file under `supabase/migrations/` (depends on T069-T070)

### Phase S3.2: User Story 1 — HU-53 Compra mensual, anual y Lifetime (Priority: Alta)

**Goal**: Consultar ofertas y precios localizados en Play, iniciar la compra elegida y mostrar Premium únicamente después de la verificación backend de HU-54.

**Independent acceptance**: Los productos mensual, anual y Lifetime se consultan del catálogo Play; no se presenta un precio de referencia como precio cobrable; el callback por sí solo mantiene el entitlement anterior; Lifetime se verifica como no consumible y sin expiración comercial.

- [x] T072 [P] [US1] Write failing JVM tests for product mapping, pending callback, server-only verification outcome, retry state, and prohibition on callback-only Premium in `app/src/test/java/com/kipu/app/feature/plans/data/billing/PlayBillingGatewayTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModelTest.kt`
- [x] T073 [P] [US1] Define immutable product, localized offer, purchase request, and typed verification-result models without exposing Play SDK types to domain code in `app/src/main/java/com/kipu/app/feature/plans/domain/model/BillingProduct.kt` and `app/src/main/java/com/kipu/app/feature/plans/domain/model/BillingVerificationResult.kt`
- [x] T074 [US1] Implement the lifecycle-safe BillingClient gateway for catalog lookup, localized ProductDetails/OfferDetails, explicit purchase launch, and purchase re-query in `app/src/main/java/com/kipu/app/feature/plans/data/billing/PlayBillingGateway.kt`
- [x] T075 [P] [US1] Implement the authenticated `verify-purchase` request/response adapter for `productId` and ephemeral `purchaseToken`, omitting client-supplied `user_id` and direct purchase-table writes in `app/src/main/java/com/kipu/app/feature/plans/data/remote/VerifyPurchaseApi.kt`
- [x] T076 [US1] Implement the billing repository boundary that joins BillingClient results to `VerifyPurchaseApi`, scrubs tokens from logs, and keeps a purchase token in memory only for verification/re-query in `app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt`
- [x] T077 [US1] Implement immutable paywall state and ViewModel transitions for catalog loading, selected offer, Play handoff, backend verification, verified success, Play-confirmed payment `PENDING`, unavailable, and verification `RETRYABLE`; keep distinct user copy, preserve prior access on `RETRYABLE`, and update access only from verified server results in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseUiState.kt` and `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModel.kt`
- [x] T078 [US1] Implement the S3 Compose purchase surface with Annual preselected, Annual/Monthly/Lifetime cards, localized prices, “Más popular”, “Pago único para siempre”, only Play-returned eligible trial offers and first-charge date, renewal detail, Google Play management link, and explicit unavailable state; keep HU-52 onboarding Trial informational only, using Stitch tokens, 48dp targets, WCAG AA minimum contrast, and AAA contrast where feasible in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt` and `app/src/main/res/values/strings.xml`
- [x] T079 [P] [US1] Implement reduced-motion-aware selected-card spring/scale, reserved-space linear price shimmer, and verified-only success-check motion using the Stitch design tokens in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseMotion.kt`
- [x] T080 [P] [US1] Add Compose and ViewModel tests for localized price rendering, empty catalog, selected-state semantics, 48dp targets, trial date disclosure, management link, distinct copy for Play payment `PENDING` versus verification `RETRYABLE`, preserved access/retry action on `RETRYABLE`, no layout shift, reduced motion, and success only after backend verification in `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreenTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModelTest.kt`
- [x] T081 [US1] Run the internal Play test-track purchase matrix for Monthly, Annual, Lifetime, user-cancelled sheet, and unavailable product; verify the backend result and record provider/device evidence in `specs/012-ep-pla-planes-monetizacion/validation/device-matrix.md` and `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md` (depends on T074-T080 and T088)

### Phase S3.3: User Story 2 — HU-54 Verificación y reconocimiento de compras (Priority: Alta)

**Goal**: Verificar cada token en Google Play desde una Edge Function autenticada, asociarlo a una sola cuenta y reconocer compras idempotentemente sin habilitar estados pendientes.

**Independent acceptance**: Token válido `PURCHASED` queda persistido una sola vez; `PENDING`, token inválido, token ajeno o indisponibilidad no amplían Premium; reconocimiento se reintenta de forma segura y Lifetime nunca se consume.

- [x] T082 [P] [US2] Write failing Edge Function tests for required JWT, method/body validation, Google response mapping, Play payment `PENDING`, distinct transient provider `RETRYABLE`, duplicate verification, foreign-account token, acknowledgement state, and Lifetime non-consumption in `supabase/functions/verify-purchase/index_test.ts`
- [x] T083 [US2] Implement the authenticated `verify-purchase` Edge Function entry point for `POST /billing/verify`, deriving the owner from Supabase Auth and validating package, product, and purchase token before provider access in `supabase/functions/verify-purchase/index.ts`
- [x] T084 [US2] Implement server-side purchase persistence with SHA-256 over the exact token bytes, atomic UNIQUE-hash owner binding, idempotent replay, cross-account conflict rejection, and no raw-token logging in `supabase/functions/verify-purchase/purchase-store.ts`
- [x] T085 [US2] Implement Google Play Developer API adapters for the relevant subscription and one-time product verification responses; normalize product, purchase state, acknowledgement state, validity, and expiry in `supabase/functions/verify-purchase/google-play-api.ts`
- [x] T086 [US2] Persist and grant only a verified `PURCHASED` entitlement, record sanitized `VERIFICATION` events, then acknowledge server-side only if the eligible initial purchase is still unacknowledged; make acknowledgement retries idempotent, never acknowledge `PENDING`, and never consume Lifetime in `supabase/functions/verify-purchase/index.ts` and `supabase/functions/verify-purchase/purchase-store.ts`
- [x] T087 [US2] Complete Edge Function and database boundary tests for concurrent retries, no duplicate purchase/entitlement/acknowledgement, token-owner isolation, service-secret isolation, and sanitized audit records in `supabase/functions/verify-purchase/index_test.ts` and `supabase/tests/database/billing_purchase_lifecycle_test.sql`
- [x] T088 [US2] Validate the S3 verification path against a Google Play license-test account, including a real server lookup and acknowledgement for a purchased test item, and record evidence/limitations in `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md` and `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md` (depends on T074-T080 and T087)

### Phase S3.4: User Story 3 — HU-56 Estados de compra y cancelación (Priority: Alta)

**Goal**: Traducir estado de compra y vigencia verificados a un ciclo de vida Kipu separado del producto contratado y del entitlement efectivo.

**Independent acceptance**: Cancelación conserva acceso hasta `expires_at`; gracia solo conserva acceso cuando Play lo autoriza; hold/expiración/revocación niegan la compra afectada; una compra Lifetime válida prevalece sobre otra suscripción vencida o revocada.

- [x] T089 [P] [US3] Write domain tests for `ACTIVE`, `IN_GRACE_PERIOD`, `ACCOUNT_HOLD`, `CANCELED_ACTIVE`, `EXPIRED`, `REVOKED`, `PENDING`, Lifetime precedence, and non-destructive Free fallback in `app/src/test/java/com/kipu/app/feature/plans/domain/PurchaseLifecycleTest.kt`
- [x] T090 [US3] Implement provider-to-Kipu lifecycle mapping, including cancellation plus unexpired `expires_at`, grace/hold normalization, expiry, revocation, and Lifetime with null commercial expiry in `app/src/main/java/com/kipu/app/feature/plans/domain/model/PurchaseLifecycle.kt`
- [x] T091 [US3] Implement effective-access aggregation over the user’s verified purchases and update the account-scoped access cache only from the verified repository result, preserving Lifetime precedence in `app/src/main/java/com/kipu/app/feature/plans/domain/EffectiveEntitlementPolicy.kt` and `app/src/main/java/com/kipu/app/feature/plans/data/local/FeatureAccessCacheEntity.kt`
- [x] T092 [US3] Surface cancellation (“No se renovará”), verified expiry, grace, hold, revoked, and Lifetime coverage in the existing purchase/status surface without adding the future HU-60 My Plan route in `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseUiState.kt`, `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt`, and `app/src/main/res/values/strings.xml`
- [x] T093 [US3] Add repository and Compose integration coverage for cancellation before/at expiry, grace, account hold, revoked purchase, simultaneous Lifetime, and preservation of financial history in `app/src/test/java/com/kipu/app/feature/plans/domain/PurchaseLifecycleTest.kt` and `app/src/androidTest/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreenTest.kt`

### Phase S3.5: Seguridad y cierre transversal

- [x] T094 [P] Audit Android artifacts, function logs/secrets, grants, RLS, token handling, pending-state gating, and non-consumption of Lifetime; update the S3 findings in `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md`
- [x] T095 Run the combined acceptance and regression checklist for HU-53/HU-54/HU-56, record test-channel limitations and review evidence, and verify all S3 functional requirements/SC IDs map to tasks in `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md`

### S3 Dependencies and Parallel Execution

- **Shared setup**: T067-T069 may run in parallel. T070 verifies the documented S3 schema/contract; T071 migration follows T069-T070.
- **HU-53/HU-54 parallel wave**: T072-T080 can progress against the approved request/response contract and provider fakes while T082-T087 build and verify the backend. Neither Android callbacks nor fixtures may simulate a verified entitlement. T081 waits for T088 and the HU-53 UI/gateway tests.
- **HU-56**: T089-T092 may start domain work after the lifecycle contract is agreed; T093 requires HU-54 verification and HU-53 test wiring.
- **Final gate**: T094 follows backend and Android security checks; T095 follows T081, T087-T088, and T093-T094.

### S3 MVP and Incremental Strategy

The smallest safe integrated slice is one recurring test purchase through HU-53 and the real HU-54 verifier, with a verified-only entitlement and a `PENDING` denial. Annual and Lifetime catalog/purchase behavior, all HU-56 lifecycle outcomes, accessibility/motion states, and the combined test-track matrix are required before marking the 21-point S3 increment complete. No partial slice is a substitute for the acceptance criteria of the three stories.

## Sprint 4 extension — HU-58 Capacidades y HU-59 Concesión offline (16 pts)

### Phase S4.1: Contracts and test vectors

- [X] T096 [S4] Propagate HU-58/HU-59 into the EP-PLA plan, tasks, data model and policy; define the ES256 grant claims and backward-compatible verify-purchase request/response in `specs/012-ep-pla-planes-monetizacion/plan.md`, `data-model.md`, `contracts/feature-access-policy.md`, `contracts/verify-purchase.openapi.yaml` and `contracts/offline-entitlement-grant.md`
- [X] T097 [P] [HU-58] Add failing domain tests for Free core/basic capabilities, limited Free quotas, Premium-only decisions and verified-entitlement-only authorization in `app/src/test/java/com/kipu/app/feature/plans/domain/FeatureAccessPolicyTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/domain/OfflineEntitlementLeasePolicyTest.kt` (FR-048/FR-049; SC-020)
- [X] T098 [P] [HU-59] Add failing grant-verifier and trusted-clock tests for valid ES256 vectors, altered payload/signature, unknown key/policy, owner/install mismatch, missing/changed boot count, elapsed regression, exact 72h boundary, commercial end, Lifetime and wall-clock jumps in `app/src/test/java/com/kipu/app/feature/plans/data/OfflineEntitlementGrantVerifierTest.kt` and `app/src/test/java/com/kipu/app/feature/plans/domain/OfflineEntitlementLeasePolicyTest.kt` (FR-050–FR-053; SC-021–SC-024)
- [X] T099 [P] [HU-59] Add failing Edge Function tests for signing exact payload bytes, 72h/commercial minimum, Lifetime cap, omitted/invalid installation key, pending/rejected/retryable no-grant behavior, authenticated Free clearing behavior and no secret/token logging in `supabase/functions/verify-purchase/offline-entitlement-grant_test.ts` and `index_test.ts` (FR-050/FR-055; SC-021/SC-023)
- [X] T100 [P] [HU-59] Add Room migration/schema tests proving v17 rows and financial/outbox data are preserved, migrated unsigned caches deny Premium, and v18 grant/anchor persistence is atomic in `app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt` (FR-053; SC-025)

### Phase S4.2: Server grant and local data boundary

- [X] T101 [HU-59] Implement the per-install P-256 Android Keystore key provider and DER public-key/thumbprint contract; fail closed when key creation/loading is unavailable in `app/src/main/java/com/kipu/app/feature/plans/data/entitlement/InstallationSigningKeyProvider.kt` (FR-051/FR-053; SC-021/SC-025)
- [X] T102 [HU-59] Implement Edge Function ES256 grant signing with server-only `ENTITLEMENT_GRANT_PRIVATE_JWK` and `ENTITLEMENT_GRANT_KEY_ID`; issue a grant only after persisted provider verification and effective Premium aggregation in `supabase/functions/verify-purchase/offline-entitlement-grant.ts` and `index.ts` (FR-050/FR-055; SC-021–SC-023)
- [X] T103 [HU-59] Extend the verify-purchase client/server DTOs compatibly with optional `installationPublicKey` and nullable signed `offlineEntitlementGrant`; keep old clients able to verify but unable to receive a grant in `app/src/main/java/com/kipu/app/feature/plans/data/remote/VerifyPurchaseApi.kt`, `BillingRepository.kt` and `supabase/functions/verify-purchase/index.ts` (FR-050/FR-055; SC-021/SC-023)
- [X] T104 [HU-59] Add nullable signed-grant and same-boot monotonic-anchor columns to `FeatureAccessCacheEntity`, bump Room 17→18 with a preserving migration, register it, and confirm Room backup/device-transfer exclusions remain active in `app/src/main/java/com/kipu/app/feature/plans/data/local/FeatureAccessCacheEntity.kt`, `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt` and migration registration (FR-053; SC-025)
- [X] T105 [HU-59] Verify ES256 signature and claims against configured public keys, active owner, Keystore thumbprint, policy version and effective Premium tier; atomically store valid grant or clear it after authenticated Free/revoked response in `app/src/main/java/com/kipu/app/feature/plans/data/entitlement/OfflineEntitlementGrantVerifier.kt` and `BillingRepository.kt` (FR-049/FR-051/FR-055; SC-021/SC-025)

### Phase S4.3: Monotonic concession and capability enforcement

- [X] T106 [HU-59] Implement a trusted lease clock from server-signed verification time, `SystemClock.elapsedRealtime()` and `Settings.Global.BOOT_COUNT`; deny Premium at `notAfter`, on reboot/continuity loss, and on expiry while preserving Free in `app/src/main/java/com/kipu/app/feature/plans/data/entitlement/OfflineEntitlementLeaseEvaluator.kt` (FR-052/FR-054; SC-022–SC-024)
- [X] T107 [HU-58/HU-59] Replace the unconditional monotonic-valid flag in `PlansMovementEntitlementProvider`, and connect history queries to the validated lease, advanced-filter gate and basic fallback/revalidation decision in `app/src/main/java/com/kipu/app/feature/movements/data/PlansMovementEntitlementProvider.kt`, `app/src/main/java/com/kipu/app/feature/movements/domain/MovementHistoryAccessPolicy.kt` and `QueryMovementHistory.kt` (FR-048/FR-049/FR-052/FR-054; SC-020/SC-022/SC-024)
- [X] T108 [HU-58/HU-59] Replace wall-clock/unsigned-cache Premium checks in existing instrument and custom-category quota paths with the shared validated lease result; keep over-limit data/history intact and Free selections/reads available in `app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt`, `app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt` and movement quota gates (FR-048/FR-049/FR-054; SC-020/SC-024)
- [X] T109 [HU-59] Add an additive local Supabase migration that retains the legacy `get_feature_access()` response shape but sets `offline_valid_until` to null, so a rolling timestamp cannot be treated as a lease; preserve existing grants, owner checks and RLS in `supabase/migrations/` and a local database regression test (FR-055; SC-025)
- [X] T110 [HU-58/HU-59] Connect denied/revalidation decisions to friendly Premium-required/reconnect UI states, preserve compatible basic filters, and ensure Premium expiry never disables manual local registration, history-basic or outbox in `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt` and the existing Compose history surface (FR-048/FR-054; SC-020/SC-024)

### Phase S4.4: Integration, security and DoD

- [X] T111 [HU-58/HU-59] Add integration coverage for verified purchase→signed grant→Room cache→capability decision, offline before/at/after expiry, clock manipulation, reboot/boot-count change, session owner change, restored cache, network retry, Free fallback and unchanged financial/outbox data in EP-PLA and EP-MOV JVM/instrumentation tests (FR-048–FR-055; SC-020–SC-024)
- [X] T112 [HU-59] Run and validate the additive Room migration and PostgreSQL migration against the local development databases; verify legacy cache denial, no rolling 72h RPC grant, function permissions/RLS, backup exclusion and no destructive financial-data changes; record evidence in `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md` (FR-053/FR-055; SC-025)
- [X] T113 [HU-58/HU-59] Run the S4 domain, Edge Function, Android integration and regression checks; map FR-048–FR-055 and SC-020–SC-025 to evidence, document the production signing-key configuration requirement, and update `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md` (FR-048–FR-055; SC-020–SC-025)

### S4 Dependencies and execution order

- T096 is complete and establishes the claims/schema contracts. T097–T100 are parallel test-first tasks. T101 and T102 may proceed independently after T098–T099; T103 follows both signing contract and key request design. T104 follows T100. T105 follows T101–T104. T106 follows T098/T104/T105. T107/T108 follow T106. T109 may proceed after review of the current function and local migration contract. T110 follows T107. T111 follows T105–T110; T112 follows T104/T109; T113 is the final gate after T111–T112.

### S4 acceptance slice

**Execution status (2026-10-02)**: T096–T111 are implemented. T112 remains open because Room migration instrumentation could not run without an Android device and the local Postgres baseline lacks two pre-existing objects; the additive SQL migration and its pgTAP ACL/lease regression passed locally. T113 remains open until the device and approved signing-key/release checks are completed.

**Follow-up validation (2026-10-08)**: T112 and T113 are complete. On Samsung SM-A165M / Android 16, `MovementRoomMigrationTest` passed 7/7 and `MovementHistoryAccessIntegrationTest` passed 6/6. The additive PostgreSQL migration's 8 pgTAP assertions and transaction-scoped smoke check were already recorded as passing; the Room test preserves financial history/outbox/cache and the backup exclusions remain configured. The complete local migration replay still stops earlier at the pre-existing `20260928110000_credit_card_pull_projection.sql` dependency on missing `public.recurrence_occurrences`; this replay limitation and the missing release signing keys remain separate deployment gates.

The smallest safe slice is a successful authenticated provider verification yielding a server-signed, account- and installation-bound grant, validated locally against monotonic time, and consumed by the Free/Premium capability policy. A fixture, client callback, `effectivePremium` boolean, legacy cache, rolling RPC timestamp or civil clock is never sufficient evidence. If signing keys are not configured, the safe result is Kipu Free with a documented reconnection/release-configuration requirement.

## S4.5: Approved UI/UX refinement (2026-10-03)

**Propagated**: 2026-10-03 — FR-056–FR-058 / SC-026–SC-027; historical tasks stay intact.

- [X] T114 Refine and propagate approved recovery/access UI states and motion contract across EP-PLA/EP-MOV; retain existing signed grant, schema and financial contracts (FR-056–058).
- [X] T115 Implement a bounded restore-and-verify result using the existing provider/server path, including no purchase, pending, network failure and owner changes; add JVM coverage in billing/history tests (FR-056; SC-026; depends on T114).
- [X] T116 Implement typed history recovery feedback, contextual access card, progress/retry/success actions, polite semantics and reduced-motion transitions in `MovementHistoryViewModel.kt` and `MovementAccessCard.kt` (FR-057/058; SC-027; depends on T115).
- [X] T117 Run targeted JVM and compile instrumentation coverage, document visual/release limitations and verify artifact consistency in `validation/automated-validation.md` (SC-026/027; depends on T116).

Dependency DAG: T114 → T115 → T116 → T117. T113 final acceptance additionally waits for T117 and EP-MOV T109; T112 retains Room/device/baseline checks. Implementation of UI tasks does not close release acceptance without runtime evidence.


**Execution evidence (2026-10-03)**: T114–T117 completed for UI/access recovery. Room and access instrumentation now execute on Pixel_10; the original T112/T113 remain open for the complete local PostgreSQL baseline and production signing/release/provider validation. Complete Sprint 4 acceptance also requires EP-MOV T110 (source-provenance baseline gap).

## Sprint 5 — HU-55: Reconciliación y restauración de compras (13 pts)

**Goal**: Process authenticated Google Play RTDN, restore purchases on another installation under the same Kipu account, and safely manage event/reconciliation work. Reuse the S3 verifier, HU-56 lifecycle rules, and the existing S4.5 restore entry point. Do not implement HU-54/HU-56, HU-06 (S9), or the full HU-60 (S10) surface.

### Phase S5.1: Dependency and contract gates

- [ ] T118 [US1] Confirm the HU-54 verifier and HU-56 projection are usable and record the live Supabase catalog/migration/function/Cron baseline before any S5 deployment; coordinate the approved no-token scope against P30 §4.3–4.4 in its owning documentation context and record the alignment gate in `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md` (FR-059–FR-067; SC-028–SC-034; external S3 and live-baseline prerequisites)
- [X] T119 [P] [US1] Finalize the HU-55 event receipt/job model and RTDN processing contract, including stable message identity, normalized outcomes, current-token source, unknown-owner handling, P30 scope note, RLS/grants, and the no-token state machine in `specs/012-ep-pla-planes-monetizacion/data-model.md` and `specs/012-ep-pla-planes-monetizacion/contracts/rtdn-event-processing.md` (FR-059–FR-067; SC-028–SC-034)

### Phase S5.2: Persistence and RTDN receiver

- [X] T120 [P] [US1] Add failing Edge Function and pgTAP coverage for Pub/Sub OIDC claim validation, malformed/unsupported messages, stable-message redelivery, duplicate/out-of-order processing, token privacy, owner isolation, receipt/job RLS, and `WAITING_FOR_TOKEN`/`RETRYABLE` without a provider call or entitlement mutation in `supabase/functions/play-rtdn/index_test.ts` and `supabase/tests/database/billing_rtdn_reconciliation_test.sql` (FR-059–FR-061/FR-064/FR-066; SC-028/SC-029/SC-032–SC-034; depends on T119)
- [X] T121 [US1] Add the versioned additive migration for internal RTDN receipt and reconciliation-job state, unique message identity, per-purchase leases, safe status constraints and minimal server grants; keep raw tokens out, preserve `internal.billing_events`, enable/force RLS, and fail visibly on unexpected schema drift in `supabase/migrations/` and migration regression coverage (FR-061/FR-064/FR-066; SC-029/SC-032–SC-034; depends on T120)
- [X] T122 [US1] Implement the `play-rtdn` Pub/Sub receiver with Google OIDC signature/issuer/email/audience/expiry checks before envelope parsing, package/type validation, safe token hashing, and acknowledgement/retry behavior from the contract in `supabase/functions/play-rtdn/index.ts` (FR-059/FR-060/FR-066; SC-028/SC-033; depends on T120–T121)
- [X] T123 [US1] Reuse one server-only billing verification boundary for RTDN and `verify-purchase`; correlate known token hashes, reject unknown RTDN ownership without creating/transferring a user, and atomically update current provider projection, sanitized `internal.billing_events`, receipt/job result and effective entitlement with idempotent duplicate/out-of-order handling, preserving the existing S3 acknowledgement rules without duplicate acknowledgement in `supabase/functions/verify-purchase/` and shared billing reconciliation code (FR-060/FR-061/FR-065–FR-067; SC-029/SC-031/SC-033; depends on T121–T122)

### Phase S5.3: Restore and no-token reconciliation

- [X] T124 [P] [US1] Add failing restore/recovery coverage for same-account second-device candidates, no candidate with an existing hash-only purchase, token-owner conflict, provider timeout, and account aggregation; prove that no-token cases remain pending/retryable and do not mint, extend, expire, revoke, or clear an entitlement by assumption in `app/src/test/java/com/kipu/app/feature/plans/data/BillingRecoveryTest.kt` and billing repository coverage (FR-062/FR-063/FR-067; SC-030/SC-031/SC-034; depends on T119)
- [X] T125 [US1] Integrate `BillingRepository.restoreAndVerifyAccess()` and `PlayBillingGateway.recoverPurchaseUpdates()` with the existing authenticated `verify-purchase` flow; retain the active Kipu owner, verify every current candidate, report explicit pending/retry/conflict/no-candidate outcomes, and preserve known access only through its verified HU-56/HU-59 validity when no token is available in `app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt` and existing recovery UI (FR-062/FR-063/FR-067; SC-030/SC-031/SC-034; depends on T115 and T124)
- [X] T126 [US1] Add failing job/lease and integration coverage for exclusive per-purchase processing, stale lease recovery, retry backoff, hash-only scheduler sweeps that make zero Google Play calls, no assumed entitlement changes, and resume only after a fresh RTDN/restore token in `supabase/tests/database/billing_rtdn_reconciliation_test.sql` and reconciliation function tests (FR-064–FR-066; SC-032–SC-034; depends on T119–T121)
- [X] T127 [US1] Implement the server-only scheduled reconciliation sweep and per-purchase lease lifecycle; reclaim abandoned leases and classify hash-only work as `WAITING_FOR_TOKEN`/`RETRYABLE`, but query Google Play only inside a current RTDN or authenticated restore request carrying an ephemeral token; keep Cron credentials in Vault and configure cadence operationally in `supabase/functions/reconcile-billing/index.ts` and the versioned Supabase migration/configuration (FR-064–FR-066; SC-032–SC-034; depends on T126)

### Phase S5.4: Acceptance and operational evidence

- [ ] T128 [US1] Run the HU-55 acceptance matrix for valid/invalid Pub/Sub auth, duplicate and out-of-order events, restore/owner conflict, transient failures, no-token waiting/resume, privacy, RLS/grants, and non-production Play/Pub/Sub integration; reconcile migration history and deployed function/scheduler evidence, map FR-059–FR-067 and SC-028–SC-034, and update `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md` and `validation/security-release-audit.md` (depends on T118, T123, T125, and T127)

### S5 dependencies and execution order

- T118 is a release/deployment prerequisite; S5 artifacts and isolated implementation may be prepared while the live baseline is reviewed, but no remote migration or production RTDN enablement precedes it. T119 establishes the state model and source-of-truth contract. T120 is test-first; T121 adds the internal schema; T122 implements the publisher-authenticated receiver; T123 follows after receipt storage and the verifier boundary are ready. T124 is test-first for restore; T125 reuses completed S4.5 T115 behavior. T126 is test-first for leases and the no-token sweep; T127 implements only lease recovery/classification absent a current token. T128 is the final acceptance/evidence gate and waits for the P30 documentation alignment in T118 and all integrated paths.

Dependency DAG: T119 → T120 → T121 → T122 → T123; T119 → T124 → T125; T119–T121 → T126 → T127; T118 + T123 + T125 + T127 → T128.

**S5 acceptance slice**: an authenticated RTDN with a known owner and request-scoped token reaches the shared verifier and applies only current provider state; a same-account restore reuses that verifier; a hash-only missed-event job remains `WAITING_FOR_TOKEN`/`RETRYABLE` with no provider call or entitlement mutation until a new RTDN/restore token arrives. No S5 task edits or replaces the S3 purchase boundary, S4 lease, or financial history.
