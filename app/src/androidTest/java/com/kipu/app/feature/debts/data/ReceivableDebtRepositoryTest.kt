package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivableDebtRepositoryTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstDebtRepository
    private val scheduler = RecordingDebtSyncScheduler()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = OfflineFirstDebtRepository(
            debtDao = database.debtDao(),
            localDataSource = DebtLocalDataSource(
                database,
                database.debtDao(),
                database.debtOutboxDao(),
                database.movementDao(),
                database.accountDao(),
                database.financialMovementDao(),
                database.categoryDao(),
            ),
            syncScheduler = scheduler,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newReceivableAtomicallyDecreasesCashAndRemainsOfflineVisible() = runBlocking {
        database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT_ID,
                userId = OWNER_ID,
                creationOperationId = "86000000-0000-4000-8000-000000000003",
                alias = "Ahorros",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 30_000L,
                openedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val result = repository.openDebt(
            userId = OWNER_ID,
            command = OpenDebtCommand(
                identity = DebtCommandIdentity(
                    OPERATION_ID,
                    DebtCommandHasher.sha256("receivable:$DEBT_ID:10000:PEN"),
                ),
                debtId = DEBT_ID,
                obligationType = DebtObligationType.RECEIVABLE,
                counterpartyName = "Amiga",
                principalMinor = 10_000L,
                currencyCode = "PEN",
                openedOn = LocalDate.parse("2026-10-08"),
                openingMode = DebtOpeningMode.NEW_CASH_FLOW,
                accountId = ACCOUNT_ID,
            ),
            hasPremiumAccess = false,
        )

        assertTrue(result is DebtCommandResult.Applied)
        assertEquals("EXPENSE", database.movementDao().getTransactionById(OWNER_ID, OPERATION_ID)?.type)
        assertEquals("DEBT_DISBURSEMENT", database.movementDao().getTransactionById(OWNER_ID, OPERATION_ID)?.operationKind)
        assertEquals(-10_000L, scalarLong("SELECT signed_amount_minor FROM ledger_entries WHERE transaction_id='$OPERATION_ID'"))
        assertEquals(-10_000L, scalarLong("SELECT amount_minor_units FROM financial_movements WHERE operation_id='$OPERATION_ID'"))
        val debt = repository.observeDebts(OWNER_ID).first().single()
        assertEquals(10_000L, debt.remainingPrincipalMinor)
        assertEquals(DebtOpeningMode.NEW_CASH_FLOW, debt.openingMode)
        assertEquals(listOf(OWNER_ID), scheduler.scheduledOwners)
    }

    private fun scalarLong(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private class RecordingDebtSyncScheduler : DebtSyncScheduler {
        val scheduledOwners = mutableListOf<String>()
        override fun schedule(userId: String) { scheduledOwners += userId }
    }

    private companion object {
        const val OWNER_ID = "86000000-0000-4000-8000-000000000001"
        const val ACCOUNT_ID = "86000000-0000-4000-8000-000000000002"
        const val DEBT_ID = "86000000-0000-4000-8000-000000000004"
        const val OPERATION_ID = "86000000-0000-4000-8000-000000000005"
    }
}
