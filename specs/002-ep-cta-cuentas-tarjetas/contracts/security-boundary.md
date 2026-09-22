# Security and Privacy Boundary

## Card Data Allowlist

Accepted card fields are limited to:

- Internal card UUID and operation UUID
- Type: debit or credit
- Alias
- Issuer display value
- Network: Visa, Mastercard, Amex or generic Other
- Exactly four ASCII digits
- Currency
- Debit linked-account UUID, or credit line and preferred days
- Approved appearance tokens
- Duplicate-warning acknowledgement

Prohibited everywhere:

- Full or partial PAN beyond the allowed last four
- CVV, CVC, CID or PIN
- Banking passwords, tokens or authentication credentials
- Generic metadata/raw payload fields on card commands
- Request-body logging or analytics containing financial fields

Unknown fields cause `INVALID_REQUEST`; they are not ignored.

## Input Boundary

- UI exposes only `Ultimos 4 digitos`; no PAN/CVV field exists.
- The last-four control accepts exactly four ASCII digits.
- Paste/input longer than four digits is rejected before entering UI state; it is never truncated.
- Rejected text must not enter `SavedStateHandle`, navigation, exceptions, logs, analytics, Room, DTOs or outbox.
- Domain validation repeats presentation validation.
- Server validates an exact payload allowlist and four-digit pattern.
- Card alias and issuer are normalized bounded display text, must contain a letter, and reject PAN-like digit sequences (including spaces/hyphens), standalone 3-4 digit security-code shapes, and CVV/CVC/CID/PIN/card-number labels before entering state. The same shared rule runs in UI, domain, DTO/outbox construction and RPC validation.

No arbitrary card free-text or metadata field exists. The conservative alias/issuer validator may reject ambiguous numeric names rather than risk accepting card secrets.

## Owner Isolation

- Android repository observes owner and local-read authorization from `SessionCoordinator.localAccess`; UI does not provide `userId`. `AuthenticatedSessionProvider` is used only at the remote request/worker boundary for current credentials.
- `LocalAccess.Available` scopes DAO flows to that owner, `Protected` blocks sensitive projections without changing ownership, and `NoOwner` exposes no financial rows.
- Every DAO query includes `user_id`; ID-only methods are prohibited.
- Remote RPC derives owner from `auth.uid()` and never accepts `user_id` in payload.
- Every child relation enforces ownership using composite `(user_id, parent_id)` references, including debit links, movement targets/links, receipts, aggregate heads and sync changes. Local primary/unique keys also include owner where a row can arrive from sync.
- RLS is enabled and forced on every user-owned public relation.
- Cross-user misses return no financial attributes and use neutral errors.

## Supabase Grants

- Revoke all financial relation privileges from `PUBLIC` and `anon`.
- `authenticated` receives SELECT only on approved projections and EXECUTE only on exact typed RPC signatures.
- Direct INSERT/UPDATE/DELETE on accounts, cards, movements, ledger, receipts and sync is revoked.
- Private/internal schemas are not exposed through Data API.
- Public views are `security_invoker=true`.
- Any necessary `SECURITY DEFINER` function uses a dedicated `NOLOGIN` owner, `search_path=''`, fully qualified objects, explicit `auth.uid()` checks and revoked default EXECUTE.
- Android uses only project URL and publishable key; service-role/secret keys never ship.

## Safe Observability

Allowed structured fields:

- Correlation/operation UUID
- Command type and contract version
- Outcome/error code
- Attempt count and duration bucket
- Worker state and app version

Disallowed fields:

- Amount, currency balance, alias, issuer, last four
- Request/response payload
- Access/refresh tokens or authorization headers
- Notification/OCR content
- SQL parameter values containing user financial data

Metrics aggregate counts and latency without user financial dimensions.

## Verification

- Static source/schema search proves no PAN/CVV fields.
- UI tests prove pasted PAN-like input never enters semantics/state.
- Room schema and outbox inspection contain only allowlisted fields.
- pgTAP validates grants, RLS, owner changes and unknown-field rejection.
- Two-user tests cover SELECT and each RPC.
- Supabase security advisors have no unresolved EP-CTA warnings before release.
