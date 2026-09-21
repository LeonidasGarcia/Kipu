package com.kipu.app.core.security.model

enum class LocalLockState {
    DISABLED,
    LOCKED,
    UNLOCKING,
    UNLOCKED,
}

enum class LockReason {
    APP_START,
    BACKGROUND_TIMEOUT,
    OWNER_CHANGED,
    AUTHENTICATOR_CHANGED,
}

enum class AuthenticatorType {
    NONE,
    BIOMETRIC_STRONG,
    BIOMETRIC_WEAK,
    DEVICE_CREDENTIAL,
}

data class LocalAuthenticatorCapability(
    val hasBiometrics: Boolean,
    val hasDeviceCredential: Boolean,
    val canAuthenticate: Boolean,
    val supportedTypes: Set<AuthenticatorType> = emptySet(),
)
