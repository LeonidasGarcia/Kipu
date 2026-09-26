# Data Model: EP-CTA - Cuentas y Tarjetas

**Date**: 2026-09-24
**Spec**: [spec.md](spec.md)  
**Plan**: [plan.md](plan.md)

## Design Invariants

1. Todo importe autoritativo usa `Long`/`BIGINT` en unidades menores y una moneda explicita.
2. El saldo de una cuenta se deriva de movimientos; ningun campo mutable de cuenta o tarjeta es saldo.
3. Cada cuenta confirmada nace con exactamente un movimiento `OPENING`, incluso si el importe es cero.
4. `initialBalanceMinorUnits` es una copia de auditoria inmutable y debe coincidir con `OPENING`; no se suma por separado.
5. Una tarjeta de debito referencia una cuenta liquida; no posee saldo ni movimiento de apertura.
6. Linea total y credito disponible son capacidad de endeudamiento, nunca activo.
7. PAN, CVV/CVC/CID y credenciales bancarias no existen en entidades, DTOs, comandos, receipts o logs.
8. Toda fila local/remota es owner-scoped por `userId`; IDs externos no autorizan acceso.
9. Los hechos financieros son append-only. Correccion/cancelacion crea ajuste o reverso relacionado.
10. Una operacion UUID identifica un unico payload canonico. Igual UUID+hash es duplicado; igual UUID+hash distinto es colision.
11. Archivo conserva historia y libera cupo; reactivacion consume cupo. Restricciones comerciales nunca borran historia.
12. PEN y USD se agregan por separado; no existe conversion en EP-CTA.
13. Archivar cambia disponibilidad operativa y visibilidad, no la existencia economica: el saldo de una cuenta archivada sigue formando parte del dinero actual hasta una operacion financiera que lo traslade o ajuste.
14. Todo dispositivo puede reconstruir saldos desde sync: los cambios remotos transportan movimientos ademas de proyecciones de cuenta/tarjeta.

## Type Conventions

| Concept | Kotlin domain | Room | PostgreSQL / JSON |
|---------|---------------|------|-------------------|
| IDs | Typed wrapper over `UUID` | canonical UUID string | `UUID` / lowercase UUID string |
| Timestamp | `Instant` | epoch microseconds `INTEGER` | `TIMESTAMPTZ` / RFC 3339 UTC |
| Money | `Money(Long, Currency)` | `INTEGER` + currency text | `BIGINT` + currency code |
| Revision | positive `Long` | `INTEGER` | `BIGINT` / decimal integer |
| Day preference | `PreferredDay(1..31)` | `INTEGER` | `SMALLINT` |
| TEA | integer basis points | `INTEGER` | `INTEGER` |
| Enum | enum/sealed hierarchy | stable uppercase string | text + CHECK |
| Lifecycle | `ACTIVE`, `ARCHIVED` | stable string | text + CHECK |

Monetary validation:

- `MAX_MONEY_MINOR = 99_999_999_999_999`.
- Initial liquid balance: `0..MAX_MONEY_MINOR`.
- Credit line: `0..MAX_MONEY_MINOR`.
- Signed effects: `-MAX_MONEY_MINOR..MAX_MONEY_MINOR`.
- Addition/subtraction must fail on overflow rather than wrap.

## Domain Models

### Money

```text
Money
├── minorUnits: Long
└── currency: PEN | USD
```

Operations require matching currency. Formatting and masking are presentation concerns.

### Account

| Field | Type | Rules |
|-------|------|-------|
| `id` | `AccountId` | Stable, client-generated UUID |
| `userId` | `UserId` | Session owner; immutable |
| `alias` | String | Trimmed, 1..80 characters; duplicates allowed |
| `type` | `CASH`, `SAVINGS`, `BANK`, `DIGITAL_WALLET` | Immutable after create |
| `currency` | `PEN`, `USD` | Immutable after create |
| `preset` | `BCP`, `BBVA`, `INTERBANK`, `SCOTIABANK`, `BANCO_NACION`, `GENERIC` | Appearance seed only |
| `iconToken` | String | Approved design token |
| `colorToken` | String | Approved design token, not arbitrary secret/text |
| `initialBalance` | Money | Immutable audit snapshot, nonnegative |
| `openedAt` | Instant | Immutable; correction through movement |
| `lifecycle` | `ACTIVE`, `ARCHIVED` | Archive/reactivate rules apply |
| `revision` | Long | Server aggregate revision, starts at 0 locally |
| `createdAt`, `updatedAt` | Instant | Audit metadata, not conflict truth |

Quota behavior:

- `CASH` does not consume quota.
- Other account types consume one active-instrument slot.
- Cuenta Metas is not an EP-CTA account type in Sprint 2; EP-MET owns that virtual object and its exemption.

### Card

Domain is sealed even if persistence is flattened.

```text
Card
├── DebitCard(linkedAccountId required, credit fields absent)
└── CreditCard(linkedAccountId absent, limit/days required)
```

Common fields:

| Field | Type | Rules |
|-------|------|-------|
| `id`, `userId` | typed UUID | Stable and immutable |
| `alias` | String? | Trimmed, 1..80 when present; required and distinct when visible identity collides |
| `issuer` | String | Trimmed, 1..80; safe display value |
| `network` | `VISA`, `MASTERCARD`, `AMEX`, `OTHER` | `OTHER` usa identidad y apariencia genericas |
| `lastFourDigits` | String | Exactly four ASCII digits |
| `currency` | `PEN`, `USD` | Debit derives from linked account; credit explicit |
| `preset`, `iconToken`, `colorToken` | appearance values | No financial effect |
| `lifecycle` | `ACTIVE`, `ARCHIVED` | Active card consumes one quota slot |
| `revision` | Long | Optimistic concurrency |
| `createdAt`, `updatedAt` | Instant | Audit only |

Debit-only:

- `linkedAccountId` required.
- Linked account must be same owner/currency, `ACTIVE`, and type `SAVINGS` or `BANK`.
- Card balance is a view of linked account balance and is never persisted on card.

Credit-only:

- `linkedAccountId = null`.
- `creditLimitMinorUnits >= 0`.
- `billingDay` and `dueDay` in `1..31`.
- No debt exists merely by registration.
- `OTHER` permite registrar una red no listada sin inventar una marca; requiere issuer y alias explicitos y usa icono generico.

Issuer+network+last4 is not unique. A collision requires a distinct alias and explicit user continuation.

### FinancialMovement

| Field | Type | Rules |
|-------|------|-------|
| `id` | `MovementId` | Stable UUID |
| `operationId` | `OperationId` | Stable command identity |
| `sequence` | Int | `>= 0`; unique with operation ID |
| `userId` | `UserId` | Owner |
| `kind` | see below | Stable enum |
| `amountMinorUnits` | Long | Signed effect, exact |
| `currency` | Currency | Must match target |
| `accountId` | AccountId? | Target liquid account when applicable |
| `cardId` | CardId? | Target credit liability when applicable |
| `effectiveAt` | Instant | Financial date/time |
| `status` | `POSTED` | Original facts remain posted; neutralization uses a separate reversal |
| `reversesMovementId` | MovementId? | Explicit reversal link |
| `adjustsMovementId` | MovementId? | Explicit correction link |
| `createdAt` | Instant | Audit |

Movement kinds reserved by the shared domain:

- Sprint 2: `OPENING`, `ADJUSTMENT`, `REVERSAL`.
- Sprint 3 aprobado: `CREDIT_PURCHASE`, `CARD_PAYMENT_CASH`, `CARD_PAYMENT_LIABILITY`.

Sprint 2 code must not emit Sprint 3 kinds. A payment in Sprint 3 uses one operation with two sequenced movements; the reporting classifier marks the pair as transfer/amortization, not expense. Un cargo real de interes requeriria evidencia, confirmacion y un requisito posterior aprobado; una simulacion nunca lo emite.

### InstrumentSyncCommand

| Field | Type | Rules |
|-------|------|-------|
| `operationId` | UUID | Primary key |
| `userId` | UUID | Indexed owner |
| `commandType` | enum | Exact allowlist |
| `aggregateType` | `ACCOUNT`, `CARD`, `MOVEMENT` | Required |
| `aggregateId` | UUID | Required |
| `predecessorOperationId` | UUID? | Previous local mutation for the same owner/aggregate |
| `expectedRevision` | Long? | Current remote base when no predecessor; null when predecessor receipt supplies the base |
| `contractVersion` | Int | Starts at 1 |
| `payloadJson` | String | Canonical allowlisted JSON; immutable |
| `payloadHash` | String | Lowercase SHA-256 hex |
| `state` | internal state enum | See transitions |
| retry/lease fields | values | Safe technical metadata |

## Local Room Model

### AccountEntity (`accounts`)

| Column | Room type | Constraints |
|--------|-----------|-------------|
| `id` | TEXT | UUID; composite PK with owner |
| `user_id` | TEXT | not null, indexed |
| `creation_operation_id` | TEXT | not null, unique per owner |
| `alias` | TEXT | not null, length 1..80 |
| `type` | TEXT | allowed AccountType |
| `currency` | TEXT | PEN/USD |
| `preset_id` | TEXT | allowed preset |
| `color` | TEXT | approved token |
| `icon` | TEXT | approved token |
| `initial_balance_minor_units` | INTEGER | immutable, `0..MAX` |
| `opened_at` | INTEGER | immutable epoch micros |
| `is_archived` | INTEGER | Boolean |
| `remote_revision` | INTEGER | `>= 0` |
| `created_at`, `updated_at` | INTEGER | epoch micros |

Primary key `(user_id, id)`. Indexes: `(user_id, is_archived)`, `(user_id, type)`, unique `(user_id, creation_operation_id)`.

### CardEntity (`cards`)

| Column | Room type | Constraints |
|--------|-----------|-------------|
| `id` | TEXT | UUID; composite PK with owner |
| `user_id` | TEXT | not null, indexed |
| `creation_operation_id` | TEXT | not null, unique per owner |
| `account_id` | TEXT? | required for DEBIT; null for CREDIT |
| `alias` | TEXT? | null or length 1..80; required/distinct on collision |
| `type` | TEXT | DEBIT/CREDIT |
| `currency` | TEXT | PEN/USD |
| `network` | TEXT | VISA/MASTERCARD/AMEX/OTHER |
| `issuer` | TEXT | not null, length 1..80 |
| `last_four_digits` | TEXT | exactly `[0-9]{4}` |
| `credit_limit_minor_units` | INTEGER? | required for CREDIT; null for DEBIT |
| `billing_day`, `due_day` | INTEGER? | CREDIT `1..31`; null for DEBIT |
| `personal_tea_bps` | INTEGER? | optional nonnegative basis points; null when no personal rate is configured |
| `preset_id`, `color`, `icon` | TEXT | appearance |
| `is_archived` | INTEGER | Boolean |
| `remote_revision` | INTEGER | `>= 0` |
| `created_at`, `updated_at` | INTEGER | epoch micros |

Constraints/indices:

- Composite FK `(user_id, account_id) -> accounts(user_id, id)` for debit link.
- Primary key `(user_id, id)` and unique `(user_id, creation_operation_id)`.
- Index `(user_id, is_archived, type)`.
- Non-unique index `(user_id, issuer, network, last_four_digits)` to detect/warn collisions.
- Debit/credit nullability CHECKs repeated in domain validation.

### FinancialMovementEntity (`financial_movements`)

| Column | Room type | Constraints |
|--------|-----------|-------------|
| `id` | TEXT | composite PK with owner |
| `operation_id` | TEXT | not null |
| `operation_sequence` | INTEGER | `>= 0` |
| `user_id` | TEXT | not null, indexed |
| `kind` | TEXT | allowed movement kind |
| `amount_minor_units` | INTEGER | in signed range |
| `currency` | TEXT | PEN/USD |
| `account_id`, `card_id` | TEXT? | At least one target according to kind |
| `opening_account_id` | TEXT? | equals account ID only for OPENING; null otherwise |
| `effective_at` | INTEGER | epoch micros |
| `status` | TEXT | POSTED |
| `reverses_movement_id`, `adjusts_movement_id` | TEXT? | Self references |
| `created_at` | INTEGER | immutable |

Primary key `(user_id, id)`. Unique `(user_id, operation_id, operation_sequence)` and `(user_id, opening_account_id)`. Owner-composite FKs cover account/card and reversal/adjustment targets. Indexes `(user_id, account_id, status, effective_at)` and `(user_id, card_id, status, effective_at)`.

Opening rules:

- At most one `OPENING` per account is structurally enforced locally through non-null `opening_account_id` uniqueness; account creation/pull transactions enforce existence atomically. Remote PostgreSQL uses an equivalent unique partial index plus command checks.
- `amount`, `currency`, `effectiveAt` equal the account audit snapshot.
- Zero opening remains a row.

### InstrumentSyncOutboxEntity (`instrument_sync_outbox`)

| Column | Room type | Constraints |
|--------|-----------|-------------|
| `operation_id` | TEXT | composite PK with owner |
| `user_id` | TEXT | not null, indexed |
| `command_type` | TEXT | allowlisted |
| `aggregate_type`, `aggregate_id` | TEXT | not null |
| `predecessor_operation_id` | TEXT? | prior command for same owner/aggregate |
| `expected_revision` | INTEGER? | `>= 0` |
| `contract_version` | INTEGER | `= 1` initially |
| `payload_json`, `payload_hash` | TEXT | immutable |
| `state` | TEXT | internal sync state |
| `attempt_count` | INTEGER | `>= 0` |
| `next_attempt_at`, `lease_until` | INTEGER? | epoch micros |
| `last_error_code` | TEXT? | safe code only |
| `created_at`, `updated_at` | INTEGER | epoch micros |

Internal states and UI projection:

| Internal | UI `SyncStatus` | Meaning |
|----------|-----------------|---------|
| `PENDING`, `IN_FLIGHT`, `WAITING_FOR_AUTH` | `PENDING` | Work remains |
| `SYNCED` | `SYNCED` | Remote receipt reconciled |
| `CONFLICT`, `ERROR` | `ERROR` | User/retry policy required |

Transitions:

```text
PENDING -> IN_FLIGHT -> SYNCED
                    -> PENDING          transient failure
                    -> WAITING_FOR_AUTH no/mismatched session
                    -> CONFLICT         stale aggregate/collision
                    -> ERROR            deterministic terminal error
WAITING_FOR_AUTH -> PENDING             same owner reauthenticates
IN_FLIGHT -> PENDING                    lease expires
```

Primary key is `(user_id, operation_id)`. Payload and IDs never mutate during transitions.

Causal rules:

- Create has no predecessor. Each later locally queued mutation points to the aggregate's current tail operation.
- Only one unsuperseded tail exists per `(user_id, aggregate_type, aggregate_id)`; appending a command and advancing that tail occurs in the same Room transaction.
- A successor is not lease-eligible until its predecessor is `SYNCED`. If the predecessor ends in `CONFLICT`/`ERROR`, successors remain blocked for explicit resolution.
- Without a predecessor, a mutable command carries the last reconciled `expectedRevision`. With a predecessor, it stores null because the future accepted revision is not guessed; after reconciliation, the predecessor receipt's `acceptedRevision` becomes the authoritative compare-and-set base. The server rejects cross-owner, cross-aggregate, missing or non-applied predecessors.
- Pull never overwrites unsynced local fields blindly; it stores/reconciles the remote projection under the conflict policy while preserving local commands.

## Atomic Local Commands

`AccountDao` y `CardDao` publican consultas reactivas `Flow<List<...>>` y ejecutan los comandos atomicos de su aggregate. `FinancialMovementDao` concentra sumas/proyecciones owner-scoped y `InstrumentSyncDao` concentra leasing/transiciones. Ningun DAO se expone fuera de data.

### CreateAccount

One Room transaction:

1. Validate existing operation/hash.
2. Count active quota-consuming accounts/cards for owner.
3. Evaluate verified entitlement; Free fifth instrument fails before inserts.
4. Insert account.
5. Insert exactly one opening movement.
6. Insert outbox command containing account ID, stable `openingMovementId` and opening data.
7. Commit all or roll back all.

`CASH` skips the quota increment but still gets opening movement.

### RegisterDebitCard

One transaction validates linked account owner, lifecycle, type and currency; counts quota; checks collision acknowledgement/alias; inserts card and outbox. It does not insert a movement.

### RegisterCreditCard

One transaction validates limit/days/currency, counts quota, checks collision acknowledgement/alias, inserts card and outbox. It does not insert liability or expense.

### UpdateAppearance

Updates local alias/preset/icon/color immediately and appends to the causal outbox. `remoteRevision` remains the last reconciled server value; it is never optimistically incremented. Opening facts, card type/link/line and movements cannot appear in payload.

### Archive / Reactivate

- Archive updates local lifecycle and outbox without incrementing `remoteRevision`, blocks new asset operations and frees quota; history remains.
- Archiving an account disables new operations through linked debit cards without deleting links.
- An archived account may be reactivated; corrections/reversals remain available because they preserve truth. Archive never substitutes for transferring a balance.
- An archived credit card cannot accept purchases, but a nonzero liability remains in current summaries and may receive an amortizing payment until settled.
- Reactivate validates quota and debit-link compatibility atomically.

### Delete

- A persisted account always has `OPENING`, so it is archive-only.
- Card hard delete requires no movements, debt or dependents. It records a tombstone command before local projection removal so stale devices cannot resurrect it.

## Derived Projections

### Liquid Balance

For each `(user, account, currency)`:

```text
balanceMinor = SUM(amountMinorUnits WHERE status = POSTED)
```

No sum across currencies. Archived accounts remain queryable for historical periods and are omitted only from active-operation selectors.

### Real Money Dashboard

```text
realMoney[PEN] = SUM(all non-deleted liquid account balances in PEN)
realMoney[USD] = SUM(all non-deleted liquid account balances in USD)
```

Debit cards add zero. Credit cards, limits and unused credit add zero. Archived accounts may be hidden from the default instrument list and cannot accept new operations, but their balances remain in current money totals; archive is not a financial transfer.

### Opening Correction

An opening amount/date correction never edits or voids the original. One idempotent operation appends two sequenced movements linked to the opening:

```text
sequence 0: REVERSAL   amount = -originalOpeningAmount   effectiveAt = originalOpenedAt
sequence 1: ADJUSTMENT amount = +correctedOpeningAmount  effectiveAt = correctedOpenedAt
```

The command carries client-generated IDs for both rows and the server rejects collision with the opening ID or either other. Both rows exist even when either amount is zero. This moves the effect between reporting periods without changing history; current balance changes only by `corrected - original`. A later correction reverses the currently effective correction pair and posts its replacement under a new operation, never the immutable opening row.

### Credit Summary (Sprint 3)

```text
usedCredit = confirmed posted liability effects
availableCredit = max(creditLimit - usedCredit, 0)
utilization = if creditLimit == 0 then Unavailable else usedCredit / creditLimit
```

Used credit may exceed line. Percent may exceed 100%.

### Effective Day

```text
effectiveDay(yearMonth, preferredDay) = min(preferredDay, yearMonth.lengthOfMonth)
```

Preferred value remains unchanged.

## Remote PostgreSQL Model

Canonical projections mirror domain semantics, not Room annotations:

- `public.accounts`
- `public.cards`
- `public.transactions` as the canonical remote transaction projection; local `financial_movements` must map to this transaction/ledger contract and cannot be a competing remote authority
- `internal.ledger_entries`
- `internal.command_receipts`
- `internal.sync_changes`
- `private.financial_user_heads`

Before final naming, the checked-in baseline must be clean-reset and compared with this model. Renames happen only in a forward migration.

Observed linked-project schema is comparison evidence, not an accepted target. T075 confirmed that its migration history and schema drift from the checked-in local baseline: remote `cards` retains legacy alias columns, remote `app_notifications` has `deleted_at`, and remote `internal.sync_changes` uses `occurred_at` while local uses `created_at`. Canonical `credit_products`, `credit_installments` and `credit_payment_allocations` are present in both. Purchase/payment RPCs still use the legacy movement path and must be reconciled through new forward-only migrations; preserve remote rows/history and do not edit applied migration files.

Remote-only rules:

- Composite ownership references include `user_id`.
- Public user-owned tables have RLS enabled and forced.
- Ledger, receipts, heads and sync live outside exposed schemas.
- Public projection views use `security_invoker=true`.
- Receipt retention lasts for the lifetime of the user account; deletion of the auth account cascades according to approved account-deletion policy.
- `sync_changes.sequence` is server-generated and ordered per user. The complete owner change stream and deletion tombstones remain for the lifetime of the user account so a fresh device can bootstrap by pulling from sequence zero without a snapshot race.

### Command Receipt

| Field | Rule |
|-------|------|
| `user_id`, `operation_id` | Composite primary identity |
| `contract_version`, `command_type` | Included in canonical hash |
| `payload_hash` | Server-computed SHA-256 |
| `result_code` | APPLIED/DUPLICATE-compatible deterministic result |
| `response_json` | Safe projection only, no raw payload |
| `created_at` | Server time |

The RPC locks/reads receipt first. Existing same hash returns `DUPLICATE`; different hash returns `OPERATION_COLLISION` with no effect.

Remote revision rules: create/register accepts expected absence and returns revision `1`. Each accepted mutable account/card command increments exactly once. Local `remoteRevision` changes only from a receipt or pull projection. Movement facts use immutable event version `1`, not aggregate revision.

## Sprint 3 Additions

The following are projections/uses of existing canonical entities, not additional parallel remote tables:

- `public.credit_products` is the sole reference catalog. Reconcile the official `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` snapshot dated `2026-09-24` into this canonical table: 44 confirmed products (BCP 18, BBVA 10, Interbank 16). Extend the existing table only where fields are missing: `reference_tea_pen_min_bps`, `reference_tea_pen_max_bps`, `reference_tea_usd_min_bps`, `reference_tea_usd_max_bps` (nullable integer basis points); `membership_fee_pen_minor` and `membership_fee_usd_minor` (nullable `BIGINT`); `membership_terms`; `catalog_as_of_date`; `source_reference`; `source_status`; and `source_notes`. Preserve published ranges and source caveats. An unpublished or conflicted value is explicit, never inferred. Keep the legacy `reference_tea_bps` only for compatibility; do not use it as a currency-specific estimate unless its currency and source are explicit. `effective_to` is populated only for a source-published end date; software does not mark the snapshot stale by age. The UI label is `Tasa referencial al 24/09/2026`.
- `public.cards.personal_tea_bps` stores the user's per-card rate. `CardEntity` must persist the same nullable integer in Room; changing it affects future simulations only.
- `public.credit_installments` is the persisted confirmed-purchase schedule. Its local Room projection uses owner-scoped UUIDs, `transaction_id`, installment number, due date, `principal_minor: Long`, `interest_minor: Long`, status and revision. It does not persist a draft simulation.
- `public.credit_payment_allocations` is the immutable relation between a payment transaction and installments. Its Room projection uses `allocated_minor: Long > 0` and owner-composite references. Allocate only outstanding installment principal in ascending `due_date`; for equal dates, order by purchase occurrence time, installment number and stable installment ID. A partial allocation reduces the oldest installment's principal and its status becomes `PARTIALLY_PAID`; do not advance to a later installment while the older one has a balance. The allocation sum equals the payment transaction amount. Allocation rows and installment status changes are applied atomically with the payment transaction.
- Existing `public.app_notifications` remains the notification-center projection owned by EP-NOT. EP-CTA produces an idempotent threshold-crossing event; it does not add `credit_alerts` or another notifications table.
- Simulated schedules are transient domain/UI values. They do not require an `installment_simulations` table or ledger entries. A confirmed purchase stores only its confirmed transaction and canonical installment rows.

No separate `credit_limit_history` table is added in this sprint. A line reduction changes future available-credit calculations and never truncates existing debt. Any effective-dated line-history requirement must be explicitly approved before it expands this Sprint 3 scope.

### Room projections for Sprint 3

Add the missing local projections for `credit_installments` and `credit_payment_allocations` in the next Room schema version. Use Kotlin `Long` for every minor-unit value, validate amounts/ranges in domain mappers, index all queries by `(user_id, ...)`, and use owner-composite foreign keys. Do not persist a second current debt or available-credit balance; derive both from confirmed ledger effects and the configured credit limit.

Threshold state remains derived from consecutive committed utilization values. Emit a stable event identity from the operation that crosses a threshold; retries of that operation cannot emit a duplicate. A fall below a threshold re-arms the next crossing. Persist/present the event only through the agreed EP-NOT `app_notifications` contract.

## State Lifecycles

### Instrument

```text
ACTIVE -> ARCHIVED -> ACTIVE
CARD_DRAFT/unused -> HARD_DELETED + tombstone
```

Account hard delete is unreachable after successful creation because `OPENING` exists.

### Movement

```text
POSTED -> POSTED + separate ADJUSTMENT/REVERSAL
```

### Capture Candidate (Sprint 3)

Required fields include stable candidate ID/owner, source kind, source reference/fingerprint, captured timestamp, extracted facts, per-candidate confidence assessment, deduplication key and review metadata. No raw notification/OCR payload is synchronized, logged or sent to telemetry; sensitive Yape OCR remains device-local. Any later evidence retention requires a separately approved policy. Provenance remains sufficient to explain the candidate without retaining raw evidence. No candidate writes a movement.

```text
PENDING -> CONFIRMED -> financial command
        -> REJECTED  -> no financial effect
```

Confirmation always issues the same typed financial command path used by manual entry. Credit-card purchases require explicit confirmation regardless of confidence; uncertain instrument matching is never guessed.

Capture validation covers source/reference and confidence presence, cross-source deduplication, no financial effect before review, mandatory credit-purchase confirmation, local-only OCR/raw evidence handling and manual correction when extraction fails.

## Migration Plan

### Room 10 -> 11 (planned from the checked-in database baseline)

1. Confirm the checked-in Room database is still version 10 immediately before migration authoring.
2. Add the two canonical credit projections plus the `personal_tea_bps` card field; do not add parallel debt, rate-catalog, simulation or notification tables.
3. Preserve all existing version-10 rows and relationships; do not backfill invented financial records.
4. Register the next forward migration and export the matching schema JSON (expected `11.json`).
5. Test representative v10 data, reopen/restart behavior, owner isolation and exact minor-unit round trips.

### Supabase

1. Validate the checked-in versioned financial baseline with a clean PostgreSQL 17 reconstruction.
2. Compare the rebuilt schema with the linked project; classify and review drift.
3. Add forward-only EP-CTA/hardening migration(s); do not invent a replacement baseline or edit applied history.
4. Reconcile `referential_rate_catalog` and the dated 44-product official snapshot into canonical `credit_products`; add only missing per-currency TEA/range, membership, snapshot, source-reference and source-status columns. Do not apply age-based expiration.
5. After the clean-reset baseline gate, extend canonical `register_transaction_v1` for `CARD_PURCHASE` and add canonical `allocate_credit_payment_v1` with FIFO partial allocation. Reconcile `confirm_credit_purchase_v1` and `pay_credit_card_v1` as delegates or retire them. Both canonical commands use receipts/idempotency, `credit_installments`, `credit_payment_allocations`, `internal.ledger_entries`, and owner-scoped sync changes.
6. Align preferred closing/due day constraints to 1..31 and retain effective-day clamping without changing stored preferences.
7. Add pgTAP for schema, commands, receipts, ledger, installment sums, allocations, sync, RLS/grants and privacy.
8. Run security/performance advisors; release-blocking findings must be resolved.
