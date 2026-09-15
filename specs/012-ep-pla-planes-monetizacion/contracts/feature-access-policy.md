# Contract: FeatureAccessPolicy

**Version**: 1  
**Scope**: HU-52 / Sprint 1

## Purpose

Evaluate access to a Kipu capability without deriving authority from a plan preference, UI state or pending synchronization.

## Boundary

Conceptual Kotlin contract:

```kotlin
interface FeatureAccessPolicy {
    fun evaluate(request: FeatureAccessRequest): FeatureAccessDecision
}
```

The interface belongs to the pure domain layer and must run in JVM tests without Android, Room, Supabase, Stitch, Google Play or network access.

## Input

`FeatureAccessRequest` contains:

| Field | Meaning |
|-------|---------|
| `capability` | Stable capability identifier and classification. |
| `currentUsage` | Current object count for a limited Free capability; absent for unlimited/manual core. |
| `freeLimits` | Versioned five-limit policy. |
| `effectiveEntitlement` | Separately verified entitlement or none. In Sprint 1 it is always none. |
| `evaluatedAt` | Clock supplied by caller for future verified-expiry evaluation; does not evaluate plan intent. |

Explicitly excluded inputs:

- `PlanSelection`
- `CommercialOption`
- `operationId` or outbox status
- Trial eligibility
- Billing state or purchase intent

## Capability Classification

| Classification | Sprint 1 behavior |
|----------------|-------------------|
| `FREE_CORE` | Always allowed for an authenticated/local user. |
| `FREE_LIMITED` | Allowed below limit; denied when creating another object at/above limit. Existing objects remain readable and financially effective. |
| `PREMIUM_ONLY` | Denied without a verified effective entitlement. |

## Results

```text
Allowed(FREE_CAPABILITY)
Denied(FREE_LIMIT_REACHED)
Denied(PREMIUM_ENTITLEMENT_REQUIRED)
```

`PREMIUM_REQUIRED` is not a v1 reason. The canonical identifier is `PREMIUM_ENTITLEMENT_REQUIRED`, matching FR-018.

## Decision Table

| Capability | Usage | Verified entitlement | Decision |
|------------|-------|----------------------|----------|
| Free core | Any | None | `Allowed(FREE_CAPABILITY)` |
| Free limited | Below limit | None | `Allowed(FREE_CAPABILITY)` |
| Free limited create | At/above limit | None | `Denied(FREE_LIMIT_REACHED)` |
| Premium only | Any | None | `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` |

`TRIAL_INTENT` and `PREMIUM_INTENT` cannot appear in the verified-entitlement column because they are different types and are explicitly excluded from `FeatureAccessRequest`. The repository boundary must construct requests with `effectiveEntitlement = None` regardless of either preference.

## Non-Destructive Rule

A denial controls only the requested capability/action. It must not delete, hide from balances, rewrite or exclude previously valid financial data. Selection of objects after downgrade belongs to HU-57.

## Required Tests

1. Every Free core capability returns `Allowed(FREE_CAPABILITY)`.
2. Each of the five limits allows below threshold and denies the next create at threshold.
3. Existing over-limit objects remain available to calculations.
4. Every Premium-only capability returns `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` in Sprint 1.
5. Boundary/architecture tests prove that `TRIAL_INTENT` and `PREMIUM_INTENT` have no converter or overload to effective entitlement and that repository requests use `None` for both.
6. Results are deterministic for identical inputs.
