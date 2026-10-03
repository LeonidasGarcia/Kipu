package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import javax.inject.Inject

class QueryMovementHistory @Inject constructor(
    private val repository: MovementHistoryQueryRepository,
    private val accessPolicy: MovementHistoryAccessPolicy = DefaultMovementHistoryAccessPolicy(),
    private val entitlementProvider: MovementEntitlementProvider? = null,
) {
    suspend operator fun invoke(
        userId: String?,
        query: MovementHistoryQuery,
    ): MovementHistoryPage {
        if (userId.isNullOrBlank()) {
            return MovementHistoryPage(
                items = emptyList(),
                nextCursor = null,
                hasMore = false,
                accessDecision = MovementHistoryAccessDecision.NotAuthorized,
            )
        }

        val decision = evaluateAccess(userId, query)

        return when (decision) {
            is MovementHistoryAccessDecision.Allowed -> {
                val page = repository.queryHistory(userId, query)
                page.copy(accessDecision = decision, fallbackUsed = false)
            }
            is MovementHistoryAccessDecision.PremiumRequired -> {
                val page = repository.queryHistory(userId, decision.fallbackQuery)
                page.copy(accessDecision = decision, fallbackUsed = true)
            }
            is MovementHistoryAccessDecision.RevalidationRequired -> {
                val page = repository.queryHistory(userId, decision.fallbackQuery)
                page.copy(accessDecision = decision, fallbackUsed = true)
            }
            is MovementHistoryAccessDecision.NotAuthorized -> {
                MovementHistoryPage(
                    items = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                    accessDecision = decision,
                )
            }
        }
    }

    /** Evaluates the capability gate without querying or paging movement data. */
    suspend fun evaluateAccess(
        userId: String?,
        query: MovementHistoryQuery,
        requiresAdvancedAccess: Boolean = query.requiresAdvancedAccess,
    ): MovementHistoryAccessDecision {
        if (userId.isNullOrBlank()) return MovementHistoryAccessDecision.NotAuthorized
        val evidence = entitlementProvider?.getEffectiveEntitlement(userId)
        return accessPolicy.evaluate(userId, query, evidence, requiresAdvancedAccess)
    }
}
