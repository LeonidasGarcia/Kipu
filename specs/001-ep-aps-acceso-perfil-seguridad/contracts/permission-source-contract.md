# Permission and Source Contract

## Concepts

- `DeviceAuthorization`: what Android currently permits for the installation/application.
- `AccountConsent`: what the active account has acknowledged or declined on this device.
- `CapabilityAuthorization`: what the verified Kipu entitlement permits for the active account.
- `ProcessingAuthorization`: true only when both authorizations are valid and the owning feature is implemented.

No client-editable preference can grant Premium capability.

## Sources

| Source | Device authorization | HU-05 processing |
|--------|----------------------|------------------|
| `OWN_NOTIFICATIONS` | Android notification runtime state where applicable | May send Kipu's own notices; denial never blocks manual core. |
| `OTHER_APP_NOTIFICATION_CONTENT` | Future notification-listener access | Always false in HU-05; listener/capture belongs to HU-45. |

## Consent Sequence

Before any platform request, UI must expose:

- benefit;
- categories of information involved;
- explicit exclusions and current scope;
- how to revoke;
- whether a verified capability is additionally required;
- confirmation that manual operations remain available after denial.

Only after acknowledging the current explanation version may the gateway request/open Android settings.

## Evaluation

```text
processingAllowed = deviceAuthorization == GRANTED
                 && accountConsent == GRANTED
                 && capabilityAuthorization == ALLOWED
                 && sourceImplementation == AVAILABLE
```

For `OTHER_APP_NOTIFICATION_CONTENT` in this increment, `sourceImplementation` is `UNAVAILABLE`, so the result is always false and no payload is accepted, logged, persisted or converted into a candidate.

## Revocation

- Re-query platform state on foreground and before a source action.
- If a previous Android grant is now absent, mark the installation authorization `REVOKED` and stop future processing immediately; preserve the account's consent history.
- If the account withdraws consent while Android remains granted, mark only that account/source consent `REVOKED` and stop processing for that account.
- Do not delete already confirmed legitimate history.
- Android authorization is installation-scoped and re-read from the platform; consent and capability state are account/device-scoped. Neither is synchronized as a grant to another device.

## Accessibility

Every source row announces source name, current state, commercial restriction if any and the available action. Color or icon alone must not communicate granted/denied state.
