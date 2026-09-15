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
- ViewModel starts on Free, changes chips deterministically, coalesces double confirmation and emits navigation only after repository success.

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
- Compose tests verify vertical scroll, four options, five Free limits, single confirmation CTA, no Top/Bottom Bar, no promotional/filler text and no payment action.
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
2. Open Pantalla 1B and keep Kipu Free selected.
3. Confirm while online and repeat while offline.

Expected: no payment method, banking data or Play UI; preference is `FREE`; Pantalla 1C opens after local commit.

### Trial Information

1. Seed a verified, unexpired `ELIGIBLE` server projection for the controlled test account and fetch it through `/plans/eligibility`.
2. Select Mensual and then Anual.
3. Inspect duration, later price, renewal cadence and cancellation information before confirming.

Expected: confirmation writes only `TRIAL_INTENT`; effective access remains Free and Google Play never opens.

### Ineligible or Unknown

1. Repeat with `INELIGIBLE` and `UNKNOWN` snapshots.
2. Inspect Mensual, Anual and Lifetime.

Expected: no Trial promise; Lifetime is one-time and has no Trial; confirmation writes `PREMIUM_INTENT`; access remains Free.

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

Compare structure, not prohibited copy. Expected Compose tokens:

- Primary `#0F766E`, Primary Dark `#115E59`, Ink `#0F172A`.
- Surface `#FFFFFF`, Background `#F8FAFC`.
- 16dp cards, 12dp controls, 8dp grid, 48dp touch targets.
- `fontFeatureSettings = "tnum"` on S/ 4.99, S/ 29.99 and S/ 49.99.
- No “Gratis”, “Recomendado”, savings slogans, fiscal filler, “Confirmar y Pagar”, `KipuTopAppBar` or `KipuBottomBar`.

Validate with font scale 200% and TalkBack: all options, prices, conditions, selected states and CTA must remain readable and operable.

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
