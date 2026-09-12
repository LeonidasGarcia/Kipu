<!--
Sync Impact Report
Version change: 1.1.0 -> 2.0.0
Modified principles:
- I. Financial Integrity First: Added confirmed-expense and category-hierarchy safeguards for
  budget consumption.
- II. Local-First, Retry-Safe Operation: Replaced the fixed offline Premium duration with a
  verified lease bounded by known entitlement validity and resistant to local reset attempts.
- III. Security and Privacy by Design: Scoped Row Level Security to user-owned data in
  Supabase/PostgreSQL and required appropriate boundaries for other privileged operations.
- V. Freemium Cannot Alter Financial Truth: Separated product configuration, purchase state,
  subscription lifecycle, and effective Kipu entitlement.
- VI. Financial Lifecycles Preserve History: Removed the behavior-specific budget Pause/Resume
  prohibition while retaining transaction-history protection.
- IX. Quality Is Part of Correctness: Required versioned, non-destructive database migrations.
Modified sections:
- Development and Release Gates: Prohibited test entitlement and billing artifacts and server-side
  secrets in production or release builds.
Added sections: None.
Removed sections: None.
Removed rules:
- The budget Pause/Resume prohibition; budget state behavior belongs in the budget specification.
Follow-up TODOs: None.
-->
# Kipu Constitution

## Core Principles

### I. Financial Integrity First

Financial correctness MUST take priority over UI convenience, automation, delivery speed, and
implementation shortcuts. Kipu MUST use one coherent financial domain and ledger for every
transaction, regardless of whether it originates from manual entry, Android notifications, Yape,
OCR, sharing, or another approved capture source.

Authoritative monetary amounts MUST use integer minor units paired with currency information.
Floating-point values MUST NOT be used for authoritative monetary calculations. Every balance
change MUST be caused by an explicit, auditable financial operation.

The financial domain MUST enforce these invariants:

- Transfers MUST NOT create income or expense.
- A credit purchase MUST create its expense when the purchase occurs. Paying the credit balance
  later MUST reduce cash and the liability without creating that expense again.
- Receiving loan principal MUST NOT count as operational income. Lending principal MUST NOT count
  as operational expense. Principal repayment or collection MUST NOT distort income or expense.
- Goal contributions MUST be represented as allocations or reserves, not expenses.
- Credit limits and unused credit MUST be represented only as borrowing capacity. They MUST NOT be
  included in available cash, assets, or net worth. Only an actual posted borrowing event MAY
  affect cash or assets, and it MUST also recognize the resulting outstanding liability.
- Net worth MUST be derived exclusively as real assets plus receivables minus outstanding
  liabilities. Every underlying economic value MUST be counted at most once.
- A goal reserve MUST remain an allocation of its underlying money. It MUST NOT be counted as an
  additional asset on top of that money. Reserving or releasing existing money MAY change an
  available-to-spend amount, but it MUST NOT by itself change total assets or net worth.
- Budget consumption MUST derive only from confirmed expenses that satisfy the budget's eligibility
  rules. The same expense MUST NOT be counted more than once when both its category and its
  subcategory hierarchy match the budget.
- Refunds MUST remain related to their original transactions and adjust the corresponding effect.
- Voided transactions MUST NOT participate in balances, metrics, or other financial results.
- An installment purchase MUST recognize its purchase principal exactly once.
- Corrections and cancellations MUST explicitly reverse or adjust prior effects; they MUST NOT
  rewrite balances or erase financial history silently.

Duplicate financial effects, silent balance rewriting, and financial history loss are prohibited.

**Rationale**: Every view and workflow depends on a single, reproducible financial truth. A
convenient interaction is invalid if it misstates that truth.

### II. Local-First, Retry-Safe Operation

Kipu MUST remain local-first. A previously authenticated user MUST be able to consult and manually
manage finances without continuous connectivity. A confirmed local operation MUST be persisted
safely and atomically before the application reports success.

Financial commands and transactions MUST have stable identifiers. Synchronization MUST be
idempotent under retries, duplicate events, reordering, and delayed devices. A conflict that could
change financial truth MUST NOT be resolved silently. Synchronization MUST preserve deletion,
cancellation, and supersession state so that an outdated device cannot resurrect obsolete data.

Premium-sensitive offline behavior MAY be authorized only by a verified, bounded lease derived
from a verified effective Kipu entitlement. The lease MUST expire no later than the known
entitlement end, and its maximum offline duration MUST be defined in the applicable product
specification rather than this constitution. Clock manipulation, reinstalling Kipu, or changing
devices MUST NOT extend, renew, or restart offline Premium authorization. Expiry or failure of
Premium revalidation MUST NOT disable the basic manual financial core available to previously
authenticated users.

**Rationale**: Connectivity and distributed-system failures are normal operating conditions; they
MUST NOT duplicate, lose, or corrupt financial facts.

### III. Security and Privacy by Design

Every financial object and every related child object MUST belong to the correct user. Reads and
writes of user-owned data stored in Supabase/PostgreSQL MUST enforce object-level authorization
through Row Level Security. Privileged backend operations and external-provider integrations MUST
enforce the authorization boundaries appropriate to their operation or provider and MUST NOT rely
on Row Level Security where it does not govern access. Authentication alone MUST NOT be treated as
authorization.

Kipu MUST NOT store full card PANs, CVVs, banking passwords, or banking authentication
credentials. Production logs and telemetry MUST NOT expose sensitive financial information,
authentication tokens, notification contents, or OCR evidence.

Permissions MUST be requested contextually, explained before use, and revocable. Refusing an
optional permission MUST NOT prevent manual financial management. Sensitive OCR for the approved
Yape flow MUST remain local to the device. Real financial transaction data MUST NOT be sent to
generative AI for OCR, extraction, or automatic classification.

**Rationale**: Financial data is highly sensitive, and user identity without resource isolation is
not a sufficient security boundary.

### IV. Safe and Explainable Automation

Automation MAY assist recording, but it MUST NOT invent financial facts, select unsupported facts,
or bypass the financial domain. Android notification capture, compatible payment notifications,
Yape notifications, Share to Kipu, and OCR MUST produce a `CaptureCandidate` before any financial
record or balance is affected. Each candidate MUST retain traceable source information and a
confidence assessment.

Uncertain candidates MUST require user review. Every detected credit-card purchase MUST require
explicit user confirmation, regardless of confidence. Kipu MUST NOT guess a financial instrument
when evidence is insufficient. OCR failure MUST always leave manual correction or manual entry
available.

Deduplication MUST operate across all supported capture sources. Once accepted, automated and
manual inputs MUST pass through the same financial registration rules and the same ledger; a
capture source MUST NOT maintain a separate accounting path.

**Rationale**: Automation is useful only when its provenance, uncertainty, and resulting financial
effect remain visible and controllable.

### V. Freemium Cannot Alter Financial Truth

Kipu Free MUST remain a permanent, usable mode for manual financial management. Premium MAY add
automation, analytics, and higher limits, but entitlement state MUST remain independent from valid
financial history.

Losing Premium, cancelling a subscription, or exceeding a Free limit MUST NOT delete, rewrite, or
exclude valid transactions, balances, liabilities, receivables, goals, reserves, or other history.
Objects above Free limits MUST be preserved under the approved blocking or selection model and
MUST continue contributing correctly to financial calculations.

Product configuration, purchase state, subscription lifecycle, and effective Kipu entitlement MUST
be represented and evaluated as separate concepts. Premium capabilities MUST be authorized only by
a verified effective Kipu entitlement. They MUST NOT depend on a client-editable boolean, UI-only
state, product configuration alone, a pending purchase, or unverified purchase or subscription
state. Purchase verification and entitlement reconciliation MUST be idempotent.

**Rationale**: Commercial access controls may govern capabilities, but they cannot change facts the
user has already recorded.

### VI. Financial Lifecycles Preserve History

Editing, completing, closing, archiving, or deleting a financial-planning object MUST preserve its
accounting consistency and relevant history. Goals with financial history MUST retain their
contributions. Corrections to debt or loan principal MUST be auditable. A debt or loan with related
operations MUST NOT be physically deleted in a way that destroys history or hides an outstanding
balance.

Budget lifecycle operations MUST NOT alter or delete the financial transactions that consumed the
budget or their financial effects. Recurring obligations MAY be paused, resumed, or cancelled, but
those lifecycle actions MUST NOT create fictitious financial transactions. Movement corrections
and cancellations MUST consistently reverse or adjust their financial effects.

**Rationale**: Planning objects can change state while their historical financial consequences
remain necessary for correct balances, metrics, and auditability.

### VII. Native Android with Clear Architectural Boundaries

Kipu MUST remain a native Android application with a local-first architecture. The financial
domain MUST be independent from presentation code and external providers. UI components and
ViewModels MUST NOT perform authoritative financial calculations.

Core financial rules MUST be independently testable without Android, OCR, Google Play, or
Supabase. Local persistence, synchronization and backend access, and capture or integration
infrastructure MUST have explicit responsibilities. Provider-specific models and behavior MUST be
translated at infrastructure boundaries and MUST NOT leak into core accounting rules.

Migration to Expo, React Native, or another application platform MUST require an explicit,
project-level architecture decision before implementation; it MUST NOT be introduced as a delivery
shortcut.

**Rationale**: Stable boundaries keep the financial model testable and prevent providers or UI
frameworks from becoming accidental sources of financial truth.

### VIII. Specification-Driven and Traceable Development

Kipu MUST use GitHub Spec Kit for Spec-Driven Development and Scrum for iterative delivery. Every
implemented capability MUST trace to an approved requirement, user story, correction, or explicit
product decision. Existing requirement and user-story identifiers MUST remain stable and MUST NOT
be silently renumbered or repurposed.

Specifications, technical plans, tasks, tests, and implementation MUST remain consistent. A
document, mock, or fixture MUST NOT be treated as proof that a feature or external integration is
implemented. Claims of implementation or compatibility MUST be supported by working code and the
required verification evidence.

Changes to fundamental financial, security, privacy, architecture, or product rules MUST receive
explicit review and approval; implementation work MUST NOT introduce them silently.

**Rationale**: Stable traceability makes product intent reviewable and prevents artifacts or
implementation accidents from redefining approved behavior.

### IX. Quality Is Part of Correctness

A feature MUST NOT be considered complete solely because its happy-path UI works. All applicable
acceptance criteria, financial and regression tests, persistence behavior, database migrations,
synchronization retries, deduplication, user isolation, offline behavior, permissions, error
states, and accessibility MUST be validated. Code MUST receive cross-review.

Database migrations MUST be versioned, reviewable, and validated against representative existing
data. They MUST preserve valid financial history and MUST NOT destructively remove it.

Behavior that depends on Android platform facilities, notifications, OCR, billing, or another
provider MUST be verified on a real device or against the real provider as applicable. Financial
integrity failures, duplicate ledger effects, cross-user access, destructive data loss, invalid
Premium entitlement, exposed secrets, and falsely claimed integration compatibility are
release-blocking defects.

**Rationale**: In a financial application, failure paths, persistence, isolation, and external
boundaries are part of correctness rather than optional polish.

### X. Protect the Approved Product Boundary

Kipu MUST remain a personal financial-management application that records and analyzes financial
information. It MUST NOT move money in banks, execute real payments, or issue credit.

Without a separately approved product specification, Kipu MUST NOT introduce iOS support, Open
Banking, banking SMS or email reading, universal bank-history import, generative-AI processing of
real transaction data, unsupported predictive financial recommendations, arbitrary tags that
replace the category model, or new transaction types. Any major new capability MUST be explicitly
specified and approved before implementation begins.

**Rationale**: Explicit product boundaries prevent scope expansion from creating unreviewed
financial, privacy, regulatory, or architectural risk.

## Constitutional Scope and Interpretation

This constitution governs durable, project-wide constraints. Feature-specific workflows,
individual user stories, sprint assignments, exact product prices, and other changeable details
MUST remain in their specifications and planning artifacts unless they become enduring governance
rules through an approved amendment.

Within this constitution, "approved" means recorded in a traceable product, financial, security,
privacy, or architecture decision by the project's authorized decision-makers. Silence, a mock, a
fixture, or implementation convenience does not constitute approval.

"Financial truth" includes persisted financial history and every balance, liability, receivable,
goal allocation, reserve, and metric derived from that history under the financial domain. Cached
views, UI state, provider payloads, and entitlement state MUST NOT override it.

For this constitution, "real assets" are recorded economic resources actually owned or controlled
by the user; they exclude receivables counted separately in the equation, credit limits, unused
borrowing capacity, and allocation labels.
"Receivables" are recorded amounts owed to the user, and "outstanding liabilities" are unpaid
obligations owed by the user. The authoritative net-worth equation is:

`net worth = real assets + receivables - outstanding liabilities`

"Available cash" MUST be derived only from owned liquid assets after applicable restrictions and
reserves. Credit limits and unused credit MUST be reported separately as borrowing capacity. A
"goal reserve" is an earmark within an underlying asset, not a second asset; an asset and its
reserve designation MUST NOT both be added to any asset or net-worth total.

When priorities conflict, financial integrity, user isolation and privacy, preservation of valid
history, and access to the basic local manual core MUST take precedence over convenience,
automation, monetization, or delivery speed.

## Development and Release Gates

- **Specification gate**: Implementation MUST begin from an approved, identified requirement,
  story, correction, or decision. Plans, tasks, tests, and code changes MUST retain that trace.
- **Financial design gate**: A change affecting balances, classification, object lifecycle,
  synchronization, capture, or entitlement MUST document its domain effects, invariants, retry and
  failure behavior, and offline behavior before implementation.
- **Verification gate**: Financial rules MUST have domain-level tests. Applicable persistence,
  migration, synchronization, deduplication, RLS, billing, permission, accessibility, and
  integration behavior MUST have verification at the boundary where it can fail.
- **Review gate**: Every code change MUST receive cross-review for requirement traceability,
  financial effects, privacy, security, architectural boundaries, and applicable test evidence.
- **Provider gate**: Android- or provider-dependent compatibility MUST NOT be claimed until it is
  verified with the real platform or provider under representative conditions.
- **Release gate**: Production and release builds MUST NOT contain fake Premium entitlements, test
  purchasers, development billing states, or server-side secrets. A release MUST NOT proceed with
  any release-blocking defect named in this constitution. Evidence and product claims MUST describe
  implemented behavior accurately.

## Governance

This constitution is the highest project-level authority for the concerns it governs. A conflicting
specification, plan, task, implementation, or delivery practice MUST be brought into compliance or
the constitution MUST be amended before the conflicting behavior proceeds.

Every amendment MUST include a written proposal, rationale, affected principles, compatibility and
migration impact, and financial, security, privacy, architecture, and product-boundary impact as
applicable. Approval MUST be explicit and recorded at project level. Once approved, the amendment
MUST update the Sync Impact Report, semantic version, and amendment date, and all affected
downstream artifacts MUST be reconciled. Fundamental rules MUST NOT be changed through an
undocumented exception or implementation precedent.

Constitution versions MUST follow semantic versioning:

- **MAJOR**: A backward-incompatible removal or redefinition of a governance rule or principle.
- **MINOR**: A new principle or section, or materially expanded normative guidance.
- **PATCH**: A clarification or non-semantic wording correction that does not change obligations.

Compliance MUST be reviewed when specifications and plans are approved, during code cross-review,
and at release readiness. Reviewers MUST verify traceability and every applicable gate. A violation
of a MUST or MUST NOT rule blocks approval until corrected or until an amendment is approved under
this governance process.

**Version**: 2.0.0 | **Ratified**: 2026-09-12 | **Last Amended**: 2026-09-12
