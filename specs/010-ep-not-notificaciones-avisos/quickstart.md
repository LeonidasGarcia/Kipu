# Quickstart: EP-NOT — Centro de Notificaciones y Avisos

## Prerequisites

- JDK 17 and Android SDK from the repository toolchain.
- Gradle wrapper available in the repository.
- For database tests: Docker plus a running local Supabase stack initialized from the migrations.
- For connected UI tests: an Android emulator or device.

## Validate the Android app

From PowerShell at the repository root:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

If a test emulator/device is available:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest
```

Expected: mapping, repository/outbox, Room migration, ViewModel, navigation fallback, badge, filters, empty state, and archive UI tests pass. The screen opens from the dashboard bell; card notice opens its existing card detail; an unavailable target leaves PNOT visible with a snackbar.

## Validate Supabase

Start the local Supabase stack, then run:

```powershell
supabase test db --local
```

Expected: the notification migration applies cleanly to the existing baseline, authenticated owner can SELECT and PATCH `is_read` / `deleted_at`, cross-account SELECT/PATCH affects no rows, anonymous clients have no table access, and authenticated clients cannot insert, delete, or update producer-owned columns.

## Manual acceptance walkthrough

1. Seed an unread `CREDIT_UTILIZATION_THRESHOLD_CROSSED` notice with a `CARD` reference and a `BILLING_DUE` notice with `event_payload.due_date` (`YYYY-MM-DD`) or a producer-supplied expected date in `body` for the signed-in test user.
2. Open the dashboard bell and verify the badge, newest-first list, alert/reminder distinction, and producer-supplied expected date labeled as a future reminder (not as payment confirmation).
3. Filter `Todos`, `Alertas`, and `Recordatorios`; mark one and all as read; verify badge counts update.
4. Dismiss a notice; verify it disappears locally and remains remotely with `deleted_at` set.
5. Disable network, mark/dismiss another notice, restore network, and verify the per-user outbox converges.
6. Tap an existing card reference and verify card detail. Tap a deleted or unsupported reference and verify a friendly message while PNOT remains visible.
7. Switch test accounts and verify no notice/count from the prior account appears.
8. Enable reduced motion and confirm all content/actions remain usable with decorative transitions removed.

The canonical acceptance scenarios are in [spec.md](spec.md); storage and REST details are in [data-model.md](data-model.md) and [contracts/notifications-rest.md](contracts/notifications-rest.md).

## Verification completed for this implementation

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`: passed; all 272 JVM tests passed and the debug APK assembled.
- `./gradlew.bat :app:compileDebugAndroidTestKotlin`: passed; Compose/Room instrumentation tests compile.
- `./gradlew.bat :app:connectedDebugAndroidTest`: not run because `adb devices` reported no attached emulator or device.
- `supabase test db --local`: passed; all 21 pgTAP files and 372 assertions passed. The pending notification migration was applied forward to the local Supabase database before rerunning the suite; no database reset was used.
