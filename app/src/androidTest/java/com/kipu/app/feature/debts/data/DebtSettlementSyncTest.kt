package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
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
class DebtSettlementSyncTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstDebtRepository
    private lateinit var scheduler: RecordingDebtScheduler

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        val localDataSource = DebtLocalDataSource(
            database = database,
            debtDao = database.debtDao(),
            debtOutboxDao = database.debtOutboxDao(),
            movementDao = database.movementDao(),
            accountDao = database.accountDao(),
            financialMovementDao = database.financialMovementDao(),
            categoryDao = database.categoryDao(),
        )
        scheduler = RecordingDebtScheduler()
        repository = OfflineFirstDebtRepository(database.debtDao(), localDataSource, scheduler)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun retryCreatesOnePrincipalEventAndAtMostOneInterestMovement() = runBlocking {
        database.accountDao().insert(account())
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = CATEGORY_ID,
                userId = OWNER_ID,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1L,
                updatedAt = 1L,
                categoryType = "EXPENSE",
            ),
        )
        assertTrue(repository.openDebt(OWNER_ID, openCommand(), hasPremiumAccess = true) is DebtCommandResult.Applied)
        scheduler.requestedOwners.clear()

        val command = SettleDebtCommand(
            identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("settle-once:$OPERATION_ID")),
            debtId = DEBT_ID,
            expectedRevision = 1L,
            accountId = ACCOUNT_ID,
            principalMinor = 2_000L,
            interestMinor = 100L,
            interestCategoryId = CATEGORY_ID,
            occurredAt = 1_791_000_000_000L,
        )

        assertTrue(repository.settleDebt(OWNER_ID, command) is DebtCommandResult.Applied)
        assertTrue(repository.settleDebt(OWNER_ID, command) is DebtCommandResult.Duplicate)
        val databaseSql = database.openHelper.writableDatabase
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM debt_events WHERE event_type='PAYMENT' AND debt_id='$DEBT_ID'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='$OPERATION_ID' AND operation_kind='DEBT_PAYMENT'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM transactions WHERE operation_kind='DEBT_AMORTIZATION'"))
        assertEquals(2L, databaseSql.scalarLong("SELECT COUNT(*) FROM financial_movements WHERE operation_id='$OPERATION_ID'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE command_type='SETTLE_DEBT'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM local_command_receipts WHERE command_type='SETTLE_DEBT'"))
        assertEquals(-2_100L, databaseSql.scalarLong("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE user_id='$OWNER_ID' AND account_id='$ACCOUNT_ID'"))
        assertEquals(listOf(OWNER_ID), scheduler.requestedOwners)
        assertEquals(8_000L, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
    }

    private fun openCommand() = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "87000000-0000-4000-8000-000000000010",
            DebtCommandHasher.sha256("open:$DEBT_ID"),
        ),
        debtId = DEBT_ID,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-08"),
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private fun account() = AccountEntity(
        id = ACCOUNT_ID,
        userId = OWNER_ID,
        creationOperationId = "87000000-0000-4000-8000-000000000011",
        alias = "Ahorros",
        type = "SAVINGS",
        currency = "PEN",
        presetId = null,
        color = null,
        icon = null,
        initialBalanceMinorUnits = 0L,
        openedAt = 1L,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private class RecordingDebtScheduler : DebtSyncScheduler {
        val requestedOwners = mutableListOf<String>()
        override fun schedule(userId: String) { requestedOwners += userId }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val OWNER_ID = "87000000-0000-4000-8000-000000000001"
        const val ACCOUNT_ID = "87000000-0000-4000-8000-000000000002"
        const val DEBT_ID = "87000000-0000-4000-8000-000000000003"
        const val CATEGORY_ID = "87000000-0000-4000-8000-000000000004"
        const val OPERATION_ID = "87000000-0000-4000-8000-000000000005"
    }
}
