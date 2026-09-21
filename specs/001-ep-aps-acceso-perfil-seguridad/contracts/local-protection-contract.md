# Local Protection Contract

## Capability Query

```text
LocalAuthenticatorCapability = Available(allowedAuthenticators)
                             | NoneEnrolled
                             | HardwareUnavailable
                             | TemporarilyUnavailable
```

Allowed authenticators are system-owned strong biometrics and/or device credential. Kipu does not create an application PIN or receive biometric samples.

## Activation

1. Require an active owner and a remote session that was valid at least once on this installation.
2. Query current platform capability.
3. Present system prompt.
4. Persist `local_unlock_enabled=true` only after success for that owner.
5. On cancellation, failure or unavailable hardware, leave the prior setting unchanged.

## Runtime Gate

Inputs: owner, enabled setting, process start, monotonic background duration and prompt result.

Outputs: `DISABLED`, `LOCKED`, `UNLOCKING`, `UNLOCKED`.

Rules:

- Process start is locked when enabled.
- Background duration below 60,000 ms does not lock.
- Continuous duration at or above 60,000 ms locks before private content is drawn.
- A foreground/background interruption restarts the continuous interval.
- Cancellation or failure remains locked and permits retry.
- Success opens only the current local owner and does not refresh Auth.
- Owner change locks synchronously before navigation changes.

## Privacy Surface

While locked or while owner/session state is transitioning:

- render an opaque non-sensitive surface;
- do not emit balances in semantics/accessibility trees;
- do not expose private content in app previews or transitions;
- do not persist prompt outcomes or biometric details in logs.

## Test Boundary

Domain tests use a fake `LocalAuthenticatorGateway`. Provider compatibility requires real-device evidence for success, cancellation, failure, no enrollment, device credential fallback and enrollment changes.
