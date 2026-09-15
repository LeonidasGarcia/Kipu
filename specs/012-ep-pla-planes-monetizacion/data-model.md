# Data Model: EP-PLA - Planes, Límites y Monetización Freemium

**Date**: 2026-09-15  
**Spec**: [spec.md](spec.md)  
**Plan**: [plan.md](plan.md)

## Design Invariants

1. `FREE`, `TRIAL_INTENT` y `PREMIUM_INTENT` son preferencias, no entitlements.
2. Ninguna escritura de preferencia modifica `entitlements`, `feature_access_cache` o datos financieros.
3. El éxito visible ocurre después de una transacción Room que contiene preferencia, revisión y outbox.
4. `selection_revision`, no el reloj, determina el orden remoto.
5. Cada `operation_id` identifica un payload inmutable para un único usuario.
6. Sin entitlement verificado, toda capacidad Premium se deniega y el núcleo Free permanece disponible.
7. Los objetos por encima de cupos Free se preservan y siguen participando en los cálculos financieros.

## Type Conventions

| Concepto | Dominio Kotlin | Room | PostgreSQL/JSON |
|----------|----------------|------|-----------------|
| User/operation ID | `UUID` | String UUID canónico en minúscula | `UUID` / string UUID |
| Timestamp | `Instant` | Epoch microseconds en `INTEGER` | `TIMESTAMPTZ` / RFC 3339 UTC |
| Revision | `Long` positivo | `INTEGER` | `BIGINT` / string decimal |
| Money | `Long amountMinor` + currency | No se persiste en HU-52 | Entero + `PEN` si se transporta |
| Enum | `enum class`/sealed type | String con converter/check | `TEXT` con `CHECK` / string enum |

El convertidor de `Instant` conserva microsegundos. `selected_at` representa el evento local; `updated_at` remoto representa la aplicación por servidor. Ninguno ordena conflictos.

## Domain Models

### PlanSelection

| Value | Meaning | Effective access in Sprint 1 |
|-------|---------|------------------------------|
| `FREE` | Confirmación explícita del plan permanente Free | Kipu Free |
| `TRIAL_INTENT` | Interés en Mensual/Anual con elegibilidad verificada | Kipu Free |
| `PREMIUM_INTENT` | Interés Premium sin Trial aplicable o verificado, incluido Lifetime | Kipu Free |

Validation:

- No se acepta ningún valor adicional.
- No se deriva de este enum una compra, Trial, renovación o entitlement.
- La modalidad exacta Mensual/Anual/Lifetime no se persiste en Sprint 1.

### CommercialOption

| Option | `amountMinor` | Currency | Renewal | Trial |
|--------|---------------|----------|---------|-------|
| `FREE` | 0 | `PEN` | Ninguna | No aplica |
| `MONTHLY` | 499 | `PEN` | Mensual futura | 7 días solo si `ELIGIBLE` |
| `ANNUAL` | 2999 | `PEN` | Anual futura | 7 días solo si `ELIGIBLE` |
| `LIFETIME` | 4999 | `PEN` | Ninguna | No aplica |

`CommercialOption` es configuración y estado UI. No constituye producto comprado ni derecho efectivo.

### TrialEligibilitySnapshot

| Field | Type | Rules |
|-------|------|-------|
| `status` | `ELIGIBLE`, `INELIGIBLE`, `UNKNOWN` | Solo los dos primeros requieren evidencia verificada. |
| `source` | `VERIFIED_ACCOUNT_HISTORY`, `UNAVAILABLE` | Un valor local editable no puede ser fuente positiva. |
| `verifiedAt` | `Instant?` | Obligatorio para historial verificado; nulo para `UNKNOWN`. |
| `validUntil` | `Instant?` | Obligatorio para `ELIGIBLE`; puede ser nulo para consumo confirmado permanente. |

State rules:

- Sin evidencia, error de red o dato contradictorio produce `UNKNOWN`.
- `ELIGIBLE` con `validUntil <= now` se degrada a `UNKNOWN`; el reloj del cliente no renueva la vigencia y una lectura online vuelve a verificarla en servidor.
- Un cache local solo puede conservar una denegación previamente verificada.
- Reinstalación, cambio de dispositivo o manipulación de reloj no produce `ELIGIBLE`.

### FreePlanLimits

| Field | Value |
|-------|-------|
| `policyVersion` | 1 |
| `instruments` | 4 |
| `customCategories` | 5 |
| `debts` | 2 |
| `goals` | 2 |
| `budgets` | 2 |

### FeatureAccessDecision

Results permitted in Sprint 1:

- `Allowed(FREE_CAPABILITY)`
- `Denied(FREE_LIMIT_REACHED)`
- `Denied(PREMIUM_ENTITLEMENT_REQUIRED)`

The policy contract is detailed in [feature-access-policy.md](contracts/feature-access-policy.md).

## Local Room Model

### PlanPreferencesEntity (`plan_preferences`)

| Column | Room type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `user_id` | `TEXT` | PK, canonical UUID, not null | Propietario autenticado. |
| `selection` | `TEXT` | Not null, enum `PlanSelection` | Preferencia confirmada. |
| `selected_at` | `INTEGER` | Not null | Epoch microseconds del evento local. |
| `updated_at` | `INTEGER` | Not null | Inicialmente igual a `selected_at`; luego timestamp remoto reconciliado. |

Rules:

- La fila se crea o actualiza únicamente al confirmar.
- Abrir o abandonar la pantalla no crea fila.
- Una fila ausente produce acceso Free por default de dominio.
- Room no replica la FK de `auth.users`; el repositorio valida el `userId` de sesión.

### PlanSelectionSyncStateEntity (`plan_selection_sync_state`)

| Column | Room type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `user_id` | `TEXT` | PK, canonical UUID | Propietario de la secuencia. |
| `last_issued_revision` | `INTEGER` | >= 0 | Mayor revisión emitida localmente. |
| `accepted_revision` | `INTEGER` | >= 0 | Mayor revisión confirmada por servidor. |
| `updated_at` | `INTEGER` | Not null | Epoch microseconds del último cambio técnico. |

Rules:

- La siguiente revisión es `max(last_issued_revision, accepted_revision) + 1`.
- Las revisiones empiezan en 1 y no necesitan ser contiguas.
- Dos dispositivos offline pueden emitir la misma revisión; el servidor devuelve `CONFLICT` a uno y nunca renumera automáticamente una intención.

### SyncOutboxEntity (`sync_outbox`)

| Column | Room type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `operation_id` | `TEXT` | PK, canonical UUID | Clave idempotente estable. |
| `user_id` | `TEXT` | Not null, indexed | Propietario; nunca se envía bajo otra sesión. |
| `aggregate_type` | `TEXT` | Constant `PLAN_SELECTION` | Tipo de operación. |
| `contract_version` | `INTEGER` | Constant 1 | Versión del payload. |
| `selection_revision` | `INTEGER` | > 0, unique with `user_id` locally | Orden lógico emitido. |
| `selection` | `TEXT` | Valid `PlanSelection` | Payload inmutable. |
| `selected_at` | `INTEGER` | Not null | Evento local inmutable. |
| `status` | `TEXT` | Valid `OutboxStatus` | Estado de procesamiento. |
| `attempt_count` | `INTEGER` | >= 0 | Intentos ejecutados. |
| `next_attempt_at` | `INTEGER?` | Nullable | Backoff/retry permitido. |
| `lease_until` | `INTEGER?` | Nullable | Recuperación de worker interrumpido. |
| `last_error_code` | `TEXT?` | No sensitive detail | Diagnóstico seguro. |
| `created_at` | `INTEGER` | Not null | Creación local. |
| `updated_at` | `INTEGER` | Not null | Última transición. |

`OutboxStatus`:

- `PENDING`
- `IN_FLIGHT`
- `WAITING_FOR_AUTH`
- `COMPLETED`
- `CONFLICT`
- `TERMINAL_ERROR`

Transitions:

| From | Event | To | Effect |
|------|-------|----|--------|
| New | Atomic insert | `PENDING` | Eligible for scheduling. |
| `PENDING` | Worker acquires lease | `IN_FLIGHT` | Increment attempt, preserve payload. |
| `IN_FLIGHT` | `APPLIED`/`DUPLICATE` | `COMPLETED` | Reconcile preference, head and limits. |
| `IN_FLIGHT` | `STALE` | `COMPLETED` | Adopt remote state; no automatic new revision. |
| `IN_FLIGHT` | `CONFLICT` | `CONFLICT` | Preserve remote state; require explicit new user action. |
| `IN_FLIGHT` | 401/expired session | `WAITING_FOR_AUTH` | Retry only with same user's valid session. |
| `WAITING_FOR_AUTH` | Same user reauthenticates | `PENDING` | Reschedule identical payload. |
| `IN_FLIGHT` | Invalid/version/forbidden | `TERMINAL_ERROR` | No automatic loop; retain safe diagnostic. |
| `IN_FLIGHT` | Network/timeout/429/5xx | `PENDING` | Apply backoff; identical retry. |
| `IN_FLIGHT` | Lease expires after process death | `PENDING` | Recover without changing operation. |

Completed rows may be compacted only after remote reconciliation and `accepted_revision` persistence. Conflict/error rows remain until acknowledged or superseded by explicit user action.

### FeatureAccessCacheEntity (`feature_access_cache`)

| Column | Room type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `user_id` | `TEXT` | PK, canonical UUID | Propietario. |
| `policy_version` | `INTEGER` | >= 1 | Versión de límites conocida. |
| `effective_tier` | `TEXT` | `FREE` or future verified tier | En Sprint 1 siempre `FREE`. |
| `entitlement_expires_at` | `INTEGER?` | Nullable | Solo de entitlement remoto verificado; nulo en S1. |
| `verified_at` | `INTEGER?` | Nullable | Momento de verificación efectiva. |
| `source` | `TEXT` | `LOCAL_DEFAULT` or `VERIFIED_SERVER` | Procedencia del acceso. |

Rules:

- Confirmar una intención no inserta ni actualiza esta tabla.
- Un cache ausente equivale a Free.
- Solo un reconciliador futuro de HU-54 puede guardar acceso Premium verificado.
- El cache no puede sobrevivir restauración de backup como prueba de Premium.

## Atomic Local Transaction

`PlanPreferencesDao.confirmSelection(userId, selection, selectedAt, operationId)` performs one Room transaction:

1. Look up `operationId` before mutation. Return the existing operation when user, contract, selection and original `selectedAt` match; reject any mismatch as a local conflict.
2. For a new operation, obtain or create `plan_selection_sync_state` with zeroed revisions.
3. Calculate next positive revision.
4. Upsert `plan_preferences`.
5. Insert immutable `sync_outbox` row in `PENDING`.
6. Persist `last_issued_revision`.
7. Commit all or roll back all.

Idempotency inside the device:

- Reusing the same `operation_id` with identical fields returns the previously created operation without another preference mutation.
- Reusing it with different fields is rejected as local conflict.
- UI double taps are coalesced while confirmation is in progress.

## Remote Supabase Model

### `public.plan_preferences`

| Column | PostgreSQL type | Constraints |
|--------|-----------------|-------------|
| `user_id` | `UUID` | PK, FK `auth.users(id) ON DELETE CASCADE` |
| `selection` | `TEXT` | Not null, default `'FREE'`, check allowed values |
| `selected_at` | `TIMESTAMPTZ` | Not null, default `now()` |
| `updated_at` | `TIMESTAMPTZ` | Not null, default `now()`, server assigned on APPLIED |

RLS:

- Enabled and forced (`ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY`).
- `SELECT`: `auth.uid() IS NOT NULL AND user_id = auth.uid()`.
- `INSERT`: `WITH CHECK (auth.uid() IS NOT NULL AND user_id = auth.uid())`.
- `UPDATE`: matching `USING` and `WITH CHECK`.
- No user `DELETE` policy; account deletion cascades.
- Direct DML grants are revoked from `anon` and `authenticated`; policies remain defense in depth for the RPC execution path and controlled cross-user tests.
- The RPC owner is a dedicated `NOLOGIN` role without `BYPASSRLS`, owns none of the four user-owned tables and receives only the required operations. Both functions have an empty `search_path`, use schema-qualified names and revoke default `EXECUTE` from `PUBLIC`/`anon`; `authenticated` receives `EXECUTE` only on the selection and eligibility RPCs.

### `private.trial_eligibility_snapshots`

| Column | PostgreSQL type | Constraints |
|--------|-----------------|-------------|
| `user_id` | `UUID` | PK, FK auth user, cascade delete |
| `status` | `TEXT` | `ELIGIBLE` or `INELIGIBLE` only |
| `source` | `TEXT` | Constant `VERIFIED_ACCOUNT_HISTORY` |
| `verified_at` | `TIMESTAMPTZ` | Not null, server assigned |
| `valid_until` | `TIMESTAMPTZ` | Required and later than verification for `ELIGIBLE`; nullable for permanent consumed state |

Only a separately authenticated server process with minimum write scope may update this projection, after validating the target account against provider/history evidence. `GET /plans/eligibility` derives `user_id` from the JWT and returns `UNKNOWN` when the row is absent, contradictory or expired. It never infers eligibility from installation state or a client-editable value.

### `private.plan_selection_heads`

| Column | PostgreSQL type | Constraints |
|--------|-----------------|-------------|
| `user_id` | `UUID` | PK, FK auth user, cascade delete |
| `accepted_revision` | `BIGINT` | Not null, >= 0 |
| `updated_at` | `TIMESTAMPTZ` | Not null, server assigned |

The RPC locks the user's head with `FOR UPDATE` before classification.

### `private.plan_selection_receipts`

| Column | PostgreSQL type | Constraints |
|--------|-----------------|-------------|
| `user_id` | `UUID` | FK auth user, cascade delete |
| `operation_id` | `UUID` | Composite PK with user |
| `contract_version` | `SMALLINT` | Must equal 1 |
| `selection_revision` | `BIGINT` | > 0, indexed with user |
| `payload_hash` | `BYTEA` | Exactly 32 bytes, server SHA-256 |
| `first_result` | `TEXT` | `APPLIED`, `STALE` or `CONFLICT` |
| `accepted_revision_at_first_seen` | `BIGINT` | >= 0 |
| `first_seen_at` | `TIMESTAMPTZ` | Server assigned |

The receipt is retained for the account lifetime. `DUPLICATE` is derived only when replaying an operation whose first result was `APPLIED` and whose canonical hash is identical; replays of `STALE` or `CONFLICT` return their original result.

### RLS for Private User-Owned Tables

RLS is enabled and forced on `private.trial_eligibility_snapshots`, `private.plan_selection_heads` and `private.plan_selection_receipts` as well as on `public.plan_preferences`:

- Selection/eligibility RPC reads use `USING (auth.uid() IS NOT NULL AND user_id = auth.uid())`.
- Head insert/update and receipt insert policies apply the same `USING`/`WITH CHECK` ownership rule to the executor role.
- Receipts are immutable after insert; no update/delete policy is exposed. Account deletion uses the separately authorized cascade path.
- The executor has no eligibility DML. The server-only history writer has no access to preferences, heads or receipts and validates its target independently because its privileged provider path is not authorized by the caller's `auth.uid()`.
- Cross-user tests run through both RPCs and under the executor role to prove that all four tables reject a mismatched `user_id`.

### Canonical Payload Hash v1

The server validates fields first, then builds this exact UTF-8 byte sequence with `LF` separators and no trailing `LF`:

```text
{contract_version as decimal}
{operation_id as canonical lowercase UUID}
{selection_revision as decimal without leading zeroes}
{selection as uppercase contract enum}
{selected_at normalized to UTC as YYYY-MM-DDTHH:MM:SS.ffffffZ}
```

It stores `SHA-256(bytes)`. JSON property order and whitespace never participate. Golden vector:

```text
1
5d92af34-c725-4a1a-a863-2c93fa214c86
3
PREMIUM_INTENT
2026-09-14T15:03:12.123456Z
```

Expected SHA-256: `93914ed97388d65ce6fe38949a17cae841f26654afaa8c168ac6ed85ab797132`.

## Remote Classification Transaction

After authentication, validation and head lock:

| Condition | Result | Writes |
|-----------|--------|--------|
| New operation and revision > accepted | `APPLIED` | Upsert preference, advance head, insert receipt |
| Known applied operation, identical hash | `DUPLICATE` | None |
| Known operation, different hash | `CONFLICT` | Safe diagnostic only |
| New operation, revision < accepted | `STALE` | Insert receipt |
| New operation, revision = accepted but different operation | `CONFLICT` | Insert receipt |

Only `APPLIED` changes `plan_preferences.updated_at`. All results return current preference, accepted revision and `FreePlanLimits` v1.

## Relationships

```text
auth.users (1)
├── (0..1) public.plan_preferences
├── (0..1) private.trial_eligibility_snapshots
├── (0..1) private.plan_selection_heads
└── (0..*) private.plan_selection_receipts

local user (1)
├── (0..1) plan_preferences
├── (0..1) plan_selection_sync_state
├── (0..*) sync_outbox
└── (0..1) feature_access_cache
```

No relationship exists from preference, outbox, head or receipt to `entitlements`.

## Migration Strategy

### Room

- Create database version 1 with all four entities and exported schema under `app/schemas/`.
- Add future versions through explicit `Migration` objects and schema tests.
- Never use destructive fallback.
- Test clean creation, upgrade from every supported prior schema and representative rows.

### Supabase

- Add an ordered migration containing schemas, tables, constraints, indexes, RLS, grants and RPC.
- Deploy database migration before Edge Function and Android client.
- Do not hide unexpected existing objects with broad `IF NOT EXISTS`; stop and reconcile drift.
- Roll back operationally by disabling/revoking the function while preserving rows and receipts.

## Retention and Deletion

- Preferences, heads and receipts live for the account lifetime and cascade on account deletion.
- Logs include operation/result identifiers only when necessary and never bearer tokens, financial data or raw sensitive payloads.
- Local outbox rows are compacted only after terminal reconciliation; unresolved conflict/error state remains observable without sensitive detail.
- Backup/restore must not move outbox or access cache across users. Until a verified restore design exists, these stores are excluded from automatic backup.
