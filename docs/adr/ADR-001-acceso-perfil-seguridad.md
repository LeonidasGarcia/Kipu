# ADR-001: Separation of Remote Session, Local Owner, and Local Device Protection

- **Status**: Approved
- **Date**: 2026-09-20
- **Decision owner**: EP-APS / Integrante 9
- **Approval evidence**: EP-APS implementation plan, post-design constitution gate PASS

## Context

EP-APS (HU-01 through HU-05) establishes identity, session management, local device protection, account preferences, and contextual permissions. A native mobile application must handle diverse lifecycle and connectivity scenarios:
1. A valid remote session may expire while a device is offline.
2. An explicit logout must clear active presentation and prevent another user from accessing cached data, but pending outbox rows belonging to the original owner must not be discarded or dispatched under another identity.
3. Local biometric or device-credential unlock must protect sensitive financial screens without substituting remote authentication or creating artificial sessions.
4. Account preferences (currency, month start, theme, balance mask) must follow the account across devices via optimistic revision sync, while device unlock and permissions must remain device-local.
5. `public.profiles` requires a reproducible baseline, RLS enforcement, and idempotent receipt tracking to prevent concurrent write conflicts and data loss.

## Decision

### 1. Three-Axis Session Architecture
We separate state into three independent axes:
- `RemoteSession`: Owned by Supabase Auth (`Absent`, `Valid`, `RefreshRequired`, `ReauthenticationRequired`). Tokens are stored in AES-GCM encrypted storage backed by Android Keystore and never leak to UI or logs.
- `LocalOwner`: Represents the verified account identifier (`auth.users.id`) bound to local cached data in Room. Private data queries in Room strictly require the active `LocalOwner`.
- `LocalLock`: In-memory process state (`DISABLED`, `LOCKED`, `UNLOCKING`, `UNLOCKED`) protecting private UI surfaces behind an opaque cover.

### 2. Monotonic Lock and Background Timeout
When local unlock is enabled:
- Process start always initializes as `LOCKED`.
- Background duration is measured using monotonic time (`SystemClock.elapsedRealtime()`).
- Background intervals $\ge 60,000\text{ ms}$ transition the state to `LOCKED` before private content is drawn.
- Cancellation or failure keeps private content hidden.
- Success unlocks only the current local owner and never refreshes or creates a remote session.

### 3. Baseline Profiles, Optimistic Revisions, and Receipts
- Remote `public.profiles` uses an atomic RPC `update_profile_preferences` with `operation_id`, expected revision, and canonical payload hash.
- Replaying the identical operation returns `DUPLICATE` without a second update.
- Mismatched revisions yield `CONFLICT` requiring user-driven resolution rather than silent last-write-wins overwriting.
- `biometric_enabled` is deprecated and excluded from API contracts; migration verifies 0 active legacy rows before proceeding.

### 4. Zero Secrets in Logs and Backups
- Room database, encrypted session storage, and local security settings are explicitly excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- A centralized `LogRedactor` prevents passwords, tokens, recovery URLs, and raw emails from appearing in diagnostics or crash logs.

## Consequences

- Offline manual core operations remain functional for a previously verified owner even if the remote token has expired.
- Switching accounts guarantees complete data isolation; one user can never observe or transmit another user's pending outbox rows.
- Device compromise or backup extraction cannot harvest credentials, database caches, or unlock settings.
- Concurrent preference updates across multiple devices resolve deterministically with receipt-backed idempotency.
