# Contract: Signed Offline Entitlement Grant

**Version**: 1 · **Stories**: HU-58/HU-59 · **Algorithm**: ES256 (P-256, SHA-256)

## Authority and issuance

Only `verify-purchase` may issue this grant. The request must have an authenticated Supabase owner, a Google Play result that is not pending, a successfully persisted provider result, and a currently effective entitlement computed by the server. A transient provider/persistence failure does not issue or refresh a grant. A verified result with no effective Premium returns `offlineEntitlementGrant: null` and clears the client's grant.

The Android client sends its Android Keystore P-256 public key in DER SubjectPublicKeyInfo encoding as standard Base64. The server computes `installationKeyThumbprint = base64url(SHA-256(publicKeyDer))`; a client-supplied thumbprint is forbidden. The private key never leaves the device. The server signing private JWK and `keyId` are Edge Function secrets; Android receives only the matching public key. Missing signing configuration fails closed without granting Premium.

## Wire format

```json
{
  "payload": "<base64url-without-padding of exact UTF-8 JSON bytes>",
  "signature": "<base64url-without-padding ES256 P-256 r||s signature>",
  "keyId": "<key id duplicated from the signed payload>"
}
```

The server serializes the payload once and signs those exact UTF-8 bytes with WebCrypto ECDSA/SHA-256. The Android verifier checks the raw 64-byte `r||s` signature using the configured public key for `keyId` before parsing or trusting any field. The top-level key ID must equal the signed `keyId`. Unknown keys and malformed encodings are denied.

## Signed payload claims

| Claim | Type | Rule |
|---|---|---|
| `version` | integer | Exactly `1`. |
| `keyId` | string | Server signing key identifier. |
| `grantId` | UUID string | Unique identifier for this issuance. |
| `userId` | UUID string | Derived from the authenticated server session. |
| `installationKeyThumbprint` | base64url string | SHA-256 thumbprint of request's DER public key. |
| `policyVersion` | integer | Exactly the policy version supported by this client. |
| `tier` | string | Exactly `PREMIUM`. |
| `serverVerifiedAt` | RFC 3339 UTC timestamp | Server time after provider verification and persistence. |
| `entitlementEndsAt` | RFC 3339 UTC timestamp or null | Null only for effective Lifetime; otherwise a verified commercial end. |
| `notAfter` | RFC 3339 UTC timestamp | `min(serverVerifiedAt + 72 hours, entitlementEndsAt)` or `serverVerifiedAt + 72 hours` for Lifetime. |

## Client acceptance

The grant is accepted only if its signature, key ID, version, policy, owner, installation thumbprint and Premium tier match; timestamps parse and satisfy `serverVerifiedAt < notAfter`; the cached boot count equals the current `Settings.Global.BOOT_COUNT`; and `SystemClock.elapsedRealtime()` has not regressed from its persisted response anchor. Trusted time is `serverVerifiedAt + elapsedDelta`. Access requires the strict comparison `trustedNow < notAfter` and, when non-null, `trustedNow < entitlementEndsAt`.

The app stores payload, signature, key ID, elapsed anchor and boot count in Room in one transaction. A cache without all these fields is Free. The cache and database stay excluded from backup/transfer. After reboot or loss of continuity, revalidation is required; Free registration, basic queries and outbox remain usable.

## Verification examples

| Case | Result |
|---|---|
| Valid signed grant, matching account/install/boot, before boundary | Premium allowed. |
| At or after `notAfter` | Premium denied; reconnection required. |
| Civil clock moved forward or backward | No effect on lease evaluation. |
| Server entitlement end earlier than 72 hours | Denied at the commercial end. |
| Lifetime with no commercial end | Denied at 72 hours. |
| Reboot, missing boot count, signature/claim mismatch or unknown key | Premium denied; reconnection required. |
| Pending purchase, failed network verification, or Free entitlement | No new grant; no Premium extension. |
