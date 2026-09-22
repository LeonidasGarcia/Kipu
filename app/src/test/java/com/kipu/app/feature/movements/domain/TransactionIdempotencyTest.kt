package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class TransactionIdempotencyTest {

    private lateinit var hasher: TransactionRequestHasher

    @Before
    fun setUp() {
        hasher = TransactionRequestHasher()
    }

    @Test
    fun `identical commands produce identical hash`() {
        val command1 = RegisterTransactionCommand(
            idempotencyKey = "key_1",
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
            occurredAt = 1000000L,
            note = "Almuerzo",
        )

        val command2 = RegisterTransactionCommand(
            idempotencyKey = "key_1",
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
            occurredAt = 1000000L,
            note = "Almuerzo",
        )

        val hash1 = hasher.computeHash(command1)
        val hash2 = hasher.computeHash(command2)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `commands with different amount produce different hash`() {
        val command1 = RegisterTransactionCommand(
            idempotencyKey = "key_1",
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
            occurredAt = 1000000L,
        )

        val command2 = command1.copy(amountMinor = 3000L)

        val hash1 = hasher.computeHash(command1)
        val hash2 = hasher.computeHash(command2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `commands with different type produce different hash`() {
        val command1 = RegisterTransactionCommand(
            idempotencyKey = "key_1",
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
            occurredAt = 1000000L,
        )

        val command2 = command1.copy(type = MovementType.INCOME)

        val hash1 = hasher.computeHash(command1)
        val hash2 = hasher.computeHash(command2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `commands with different source account produce different hash`() {
        val command1 = RegisterTransactionCommand(
            idempotencyKey = "key_1",
            userId = "user_1",
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = "acc_1",
            categoryId = "cat_food",
            occurredAt = 1000000L,
        )

        val command2 = command1.copy(sourceAccountId = "acc_2")

        val hash1 = hasher.computeHash(command1)
        val hash2 = hasher.computeHash(command2)

        assertNotEquals(hash1, hash2)
    }
}
