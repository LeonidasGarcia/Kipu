package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtEventEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                database.categoryDao(),
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
        assertEquals(DEBT_ID, visible.single().debtId)
        assertEquals(5_000L, visible.single().remainingPrincipalMinor)
        assertEquals("HISTORICAL", visible.single().openingMode.name)
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
                debtId = DEBT_ID,
                eventType = DebtEventType.ADJUSTMENT.name,
                amountMinor = 100L,
                principalDeltaMinor = -100L,
                occurredAt = 2L,
                createdAt = 2L,
            ),
        )

        val retry = repository.openDebt("owner", command(), hasPremiumAccess = false)
        val visible = repository.observeDebts("owner").first().single()

        assertEquals(DebtCommandResult.Duplicate(DEBT_ID, 1L, 4_900L), retry)
        assertEquals(4_900L, visible.remainingPrincipalMinor)
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM debt_events"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox"))
    }

    @Test
    fun descriptiveEditChangesOnlyLabelsAndQueuesAnIdempotentRevision() = runBlocking {
        repository.openDebt("owner", command(), hasPremiumAccess = false)
        val originalEvents = scalarLong("SELECT COUNT(*) FROM debt_events")
        val identity = DebtCommandIdentity(
            "84000000-0000-4000-8000-000000000010",
            DebtCommandHasher.sha256("edit:$DEBT_ID:Banco actualizado:2026-11-01:nota"),
        )

        val result = repository.editDebtDetails(
            userId = "owner",
            debtId = DEBT_ID,
            patch = DebtDescriptionPatch(" Banco   actualizado ", LocalDate.parse("2026-11-01"), " nota ", 1L),
            identity = identity,
        )

        assertTrue(result is DebtDetailsEditResult.Applied)
        val updated = repository.observeDebts("owner").first().single()
        assertEquals("Banco actualizado", updated.counterpartyName)
        assertEquals(5_000L, updated.principalMinor)
        assertEquals(5_000L, updated.remainingPrincipalMinor)
        assertEquals("2026-11-01", updated.dueDate.toString())
        assertEquals("nota", updated.notes)
        assertEquals(originalEvents, scalarLong("SELECT COUNT(*) FROM debt_events"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE command_type='EDIT_DEBT_DETAILS'"))
    }

    @Test
    fun historicalOpeningEventPreservesBalanceAndBlocksPhysicalDeletion() = runBlocking {
        repository.openDebt("owner", command(), hasPremiumAccess = false)
        val identity = DebtCommandIdentity(
            "84000000-0000-4000-8000-000000000011",
            DebtCommandHasher.sha256("delete:$DEBT_ID"),
        )

        val result = repository.deleteDebtIfUnreferenced("owner", DEBT_ID, identity)

        assertEquals(DebtDeleteResult.Rejected("HISTORY_PRESERVED"), result)
        assertEquals(5_000L, repository.observeDebt("owner", DEBT_ID).first()?.remainingPrincipalMinor)
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debts WHERE id='$DEBT_ID'"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_events WHERE debt_id='$DEBT_ID'"))
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE command_type='DELETE_DEBT_IF_UNREFERENCED'"))
        assertEquals(true, database.debtDao().observeHasFinancialHistory("owner", DEBT_ID).first())
    }

    @Test
    fun legacyDebtWithoutEventsCanBeRemovedEvenWhenItHasPositivePrincipal() = runBlocking {
        database.debtDao().insertDebt(
            DebtEntity(
                id = DEBT_ID,
                userId = "owner",
                obligationType = "PAYABLE",
                counterpartyName = "Deuda heredada",
                totalMinor = 2_500L,
                currencyCode = "PEN",
                openedOn = "2026-10-01",
            ),
        )
        val identity = DebtCommandIdentity(
            "84000000-0000-4000-8000-000000000012",
            DebtCommandHasher.sha256("delete-legacy:$DEBT_ID"),
        )

        assertEquals(false, database.debtDao().observeHasFinancialHistory("owner", DEBT_ID).first())

        val result = repository.deleteDebtIfUnreferenced("owner", DEBT_ID, identity)

        assertEquals(DebtDeleteResult.Deleted(DEBT_ID), result)
        assertNull(repository.observeDebt("owner", DEBT_ID).first())
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM debt_events WHERE debt_id='$DEBT_ID'"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE command_type='DELETE_DEBT_IF_UNREFERENCED'"))
    }

    private fun command() = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "84000000-0000-4000-8000-000000000001",
            DebtCommandHasher.sha256("payable:$DEBT_ID:5000:PEN"),
        ),
        debtId = DEBT_ID,
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

    private companion object {
        const val DEBT_ID = "84000000-0000-4000-8000-000000000002"
    }
}
