package com.kipu.app.feature.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDaoTest {

    private lateinit var database: KipuDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var cardDao: CardDao

    private val testUserId = UUID.randomUUID().toString()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        cardDao = database.cardDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertDebitCardLinkedToAccount_observesCardUnderAccount() = runTest {
        val accountId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        // 1. Insert parent account
        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = testUserId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Ahorros BCP",
                type = "SAVINGS",
                currency = "PEN",
                presetId = "BCP",
                color = null,
                icon = null,
                initialBalanceMinorUnits = 500_00L,
                openedAt = nowMicros,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        // 2. Insert linked debit card
        cardDao.insert(
            CardEntity(
                id = cardId,
                userId = testUserId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = accountId,
                alias = "Tarjeta Débito BCP",
                type = "DEBIT",
                currency = "PEN",
                network = "VISA",
                issuer = "BCP",
                lastFourDigits = "4321",
                creditLimitMinorUnits = null,
                billingDay = null,
                dueDay = null,
                presetId = "BCP_VISA",
                color = null,
                icon = null,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        val debitCards = cardDao.observeActiveDebitCardsForAccount(testUserId, accountId).first()
        assertEquals(1, debitCards.size)
        assertEquals("4321", debitCards[0].lastFourDigits)
    }

    @Test
    fun duplicateDetection_findsMatchingIssuerNetworkAndLastFourDigits() = runTest {
        val cardId1 = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        cardDao.insert(
            CardEntity(
                id = cardId1,
                userId = testUserId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = null,
                alias = "Mi Primera Tarjeta",
                type = "CREDIT",
                currency = "USD",
                network = "MASTERCARD",
                issuer = "Interbank",
                lastFourDigits = "9999",
                creditLimitMinorUnits = 3000_00L,
                billingDay = 20,
                dueDay = 10,
                presetId = null,
                color = null,
                icon = null,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        val duplicates = cardDao.findDuplicates(testUserId, "Interbank", "MASTERCARD", "9999")
        assertEquals(1, duplicates.size)
        assertEquals(cardId1, duplicates[0].id)

        val noDuplicates = cardDao.findDuplicates(testUserId, "BCP", "VISA", "9999")
        assertEquals(0, noDuplicates.size)
    }

    @Test
    fun deleteParentAccount_cascadesDebitCardDeletion() = runTest {
        val accountId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = testUserId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta Temporal",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = nowMicros,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        cardDao.insert(
            CardEntity(
                id = cardId,
                userId = testUserId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = accountId,
                alias = "Débito Temporal",
                type = "DEBIT",
                currency = "PEN",
                network = "VISA",
                issuer = "BBVA",
                lastFourDigits = "1111",
                creditLimitMinorUnits = null,
                billingDay = null,
                dueDay = null,
                presetId = null,
                color = null,
                icon = null,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        assertNotNull(cardDao.getById(testUserId, cardId))

        // Delete parent account
        accountDao.delete(testUserId, accountId)

        // Debit card should be cascaded
        assertNull(cardDao.getById(testUserId, cardId))
    }
}
