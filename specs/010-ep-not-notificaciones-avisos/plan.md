# Implementation Plan: EP-NOT — Centro de Notificaciones y Avisos

**Branch**: `010-ep-not-notificaciones-avisos` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

## Summary

Implement HU-42 as a local-first financial notification center. Supabase remains the owner-scoped source for server-created notices; a Room projection provides immediate reads and offline actions, and a per-user WorkManager outbox retries idempotent read/soft-delete patches. Compose exposes PNOT from the dashboard bell, with a resilient destination resolver and accessible filtering, empty, read, and dismissed states.

## Technical Context

**Language/Version**: Kotlin, JDK 17, Android minSdk 24 / targetSdk 36.
**Primary Dependencies**: Jetpack Compose Material 3, AndroidX Navigation, Room, Hilt, Kotlin Coroutines/Flow, WorkManager, Ktor, existing Supabase Auth/PostgREST client. No new library required.
**Storage**: Supabase Postgres `public.app_notifications`; Room `app_notifications` projection and `notification_sync_outbox` (schema version 12).
**Testing**: JUnit/Kotlin test, MockK, Room testing, Compose UI tests, and Supabase pgTAP via `supabase test db --local` when local Supabase is available.
**Target Platform**: Android.
**Project Type**: Existing Android mobile application with Supabase backend.
**Performance Goals**: Display the latest Room snapshot within the existing dashboard navigation and within the specified 2-second center-load criterion; badge and filters derive from Room Flow.
**Constraints**: Offline reads and management; all Room access filtered by active `user_id`; RLS remains authoritative remotely; retries are idempotent; physical remote deletion is prohibited; legacy notification types remain readable; no navigation to unregistered destinations.
**Scale/Scope**: One account-scoped list, five canonical notice types plus the historical credit-threshold alias, individual and bulk read, soft-delete, unread badge, and one existing deep-link destination (card detail).

## Constitution Check

*Gate before research — PASS.*

- **Financial Integrity First**: Notices are descriptive only; they do not create or change ledger entries, balances, budgets, or payment state.
- **Local-First, Retry-Safe Operation**: Cache-first observation and durable per-notice desired-state commands make read/soft-delete retry-safe. Remote refresh must not overwrite a pending local command.
- **Security and Privacy by Design**: Room queries include the active user ID; Supabase RLS isolates `auth.uid() = user_id`; PostgREST grants only SELECT and column-level UPDATE for `is_read` and `deleted_at`; clients cannot insert or physically delete notices.
- **Financial Lifecycles Preserve History**: Dismissal sets `deleted_at`; it never removes the remote row.
- **Quality Is Part of Correctness**: Versioned Room and Supabase migrations, RLS/privilege tests, offline/retry tests, route fallback tests, and accessibility coverage are required.
- **Protect the Approved Product Boundary**: PNOT remains separate from P19 import review; this change adds no payment or external financial action.

No constitutional exceptions are required.

## Research Decisions

See [research.md](research.md) for source evidence and alternatives. Key decisions:

1. Extend the existing `app_notifications` table, not a second notification store. Its owner RLS policy and credit-event uniqueness fields already exist; add nullable `deleted_at` and the minimum update privilege in a forward-only migration.
2. Preserve legacy `CREDIT_UTILIZATION_THRESHOLD_CROSSED` and uppercase `CARD` records. Normalize aliases at the domain boundary; do not rewrite historical rows or constrain the server enum in a way that breaks producers.
3. Use Room as the immediate presentation source. Pull remote rows on center entry; retain pending outbox state during refresh; schedule per-user retry work after mutations.
4. Only `BILLING_DUE` is currently classified as a reminder. Existing card detail is routable. Budget, debt, and goal screens are not registered, so show a non-blocking unavailable message and remain in PNOT.
5. Use an explicit per-row archive action with collapse/fade motion. This satisfies the prototype's quick-action alternative without requiring gesture-only interaction.
6. Apply Kipu design tokens as authoritative. UI Pro Max style-search results did not match the product palette; use semantic Kipu MaterialTheme colors and Inter already bundled in the app. Adapt transition timing guidance to Compose APIs and system reduced-motion settings.

## Architecture

### Data flow

1. `NotificationsViewModel` observes an account-scoped Room Flow and requests a remote refresh when PNOT opens.
2. `NotificationsRepository` maps DTOs to domain models, normalizes type/reference aliases, and upserts the remote snapshot. Rows with pending local outbox commands are excluded from remote overwrite.
3. Mark-read, mark-all-read, and dismiss run in Room transactions: update projection plus upsert a durable desired-state outbox record. The UI reflects the change immediately.
4. A unique WorkManager job per `user_id` checks `SessionCoordinator.currentOwner` before work, PATCHes pending rows through authenticated PostgREST, and clears successful commands. Network/auth failures retry; a different active owner never processes the queued user's work.
5. Dashboard badge count is a Room Flow query for the active user where `is_read = false AND deleted_at IS NULL`. Center filters derive from the same scoped list.

### Remote database and API

- New timestamped migration alters `public.app_notifications` to add `deleted_at timestamptz` and an index supporting active unread/created-time queries.
- Preserve existing ENABLE/FORCE RLS and `app_notifications_own`; test owner access and cross-user denial.
- Keep SELECT for `authenticated`; grant UPDATE only on `is_read` and `deleted_at`; do not grant INSERT or DELETE.
- GET the account's notifications ordered by `created_at DESC`, including `deleted_at` so Room can learn tombstones; screen and badge exclude tombstones. PATCH filters by both `id` and `user_id`; RLS is still the security boundary.
- The REST DTO includes existing `event_key` only if needed for decoding compatibility, while client mutations never touch producer-owned event fields.

### Android modules

- `feature/notifications/domain/model`: notification model, category, destination and action result.
- `feature/notifications/domain`: repository contract and observe/refresh/read/read-all/dismiss use cases.
- `feature/notifications/data/local`: Room entity, DAO and outbox entity/DAO.
- `feature/notifications/data/remote`: PostgREST API and serialization DTOs.
- `feature/notifications/data`: offline-first repository and DTO/entity/domain mappers.
- `feature/notifications/data/sync`: account-keyed WorkManager scheduler and worker.
- `feature/notifications/di`: Hilt bindings.
- `feature/notifications/presentation`: Hilt ViewModel, UI state, center screen, row/filter/empty components and reduced-motion helper.
- `navigation`: PNOT route, dashboard bell callback and destination routing. Card links use the existing `accounts/instrument/{instrumentId}?isCard=true` destination; unsupported/unavailable links return a friendly event without changing read state.
- `core/database`: Room schema v12, migration 11→12, database registration and DAO providers.
- `supabase/migrations` and `supabase/tests/database`: nullable soft-delete field, index, least-privilege grant, and RLS/privilege regression tests.

### Interaction and visual behavior

- Background `#F7F9FB`; white notice cards; unread state uses subtle tonal surface and a teal `#0F766E` dot plus semantic text/state, never color alone.
- Kipu theme Inter typography, 48dp minimum touch areas, WCAG AA contrast, all filter chips `Todos`, `Alertas`, `Recordatorios`, empty copy exactly as specified, and a `Marcar todo como leído` action.
- `app_notifications` has no dedicated due-date column; its pre-existing `event_payload` JSONB carries `due_date` (ISO `YYYY-MM-DD`) when supplied. Persist and render that value as an expected future date, falling back to the producer-supplied `body`; never infer a due date from `created_at` or claim the reminder was paid.
- Use Compose animated visibility/size for route entrance, badge changes, and archive collapse. Read reduced-motion preference and render the same final state with no decorative animation when enabled.
- Accessibility labels expose notice category/read state; badge semantics communicate count (including 99+) without moving focus or creating competing live regions.

## Project Structure

### Feature documentation

```text
specs/010-ep-not-notificaciones-avisos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/notifications-rest.md
└── tasks.md
```

### Source code

```text
app/src/main/java/com/kipu/app/
├── core/database/{KipuDatabase.kt,RoomMigrations.kt}
├── core/di/CoreModule.kt
├── feature/notifications/
│   ├── data/{OfflineFirstNotificationsRepository.kt}
│   ├── data/local/{AppNotificationDao.kt,AppNotificationEntity.kt,NotificationSyncOutboxEntity.kt}
│   ├── data/remote/{NotificationsApi.kt,NotificationDtos.kt}
│   ├── data/sync/{NotificationSyncScheduler.kt,SyncNotificationsWorker.kt}
│   ├── di/NotificationsModule.kt
│   ├── domain/{NotificationsRepository.kt,usecase/*}
│   ├── domain/model/Notification.kt
│   └── presentation/{NotificationsViewModel.kt,NotificationCenterScreen.kt}
├── navigation/{AccountsNavigation.kt,KipuNavHost.kt,NotificationsNavigation.kt}
└── feature/accounts/presentation/dashboard/DashboardScreen.kt
app/src/test/java/com/kipu/app/feature/notifications/...
app/src/androidTest/java/com/kipu/app/feature/notifications/...
supabase/migrations/<timestamp>_notification_center_hu42.sql
supabase/tests/database/notifications_center_test.sql
```

**Structure Decision**: Add a vertical-slice `feature/notifications` to the existing single Android app. Database migration remains versioned under the existing Supabase directory; no new backend service or project is introduced.

## Constitution Check After Design

*PASS.* Design preserves existing financial truth and producer records, scopes local/remote access by account, stores offline mutations durably, performs only soft deletion, and defines tests at Room, API, RLS, navigation, and UI boundaries. Cross-review and validation remain implementation completion gates, not design exceptions.

## Complexity Tracking

No constitution violations or extra projects. The outbox is necessary to satisfy offline mutation durability, retry safety, and account-change isolation already required by HU-42.
