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


// Separate ports preserve the existing registration API and its S2 clients.
interface MovementMaintenanceRepository {
    suspend fun getRevisionHead(userId: String, transactionId: String): com.kipu.app.feature.movements.domain.model.MovementRevisionHead?
    suspend fun revise(userId: String, command: com.kipu.app.feature.movements.domain.model.MovementRevisionCommand.Revise): com.kipu.app.feature.movements.domain.model.MovementMutationResult
    suspend fun void(userId: String, command: com.kipu.app.feature.movements.domain.model.MovementRevisionCommand.Void): com.kipu.app.feature.movements.domain.model.MovementMutationResult
    suspend fun getConflictProposals(userId: String, transactionId: String): List<com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity>
    suspend fun discardProposal(userId: String, proposalId: String): Boolean
    suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String = java.util.UUID.randomUUID().toString()): com.kipu.app.feature.movements.domain.model.MovementMutationResult
}
interface ExpenseConsumptionRepository {
    suspend fun queryConsumption(userId: String, query: com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery): com.kipu.app.feature.movements.domain.model.ExpenseConsumptionResult
}
