# Synchronization Contract

## Scope

This contract governs Room outbox delivery, PostgreSQL command receipts, optimistic revisions and ordered pull for EP-CTA. It applies to all devices of one authenticated owner.

## Identities

- `operation_id`: UUID created once before the local transaction and reused unchanged for every retry.
- `aggregate_id`: stable account/card UUID created by the client.
- `payload_hash`: local outbox content fingerprint only. The remote receipt's `request_hash` is computed by the server from the normalized typed command; the client digest is not compared with the receipt digest.
- `expected_revision`: compare-and-set precondition when there is no causal predecessor; null when the predecessor receipt will supply the accepted base revision.
- `predecessor_operation_id`: prior local mutation for the same owner/aggregate, null only when no pending causal predecessor exists.
- `sync_sequence`: server cursor for pull only; never a business revision.

Issuer, network and last four are not identities. Timestamps never decide command precedence.

## Canonical Payload

Contract v1 canonicalization:

1. Build one envelope whose ordered keys are `contract_version`, `command_type`, `operation_id`, `aggregate_id`, `predecessor_operation_id`, `occurred_at`, followed by the command schema's fields in their published order.
2. Reject unknown fields before hashing. `command_type` is explicit and must match the typed RPC.
3. Normalize UUIDs to lowercase canonical text.
4. Normalize enums to uppercase contract values.
5. Normalize timestamps to UTC RFC 3339 with six fractional digits.
6. Preserve integer decimal representation without leading zeroes and encode absent nullable fields as JSON `null`.
7. Serialize ordered keys as UTF-8 JSON with no insignificant whitespace. `operation_id` and `predecessor_operation_id` are included.
8. Exclude authorization headers, `user_id`, retry metadata and server fields.
9. Hash with SHA-256 on the server. Android stores the immutable canonical payload and a deterministic typed-payload hash for local collision checks; that local hash is not compared to the PostgreSQL JSONB receipt hash. Server hash is authoritative.

Shared byte-level fixtures cover every command type, Unicode display text, nulls, timestamps and integer limits on Android and PostgreSQL.

Raw request bodies are never retained remotely.

## Local Commit Boundary

The UI sees success after one Room transaction commits all local effects and one outbox row. Network scheduling occurs after commit. A scheduling failure does not undo the financial command; startup/session restoration must discover and schedule pending rows.

## Worker Rules

- Unique work name: `instrument-sync-{userId}`.
- `ExistingWorkPolicy.KEEP`, network constraint and exponential backoff.
- Lease eligible rows in `createdAt, operationId` order, but only when their causal predecessor is already `SYNCED`.
- Never dispatch a row when active session owner differs.
- On process death, expired `IN_FLIGHT` leases return to `PENDING`.
- Retry the exact operation ID and payload after timeout or ambiguous response.
- Do not calculate balances or resolve conflicts in the worker.
- If a predecessor reaches `CONFLICT` or terminal `ERROR`, block its successors and surface one aggregate-level resolution path; do not skip ahead.

## Server Transaction

Each command RPC atomically:

1. Derives `user_id` from `auth.uid()`.
2. Validates contract shape and values.
3. Computes canonical hash.
4. Locks/reads receipt for `(user_id, operation_id)`.
5. Returns `DUPLICATE` for same hash or `OPERATION_COLLISION` for a different hash.
6. Validates an optional predecessor receipt is `APPLIED` for the same owner/aggregate and uses its accepted revision as the successor's compare-and-set base; a command without predecessor must provide `expected_revision`.
7. Locks relevant owner/aggregate rows.
8. Validates quota, links, lifecycle and expected revision.
9. Applies projection and financial effects.
10. Appends ordered sync changes/tombstones, including every created movement.
11. Inserts immutable receipt and returns safe projection metadata.

No partial effect or receipt may survive rollback.

## Result Matrix

| Result | Local transition | Retry |
|--------|------------------|-------|
| `APPLIED` | Reconcile aggregate identity/revision from required safe result; `SYNCED` | No |
| `DUPLICATE` | Reconcile the original required aggregate identity/revision result; `SYNCED` | No |
| `OPERATION_COLLISION` | `CONFLICT`; preserve local facts for review | No automatic retry |
| `CONFLICT` | `CONFLICT`; preserve authoritative and local versions | New explicit command only |
| `REJECTED` | `ERROR`; preserve safe code, no remote effect | Correct input with new operation ID |
| 401 / expired session | `WAITING_FOR_AUTH` | Same owner/session later |
| 429 / timeout / network / 5xx | `PENDING` with backoff | Identical command |
| Invalid response / unsupported contract | `ERROR` | Upgrade or explicit recovery |

## Pull Contract

- Pull by server `after_sequence`, max 500 changes per page.
- Changes are owner-scoped and ordered ascending.
- Change entity types are `ACCOUNT`, `CARD` and canonical `TRANSACTION`; legacy `MOVEMENT` values are accepted as an alias only while old streams remain. Transaction upserts carry immutable facts needed to rebuild balances on a fresh device.
- `UPSERT` replaces local remote projection only after conflict policy permits.
- `ARCHIVE` preserves data/history and blocks new operations.
- `DELETE` is a tombstone and must remove only an eligible unused card projection.
- A stale device cannot reactivate or recreate a tombstoned aggregate with an old command.
- Cursor advances only after all changes in a page commit to Room atomically.
- A push response never advances the pull cursor. Only a contiguous pull page can prove all preceding owner changes were consumed.
- `ACCOUNT`/`CARD` `UPSERT` and `ARCHIVE` changes carry their matching owner-scoped projection and aggregate revision. A credit-card `CARD` projection links an owner-scoped `CREDIT_LIABILITY` account; registration publishes that `ACCOUNT` projection before the card. The client may seed the zero-balance liability account from a complete card projection when reading older ordered streams where the card event predates its account event. `CARD` `DELETE` carries tombstone metadata only and is valid only for a card with no financial history. `TRANSACTION` (or legacy `MOVEMENT`) accepts only immutable `UPSERT` with event version and a complete movement projection, including liability and source ledger rows for credit purchases and payments.
- For mutable `CARD` aggregates, pull may resolve a legacy sequence entry to the current card snapshot and matching current revision after a forward schema backfill; sequence remains the pagination cursor, not an audit log of every intermediate card value. Preserve the stored operation for existing cards: in particular, an archived card's original `UPSERT` must carry its current archived snapshot so a fresh client can create it before processing a later `ARCHIVE`. Only a physically absent card is projected as a `DELETE` tombstone. Immutable financial facts remain represented by transaction events.
- A page applies movement and instrument projections transactionally. It never advances a cursor after receiving an unknown entity type or incomplete movement.

## Conflict Policy

- Immutable movement facts are never merged or overwritten.
- Appearance/lifecycle uses `expected_revision`; stale values require explicit user retry against the current revision.
- Safe UI may offer to reapply alias/color/icon choices, but must issue a new operation ID and current expected revision.
- Owner, currency, opening facts, card type/link, debt and ledger effects cannot be merged silently.
- Create -> edit -> archive queued offline is not a revision conflict by itself: explicit predecessor receipts serialize that chain. A concurrent command from another device against the same base still conflicts normally.

## Retention

- Receipts remain for the lifetime of the Kipu user account.
- Conflict/error outbox rows remain until acknowledged or superseded explicitly.
- Synced local outbox rows may be compacted only after accepted revision/cursor is durable.
- The complete owner change stream and tombstones remain for the lifetime of the Kipu user account, allowing fresh-device bootstrap from sequence zero and preventing resurrection. Account-level deletion policy ultimately removes user data.

## Sprint 3 Financial Commands (HU-09..HU-13)

- A confirmed card purchase is synced through the canonical transaction command as one `EXPENSE` / `CARD_PURCHASE` operation. Its idempotency identity covers the card, amount, currency, category, occurrence time, installment count and confirmed actual purchase data.
- A card payment is synced as one `TRANSFER` / `CARD_PAYMENT` operation through the canonical allocation command. The transaction, a negative `SOURCE` entry for its paying asset, equal positive `LIABILITY` entry for the card, installment allocations, status updates, receipt and sync change commit atomically; it does not post a second expense.
- Use existing `credit_installments` and `credit_payment_allocations` rows as the remote source of truth. Local Room projections apply together with their source transaction/payment; they never become an independent balance authority.
- Persist no simulation command or estimated interest. Only a user-confirmed purchase can create an actual transaction and installment schedule.
- Threshold crossing events are emitted only after a committed utilization change. Their stable identity includes the accepted financial operation and threshold; EP-NOT/HU-42 consumes them idempotently through the existing notification contract.
- The current movement-only payment/purchase RPCs must delegate to or be retired in favor of these operations before the worker dispatches Sprint 3 commands. A command must never be sent down two accounting paths.
