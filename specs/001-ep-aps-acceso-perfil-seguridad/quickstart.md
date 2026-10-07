D# Quickstart Validation: EP-APS

## Prerequisites

- JDK 17 and Android SDK for API 36/37.
- Android emulator plus at least one real device with biometrics/device credential.
- Supabase CLI version discovered with `supabase --version` and command syntax confirmed with `supabase --help`.
- Deno for Edge Function tests.
- Debug values for `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`; never use a secret/service-role key in Android.
- A verified release App Link domain and matching Supabase Auth redirect before release evidence.

## 1. Static and Unit Validation

From repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: validation, session-owner, cooldown, local-lock, preference, redaction and repository tests pass; the APK contains no server-side secret.

## 2. Local Supabase Validation

Confirm commands against the installed CLI, then reset the disposable local database and run database tests:

```powershell
supabase --help
supabase start
supabase db reset
supabase test db
```

Expected:

- the migration history reconstructs `public.profiles` from an empty database;
- RLS is enabled and forced;
- `anon` cannot read/write profiles or execute privileged helpers;
- authenticated user A cannot read/update user B;
- profile RPC accepts a matching revision once, replays the identical `operation_id` without a second write, rejects operation-ID payload collisions and rejects a stale revision without changing data;
- the Auth trigger, migration backfill and `ensure_profile()` create at most one default profile and repair a missing row idempotently;
- RPC owners are `NOLOGIN`, non-superuser and non-`BYPASSRLS`; `PUBLIC`, `anon` and `authenticated` have exactly the documented table/function privileges;
- concurrent identical profile operations produce one update and one replay; concurrent stale/different operations do not overwrite silently;
- biometric state is absent from the API contract, and migration aborts rather than silently proceeding if any legacy `biometric_enabled=true` row exists.

Run Supabase security/performance advisors before and after the migration. EP-APS must add no advisory and must remove profile/function findings it owns; unrelated baseline findings remain explicitly tracked release risks.

## 3. Edge Function Contract

Run the function tests using the repository's Deno configuration:

```powershell
deno test supabase/functions/auth-access/
```

Expected matrix:

| Case | Result |
|------|--------|
| New valid email | `201 ACCEPTED`; confirmation is required and no session is returned. |
| Existing email | `409 ACCOUNT_EXISTS`; offers sign-in only in registration UI. |
| Invalid policy | `422 INVALID_INPUT`; no Auth call. |
| Valid login | sensitive session envelope is returned only to the Auth adapter and persisted encrypted. |
| Invalid login | neutral `401 AUTH_REJECTED`. |
| Existing/absent recovery email | identical `202 ACCEPTED` body and normalized response window. |
| Repeated register/login/recovery origin | finite progressive `429 RATE_LIMITED` with `Retry-After`. |
| Provider interruption | `503`; UI does not claim account creation. |

Inspect captured logs: no password, email, token, CAPTCHA proof, recovery URL or raw IP may appear.

## 4. Instrumented Android Validation

With an emulator/device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected automated evidence:

- Room migrates 1→2 with all five EP-APS tables without losing EP-PLA preferences or outbox rows;
- two local owners see only their own profile/settings/outboxes;
- Android permission state persists once per installation while source consent remains distinct for both owners;
- auth forms preserve email but never password after recoverable errors;
- profile edits persist locally offline and enter the correct owner outbox;
- sign-out warning appears when either EP-PLA or EP-APS has pending changes;
- cancel preserves state, while confirm hides it and marks work waiting for the same owner;
- Compose semantics expose labels/errors/states and remain usable at 200% text scale.

## 5. Provider and Real-Device Matrix

Use two Supabase test accounts and never production financial data.

### Authentication and Session

1. Register a new account and complete email confirmation through the approved callback.
2. Verify existing-account registration disclosure, then verify login/recovery remain neutral.
3. Request recovery for existing and absent emails; compare visible copy and timing tolerance.
4. Open valid, used, expired and altered reset links. Only the first valid use updates the password.
5. Reopen a valid session, force token refresh and verify owner remains unchanged.
6. Disable network after prior authentication and confirm permitted local operations continue while remote actions report network need.
7. Sign out with pending work, then sign in as account B; account A rows must remain invisible and unsent.

### Biometric and Privacy

1. Enable protection only after a successful system prompt.
2. Restart the process: private content must never flash before the lock.
3. Background for 59 seconds: no timeout lock. Repeat for at least 60 continuous seconds: lock before drawing private content.
4. Cancel and fail prompts: remain locked; retry succeeds for the same owner.
5. Change enrolled authenticators and verify capability is reevaluated.
6. Inspect app switcher/screenshots and accessibility semantics while locked; no balance/private value may appear.

### Preferences and Permissions

1. Change each account preference, reopen, then authenticate the same account on a second device and verify sync.
2. Confirm currency preference changes no transaction amount/currency.
3. Reject invalid month day and preserve the prior value.
4. Grant own-notification permission as account A, switch to account B and verify Android grant remains installation-wide while consent copy/state remains account-specific.
5. Reject/revoke own-notification permission and confirm manual core remains available for both accounts.
6. Confirm other-app content remains unavailable and processes zero payloads in HU-05, regardless of UI state or Free/Premium fixture.

## 6. Measurable Outcome Protocols

### SC-002 and timing privacy

Run 10 matched pairs (20 requests) between existing and absent accounts in randomized order from the same staging network profile. Before each pair, reset only the staging rate-bucket fixture through direct test-database setup so both requests begin at the same tier; no reset endpoint exists in release. Record status, public body and client-observed duration. Within every pair, bodies/statuses must match; p95 duration difference between both groups must be at most 250 ms after the server's configured response-floor/jitter policy. Preserve only pseudonymous case IDs and timings.

### SC-011 usability

Recruit 20 representative participants who did not implement the feature. Randomly assign one of three scripts: registration, recovery, or privacy setting. Score one point each for: completion without intervention, correctly naming the protected information, and correctly naming a limit/optional behavior of the control. Success requires 3/3 on the first attempt; at least 18 of 20 must succeed. Record consent, script version, completion, errors and the three binary rubric items without real credentials/financial data.

### SC-013 local latency

Use one API-24 class low-resource physical device and one current API-36 representative device. After warm-up, execute 20 measured operations per device across balance mask, local preference save and lock presentation, using monotonic instrumentation timestamps from input dispatch to first committed frame. At least 38 of 40 measurements must be below 1,000 ms; attach raw anonymized measurements and device/build identifiers.

### SC-014 offline matrix

Exercise every row in the [offline command matrix](./contracts/auth-session-contract.md#offline-command-matrix) with a previously authenticated owner and airplane mode. Verify permitted local actions persist atomically under that owner, remote-only actions report network need, and zero queued operation is sent after switching to another account.

### SC-015 progressive throttling

Use isolated staging buckets for register, login and recovery. For each operation, trigger consecutive rejected/repeated attempts and assert server `Retry-After` follows 1, 2, 4, 8, 16, 32, then 60 seconds and remains capped at 60. At every tier, login remains neutral and recovery preserves its neutral public body. Verify a successful login resets its bucket; separately advance the staging clock beyond 15 minutes and verify reset without success. After the maximum tier, wait the declared finite interval and confirm legitimate access is possible. Repeat from another installation against the same server origin to prove Android cooldown is not the sole enforcement boundary.

## 7. Acceptance Evidence

Record results against FR-001..FR-051 and SC-001..SC-016. Required attachments include:

- test reports and Room schema diff;
- pgTAP and Edge Function output;
- two-user RLS matrix;
- redacted logs inspection;
- real-device biometric/background evidence;
- TalkBack/focus/text-scale evidence;
- advisor results with preexisting findings separated from EP-APS regressions.

Do not mark HU-45 capture, Premium authorization, HU-06 deletion/export or verified App Link compatibility complete without their own implementation and provider evidence.
