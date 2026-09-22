package com.kipu.app.core.session

import java.util.UUID

/**
 * Contributor interface for aggregating pending outbox changes across different features.
 * Complies with RN-APS-006, FR-014, and EP-CTA sync requirements.
 */
interface PendingChangesSource {
    suspend fun count(userId: UUID): Int
    suspend fun markWaitingForAuth(userId: UUID)
}
