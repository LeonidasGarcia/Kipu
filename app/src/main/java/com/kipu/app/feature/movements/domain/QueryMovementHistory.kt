package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementHistorySummary
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow
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

    /** Returns unpaged aggregates for the query that the caller is allowed to execute. */
    suspend fun getHistorySummary(
        userId: String?,
        query: MovementHistoryQuery,
    ): MovementHistorySummary? {
        if (userId.isNullOrBlank()) return null
        return when (val decision = evaluateAccess(userId, query)) {
            is MovementHistoryAccessDecision.Allowed -> repository.getHistorySummary(userId, query.copy(cursor = null))
            is MovementHistoryAccessDecision.PremiumRequired ->
                repository.getHistorySummary(userId, decision.fallbackQuery.copy(cursor = null))
            is MovementHistoryAccessDecision.RevalidationRequired ->
                repository.getHistorySummary(userId, decision.fallbackQuery.copy(cursor = null))
            is MovementHistoryAccessDecision.NotAuthorized -> null
        }
    }

    /** Emits lightweight database invalidations; callers decide which history data to reload. */
    fun observeHistoryInvalidations(): Flow<Unit> = repository.observeHistoryInvalidations()

    suspend fun getHistoryItemById(userId: String?, transactionId: String): TransactionItem? {
        if (userId.isNullOrBlank() || transactionId.isBlank()) return null
        return repository.getHistoryItemById(userId, transactionId)
    }
}
