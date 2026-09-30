# Data Model: EP-NOT — Centro de Notificaciones y Avisos

## AppNotification

One server-created financial notice owned by one Kipu account. It may reference an object that is no longer available.

| Field | Remote type | Room type | Rules |
|---|---|---|---|
| `id` | UUID | UUID | Primary identifier; composite local key with `user_id`. |
| `user_id` | UUID | UUID | Required owner; local query predicate and Supabase RLS owner. |
| `title` | text | String | Required, producer-owned. |
| `body` | text | String | Required, producer-owned. |
| `notification_type` | text | String | Preserve raw string; normalize known canonical and legacy aliases in domain. |
| `reference_entity_type` | text, nullable | String? | Weak reference: card, budget, debt, goal; case-insensitive. |
| `reference_entity_id` | UUID/text, nullable | String? | Weak reference only; target may be deleted. |
| `is_read` | boolean, default false | Boolean | User interaction state. |
| `created_at` | timestamptz, default now() | Instant | Server creation time; list sorts descending. |
| `deleted_at` | timestamptz, nullable | Instant? | Soft-delete tombstone; dismissed notices never appear in center or badge. |
| `event_key` | text, nullable, pre-existing | not required for presentation | Producer idempotency metadata, not client mutable. |
| `event_key` | text, nullable, pre-existing | Not projected to Room | Producer idempotency metadata; client read-only. |
| `event_payload` | jsonb, NOT NULL DEFAULT `{}`, pre-existing | Stored in Room as JSON text | Producer metadata; `due_date` (ISO `YYYY-MM-DD`) is preferred for `BILLING_DUE`; client read-only. |

### Domain interpretation

- Category `REMINDER`: `BILLING_DUE`.
- Category `ALERT`: `BUDGET_ALERT`, `GOAL_REACHED`, `CREDIT_THRESHOLD`, historical `CREDIT_UTILIZATION_THRESHOLD_CROSSED`, `SYSTEM`, and unknown values.
- Credit legacy alias and reference casing are normalized only in memory; persisted raw values remain unchanged.
- No dedicated due-date column exists. When supplied, `event_payload.due_date` (ISO `YYYY-MM-DD`) is preferred and rendered with an explicit future prefix; otherwise display the producer's `body` verbatim with future/reminder labeling. Never derive a due date from `created_at` or imply payment occurred.

## NotificationSyncOutbox

Durable local desired state for one notice. Its unique key is `(user_id, notification_id)` so newer intent coalesces older retry work.

| Field | Type | Rules |
|---|---|---|
| `operation_id` | UUID | Stable primary key for diagnostics and deterministic local handling. |
| `user_id` | UUID | Must match both the active owner and notice owner before remote work. |
| `notification_id` | UUID | Identifies the remote row; unique with owner. |
| `is_read` | Boolean? | Desired value to PATCH when non-null. |
| `deleted_at` | Instant? | Desired tombstone timestamp; dismissal stores a non-null value. |
| `attempt_count` | Int | Increments on failed sync attempts. |
| `created_at` | Instant | Local enqueue time. |

The DAO transaction updates the notice and inserts/updates its outbox record atomically. Read-all creates or merges desired read state only for active, non-dismissed notices owned by the current user.

## Derived values

- **Active notices**: `user_id = activeUserId AND deleted_at IS NULL`.
- **Unread badge**: active notices with `is_read = false`; display `99+` above 99, with a semantic count.
- **Filtered list**: active rows ordered by `created_at DESC`; alert/reminder classification is performed by the domain mapper.
- **Destination**: a weak reference resolved by normalized type, registered route, and owner-scoped object existence; unresolved destinations do not mutate the notice.
