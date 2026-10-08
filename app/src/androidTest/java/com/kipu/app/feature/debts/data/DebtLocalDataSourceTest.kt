package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.SetDebtScheduleCommand
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtLocalDataSourceTest {
    private lateinit var database: KipuDatabase
    private lateinit var source: DebtLocalDataSource

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        source = DebtLocalDataSource(
            database,
            database.debtDao(),
            database.debtOutboxDao(),
            database.movementDao(),
            database.accountDao(),
            database.financialMovementDao(),
            database.categoryDao(),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun historicalOpeningPersistsDebtEventReceiptAndOutboxAtomicallyAndRetryIsIdempotent() = runBlocking {
        val command = command(
            operationId = "81000000-0000-4000-8000-000000000001",
            debtId = "81000000-0000-4000-8000-000000000002",
            mode = DebtOpeningMode.HISTORICAL,
            accountId = null,
        )

        assertTrue(source.openDebt("owner", command, hasPremiumAccess = false) is DebtCommandResult.Applied)
        assertTrue(source.openDebt("owner", command, hasPremiumAccess = false) is DebtCommandResult.Duplicate)
        assertEquals(1L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debts"))
        assertEquals(1L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debt_events"))
        assertEquals(1L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debt_command_outbox"))
        assertEquals(1L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM local_command_receipts WHERE command_type='OPEN_DEBT'"))
        assertEquals(0L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM transactions"))
        assertEquals(0L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
    }

    @Test
    fun newPayableOpeningChangesCashAsPrincipalWithoutCreatingAnOperatingIncome() = runBlocking {
        database.accountDao().insert(
            AccountEntity(
                id = "cash-1",
                userId = "owner",
                creationOperationId = "account-op",
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
            ),
        )

        val command = command(
            operationId = "81000000-0000-4000-8000-000000000003",
            debtId = "81000000-0000-4000-8000-000000000004",
            mode = DebtOpeningMode.NEW_CASH_FLOW,
            accountId = "cash-1",
        )

        assertTrue(source.openDebt("owner", command, hasPremiumAccess = false) is DebtCommandResult.Applied)
        val tx = database.movementDao().getTransactionById("owner", "81000000-0000-4000-8000-000000000003")
        assertEquals("INCOME", tx?.type)
        assertEquals("DEBT_DISBURSEMENT", tx?.operationKind)
        assertEquals(5_000L, database.openHelper.writableDatabase.scalarLong("SELECT signed_amount_minor FROM ledger_entries"))
        assertEquals(5_000L, database.financialMovementDao().getAccountBalance("owner", "cash-1"))
    }

    @Test
    fun freePlanRejectsThirdActiveObligationWithoutPartialWrites() = runBlocking {
        val first = command("81000000-0000-4000-8000-000000000005", "81000000-0000-4000-8000-000000000006", DebtOpeningMode.HISTORICAL, null)
        val second = command("81000000-0000-4000-8000-000000000007", "81000000-0000-4000-8000-000000000008", DebtOpeningMode.HISTORICAL, null)
        val third = command("81000000-0000-4000-8000-000000000009", "81000000-0000-4000-8000-000000000010", DebtOpeningMode.HISTORICAL, null)

        source.openDebt("owner", first, hasPremiumAccess = false)
        source.openDebt("owner", second, hasPremiumAccess = false)
        val result = source.openDebt("owner", third, hasPremiumAccess = false)

        assertEquals(DebtCommandResult.Rejected("FREE_DEBT_QUOTA_EXCEEDED"), result)
        assertEquals(2L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debts"))
        assertEquals(2L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debt_command_outbox"))
    }

    @Test
    fun schedulePersistsExactPendingInstallmentsWithoutLedgerEffectsAndReplayIsIdempotent() = runBlocking {
        val debtId = "81000000-0000-4000-8000-000000000011"
        val open = command("81000000-0000-4000-8000-000000000012", debtId, DebtOpeningMode.HISTORICAL, null)
        source.openDebt("owner", open, hasPremiumAccess = false)
        val plan = scheduleCommand(debtId, open.identity.operationId)

        assertTrue(source.setDebtSchedule("owner", plan) is DebtCommandResult.Applied)
        assertTrue(source.setDebtSchedule("owner", plan) is DebtCommandResult.Duplicate)
        assertEquals(3L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debt_installments WHERE status='PENDING'"))
        assertEquals(5_000L, database.openHelper.writableDatabase.scalarLong("SELECT SUM(amount_minor) FROM debt_installments WHERE debt_id='$debtId'"))
        assertEquals(0L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM transactions"))
        assertEquals(0L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
    }

    @Test
    fun cancelClosurePreservesBalanceAndScheduleHistoryWithoutMovement() = runBlocking {
        val debtId = "81000000-0000-4000-8000-000000000013"
        val open = command("81000000-0000-4000-8000-000000000014", debtId, DebtOpeningMode.HISTORICAL, null)
        source.openDebt("owner", open, hasPremiumAccess = false)
        source.setDebtSchedule("owner", scheduleCommand(debtId, open.identity.operationId))
        val closure = DebtClosureCommand(
            identity = DebtCommandIdentity(
                "81000000-0000-4000-8000-000000000015",
                DebtCommandHasher.sha256("$debtId:CANCEL:agreement"),
            ),
            debtId = debtId,
            expectedRevision = 2L,
            action = CloseDebtAction.CANCEL,
            reason = "Acuerdo cancelado",
        )

        assertTrue(source.closeDebt("owner", closure) is DebtCommandResult.Applied)
        assertEquals("CANCELLED", database.debtDao().getDebt("owner", debtId)?.status)
        assertEquals(5_000L, database.openHelper.writableDatabase.scalarLong("SELECT remaining_minor FROM (SELECT total_minor AS remaining_minor FROM debts WHERE id='$debtId')"))
        assertEquals(3L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM debt_installments WHERE debt_id='$debtId' AND status='CANCELLED'"))
        assertEquals(0L, database.openHelper.writableDatabase.scalarLong("SELECT COUNT(*) FROM transactions"))
        assertEquals(DebtLifecycleStatus.CANCELLED.name, database.debtDao().getDebt("owner", debtId)?.status)
    }

    private fun scheduleCommand(debtId: String, operationSuffix: String) = SetDebtScheduleCommand(
        identity = DebtCommandIdentity(
            "81000000-0000-4000-8000-000000000016",
            DebtCommandHasher.sha256("schedule:$operationSuffix:$debtId"),
        ),
        debtId = debtId,
        expectedRevision = 1L,
        installments = listOf(
            DebtScheduleItem("81000000-0000-4000-8000-000000000017", 1, LocalDate.parse("2026-11-10"), 1_667L),
            DebtScheduleItem("81000000-0000-4000-8000-000000000018", 2, LocalDate.parse("2026-12-10"), 1_667L),
            DebtScheduleItem("81000000-0000-4000-8000-000000000019", 3, LocalDate.parse("2027-01-10"), 1_666L),
        ),
        reminderLeadDays = 3,
    )

    private fun command(
        operationId: String,
        debtId: String,
        mode: DebtOpeningMode,
        accountId: String?,
    ) = OpenDebtCommand(
        identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256("$operationId:$debtId:5000:PEN")),
        debtId = debtId,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 5_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-08"),
        openingMode = mode,
        accountId = accountId,
    )

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
