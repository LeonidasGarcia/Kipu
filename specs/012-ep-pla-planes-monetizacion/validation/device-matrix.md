# Device Matrix

**Date**: 2026-09-15
**Status**: BLOCKED; no physical Android device is attached and the integrated HU-01/Pantalla 1C host is unavailable.

## Automated Evidence

- The plans instrumented suite passed 30/30 tests on the Pixel 8 API 36 AVD.
- Room tests cover atomic confirmation, rollback, monotonic revisions, operation identity, leases, and account isolation.
- Worker tests cover APPLIED, DUPLICATE, STALE, CONFLICT, authentication waiting, retry, timeout, and lease recovery.
- PostgreSQL tests cover 3-2-1 delivery and immutable operation replay.

Automated tests do not substitute for the real-device timing and scheduler evidence required by SC-005, SC-006, and SC-010.

## SC-005 Confirmation Matrix

| Required Runs | Executed | Passing | Result |
|---------------|----------|---------|--------|
| 40 | 0 | 0 | BLOCKED |

Each run must record device/API, selection, online/offline state, process state, local commit duration, navigation duration, and whether both complete within two seconds.

## SC-006/SC-010 Synchronization Matrix

| Required Runs | Executed | Reconciled Within Target | Result |
|---------------|----------|--------------------------|--------|
| 30 | 0 | 0 | BLOCKED |

The matrix must include stable connectivity, network recovery, 3-2-1 delivery, ambiguous timeout, restart, process death, Doze, battery restriction, force-stop, and reopen. It must distinguish operating-system deferral from an application failure and verify same-user session isolation.

## Blockers

- `adb devices -l` reports no attached device.
- A `Pixel_8` AVD exists, but the plan and constitution require representative real-device validation for Android scheduler behavior.
- T048 remains blocked because production `LoginScreen` and `BiometricConfigScreen` destinations do not exist; therefore the required integrated navigation timing cannot be measured without creating prohibited placeholders.

T052 must remain unchecked until the 40/30 matrices are executed and raw measurements are attached.
