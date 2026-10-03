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
    @Test
    fun `delimiters in different fields do not collide`() {
        val first = sample().copy(merchantProvisionalText = "a;note:b", note = "c")
        val second = sample().copy(merchantProvisionalText = "a", note = "b;note:c")
        assertNotEquals(hasher.computeHash(first), hasher.computeHash(second))
    }

    @Test
    fun `null and empty text do not collide`() {
        assertNotEquals(hasher.computeHash(sample().copy(note = null)), hasher.computeHash(sample().copy(note = "")))
        assertNotEquals(hasher.computeHash(sample().copy(merchantProvisionalText = null)),
            hasher.computeHash(sample().copy(merchantProvisionalText = "")))
    }

    @Test
    fun `unicode escapes and controls match shared golden vector`() {
        val command = sample().copy(merchantProvisionalText = "Bodega ñ 🦙", note = "a;note:b \"x\"\\\n")
        assertEquals("de3c19c4dae2a263d61fb22d62e1d46847ae83379e954fdd5e3377b63b2d5218", hasher.computeHash(command))
    }

    private fun sample() = RegisterTransactionCommand(
        idempotencyKey = "33333333-3333-3333-3333-333333333333",
        userId = "11111111-1111-1111-1111-111111111111",
        type = MovementType.EXPENSE, amountMinor = 2500L, currency = "PEN",
        sourceAccountId = "22222222-2222-2222-2222-222222222222", occurredAt = 1_000_000L,
    )

}
