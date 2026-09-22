package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow

interface MovementRepository {
    fun observeTransactions(userId: String): Flow<List<TransactionItem>>

    fun observeRecentTransactions(userId: String, limit: Int = 50): Flow<List<TransactionItem>>

    suspend fun getTransactionById(userId: String, transactionId: String): Transaction?

    suspend fun registerTransaction(command: RegisterTransactionCommand): RegisterTransactionResult

    suspend fun findSimilarTransactions(
        userId: String,
        sourceAccountId: String,
        type: MovementType,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        windowMillis: Long = 300_000L,
    ): List<Transaction>

    fun observeBalance(userId: String, accountId: String): Flow<Long?>
}
