package com.kipu.app.feature.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.core.finance.domain.model.MovementStatus
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {

    private lateinit var database: KipuDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var movementDao: FinancialMovementDao

    private val testUserId = UUID.randomUUID().toString()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        movementDao = database.financialMovementDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAccountAndOpeningMovement_derivesCorrectBalance() = runTest {
        val accountId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        val account = AccountEntity(
            id = accountId,
            userId = testUserId,
            creationOperationId = operationId,
            alias = "Sueldo BCP",
            type = "SAVINGS",
            currency = "PEN",
            presetId = "BCP",
            color = null,
            icon = null,
            initialBalanceMinorUnits = 1200_00L,
            openedAt = nowMicros,
            isArchived = false,
            remoteRevision = 0L,
            createdAt = nowMicros,
            updatedAt = nowMicros,
        )
        accountDao.insert(account)

        val openingMovement = FinancialMovementEntity(
            id = UUID.randomUUID().toString(),
            operationId = operationId,
            operationSequence = 0,
            userId = testUserId,
            kind = MovementKind.OPENING.name,
            amountMinorUnits = 1200_00L,
            currency = "PEN",
            accountId = accountId,
            openingAccountId = accountId,
            effectiveAt = nowMicros,
            status = MovementStatus.POSTED.name,
            createdAt = nowMicros,
        )
        movementDao.insert(openingMovement)

        val retrievedAccount = accountDao.getById(testUserId, accountId)
        assertNotNull(retrievedAccount)
        assertEquals("Sueldo BCP", retrievedAccount?.alias)

        val balance = movementDao.getAccountBalance(testUserId, accountId)
        assertEquals(1200_00L, balance)
    }

    @Test
    fun openingAdjustment_updatesBalanceCorrectlyWithoutMutatingAccountSnapshot() = runTest {
        val accountId = UUID.randomUUID().toString()
        val operationId1 = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        val account = AccountEntity(
            id = accountId,
            userId = testUserId,
            creationOperationId = operationId1,
            alias = "Caja Chica",
            type = "CASH",
            currency = "PEN",
            presetId = "CASH",
            color = null,
            icon = null,
            initialBalanceMinorUnits = 100_00L,
            openedAt = nowMicros,
            isArchived = false,
            remoteRevision = 0L,
            createdAt = nowMicros,
            updatedAt = nowMicros,
        )
        accountDao.insert(account)

        val openingMovementId = UUID.randomUUID().toString()
        movementDao.insert(
            FinancialMovementEntity(
                id = openingMovementId,
                operationId = operationId1,
                operationSequence = 0,
                userId = testUserId,
                kind = MovementKind.OPENING.name,
                amountMinorUnits = 100_00L,
                currency = "PEN",
                accountId = accountId,
                openingAccountId = accountId,
                effectiveAt = nowMicros,
                status = MovementStatus.POSTED.name,
                createdAt = nowMicros,
            )
        )

        // Record opening adjustment: Reversal + Adjustment
        val operationId2 = UUID.randomUUID().toString()
        movementDao.insert(
            FinancialMovementEntity(
                id = UUID.randomUUID().toString(),
                operationId = operationId2,
                operationSequence = 0,
                userId = testUserId,
                kind = MovementKind.REVERSAL.name,
                amountMinorUnits = -100_00L,
                currency = "PEN",
                accountId = accountId,
                effectiveAt = nowMicros,
                status = MovementStatus.POSTED.name,
                reversesMovementId = openingMovementId,
                createdAt = nowMicros,
            )
        )
        movementDao.insert(
            FinancialMovementEntity(
                id = UUID.randomUUID().toString(),
                operationId = operationId2,
                operationSequence = 1,
                userId = testUserId,
                kind = MovementKind.ADJUSTMENT.name,
                amountMinorUnits = 150_00L,
                currency = "PEN",
                accountId = accountId,
                effectiveAt = nowMicros,
                status = MovementStatus.POSTED.name,
                adjustsMovementId = openingMovementId,
                createdAt = nowMicros,
            )
        )

        val newBalance = movementDao.getAccountBalance(testUserId, accountId)
        assertEquals(150_00L, newBalance)

        // Account initial balance snapshot remains unchanged (audit trail)
        val storedAccount = accountDao.getById(testUserId, accountId)
        assertEquals(100_00L, storedAccount?.initialBalanceMinorUnits)
    }

    @Test
    fun archiveAccount_preservesHistoryAndUpdatesActiveList() = runTest {
        val accountId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val nowMicros = System.currentTimeMillis() * 1000L

        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = testUserId,
                creationOperationId = operationId,
                alias = "Ahorro Pasado",
                type = "SAVINGS",
                currency = "USD",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 50_00L,
                openedAt = nowMicros,
                isArchived = false,
                remoteRevision = 0L,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            )
        )

        val activeBefore = accountDao.observeActive(testUserId).first()
        assertEquals(1, activeBefore.size)

        // Archive
        accountDao.setArchived(testUserId, accountId, true, nowMicros)

        val activeAfter = accountDao.observeActive(testUserId).first()
        assertEquals(0, activeAfter.size)

        val archivedList = accountDao.observeArchived(testUserId).first()
        assertEquals(1, archivedList.size)
        assertTrue(archivedList[0].isArchived)
    }
}
