# Sprint 3 Credit Command Contract

**Feature**: EP-CTA HU-09..HU-13 only  
**Status**: Planning contract; not implemented

This contract reconciles Sprint 3 with the canonical `transactions`, `internal.ledger_entries`, `credit_installments`, `credit_payment_allocations`, `credit_products`, `cards` and `app_notifications` model. It does not authorize a parallel financial ledger or duplicate catalog/notification tables.

## Purchase confirmation — HU-13

Use the existing canonical `register_transaction_v1` command after extending and validating its schema to support a confirmed credit purchase. The command carries:

- client-generated operation/transaction identity for idempotent retry;
- the credit card ID, category, currency, positive `amount_minor`, occurred-at instant, installment count 1..36, and confirmed purchase details;
- `transaction_type = EXPENSE` and `operation_kind = CARD_PURCHASE`;
- only actual confirmed purchase data; preview/simulation interest is never sent as posted financial data.

One accepted operation creates one expense transaction, recognizes principal once in expense reporting and increases the card liability once. For installment purchases, it inserts the canonical `credit_installments` rows atomically. It returns the original result for the same operation and payload; reuse of an operation identity with a different payload is a conflict. A rejected or unconfirmed candidate has no transaction, ledger, installment or debt effect.

For an interest-free purchase, split integer minor units with `base = total_minor / count` and `remainder = total_minor % count`; add one minor unit to installments 1 through `remainder`. Therefore PEN 100.00 over three installments is PEN 33.34, PEN 33.33, PEN 33.33.

## Card payment — HU-12

`allocate_credit_payment_v1` is the single canonical database command. After T075 validates the checked-in baseline, it must atomically:

1. validate owner, active source account, card ownership/type, same currency, sufficient liquid funds, and payment amount not exceeding posted card debt;
2. create one `transaction_type = TRANSFER`, `operation_kind = CARD_PAYMENT` transaction with immutable operation identity;
3. create equal and opposite source-asset and card-liability ledger effects;
4. create canonical `credit_payment_allocations` rows in ascending `due_date` order and update installment payment states;
5. write the idempotency receipt and owner-scoped sync changes.

Allocate only outstanding installment principal. Never skip an older unpaid installment. If the payment is smaller than the oldest installment's outstanding principal, allocate the payment to that installment, retain its unpaid principal and partial state, and stop. Continue to later installments only after the current one is fully paid. For equal `due_date`, use purchase occurrence time, installment number and stable installment ID as deterministic tie-breaks. The sum of allocation rows must equal the transfer amount; reject atomically if the full amount cannot be allocated to posted debt.

The transfer reduces the liquid asset and credit liability by the same amount. It is not an `EXPENSE`, creates no operational expense, and cannot re-recognize purchase principal. A retry returns the same receipt and does not apply allocations or balance effects twice.

## Reference rates — HU-11 / HU-13

- Use canonical `credit_products` for the official catalog snapshot and `cards.personal_tea_bps` for a card's personal rate. Do not create a parallel catalog table.
- The legacy single-currency `reference_tea_bps` field is compatibility-only; do not use it for an estimate unless its currency and source are explicit.
- The Sprint 3 snapshot is `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`, cut off on `2026-09-24`, with 44 confirmed products: BCP 18, BBVA 10 and Interbank 16. Exclude the pending Interbank Benefit/Blue candidate from that confirmed set.
- Preserve source-supported purchase TEA using `reference_tea_pen_min_bps`, `reference_tea_pen_max_bps`, `reference_tea_usd_min_bps` and `reference_tea_usd_max_bps`; equal min/max values represent a single published value. Preserve membership fees in their source currency (`membership_fee_pen_minor`, `membership_fee_usd_minor`) and the source's conditions in `membership_terms`.
- Preserve `catalog_as_of_date`, source reference, source status and source notes. A missing currency value is shown as not published/not disaggregated; a conflict remains a conflict and is not selected for simulation. Do not infer a rate or membership condition.
- Display `Tasa referencial al 24/09/2026` with a non-contractual disclaimer. Do not expire the snapshot automatically by age. `effective_to` is used only when the source explicitly publishes an end date.
- TEA basis points convert to decimal by dividing by 10,000. `TEM = (1 + TEA)^(1/12) - 1`.
- French fixed-payment estimate: `payment = principal * TEM / (1 - (1 + TEM)^(-n))`; at TEM zero use `principal / n`.
- Simulations are transient estimates and create no `transactions`, ledger entries, payment allocations or installment records.

## Task ownership

- T080 owns the canonical database purchase command and its compatibility behavior.
- T065 owns Android wiring to T080.
- T081 owns the canonical database FIFO payment command and its compatibility behavior.
- T058 owns Android wiring to T081.
- Android integrations call only the canonical commands; legacy movement-only RPCs cannot write a separate ledger path.

## Utilization event — HU-10 / HU-42 integration

For each committed utilization transition, the producer may emit one event per newly crossed threshold in `[5000, 8000, 10000]` basis points. The event includes a stable event identity derived from the accepted operation, owner-scoped card reference, operation reference, threshold, utilization and event time. A transition below a threshold re-arms that threshold; a repeated command does not emit another event. A single transaction can cross multiple thresholds and emits each applicable threshold once.

Event type: `CREDIT_UTILIZATION_THRESHOLD_CROSSED`. EP-NOT/HU-42 owns persistence, read state and in-app presentation through existing `app_notifications`. Agree the event identity/deduplication field with EP-NOT before implementation. OS notification permission never controls whether the in-app event exists.

## Security and consistency

- Derive `user_id` from the authenticated session; never trust an owner identifier in client payload.
- Enforce owner-composite references, RLS and exact RPC grants for cards, transactions, installments and allocations.
- Client money values use Kotlin `Long` minor units; database values use `BIGINT`; reject overflow and non-positive purchase/payment amounts.
- Serialize writes for a card and use canonical receipts/operation hashes so simultaneous retries cannot double-apply debt, payment, schedule rows or threshold events.
- Retire or turn existing `pay_credit_card_v1` and `confirm_credit_purchase_v1` into compatibility delegates to the canonical operations. They must not remain an independent `financial_movements` accounting path.

## Required contract tests

- Same operation and payload returns duplicate/original result; different payload with same identity is rejected.
- Purchase stores one expense principal and installment principal sums exactly to the confirmed amount.
- Card payment allocates FIFO by `due_date`; a partial payment reduces the oldest installment principal, allocation sum equals the payment and equal asset/liability reductions create no expense entry.
- The official catalog contains 44 confirmed rows (18 BCP, 10 BBVA, 16 Interbank), shows the fixed as-of label, source conditions/caveats, and does not auto-expire by age.
- A token/card or source account owned by another user is rejected without effects.
- Concurrent threshold-crossing operations are serialized and deduplicated; falling below then recrossing creates a new event.
- RLS blocks cross-user reads/writes and direct client DML cannot bypass the RPC boundary.
