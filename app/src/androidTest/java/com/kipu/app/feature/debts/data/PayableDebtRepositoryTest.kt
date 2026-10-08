package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtEventEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtEventType
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
class PayableDebtRepositoryTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstDebtRepository
    private val scheduler = RecordingDebtSyncScheduler()

    @Before
    fun createRepository() {
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
            ),
            syncScheduler = scheduler,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun historicalDebtIsVisibleOfflineAndSchedulesOwnerScopedSync() = runBlocking {
        val result = repository.openDebt("owner", command(), hasPremiumAccess = false)

        assertTrue(result is DebtCommandResult.Applied)
        val visible = repository.observeDebts("owner").first()
        assertEquals(1, visible.size)
        assertEquals("debt-1", visible.single().debtId)
        assertEquals(5_000L, visible.single().remainingPrincipalMinor)
        assertEquals(listOf("owner"), scheduler.scheduledOwners)
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE state='PENDING'"))
    }

    @Test
    fun retryDoesNotDuplicateOpeningOrEraseExistingFinancialHistory() = runBlocking {
        repository.openDebt("owner", command(), hasPremiumAccess = false)
        database.debtDao().insertEvent(
            DebtEventEntity(
                id = "later-adjustment",
                userId = "owner",
                debtId = "debt-1",
                eventType = DebtEventType.ADJUSTMENT.name,
                amountMinor = 100L,
                principalDeltaMinor = -100L,
                occurredAt = 2L,
                createdAt = 2L,
            ),
        )

        val retry = repository.openDebt("owner", command(), hasPremiumAccess = false)
        val visible = repository.observeDebts("owner").first().single()

        assertEquals(DebtCommandResult.Duplicate("debt-1", 1L, 4_900L), retry)
        assertEquals(4_900L, visible.remainingPrincipalMinor)
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM debt_events"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox"))
    }

    private fun command() = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "84000000-0000-4000-8000-000000000001",
            DebtCommandHasher.sha256("payable:debt-1:5000:PEN"),
        ),
        debtId = "debt-1",
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor local",
        principalMinor = 5_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-08"),
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private fun scalarLong(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private class RecordingDebtSyncScheduler : DebtSyncScheduler {
        val scheduledOwners = mutableListOf<String>()
        override fun schedule(userId: String) {
            scheduledOwners += userId
        }
    }
}
