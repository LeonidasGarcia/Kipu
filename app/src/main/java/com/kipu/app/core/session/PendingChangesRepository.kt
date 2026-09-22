package com.kipu.app.core.session

import java.util.UUID

/**
 * Aggregates pending outbox changes across all features for a given user.
 * Complies with RN-APS-006, FR-014, and FR-017.
 */
interface PendingChangesRepository {
    suspend fun count(userId: UUID): Int
    suspend fun markWaitingForAuth(userId: UUID)
}
