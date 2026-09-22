package com.kipu.app.core.session

import java.time.Instant

/**
 * Represents the verified local owner of application data on this installation.
 * Complies with RN-APS-001, RN-APS-005, and FR-007.
 */
data class LocalOwner(
    val verifiedUserId: String,
    val explicitlySignedOut: Boolean = false,
    val verifiedAt: Instant = Instant.now(),
)
