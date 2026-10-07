package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementHistorySummary
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MovementHistoryQueryRepository {
    suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage

    /** Returns aggregates for all rows matching [query], ignoring its pagination cursor. */
    suspend fun getHistorySummary(userId: String, query: MovementHistoryQuery): MovementHistorySummary =
        MovementHistorySummary(0L, emptyMap(), null)

    /** Emits when data that can affect a history item or its presentation changes. */
    fun observeHistoryInvalidations(): Flow<Unit> = emptyFlow()

    /** Loads one presentation-ready item, for refreshing an already selected detail. */
    suspend fun getHistoryItemById(userId: String, transactionId: String): TransactionItem? = null
}
