# Auth and Session Contract

## Purpose

Define the provider-independent boundary used by Android. Supabase-specific models, tokens and exceptions do not cross this interface.

## Commands

| Command | Input | Success | Safe failures |
|---------|-------|---------|---------------|
| `register` | normalized email, transient password, CAPTCHA proof | `ConfirmationRequired` | `AccountExists`, `RateLimited(retryAfter)`, `NetworkRequired`, `Rejected` |
| `signIn` | normalized email, transient password, CAPTCHA proof | `Authenticated(owner)` | `InvalidCredentials` (neutral), `RateLimited`, `NetworkRequired` |
| `requestRecovery` | normalized email, CAPTCHA proof | `RequestAccepted` for both existing/non-existing emails | `RateLimited`, `NetworkRequired`; public copy remains neutral |
| `completeRecovery` | validated recovery event, transient new password | `PasswordUpdated` | `LinkInvalidOrExpired`, `RateLimited`, `NetworkRequired` |
| `refresh` | no UI token input | `Authenticated(same owner)` | `NetworkRequired`, `ReauthenticationRequired`, `OwnerMismatch` |
| `signOut` | confirmed pending-change decision | `SignedOut` | `Cancelled`; local cleanup still protects UI if remote revoke cannot complete |

Passwords are accepted only as short-lived command input and are cleared from presentation state after dispatch. `auth-access` returns `{accessToken, refreshToken, expiresIn, tokenType}` only for successful sign-in with `Cache-Control: private, no-store` and `Pragma: no-cache`; the data adapter imports it directly, asks Auth to validate/retrieve the session user, compares that subject with the returned owner, and only then persists the encrypted session and exposes `Authenticated(owner)`. Any mismatch/failure clears the imported session. Commands and errors must not stringify password, token, recovery URI, session envelope or provider response headers.

## Observable Session

```text
RemoteSession = Absent
              | Valid(userId, expiresAt)
              | RefreshRequired(userId)
              | ReauthenticationRequired(previousUserId)

LocalAccess = NoOwner
            | Protected(userId, reason)
            | Available(userId, remoteState)
```

Invariants:

- `Available.userId` must equal the owner selected in every Room query.
- A refresh result with another `userId` is `OwnerMismatch`: clear visible state and require explicit login.
- Explicit logout sets `NoOwner` even if remote sign-out fails offline.
- An expired remote token is never used for Data API calls. Previously verified local access may continue only for owner-scoped local operations.

## Deep-Link Contract

Release callbacks must use the configured verified HTTPS origin and one of these paths:

| Path | Purpose | Accepted once |
|------|---------|---------------|
| `/auth/confirm` | Email confirmation result | Yes |
| `/auth/recovery` | Password recovery | Yes |

The handler rejects an unexpected scheme, host, path, purpose, missing provider proof, malformed URI or replay. Raw URIs and fragments are never logged. Debug custom schemes are not enabled in release manifests.

## Pending-Change Contract

`PendingChangesRepository.count(userId)` aggregates every owner-scoped outbox. `signOut` behaves as follows:

- `count = 0`: proceed after normal confirmation policy.
- `count > 0`: show risk and count, then require `Cancel` or `Sign out anyway`.
- Cancel changes nothing.
- Confirm marks pending work `WAITING_FOR_AUTH`, clears active owner/navigation and leaves rows under the original `userId`.

## Offline Command Matrix

| Command class | `LocalOnlyOpen` | Owner requirement | Remote behavior |
|---------------|-----------------|-------------------|-----------------|
| Read owner-scoped Room data | Allowed | Query must include active `userId` | None. |
| Manual financial command owned by another epic | Allowed only if that epic provides atomic local persistence/outbox | New row/outbox uses active `userId` | Waits for same user's valid session. |
| Edit profile preferences | Allowed | Cache/outbox use active `userId` | Waits for same user's valid session. |
| Register, sign in, recover, confirm email | Not allowed offline | N/A | Report `NetworkRequired`; no simulated success. |
| Refresh/sync | Not allowed until network and same remote owner | Remote subject must equal local owner | Mismatch closes private graph. |
| Enable local authenticator | Requires a currently valid remote session | Setting uses active `userId` | No Auth session is created by prompt. |
| Unlock, mask values, inspect local permission state | Allowed | Active owner only | None. |
| Premium capture/content processing | Unavailable in EP-APS | N/A | No payload accepted. |

EP-APS tests the gate with representative doubles; each financial epic remains responsible for proving its own atomic local command and outbox behavior.

## Error Presentation

- Sign-in errors do not reveal account existence or enablement.
- Recovery always shows the same accepted message.
- Registration alone maps server `ACCOUNT_EXISTS` to the sign-in offer required by FR-051; implementation waits for formal spec/risk approval.
- `RateLimited` presents finite retry time and never claims permanent account lock.
