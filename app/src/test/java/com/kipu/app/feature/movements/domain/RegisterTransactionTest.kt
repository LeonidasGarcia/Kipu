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

class RegisterTransactionTest {

    private lateinit var validator: RegisterTransactionValidator
    private lateinit var fakeRepository: FakeMovementRepository
    private lateinit var useCase: RegisterTransaction

    @Before
    fun setUp() {
        validator = RegisterTransactionValidator()
        fakeRepository = FakeMovementRepository()
        useCase = RegisterTransaction(fakeRepository, validator)
    }

    @Test
    fun `expense with positive amount, account and category is valid and registered`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2000L, // S/ 20.00
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.Success)
        val success = result as RegisterTransactionResult.Success
        assertEquals(2000L, success.transaction.amountMinor)
        assertEquals(MovementType.EXPENSE, success.transaction.type)
    }

    @Test
    fun `expense without category returns validation error`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2000L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = null,
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.ValidationError)
        val error = result as RegisterTransactionResult.ValidationError
        assertEquals("category", error.field)
    }

    @Test
    fun `income without category is valid and registered`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.INCOME,
            amountMinor = 5000L, // S/ 50.00
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = null,
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.Success)
        val success = result as RegisterTransactionResult.Success
        assertEquals(5000L, success.transaction.amountMinor)
        assertEquals(MovementType.INCOME, success.transaction.type)
    }

    @Test
    fun `transfer between two different accounts is valid and registered`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.TRANSFER,
            amountMinor = 4000L,
            currency = "PEN",
            sourceAccountId = "acc_source",
            destinationAccountId = "acc_dest",
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.Success)
        val success = result as RegisterTransactionResult.Success
        assertEquals(MovementType.TRANSFER, success.transaction.type)
        assertEquals("acc_source", success.transaction.sourceAccountId)
        assertEquals("acc_dest", success.transaction.destinationAccountId)
    }

    @Test
    fun `transfer with same source and destination account returns validation error`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.TRANSFER,
            amountMinor = 4000L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            destinationAccountId = "acc_1",
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.ValidationError)
        val error = result as RegisterTransactionResult.ValidationError
        assertEquals("destination_account", error.field)
    }

    @Test
    fun `zero or negative amount returns validation error`() = runTest {
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 0L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
        )

        val result = useCase(command)
        assertTrue(result is RegisterTransactionResult.ValidationError)
        val error = result as RegisterTransactionResult.ValidationError
        assertEquals("amount", error.field)
    }

    private class FakeMovementRepository : MovementRepository {
        val transactions = mutableListOf<Transaction>()

        override fun observeTransactions(userId: String): Flow<List<TransactionItem>> =
            flowOf(transactions.map { TransactionItem(it) })

        override fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionItem>> =
            flowOf(transactions.take(limit).map { TransactionItem(it) })

        override suspend fun getTransactionById(userId: String, transactionId: String): Transaction? =
            transactions.find { it.id == transactionId }

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
                merchantId = command.merchantId,
                occurredAt = command.occurredAt,
                note = command.note,
            )
            transactions.add(tx)
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
        ): List<Transaction> = emptyList()

        override fun observeBalance(userId: String, accountId: String): Flow<Long?> = flowOf(0L)
    }
}
