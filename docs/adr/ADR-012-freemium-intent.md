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

Network failures, timeouts, rate limits, and server unavailability retry the identical operation.
Authentication failure waits for a valid session belonging to the same user. Validation and
authorization failures are terminal and retain only safe diagnostics.

## Consequences

- Plan selection remains safe to commit locally and show as successful while offline.
- UI choices and outbox records cannot authorize Premium.
- Receipt retention and per-user revision state are required for deterministic retry handling.
- Conflicts are visible synchronization outcomes rather than silent last-write-wins changes.
- Billing and entitlement reconciliation remain outside HU-52 and require their own approved design.
