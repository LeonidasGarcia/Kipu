> **Reconciled**: 2026-09-16 against the refined Pantalla 1B specification.

# Quickstart Validation: EP-PLA / HU-52

**Purpose**: Validate the implemented feature end-to-end without duplicating implementation code.  
**Plan**: [plan.md](plan.md)  
**Data model**: [data-model.md](data-model.md)  
**API contract**: [plans-selection.openapi.yaml](contracts/plans-selection.openapi.yaml)

## Prerequisites

- JDK 17 and the repository Gradle wrapper.
- Android SDK with an emulator and at least one physical device compatible with `minSdk 24`.
- Supabase CLI, Docker and a local/test Supabase project.
- A development user authenticated through HU-01.
- Test configuration containing only the Supabase URL and publishable/anon key. Never use a `service_role` key in Android or committed files.
- Pantalla 1C available as navigation target for the integrated flow.

## 1. Static and Build Validation

From repository root in PowerShell:

```powershell
.\gradlew.bat :app:kspDebugKotlin :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

Expected:

- Hilt, Room and Worker generated sources compile.
- WorkManager, AndroidX Hilt Work and direct Ktor JSON dependencies resolve once.
- No use of `BillingClient` exists under `feature/plans`.
- Room schemas are exported under `app/schemas/`.

## 2. JVM Domain and ViewModel Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kipu.app.feature.plans.*"
```

Expected:

- Free core capabilities return `Allowed(FREE_CAPABILITY)`.
- The fifth-limit boundary for each object type returns `Denied(FREE_LIMIT_REACHED)` only for a new over-limit action.
- Premium-only capabilities return `Denied(PREMIUM_ENTITLEMENT_REQUIRED)`.
- `TRIAL_INTENT` and `PREMIUM_INTENT` never become an entitlement.
- ViewModel starts on Annual, changes the three Premium options deterministically, confirms Free through its dedicated action, coalesces double confirmation and emits navigation only after repository success.

## 3. Room, Worker and Compose Instrumented Tests

Start an emulator or connect a device, then run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.kipu.app.feature.plans"
```

Expected:

- In-memory Room confirms preference, revision and outbox atomically; injected failure rolls all three back.
- Reusing an operation ID with another payload is rejected.
- Process/lease recovery returns unfinished work to `PENDING`.
- Worker maps APPLIED, DUPLICATE, STALE, CONFLICT and documented errors to the correct outbox state.
- Compose tests verify vertical order Free→Trial→Annual→Monthly→Lifetime, six Free rows, static Trial copy, three selectable Premium options, approved badges/prices/savings, two CTA, fiscal footer, and no Top/Bottom Bar or payment action.
- Prices expose tabular figures; interactive nodes expose at least 48dp targets and useful selected-state semantics.
- Navigation callback occurs immediately after local commit, including offline.

## 4. Supabase Migration and Contract Tests

```powershell
supabase start
supabase db reset
supabase test db
supabase functions serve plans
```

Execute contract/integration tests against the local function endpoint:

```text
http://127.0.0.1:54321/functions/v1/plans/selection
```

Expected:

- Clean migration creates public preference and private sync structures.
- RLS is enabled and forced on preference, eligibility, head and receipt tables; user A cannot read or modify user B, including through the executor role, and anonymous access is denied.
- Direct writes cannot bypass the RPC protocol; the dedicated RPC owner has no login, table ownership or `BYPASSRLS`.
- Eligibility reads expose only the caller's current server projection; absent or expired evidence returns `UNKNOWN`.
- The server-only eligibility writer cannot access preference/head/receipt data and rejects an unverified target account.
- Revision 1 returns APPLIED; exact replay returns DUPLICATE.
- Same operation with changed payload returns CONFLICT.
- The canonical hash golden vector from `data-model.md` returns `93914ed97388d65ce6fe38949a17cae841f26654afaa8c168ac6ed85ab797132` regardless of JSON property order.
- An older revision returns STALE; delivery 3,2,1 leaves revision 3 effective.
- Concurrent requests produce one deterministic applied result and no duplicate preference effects.
- Every successful response returns all five Free limits.
- No response or database transition creates/modifies entitlements, purchases, subscriptions or financial records.

Stop local services after validation:

```powershell
supabase stop
```

## 5. Official HU-52 Acceptance Flow

### Free

1. Register/login with the development user.
2. Open Pantalla 1B and verify Annual is selected while the Free card has no selection action.
3. Press “Continuar con Plan Free” while online and repeat while offline.

Expected: no payment method, banking data or Play UI; preference is `FREE`; Pantalla 1C opens after local commit.

### Trial Information

1. Seed a verified, unexpired `ELIGIBLE` server projection for the controlled test account and fetch it through `/plans/eligibility`.
2. Verify the static Trial card, select Monthly and then Annual.
3. Inspect duration, later price, renewal cadence and cancellation information before confirming.

Expected: confirmation writes only `TRIAL_INTENT`; effective access remains Free and Google Play never opens.

### Ineligible or Unknown

1. Repeat with `INELIGIBLE` and `UNKNOWN` snapshots.
2. Inspect Mensual, Anual and Lifetime.

Expected: Trial presentation is unchanged; confirmation writes `PREMIUM_INTENT`; Lifetime remains one-time; effective access remains Free.

### Abandonment

1. Open Pantalla 1B and change the visual option.
2. Leave without pressing “Confirmar Plan”.

Expected: no new preference/outbox row, purchase, Trial, charge or entitlement.

## 6. Stitch and Visual Acceptance

Reference the extracted MCP artifacts:

```text
Project: projects/5775615138851387862 (Kipu V4 Finale)
Design system: assets/a21e2e45f51e490fa03b92fb8cb83c55
Screen: projects/5775615138851387862/screens/b8b4bfdcf384409887e54a975c549797
```

Compare the approved structure and copy. Expected Compose tokens:

- CTA base `#0F766E`, pressed `#005C55`, selection ring `#006A63`, onSurface `#191C1E`.
- Card surface `#FFFFFF`, background `#F7F9FB`, outline `#6E7977`/`#BDC9C6`.
- Inter 400/500/600 empaquetada en `res/font` y aplicada globalmente.
- 16dp cards, 12dp controls, 8dp grid, 48dp touch targets.
- `fontFeatureSettings = "tnum"` on S/ 4.99, S/ 29.99 and S/ 49.99.
- Includes “Gratis”, “Recomendado”, savings, fiscal footer and the dedicated Free CTA; excludes “Confirmar y Pagar”, `KipuTopAppBar` and `KipuBottomBar`.

Validate with font scale 200% and TalkBack: all cards, six Free rows, prices, conditions, selected states, icons and both CTA must remain readable and operable.

## 7. Real Device Offline and Scheduler Validation

With a debug build installed:

```powershell
adb shell am force-stop com.kipu.app
adb shell monkey -p com.kipu.app 1
adb shell cmd deviceidle force-idle
adb shell cmd deviceidle unforce
```

Run the matrix with network loss/recovery, process death, Doze, battery restriction and app reopen.

Expected:

- Local confirmation and Pantalla 1C are never blocked by network.
- Pending outbox survives process death.
- At least 95% of the stable-connectivity matrix reconciles within the 15-minute target. Doze/force-stop may defer work while Android prohibits it, but the first permitted opportunity or app reopen reschedules it.
- A session for another user never consumes the original user's operation.
- Ambiguous timeout retries the identical payload and converges to APPLIED or DUPLICATE.

## 8. Release Gate

Before Sprint Review:

```powershell
.\gradlew.bat :app:check :app:assembleRelease
```

Confirm:

- All official and complementary HU-52 tests pass.
- Cross-review is recorded.
- Migration evidence is attached.
- Production/release contains no fake entitlement, test purchaser, development billing state, server secret or claim of verified Play Billing.
- The operational demo covers Free, informative Premium intent, offline navigation and later synchronization.

## 9. Sprint 5 — HU-55 RTDN, restore, and no-token recovery

These are future acceptance scenarios. Do not apply migrations or enable the push subscription until the live Supabase baseline and deployment gates in the plan are reconciled.

1. **Authenticated RTDN**: Deliver a valid non-production Pub/Sub message with the configured Google OIDC issuer, service-account identity, exact audience, and a purchase token already associated with the test account. Confirm server verification uses the current Google Play result and only the request-scoped token.
2. **Reject invalid delivery**: Repeat with invalid JWT signature/claims, malformed envelope, wrong application package, and unsupported notification type. Confirm there is no purchase, receipt terminalization, acknowledgement, or entitlement mutation; any security log contains only a safe reason code.
3. **Duplicate and order**: Redeliver the same Pub/Sub `messageId` and then deliver a distinct older RTDN event after a newer one. Confirm the duplicate returns the canonical receipt without repeating effects, while the distinct old event rechecks current Play state and cannot roll back the projection.
4. **Restore on another device**: Sign into the same Kipu account on a second test installation, restore a current Play-owned candidate, and verify it through the existing authenticated `verify-purchase` path. Confirm no new purchase is opened and no owner is changed.
5. **Owner conflict**: Restore a token already associated with another Kipu account. Confirm rejection without reassignment, grant issuance, or change to the original owner's purchase.
6. **Retryable provider/database failure**: Fail the current provider or persistence attempt. Confirm `RETRYABLE`, Pub/Sub redelivery or explicit restore retry, no raw-token persistence, and no Free downgrade or access extension.
7. **Lost event / hash only**: Start with a known purchase hash and no RTDN or restore token. Run the scheduled sweep. Confirm `WAITING_FOR_TOKEN`/`RETRYABLE`, no Google Play call, no grant, extension, or revocation based on assumed absence; any previously verified expiry still applies and waiting does not extend access beyond HU-56/HU-59 validity. Then supply a fresh token through RTDN or restore and confirm one idempotent verification.
8. **Privacy and least privilege**: Inspect tables, function logs, retry metadata, receipts, responses, RLS, and grants. Confirm no raw purchase token, OIDC JWT, unfiltered RTDN body, or client billing DML is present or permitted.
9. **Live readiness**: Compare remote objects and migration history with the local migration sequence (remote history currently reported only through 2026-09-30); verify deployed functions and Cron extensions/secrets without applying a remote change in this validation step. Record any P30 §4.3–4.4 wording alignment before release acceptance.
