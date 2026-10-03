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
        trustedNowMillis: Long = System.currentTimeMillis(),
    ): MovementHistoryPage {
        if (userId.isNullOrBlank()) {
            return MovementHistoryPage(
                items = emptyList(),
                nextCursor = null,
                hasMore = false,
                accessDecision = MovementHistoryAccessDecision.NotAuthorized,
            )
        }

        val evidence = entitlementProvider?.getEffectiveEntitlement(userId)
        val decision = accessPolicy.evaluate(userId, query, evidence, trustedNowMillis)

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
}
