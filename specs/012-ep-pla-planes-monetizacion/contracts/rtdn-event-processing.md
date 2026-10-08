# RTDN event processing contract — HU-55

**Status**: S5 design contract, bounded by the approved token-lifecycle clarification. This does not add an Android-facing restore endpoint; restore reuses the authenticated `POST /billing/verify` contract.

## RTDN delivery and authentication

Google Cloud Pub/Sub sends the RTDN envelope to a server-only receiver (planned function: `play-rtdn`). The endpoint accepts the Pub/Sub push OIDC bearer token, not a Supabase user JWT. Before parsing or persisting billing data, the receiver validates the JWT signature, configured issuer, expected service-account email, exact audience, expiration, and configured push subscription. It then validates the envelope, stable `messageId`, base64 message, application package, and allowlisted notification type. Invalid authentication, malformed data, wrong package, or unsupported type cannot mutate a purchase, entitlement, or terminal billing receipt.

The provider token is decoded only into request-scoped memory. The receiver hashes its exact UTF-8 bytes for correlation. It never writes the raw token, unfiltered RTDN payload, OIDC token, or service credential to a table, retry message, log, receipt, or response.

## Processing rules

1. Use the stable Pub/Sub message identity plus configured subscription identity to derive a unique event identity. A terminal duplicate returns its canonical receipt without repeating persistence, acknowledgement, or entitlement effects.
2. A valid event is a trigger only. If its token hash maps to an existing `billing_purchases` owner, pass the current token to the shared server verifier and use Google Play's current response as authority. An older but distinct event still queries current Play state; arrival time and RTDN type never override that state.
3. Pub/Sub has no Kipu user session. An unknown token hash cannot create or transfer ownership. Store only a safe `WAITING_FOR_TOKEN` receipt/job, acknowledge that message, and wait for an authenticated device restore/verification to establish an owner. A later restore can correlate the same token hash and resume the receipt through the shared verifier.
4. A temporary provider or persistence failure leaves a nonterminal `RETRYABLE` result and no entitlement mutation. Return a retryable server response so Pub/Sub can redeliver the event with a fresh request-scoped token. Do not persist a token to make the retry possible.
5. Restore reuses `BillingRepository.restoreAndVerifyAccess()` and current candidates from `PlayBillingGateway.recoverPurchaseUpdates()`. Each available candidate is sent through the existing authenticated `verify-purchase` operation; Supabase Auth supplies the owner. A token owned by another Kipu account is rejected without reassignment.
6. No candidate, no incoming token, or a hash-only scheduled job is not proof that a known purchase is absent. The result stays `WAITING_FOR_TOKEN`/`RETRYABLE`, does not call Play or mutate purchase/entitlement based on assumed absence. A previously verified expiry still applies, and waiting does not extend validity. A subsequent RTDN or restore token can resume verification.
7. The scheduled function uses a per-purchase lease to reclaim abandoned work and classify waiting jobs. It cannot make a provider query without a token supplied to that current RTDN/restore execution. Retryable jobs resume only when such a fresh token arrives.
8. On a complete provider response, update the purchase projection, sanitized append-only `internal.billing_events`, event receipt, reconciliation job, and effective entitlement atomically according to HU-56. Existing HU-59 grant issuance remains the only grant issuer. A transaction failure rolls back all state changes and remains retryable.

## Result and transport behavior

| Condition | Logical result | Receiver behavior |
|---|---|---|
| Valid event applied or terminal duplicate | `COMPLETED` / canonical prior result | Acknowledge with successful 2xx. |
| Valid authenticated event has no owner association or no usable token | `WAITING_FOR_TOKEN` | Persist only safe identity/hash metadata and acknowledge; a future authenticated restore can resume it. |
| Temporary Play, database, or internal-service failure with current token | `RETRYABLE` | Return retryable 5xx so Pub/Sub redelivers; never change entitlement. |
| Invalid push identity | Rejected before billing mutation | Return authentication failure; do not create a billing receipt. |
| Malformed envelope, wrong package, or unsupported type | Rejected before billing mutation | Return client error; do not change purchase or entitlement. |
| Permanent provider or owner conflict after authenticated verification | `REJECTED` | Record safe terminal result; never reassign ownership. |

## Deployment constraints

Before migration or endpoint enablement, reconcile the live Supabase catalog, migration history, grants/RLS, deployed functions, and Cron/`pg_net`/Vault state with the local migration chain. Current evidence reports `billing_purchases` and `internal.billing_events`, migrations only through 2026-09-30, and no proof of live RTDN processing; this is not deployment proof. Align P30 §4.3–4.4 in its owning documentation context with the approved no-token behavior before claiming automatic recovery of lost events without a new token.
