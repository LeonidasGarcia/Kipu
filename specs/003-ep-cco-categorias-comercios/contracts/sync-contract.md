# Sync Contract: Categories, Merchant Aliases and Preferences

## Local command envelope

Every command is stored locally before sync and includes `operationId`, `ownerId`, type, aggregate ID, expected revision when relevant, canonical payload, payload hash, timestamps, state and retry count. The same operation ID and payload returns the same receipt. Reusing an operation ID with a different payload is rejected. The server derives the owner from `auth.uid()`; it never trusts a client-supplied owner ID.

## Command types

| Command | Aggregate | Required remote checks |
|---------|-----------|------------------------|
| `CREATE_CATEGORY` | Category | Owner, root/subcategory hierarchy, active-root quota and root/inherited type. |
| `UPDATE_PRESENTATION` | Presentation | Owner, expected revision and explicit conflict result. |
| `SET_CATEGORY_ACTIVE` | Category | Owner, parent lifecycle, quota when reactivating a custom root and expected revision. |
| `UPDATE_MOVEMENT_CLASSIFICATION` | Movement | Movement owner, eligible category, active merchant and mutually exclusive catalog/provisional merchant fields. |
| `PRESERVE_MERCHANT_SOURCE_TEXT` | Movement source text | Movement owner and non-empty exact source value. Write only when empty; never replace a different value. |
| `RESOLVE_CONFLICT` | Category conflict | Owner, open conflict and unique resolution operation. |
| `UPSERT_MERCHANT_ALIAS_RULE` | Alias rule | Owner, active canonical merchant, normalized exact pattern, expected revision; verified Premium required for a new remote rule. |
| `DELETE_MERCHANT_ALIAS_RULE` | Alias rule | Owner, expected revision and logical tombstone; no direct table DELETE. |
| `UPSERT_MERCHANT_CATEGORY_PREFERENCE` | Merchant preference | Owner, active catalog merchant, owned/system category, active root, plan eligibility and expected revision. No independent Premium gate. |
| `DELETE_MERCHANT_CATEGORY_PREFERENCE` | Merchant preference | Owner, expected revision and logical tombstone. |

Category type compatibility is checked when resolving a preference for a future operation: `GENERAL` works for income or expense, `EXPENSE` and `INCOME` work only for their matching operation, and transfers receive no category. An ineligible preference returns `NeedsNewChoice`; it never changes a confirmed movement.

## Merchant command APIs

PostgREST RPC payloads use `{ "p_payload": { ... } }`. Every command carries an `operation_id` and `payload_hash`; upsert commands also carry their stable row ID and `expected_revision` for edits. Create uses a null expected revision. A stale revision returns `CONFLICT`; a repeated matching operation returns `DUPLICATE`.

`PRESERVE_MERCHANT_SOURCE_TEXT` stores raw text separately from the normalized alias pattern. The Android client accepts a new alias locally only when its verified Premium entitlement or bounded signed offline lease is current. The remote alias RPC independently checks the server-verified billing projection before creating a new rule. If a pending offline command no longer satisfies that server check, it becomes a visible command rejection and is not retried as a network failure.

The local S5 migrations are:

- `20261008161406_ep_cco_s5_alias_preference_foundation.sql`
- `20261008161540_ep_cco_s5_alias_commands.sql`
- `20261008161631_ep_cco_s5_preference_commands.sql`

They add `public.merchant_alias_rules`, `public.merchant_category_preferences`, the `financial_movements.merchant_raw_text` column, and the RPCs `preserve_merchant_source_text_v1`, `upsert_merchant_alias_rule_v1`, `delete_merchant_alias_rule_v1`, `upsert_merchant_category_preference_v1`, and `delete_merchant_category_preference_v1`. Private tables force RLS and grant authenticated clients SELECT only; writes use owner-checking RPCs. These migrations and tests were applied only to the local Supabase stack. The remote project still requires migration-history reconciliation, approved application and effective RLS/grant review.

## Results

| Result | Local behavior |
|--------|----------------|
| `APPLIED` | Mark the command synced and retain its local projection. |
| `DUPLICATE` | Complete the command from its idempotent receipt without repeating an effect. |
| `CONFLICT` | Mark the affected rule/preference and outbox command as conflicted; require a refresh or user correction before another edit. |
| `REJECTED` / `COMMAND_REJECTED` | Keep the local row visible with an actionable sync error; do not change financial history. |
| `REVIEW_REQUIRED` | Assign no merchant and retain source evidence until a human selects a destination. |
| `SIGNAL_NOT_AUTHORIZED` | Do not evaluate a signal when entitlement or consent is missing; keep manual movement entry available. |
| `RETRYABLE_FAILURE` | Keep the command `PENDING` and retry transient failures with backoff. |

## Ordering and failure

- Movement classification and its outbox command are written in one Room transaction.
- Raw source text, aliases and category preferences are stored with their command before sync. Tombstones remain until a remote receipt confirms deletion.
- The worker runs for the currently active owner only. `Protected` or `NoOwner` access does not process persisted commands.
- Connectivity, timeout, authentication refresh and rate-limit failures remain pending; functional 4xx rejection and stale revisions become visible failures instead of endless retries.
- Alias matching requires a provenance-bearing `CaptureCandidate` issued only after current entitlement and consent checks. It uses exact equality after case, accent and whitespace normalization; punctuation is retained. Different canonical destinations produce `NeedsReview` with no priority tie-break.
- Raw source text is never substituted with its normalized pattern and is not sent to logs or telemetry.
- The repository migration history is ahead of the linked Supabase history. Remote integration and release remain blocked until the normal migration process reconciles the history, applies approved migrations and reviews effective RLS/grants with two owners.
