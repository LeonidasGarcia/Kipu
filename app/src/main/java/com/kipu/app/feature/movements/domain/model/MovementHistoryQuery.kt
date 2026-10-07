package com.kipu.app.feature.movements.domain.model

import java.math.BigInteger

data class MovementHistoryCursor(
    val occurredAt: Long,
    val transactionId: String,
) {
    init {
        require(transactionId.isNotBlank()) { "transactionId cannot be blank in cursor" }
    }
}

data class AdvancedHistoryCriteria(
    val accountIds: Set<String> = emptySet(),
    val cardIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val merchantIds: Set<String> = emptySet(),
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val currency: String? = null,
    val financialStates: Set<MovementFinancialState> = emptySet(),
    val syncStatuses: Set<MovementSyncStatus> = emptySet(),
) {
    init {
        if (minAmountMinor != null && maxAmountMinor != null) {
            require(minAmountMinor <= maxAmountMinor) {
                "minAmountMinor ($minAmountMinor) must be <= maxAmountMinor ($maxAmountMinor)"
            }
        }
        if (minAmountMinor != null || maxAmountMinor != null) {
            require(!currency.isNullOrBlank()) {
                "currency must be specified when filtering by amount"
            }
        }
    }

    fun hasCriteria(): Boolean {
        return accountIds.isNotEmpty() ||
            cardIds.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            merchantIds.isNotEmpty() ||
            minAmountMinor != null ||
            maxAmountMinor != null ||
            financialStates.isNotEmpty() ||
            syncStatuses.isNotEmpty()
    }
}

data class MovementHistoryQuery(
    val queryText: String? = null,
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null,
    val types: Set<MovementType> = emptySet(),
    val advancedCriteria: AdvancedHistoryCriteria? = null,
    val limit: Int = 50,
    val cursor: MovementHistoryCursor? = null,
) {
    init {
        require(limit in 1..200) { "limit must be between 1 and 200, got $limit" }
        if (fromInclusive != null && toExclusive != null) {
            require(fromInclusive <= toExclusive) {
                "fromInclusive ($fromInclusive) must be <= toExclusive ($toExclusive)"
            }
        }
    }

    val requiresAdvancedAccess: Boolean
        get() = advancedCriteria?.hasCriteria() == true

    /**
     * Fallback basic query when advanced filter access is denied/expired.
     * Preserves text, dates, types, limit, and cursor while stripping advanced criteria so
     * subsequent fallback pages keep their keyset position.
     */
    fun toBasicFallback(): MovementHistoryQuery {
        return copy(
            advancedCriteria = null,
        )
    }
}

sealed interface MovementHistoryAccessDecision {
    data object Allowed : MovementHistoryAccessDecision
    data class PremiumRequired(val fallbackQuery: MovementHistoryQuery) : MovementHistoryAccessDecision
    data class RevalidationRequired(val fallbackQuery: MovementHistoryQuery) : MovementHistoryAccessDecision
    data object NotAuthorized : MovementHistoryAccessDecision
}

data class MovementHistoryPage(
    val items: List<TransactionItem>,
    val nextCursor: MovementHistoryCursor?,
    val hasMore: Boolean,
    val accessDecision: MovementHistoryAccessDecision,
    val fallbackUsed: Boolean = false,
)

data class MovementHistorySummary(
    val totalCount: Long,
    val netByCurrency: Map<String, BigInteger>,
    val mostUsedAccountId: String?,
)
