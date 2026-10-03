> **Reviewed**: 2026-09-16. Pantalla 1B remains entitlement-neutral; this access contract is unchanged.

# Contract: FeatureAccessPolicy

**Version**: 2
**Scope**: HU-52 / Sprint 1 and HU-58/HU-59 / Sprint 4

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

## Sprint 4 verified capability evidence

For Sprint 4, a non-null `effectiveEntitlement` is permitted only after the infrastructure boundary has validated a server-signed offline grant, current owner, installation key, policy version, same-boot monotonic anchor, and strict `notAfter` bound. A `verified = true` bit copied from a purchase response or a legacy cache row is not sufficient evidence. Failed signature, expired lease, reboot without continuity, or missing/unknown key produces no effective entitlement and therefore follows the Free decision table.

The five existing Free limits remain unchanged. Kipu Free manual registration, local reads/writes, sync/outbox and basic history query remain available. Premium-only capabilities include capture/OCR/automatic categorization, historical analytics and advanced history criteria. For history, a query containing any one advanced criterion is Premium-only, even when combined with a basic text/date/type criterion; the movement-query boundary may return a basic fallback without dropping compatible basic criteria.

The domain policy remains deterministic and platform-independent. Android/Room/Keystore/clock code must produce a validated `EffectiveEntitlement` before calling it. Trusted offline time comes from the lease evaluator, not a device wall clock. An `Allowed` decision cannot be produced from a legacy cache or purchase intent.

### HU-58/HU-59 assertions

1. Free core and below-limit Free capabilities are allowed without an entitlement.
2. Premium-only and over-limit actions are denied without validated entitlement; existing data remains readable and contributes to financial calculations.
3. A valid lease is allowed only while trusted monotonic time is strictly before its signed `notAfter` and known commercial end.
4. At expiration, reboot/continuity loss, mismatched owner/install, invalid signature or unknown policy, Premium is denied and Free remains allowed.
5. A network error cannot extend a lease; an authenticated effective-Free response clears it.

## Non-Destructive Rule

A denial controls only the requested capability/action. It must not delete, hide from balances, rewrite or exclude previously valid financial data. Selection of objects after downgrade belongs to HU-57.

## Required Tests

1. Every Free core capability returns `Allowed(FREE_CAPABILITY)`.
2. Each of the five limits allows below threshold and denies the next create at threshold.
3. Existing over-limit objects remain available to calculations.
4. Every Premium-only capability returns `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` in Sprint 1.
5. Boundary/architecture tests prove that `TRIAL_INTENT` and `PREMIUM_INTENT` have no converter or overload to effective entitlement and that repository requests use `None` for both.
6. Results are deterministic for identical inputs.
# Recuperación y presentación aprobadas — 2026-10-03

Restaurar/verificar acceso es un comando explícito y acotado, independiente del catálogo, con resultado terminal para ausencia de compra, pendiente, éxito autenticado, error y timeout. Solo la verificación de servidor y el evaluador existente conceden acceso. La UI distingue ofertas/revalidación y no prolonga grants mediante feedback, animaciones o errores. Los criterios avanzados retenidos son borradores y no se anuncian como aplicados sin autorización. No registrar filtros, tokens o importes.
