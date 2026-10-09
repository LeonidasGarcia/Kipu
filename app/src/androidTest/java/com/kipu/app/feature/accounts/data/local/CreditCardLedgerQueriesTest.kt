package com.kipu.app.feature.accounts.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardLedgerQueriesTest {
    private lateinit var database: KipuDatabase
    private val userId = UUID.randomUUID().toString()
    private val cardId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun nextPaymentSuggestionSumsUnpaidPrincipalForEarliestDueDate() = runTest {
        val nextDue = LocalDate.parse("2026-11-10").toEpochDay()
        val laterDue = LocalDate.parse("2026-12-10").toEpochDay()
        val firstPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_000L,
            operationKind = "CARD_PURCHASE",
        )
        val secondPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_001L,
            operationKind = "CARD_PURCHASE",
        )
        val payment = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_002L,
            operationKind = "CARD_PAYMENT",
            type = "TRANSFER",
        )
        val rejectedPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_003L,
            operationKind = "CARD_PURCHASE",
            syncStatus = "FAILED_PERMANENT",
        )
        database.movementDao().insertTransactions(listOf(firstPurchase, secondPurchase, payment, rejectedPurchase))

        val firstInstallment = installment(firstPurchase.id, "first", nextDue, principal = 2_500L, status = "PARTIAL")
        val secondInstallment = installment(secondPurchase.id, "second", nextDue, principal = 3_000L)
        val futureInstallment = installment(secondPurchase.id, "future", laterDue, principal = 5_000L, number = 2)
        val rejectedInstallment = installment(rejectedPurchase.id, "rejected", LocalDate.parse("2026-10-10").toEpochDay(), 8_000L)
        database.creditDao().insertInstallments(
            listOf(firstInstallment, secondInstallment, futureInstallment, rejectedInstallment),
        )
        database.creditDao().insertAllocation(
            CreditPaymentAllocationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                paymentTransactionId = payment.id,
                installmentId = firstInstallment.id,
                allocatedMinor = 1_000L,
                createdAt = 1_800_000_000_004L,
            ),
        )

        val suggestion = database.creditDao()
            .observeNextInstallmentDueForCard(userId, cardId)
            .first()

        assertNotNull(suggestion)
        assertEquals(nextDue, suggestion?.dueDateEpochDay)
        assertEquals(4_500L, suggestion?.amountMinor)
    }

    @Test
    fun cardMovementQueryIncludesLocalPurchasesPaymentsAndMerchantNames() = runTest {
        val merchantId = UUID.randomUUID().toString()
        val purchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_000L,
            operationKind = "CARD_PURCHASE",
            merchantId = merchantId,
        )
        val payment = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_001L,
            operationKind = "CARD_PAYMENT",
            type = "TRANSFER",
        )
        val rejected = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_002L,
            operationKind = "CARD_PURCHASE",
            syncStatus = "FAILED_PERMANENT",
        )
        database.movementDao().insertTransactions(listOf(purchase, payment, rejected))
        database.merchantCatalogDao().insertMerchants(
            listOf(
                MerchantCatalogEntity(
                    id = merchantId,
                    name = "Mercado Central",
                    normalizedName = "mercado central",
                    lastSyncedAt = 1_800_000_000_000L,
                ),
            ),
        )

        val rows = database.movementDao().observeCardTransactions(userId, cardId).first()

        assertEquals(listOf("CARD_PAYMENT", "CARD_PURCHASE"), rows.map { it.transaction.operationKind })
        assertEquals("Mercado Central", rows.last().merchantName)
    }

    private fun transaction(
        id: String,
        occurredAt: Long,
        operationKind: String,
        type: String = "EXPENSE",
        syncStatus: String = "PENDING",
        merchantId: String? = null,
    ) = TransactionEntity(
        id = id,
        userId = userId,
        type = type,
        amountMinor = 2_500L,
        currencyCode = "PEN",
        merchantId = merchantId,
        occurredAt = occurredAt,
        status = "ACTIVE",
        syncStatus = syncStatus,
        createdAt = occurredAt,
        cardId = cardId,
        operationKind = operationKind,
    )

    private fun installment(
        transactionId: String,
        suffix: String,
        dueDate: Long,
        principal: Long,
        status: String = "PENDING",
        number: Int = 1,
    ) = CreditInstallmentEntity(
        id = "${transactionId}_$suffix",
        userId = userId,
        transactionId = transactionId,
        installmentNumber = number,
        dueDate = dueDate,
        principalMinor = principal,
        interestMinor = 0L,
        status = status,
        revision = 1L,
        createdAt = 1_800_000_000_000L,
        updatedAt = 1_800_000_000_000L,
    )
}
