# ADR-012: Separate Commercial Intent from Effective Entitlement

- **Status**: Approved
- **Date**: 2026-09-15
- **Decision owner**: EP-PLA / Leonidas Garcia
- **Approval evidence**: EP-PLA implementation plan, post-design constitution gate PASS

## Context

HU-52 lets a person choose Free or record interest in a future Premium offer. Sprint 1 does not
perform a purchase, activate a trial, or verify a subscription. Local-first confirmation and
retries also require deterministic ordering and idempotency across devices without trusting a
client clock.

Treating a commercial preference as access authority would let client-editable or pending state
grant Premium. Resolving concurrent offline writes by timestamp or silently issuing another
revision would also change a person's intent without a new explicit action.

## Decision

`FREE`, `TRIAL_INTENT`, and `PREMIUM_INTENT` are entitlement-neutral preferences. They must not
write or derive an effective entitlement, billing state, subscription, purchase, or
`feature_access_cache` entry. Premium access may be granted only from a separately verified
effective entitlement.

Each user has a monotonic selection revision. A device allocates the next revision as
`max(lastIssuedRevision, acceptedRevision) + 1`; the server serializes changes against its accepted
revision. Timestamps describe events but never order conflicts.

Every operation has an immutable UUID and a versioned canonical SHA-256 hash. Hash version 1 uses
UTF-8 for exactly five LF-separated values with no final LF: contract version, lowercase canonical
operation UUID, decimal revision without leading zeroes, uppercase selection, and the selected
timestamp normalized to UTC with six fractional digits. JSON formatting is not part of identity.

The server classifies retries and races as follows:

| Result | Meaning and handling |
|---|---|
| `APPLIED` | Advance the accepted revision and reconcile the local projection. |
| `DUPLICATE` | The same operation and canonical payload was already applied; reconcile as success. |
| `STALE` | A newer revision is authoritative; adopt remote state without renumbering the intent. |
| `CONFLICT` | The operation identity or same revision disagrees; preserve remote state and require a new explicit action. |

Quota resource selections use a separate full-snapshot contract. Room stores the current selected
items per feature key, not a history of every offline edit, so several local revisions may be
coalesced before synchronization. The quota RPC accepts and records any revision strictly greater
than its accepted head; equal or older revisions are `STALE`. A revision gap is therefore valid for
a newer complete snapshot and is not a conflict. When a delayed `STALE` or `CONFLICT` response is
rebased, the client advances beyond both the latest local revision and the returned accepted head,
while retaining the latest local items instead of replacing them with the response's older remote
snapshot. Operation IDs remain deterministic for the exact request payload, and reusing an ID with
a different canonical payload remains a receipt-hash conflict.

Network failures, timeouts, rate limits, and server unavailability retry the identical operation.
Authentication failure waits for a valid session belonging to the same user. Validation and
authorization failures are terminal and retain only safe diagnostics.

## Consequences

- Plan selection remains safe to commit locally and show as successful while offline.
- UI choices and outbox records cannot authorize Premium.
- Receipt retention and per-user revision state are required for deterministic retry handling.
- Conflicts are visible synchronization outcomes rather than silent last-write-wins changes.
- Billing and entitlement reconciliation remain outside HU-52 and require their own approved design.
