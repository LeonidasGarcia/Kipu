package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FreeDeduplicationTest {

    private lateinit var validator: RegisterTransactionValidator
    private lateinit var fakeRepository: DeduplicationFakeRepository
    private lateinit var useCase: RegisterTransaction

    @Before
    fun setUp() {
        validator = RegisterTransactionValidator()
        fakeRepository = DeduplicationFakeRepository()
        useCase = RegisterTransaction(fakeRepository, validator)
    }

    @Test
    fun `deduplication warning is triggered for free tier without consuming quota`() = runTest {
        val baseTime = System.currentTimeMillis()

        // Existing transaction registered 1 minute ago
        val existing = Transaction(
            id = "tx_existing",
            userId = "free_user",
            type = MovementType.EXPENSE,
            amountMinor = 1500L,
            currency = "PEN",
            sourceAccountId = "acc_cash",
            categoryId = "cat_food",
            occurredAt = baseTime - 60_000L,
        )
        fakeRepository.existingTransactions.add(existing)

        // Attempting to register identical transaction
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "free_user",
            type = MovementType.EXPENSE,
            amountMinor = 1500L,
            currency = "PEN",
            sourceAccountId = "acc_cash",
            categoryId = "cat_food",
            occurredAt = baseTime,
            ignoreSimilarityWarning = false,
        )

        val result = useCase(command)
        assertTrue("Expected SimilarTransactionWarning on free tier", result is RegisterTransactionResult.SimilarTransactionWarning)
        val warning = result as RegisterTransactionResult.SimilarTransactionWarning
        assertEquals(existing.id, warning.existingTransaction.id)
    }

    @Test
    fun `confirming duplicate on free tier registers transaction with new identity`() = runTest {
        val baseTime = System.currentTimeMillis()

        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "free_user",
            type = MovementType.EXPENSE,
            amountMinor = 1500L,
            currency = "PEN",
            sourceAccountId = "acc_cash",
            categoryId = "cat_food",
            occurredAt = baseTime,
            ignoreSimilarityWarning = true, // User confirmed
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.Success)
        val success = result as RegisterTransactionResult.Success
        assertEquals(1500L, success.transaction.amountMinor)
    }

    private class DeduplicationFakeRepository : MovementRepository {
        val existingTransactions = mutableListOf<Transaction>()

        override fun observeTransactions(userId: String): Flow<List<TransactionItem>> =
            flowOf(existingTransactions.map { TransactionItem(it) })

        override fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionItem>> =
            flowOf(existingTransactions.take(limit).map { TransactionItem(it) })

        override suspend fun getTransactionById(userId: String, transactionId: String): Transaction? =
            existingTransactions.find { it.id == transactionId }

        override suspend fun registerTransaction(command: RegisterTransactionCommand): RegisterTransactionResult {
            val tx = Transaction(
                id = UUID.randomUUID().toString(),
                userId = command.userId,
                type = command.type,
                amountMinor = command.amountMinor,
                currency = command.currency,
                sourceAccountId = command.sourceAccountId,
                destinationAccountId = command.destinationAccountId,
                categoryId = command.categoryId,
                occurredAt = command.occurredAt,
            )
            existingTransactions.add(tx)
            return RegisterTransactionResult.Success(tx)
        }

        override suspend fun findSimilarTransactions(
            userId: String,
            sourceAccountId: String,
            type: MovementType,
            amountMinor: Long,
            currency: String,
            occurredAt: Long,
            windowMillis: Long,
        ): List<Transaction> {
            return existingTransactions.filter {
                it.userId == userId &&
                    it.sourceAccountId == sourceAccountId &&
                    it.type == type &&
                    it.amountMinor == amountMinor &&
                    it.currency == currency &&
                    kotlin.math.abs(it.occurredAt - occurredAt) <= windowMillis
            }
        }

        override fun observeBalance(userId: String, accountId: String): Flow<Long?> = flowOf(0L)
    }
}
