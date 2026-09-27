# REST Contract: `public.app_notifications`

PostgREST requests use the existing Supabase URL, publishable API key, and authenticated user's bearer access token. Supabase RLS remains the authoritative account boundary. The client includes `user_id` in filters as defense in depth and never sends or accepts a different owner as authorization.

## Read account notices

`GET /rest/v1/app_notifications`

Query parameters:

```text
select=id,user_id,title,body,notification_type,reference_entity_type,reference_entity_id,is_read,created_at,deleted_at,event_payload
user_id=eq.<authenticated-user-uuid>
order=created_at.desc
```

The response is a JSON array. Include tombstones so the Room projection learns remote dismissals. The center and badge exclude every row with non-null `deleted_at`. Preserve unknown fields for forward compatibility. Select the pre-existing read-only `event_payload` JSONB field. For `BILLING_DUE`, prefer an ISO `due_date` value from that payload and render it as an expected future date; when absent, keep the producer's `body` visible. Never derive a due date from `created_at`.

## Mark one notice read / dismiss one notice

`PATCH /rest/v1/app_notifications?id=eq.<notice-uuid>&user_id=eq.<authenticated-user-uuid>`

Headers:

```text
Authorization: Bearer <user access token>
Content-Type: application/json
Prefer: return=minimal
```

Bodies:

```json
{"is_read": true}
```

```json
{"deleted_at": "<UTC ISO-8601 timestamp>"}
```

The API performs no insert and no physical delete. Read-all is represented as idempotent per-row desired-state patches for the currently active, non-dismissed rows; UI changes are committed to Room first.

## Failure behavior

- No valid session, network failure, timeout, or retryable server error: keep the Room action and outbox entry; retry only for the same verified owner.
- Row no longer exists or RLS returns no matching row: remove/resolve the queued command without navigating to a deleted entity; report unavailable destination when relevant.
- 401/403: preserve pending state and defer until that account reauthenticates; never submit using a different session.
- Duplicate PATCH: safe because each operation sets a desired state and the same `(user_id, id)` filter is used.

## Database privileges

Authenticated clients retain SELECT and receive column-level UPDATE only for `is_read` and `deleted_at`. RLS policy `app_notifications_own` restricts those operations to `auth.uid() = user_id`. No client INSERT, DELETE, or UPDATE privilege for producer-owned columns is introduced.
