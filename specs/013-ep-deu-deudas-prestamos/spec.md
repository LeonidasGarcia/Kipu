# Feature Specification: EP-DEU — Deudas y Préstamos (Sprint 5)

**Feature Branch**: `[013-ep-deu-deudas-prestamos]`

**Created**: 2026-10-08

**Status**: Draft

**Input**: Sprint 5 scope for HU-26, HU-27, HU-28, and HU-29 from Kipu Product Backlog V4.2, supported by process P17 and Architecture and Data V4.2.

## User Scenarios & Testing

### User Story 1 — Registrar y administrar una deuda propia (Priority: P1)

Como persona usuaria, quiero registrar una obligación que debo y distinguir si recibí el dinero ahora o si ya formaba parte de mi situación financiera, para conocer el pasivo sin inventar ingresos ni alterar el saldo inicial.

**Why this priority**: HU-26 provides the debt lifecycle required by repayments and schedules, while preserving the distinction between new disbursement and historical opening.

**Independent Test**: Register one new debt with a receiving account and one historical debt without a cash movement. Compare the resulting cash, liability, and operating income with the expected values, then edit descriptive details and confirm those balances remain unchanged.

**Acceptance Scenarios**:

1. **Given** a user receives S/500 into an active account as a new loan, **When** the user confirms a PAYABLE obligation, **Then** the account and outstanding liability each increase by S/500 and operating income does not increase.
2. **Given** a user records a debt that predates the app or is already reflected in opening balances, **When** the user confirms it as historical, **Then** the liability is recorded without increasing cash.
3. **Given** a debt has confirmed payments, **When** the user edits its counterparty name or notes, **Then** the principal, payments, and derived balance remain unchanged.
4. **Given** a debt has no related financial operation or event, **When** the user confirms its removal, **Then** the obligation and any opening effect are removed consistently.
5. **Given** a debt has a related payment or other financial history, **When** the user requests physical deletion, **Then** the system preserves the history and offers a non-destructive lifecycle action.
6. **Given** the user's Free plan already has two active obligations of either type, **When** the user tries to create another active debt, **Then** the system blocks the new obligation without changing existing records.

---

### User Story 2 — Registrar dinero prestado a otra persona (Priority: P1)

Como persona usuaria, quiero registrar el dinero que presto y los préstamos históricos, para conocer lo que me deben sin duplicar salidas de dinero.

**Why this priority**: HU-27 models receivables and must be available before collecting principal or creating schedules.

**Independent Test**: Create a new RECEIVABLE loan from an active account and a historical loan already included in the opening balance. Confirm the new loan transfers value from cash to receivables, while the historical loan does not reduce cash a second time.

**Acceptance Scenarios**:

1. **Given** an active account has S/300, **When** the user lends S/100 from that account and confirms, **Then** cash decreases to S/200, the receivable increases by S/100, and operating expenses do not increase.
2. **Given** a new loan has no selected active account of the same currency, **When** the user attempts to confirm it, **Then** the system requests a valid source account and creates no loan or financial movement.
3. **Given** a historical loan was already reflected in the account's opening balance, **When** the user records it, **Then** the receivable is established without another cash deduction.
4. **Given** a loan has confirmed collections, **When** the user edits its counterparty name or notes, **Then** its collections and remaining principal are preserved.
5. **Given** a loan has a related disbursement, **When** the user requests physical deletion, **Then** the system preserves the linked financial history and offers a non-destructive lifecycle action.

---

### User Story 3 — Pagar o cobrar principal e interés por separado (Priority: P1)

Como persona usuaria, quiero registrar una amortización o cobranza con principal e interés real separados, para que el saldo de la obligación y mis resultados operativos sean correctos.

**Why this priority**: HU-28 is the only valid way to change principal after opening and is required before HU-29 can mark an obligation settled.

**Independent Test**: Apply a partial principal payment and a partial principal collection, including a real-interest amount. Verify that the account changes by the total cash flow, the obligation changes only by principal, and only real interest affects operating income or expense.

**Acceptance Scenarios**:

1. **Given** a PAYABLE debt has S/200 outstanding, **When** the user pays S/50 of principal, **Then** cash decreases by S/50, the liability decreases to S/150, and operating expenses do not increase.
2. **Given** a RECEIVABLE loan has S/200 outstanding, **When** the user collects S/50 of principal, **Then** cash increases by S/50, the receivable decreases to S/150, and operating income does not increase.
3. **Given** a user pays S/50 of principal and S/5 of real interest on a PAYABLE debt, **When** the user confirms the settlement, **Then** cash decreases by S/55, the liability decreases by S/50, and only S/5 is recognized as operating expense.
4. **Given** a user collects S/50 of principal and S/5 of real interest on a RECEIVABLE loan, **When** the user confirms the collection, **Then** cash increases by S/55, the receivable decreases by S/50, and only S/5 is recognized as operating income.
5. **Given** the outstanding principal is S/30, **When** the user tries to pay or collect S/40 of principal, **Then** the operation is rejected without changing cash, principal, or history.
6. **Given** a confirmed settlement is later voided or corrected, **When** the correction is applied, **Then** the principal and lifecycle state are recalculated from valid events and the correction remains auditable.
7. **Given** an identical settlement is retried after a connectivity failure, **When** the system receives the retry, **Then** it returns the prior result and does not create a second financial effect.

---

### User Story 4 — Planificar cuotas, recibir avisos y cerrar obligaciones (Priority: P2)

Como persona usuaria, quiero planificar cuotas y vencimientos sin que se interpreten como pagos, y cerrar una obligación solo cuando su saldo real esté resuelto.

**Why this priority**: HU-29 builds on both obligation types and confirmed settlement behavior. Its debt-specific reminder is in Sprint 5; generalized reminder configuration and cross-object deduplication remain in HU-44 for Sprint 6.

**Independent Test**: Generate an installment schedule whose principal sums to the opening principal, confirm that it creates no account movement, then exercise a due reminder, a valid zero-balance closure, and a closure request with remaining principal.

**Acceptance Scenarios**:

1. **Given** a S/100 principal is divided into three installments without interest, **When** the user confirms the schedule, **Then** the installment principal totals exactly S/100 and no cash, income, expense, or outstanding-principal amount changes.
2. **Given** an installment has a selected reminder lead time, **When** that lead time is reached, **Then** the user receives a debt reminder that does not record or imply a payment.
3. **Given** an obligation has zero outstanding principal, **When** the user confirms closure, **Then** it becomes settled and no longer consumes an active-obligation slot.
4. **Given** an obligation still has S/20 outstanding, **When** the user tries to mark it settled, **Then** closure is rejected unless the user records an explicit, auditable adjustment or forgiveness.
5. **Given** a user cancels an obligation with an outstanding balance, **When** cancellation is confirmed, **Then** cancellation is not treated as payment and the remaining balance and history remain visible.
6. **Given** a closed obligation's payment is voided and a balance returns, **When** the balance is recalculated, **Then** the obligation becomes active again and the restored balance is shown.
7. **Given** the due date changes or an installment is cancelled, **When** the schedule is updated, **Then** obsolete pending reminders are replaced or cancelled and a retry does not create duplicates.

### Edge Cases

- A principal, account, and debt currency do not match; confirmation must stop without partial records.
- A Free user reaches the combined limit of two active PAYABLE and RECEIVABLE obligations; existing records and history remain intact.
- A selected account or obligation is inactive, deleted, or owned by another user.
- A payment is retried, arrives after an earlier command, or uses an outdated obligation revision.
- A schedule has a remainder that cannot be split evenly into minor currency units; the remainder must be distributed so the sum remains exact.
- An installment becomes due while notifications are denied or unavailable; no payment is inferred from the missing reminder.
- A concurrent edit or settlement changes the balance before confirmation; stale input must not silently overwrite the newer state.
- The user is offline after prior authentication; an accepted local operation remains visible and sync retry must not duplicate it.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST distinguish obligations owed by the user (PAYABLE) from money lent to others (RECEIVABLE).
- **FR-002**: The system MUST record a positive principal, currency, counterparty, opening date, optional due date, and optional notes for each obligation.
- **FR-003**: A PAYABLE opening MUST ask whether money was received now. If yes, it MUST increase the selected same-currency account and the liability by equal principal amounts without creating operating income. If historical, it MUST create no cash movement.
- **FR-004**: A new RECEIVABLE opening MUST require an active, user-owned, same-currency source account and MUST decrease that account while increasing receivables by equal principal amounts without creating operating expense.
- **FR-005**: A historical RECEIVABLE whose disbursement is already reflected in opening balances MUST NOT decrease cash again and MUST retain an auditable opening basis.
- **FR-006**: The system MUST keep descriptive edits separate from financial corrections; editing a counterparty name or note MUST NOT change principal or confirmed settlements.
- **FR-007**: The system MUST derive principal outstanding from the opening amount and valid confirmed principal events. A stored, editable balance MUST NOT override those events.
- **FR-008**: Every settlement MUST capture principal separately from any real interest and MUST validate that principal is positive and does not exceed the current outstanding principal.
- **FR-009**: A PAYABLE settlement MUST reduce cash by principal plus real interest, reduce the liability by principal only, and recognize only real interest as operating expense.
- **FR-010**: A RECEIVABLE collection MUST increase cash by principal plus real interest, reduce receivables by principal only, and recognize only real interest as operating income.
- **FR-011**: The system MUST reject an overpayment or over-collection of principal without a partial write or silent truncation.
- **FR-012**: A correction or void of a related settlement MUST recalculate principal and lifecycle state while retaining an auditable history.
- **FR-013**: Retried or duplicated financial commands MUST NOT create duplicate cash, ledger, principal, or installment effects.
- **FR-014**: A schedule MUST contain positive principal amounts and due dates; the sum of planned principal MUST equal the configured principal exactly, including any minor-unit remainder.
- **FR-015**: Creating, editing, or reaching the due date of a schedule MUST NOT itself create a financial movement, income, expense, payment, or reduction of principal.
- **FR-016**: A due reminder MUST notify the user at the chosen lead time when notification delivery is available. It MUST NOT imply that a payment occurred.
- **FR-017**: Changing or cancelling a due date or installment MUST replace or cancel its pending debt reminder idempotently. General reminder settings and cross-object reminder deduplication remain HU-44 scope.
- **FR-018**: An obligation MUST be marked settled only when its derived principal is zero or after an explicit, auditable adjustment or forgiveness resolves the remainder.
- **FR-019**: Cancellation MUST NOT be presented as payment or conceal outstanding principal.
- **FR-020**: Physical deletion MUST be permitted only when no related financial operation or event would be lost. An obligation with history MUST remain consultable through a non-destructive lifecycle state.
- **FR-021**: The Free plan MUST allow at most two active obligations combined across PAYABLE and RECEIVABLE. Plan limits MUST NOT delete, rewrite, or hide valid financial history.
- **FR-022**: Previously authenticated users MUST be able to consult and manually record obligations offline; accepted local changes MUST persist atomically and synchronize idempotently when connectivity returns.
- **FR-023**: Each obligation, installment, event, and related financial operation MUST be accessible only to its owner. Account ownership MUST be verified before any cash movement is confirmed.
- **FR-024**: A conflict that could change financial truth, including a stale revision, MUST be surfaced for resolution rather than silently overwriting newer data.
- **FR-025**: Sprint 5 delivery MUST cover only HU-26 through HU-29 for EP-DEU. HU-24 application of movements to obligations (Sprint 6), HU-40 calendar (Sprint 7), and HU-44 generalized reminders (Sprint 6) MUST remain separate follow-on scope.

### Key Entities

- **Obligation**: A PAYABLE liability or RECEIVABLE loan with owner, counterparty, opening principal, currency, dates, notes, status, and revision.
- **Debt event**: An auditable opening, principal payment or collection, adjustment, or forgiveness linked to its obligation and, when applicable, a confirmed financial movement.
- **Installment**: A planned portion of principal with a sequence number, due date, planned amount, and lifecycle state. It is not proof of payment.
- **Settlement**: A confirmed PAYABLE payment or RECEIVABLE collection with separately identified principal and real interest, account, date, and resulting financial effect.
- **Financial movement**: The account cash change and related financial history created by an opening or settlement, linked to the obligation event that caused it.
- **Debt reminder**: A scheduled notice associated with an obligation or installment and a selected lead time; it has no accounting effect.
- **Plan allowance**: The effective plan capability and active-obligation count used to decide whether a new obligation can be created.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In all acceptance cases, the displayed account cash and obligation principal match the expected result to the smallest supported currency unit.
- **SC-002**: 100% of principal-only openings, payments, and collections create no operating income or expense; only separately entered real interest affects operating results.
- **SC-003**: 100% of schedules sum exactly to their planned principal and create zero financial movement until a payment or collection is confirmed.
- **SC-004**: 100% of over-limit principal settlements, invalid account references, owner mismatches, and stale financial commands are rejected without partial financial effects.
- **SC-005**: Replaying any confirmed opening, settlement, correction, or reminder scheduling request produces no duplicate financial effect or duplicate pending reminder.
- **SC-006**: No obligation with a non-zero derived principal can be shown as settled unless an explicit auditable adjustment or forgiveness resolves that amount.
- **SC-007**: A previously authenticated user can record an obligation while offline, see it locally before reconnecting, and synchronize it once without duplicating its financial effect.
- **SC-008**: Users can distinguish scheduled installments from completed payments in every debt list and detail view.

## Assumptions

- Sprint 5 includes only EP-DEU HU-26, HU-27, HU-28, and HU-29, totaling 29 story points. The Sprint 5 roadmap also includes EP-CCO HU-16/17 and EP-PLA HU-55; this specification does not add those stories to EP-DEU.
- HU-07, HU-18/HU-19, and HU-57 are blocking dependencies as listed per story in the backlog. HU-42 is a partial dependency of HU-29. HU-24, HU-40, and HU-44 are later or related work and are not pulled into this sprint.
- The Free limit of two active obligations is combined across debts owed and loans given. Other plan allowances follow the verified plan policy already used by Kipu.
- A historic opening records the principal already owed or receivable at the opening date. It does not imply that cash moved during registration.
- An installment schedule is a principal plan; interest is captured only when a real payment or collection is confirmed.
- The selected account must be active, owned by the current user, and use the obligation's currency. Currency conversion is outside this feature.
- A reminder is an ordinary notice, not a payment instruction or an executed payment. Delivery depends on the user's notification availability; the obligation remains usable without notification permission.
- Existing users manage their own obligations. Shared obligations, automatic bank access, and execution of real payments are outside scope.
- Source-of-truth references: Kipu Product Backlog V4.2 (HU-26–HU-29), process P17, Architecture and Data V4.2, the Sprint 5 roadmap, and the project constitution.
