# Research: EP-NOT — Centro de Notificaciones y Avisos

## R-001 — Existing notification table and remote permissions

**Decision**: Extend `public.app_notifications` through a forward-only migration. Add nullable `deleted_at`, an active-notice/unread ordering index, and column-level authenticated UPDATE privilege for `is_read` and `deleted_at`. Retain the existing owner RLS policy and current SELECT grant; do not grant client INSERT or DELETE.

**Evidence**: `20260920000000_financial_core_baseline.sql` creates the table, enables and forces RLS, and defines `app_notifications_own` using `auth.uid() = user_id`. `20260925022130_s3_credit_canonical_purchase_payment.sql` revokes broad access, grants SELECT, and grants UPDATE only on `is_read`. Its producer already writes `event_key`, `event_payload`, and canonical credit-crossing records.

**Rationale**: The feature needs client-side interaction state but server-owned notification content. A scoped UPDATE grant plus RLS allows those exact actions while preserving producer integrity and account separation. Soft-delete retains history.

**Alternatives considered**: Recreate the table (rejected: would duplicate and risk losing producer fields/history); use a privileged RPC (unnecessary for setting these two owner-scoped state fields); grant table-wide UPDATE (rejected: permits modifying title, body, owner, and event metadata).

## R-002 — Local-first cache and retry behavior

**Decision**: Add Room projection and desired-state outbox tables in schema version 12. Repository writes update the projection and coalesce the latest `is_read` / `deleted_at` desired state per `(user_id, notification_id)` in one Room transaction. A WorkManager worker processes only the active verified owner and uses idempotent PATCH operations.

**Evidence**: The app is local-first and already uses Room migrations, Hilt, `SessionCoordinator`, Ktor, and per-user WorkManager sync schedulers. Existing feature outboxes persist retry state. HU-42 requires immediate offline changes and account isolation.

**Rationale**: A desired-state patch is idempotent across retries and coalescing prevents stale commands from undoing a later dismissal. The composite account key plus worker owner check prevents stale jobs from applying another account's operations.

**Conflict rule**: Pending local outbox commands win over remote refresh for that notification. Remote `deleted_at` tombstones are retained in Room and omitted by active-list/count queries. A successful PATCH removes the queued command; failed network/auth attempts remain queued for retry.

**Alternatives considered**: Write-through-only requests (rejected: offline actions would be lost); enqueue one uncoalesced command per tap (rejected: old retry order could restore stale state); clear the Room database on account changes (rejected: unnecessary data loss when all reads are explicitly owner-filtered).

## R-003 — Notification category and producer compatibility

**Decision**: Keep `notification_type` as an unconstrained string in Room and Supabase. Normalize known values for presentation: `BILLING_DUE` is a reminder; `BUDGET_ALERT`, `GOAL_REACHED`, `CREDIT_THRESHOLD`, legacy `CREDIT_UTILIZATION_THRESHOLD_CROSSED`, and `SYSTEM` are alerts. Accept reference types case-insensitively (`CARD`/`card`, etc.) without rewriting stored rows. Unknown types remain visible as a generic alert. There is no dedicated due-date column; the pre-existing `event_payload` JSONB can provide ISO `due_date`, with producer-authored `body` as fallback. The client labels the date as expected rather than deriving it from `created_at` or claiming payment.

**Evidence**: Product/entity docs define `BUDGET_ALERT`, `BILLING_DUE`, `GOAL_REACHED`, and `SYSTEM`; HU-42 additionally requires `CREDIT_THRESHOLD`. The current Android dashboard/API and server producer use the historical credit type and uppercase `CARD` reference. The canonical server migration adds `event_payload` JSONB and producers use it for event metadata.

**Rationale**: The database already contains historical data and producers outside this feature. A restrictive CHECK or rewrite would risk breaking valid server-side events. Presentation normalization allows gradual migration. The existing JSONB event payload carries an expected due date without adding a dedicated column; `body` remains a compatible fallback.

## R-004 — Deep-link availability and deleted destinations

**Decision**: Route `card` references through the registered card detail route after verifying the card exists in the active owner's local projection. Unsupported types or absent cards stay on PNOT and show a snackbar. Budget, debt, and goal screens are not registered in this repository and therefore use the specified safe-unavailable behavior until their owning features add routes.

**Evidence**: `AccountsNavigation.kt` registers `accounts/instrument/{instrumentId}?isCard={isCard}`; other listed product entities have no route in the app navigation graph. `CardDao.getById(userId, id)` is already account-scoped.

**Rationale**: A route string must not be fabricated for an unregistered screen, and checking the owner-scoped local card before navigation gives a friendly result for a deleted card without risking a stale destination.

## R-005 — Visual design, accessibility, and motion

**Decision**: Use `docs/stitch-design-system.md` semantic Kipu theme colors and the already bundled Inter typeface. Build filter chips and explicit archive actions with at least 48dp targets. Use accessible read/category labels, a semantic unread count, and a text-plus-dot distinction. Apply Compose enter/exit/badge motion only when reduced motion is not enabled.

**Evidence**: The product prompt fixes background `#F7F9FB`, white cards, teal `#0F766E`, Inter, and 48dp targets. `ui-ux-pro-max` search results for general financial apps recommended an unrelated palette and were rejected. `transitions-dev` motion timing examples are CSS-specific; their intent is adapted to native Compose, without adding dependencies.

**Rationale**: Product tokens outrank generic search output. A quick archive button is an accessible alternative to gesture-only swipe. Reduced-motion behavior preserves the same information and end state.

## R-006 — Tests and verification boundary

**Decision**: Add JVM tests for mapping, category filters, per-user repository actions, outbox coalescing, retry and route resolution; Room migration/DAO tests; Compose tests for filters, empty text, read/archive state and badge semantics; and pgTAP assertions for RLS plus column privileges. Run Gradle unit tests and local Supabase DB tests; run connected Compose tests when an emulator/device is available.

**Rationale**: Each failure boundary is tested where it can fail, consistent with HU-42 and the project verification gate. Device-dependent results will not be claimed unless actually run.
