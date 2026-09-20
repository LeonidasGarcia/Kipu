---

description: "Implementation tasks for HU-52 plan selection and entitlement-safe freemium foundation"
---

# Tasks: EP-PLA - Planes, Limites y Monetizacion Freemium

**Propagated**: 2026-09-15 — Updated from spec.md refinement (tokens del design system Stitch adoptados como normativos; T042 actualizada y T056 añadida).

**Propagated**: 2026-09-15 — Updated from spec.md refinement (cleanup sin cambios funcionales: eliminadas las referencias residuales al sistema de diseño previo; ninguna tarea afectada, T042/T056 ya apuntan a `docs/stitch-design-system.md`).

**Propagated**: 2026-09-16 — Updated from spec.md refinement (fidelidad de Pantalla 1B Stitch; tareas correctivas T057-T060 para pruebas, copy/documentación, implementación y revalidación).

**Input**: Design documents from `specs/012-ep-pla-planes-monetizacion/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `.specify/memory/constitution.md`

**Tests**: Tests are required by the specification and Definition of Done. Write each test task before its corresponding implementation and verify that it fails for the expected missing behavior.

**Organization**: The specification contains one P1 user story. Setup and foundational work establish shared Android/backend infrastructure; all behavior and tests that deliver HU-52 remain labeled `[US1]`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its phase prerequisites because it changes different files and does not depend on an incomplete task in the same parallel group.
- **[US1]**: User Story 1, "Elegir Free o conocer Premium sin riesgo".
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

- [ ] T048 [US1] After HU-01 and Pantalla 1C provide real destinations, wire the real post-registration callback to the plan destination and its local-commit callback to Biometria, remove plan selection from the back stack, and reschedule pending work on restored sessions without creating placeholder auth/biometric screens in `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt` and `app/src/main/java/com/kipu/app/MainActivity.kt`

**Checkpoint**: US1 is independently functional with a host callback; integrated onboarding additionally requires the external HU-01/Pantalla 1C dependency identified in T048.

---

## Phase 4: Polish and Cross-Cutting Validation

**Purpose**: Generate durable evidence for migration safety, architecture boundaries, performance, accessibility, security, and release readiness.

- [X] T049 Generate and review the Room v1 schema produced by KSP, then commit the verified artifact in `app/schemas/com.kipu.app.core.database.KipuDatabase/1.json`
- [X] T050 [P] Run and harden the architecture boundary test authored in T010 against the completed source tree, covering Billing imports, domain dependencies, intent conversion, and plans-layer secrets in `app/src/test/java/com/kipu/app/feature/plans/PlanFeatureBoundaryTest.kt`
- [X] T051 Run KSP, debug/release builds, dependency inspection, JVM/instrumented tests, lint, clean and representative-data Supabase migration/drift tests, and all backend tests after T049-T050; record command results and migration evidence in `specs/012-ep-pla-planes-monetizacion/validation/automated-validation.md`
- [ ] T052 [P] Execute the 40-confirmation performance matrix and 30-operation synchronization matrix, including restart, 3-2-1 delivery, timeout, Doze, battery restriction, force-stop, and reopen; record SC-005/SC-006/SC-010 evidence in `specs/012-ep-pla-planes-monetizacion/validation/device-matrix.md`
- [ ] T053 [P] Validate all eight critical flows with TalkBack and 200% font scale and run the 20-participant commercial-comprehension protocol; record SC-008/SC-009 results in `specs/012-ep-pla-planes-monetizacion/validation/accessibility-and-comprehension.md`
- [X] T054 Audit release artifacts, logs, grants, backup output, Android sources, and Edge Function bundles for fake entitlements, Billing calls, service-role secrets, sensitive data, destructive migrations, and cross-account access; record sign-off in `specs/012-ep-pla-planes-monetizacion/validation/security-release-audit.md`
- [ ] T055 Run every scenario and release command in `specs/012-ep-pla-planes-monetizacion/quickstart.md` and record final cross-review/Sprint Review readiness in `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md`

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
