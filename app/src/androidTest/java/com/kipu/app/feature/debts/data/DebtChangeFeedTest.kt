package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.sync.DebtChangeFeedApplier
import com.kipu.app.feature.debts.data.sync.DebtChangeApplyResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtChangeFeedTest {
    private lateinit var database: KipuDatabase
    private lateinit var applier: DebtChangeFeedApplier

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        applier = DebtChangeFeedApplier(database, database.debtDao(), database.movementDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun debtAndEventPageAppliesAtomicallyAndAdvancesCheckpoint() = runBlocking {
        val result = applier.applyPage("owner", page(
            change(1, "DEBT", DEBT_ID, debtPayload(revision = 1L)),
            change(2, "DEBT_EVENT", EVENT_ID, eventPayload()),
            nextSequence = 2L,
        ))

        assertTrue(result is DebtChangeApplyResult.Applied)
        assertEquals(5_000L, database.debtDao().getDebt("owner", DEBT_ID)?.totalMinor)
        assertEquals("NEW_CASH_FLOW", database.debtDao().getDebt("owner", DEBT_ID)?.openingMode)
        assertEquals(EVENT_ID, database.debtDao().getEvent("owner", EVENT_ID)?.id)
        assertEquals(2L, database.debtDao().getSyncCheckpoint("owner")?.sequence)
    }

    @Test
    fun olderRemoteRevisionSurfacesConflictAndPreservesLocalValue() = runBlocking {
        database.debtDao().insertDebt(
            DebtEntity(
                id = DEBT_ID,
                userId = "owner",
                obligationType = "PAYABLE",
                counterpartyName = "Local newer name",
                totalMinor = 5_000L,
                currencyCode = "PEN",
                openedOn = "2026-10-01",
                revision = 3L,
                syncState = "PENDING",
            ),
        )

        val result = applier.applyPage("owner", page(
            change(1, "DEBT", DEBT_ID, debtPayload(revision = 2L, counterparty = "Remote stale name"), revision = 2L),
            nextSequence = 1L,
        ))

        assertEquals(DebtChangeApplyResult.Conflict(DEBT_ID, localRevision = 3L, remoteRevision = 2L), result)
        assertEquals("Local newer name", database.debtDao().getDebt("owner", DEBT_ID)?.counterpartyName)
        assertEquals("CONFLICT", database.debtDao().getDebt("owner", DEBT_ID)?.syncState)
        assertNull(database.debtDao().getSyncCheckpoint("owner"))
    }

    @Test
    fun sequenceGapRejectsWholePageWithoutPartialWrites() = runBlocking {
        val result = applier.applyPage("owner", page(
            change(2, "DEBT", DEBT_ID, debtPayload(revision = 1L)),
            nextSequence = 2L,
        ))

        assertEquals(DebtChangeApplyResult.Rejected("SYNC_SEQUENCE_GAP"), result)
        assertNull(database.debtDao().getDebt("owner", DEBT_ID))
        assertNull(database.debtDao().getSyncCheckpoint("owner"))
    }

    @Test
    fun ownerScopedDeleteChangeRemovesUnreferencedHistoricalDebtAndAdvancesCheckpoint() = runBlocking {
        database.debtDao().insertDebt(
            DebtEntity(
                id = DEBT_ID,
                userId = "owner",
                obligationType = "PAYABLE",
                counterpartyName = "Historica",
                totalMinor = 5_000L,
                currencyCode = "PEN",
                openedOn = "2026-10-01",
                openingMode = "HISTORICAL",
            ),
        )

        val result = applier.applyPage(
            "owner",
            page(
                change(1, "DEBT", DEBT_ID, """{"id":"$DEBT_ID","user_id":"owner","revision":2}""", revision = 2L)
                    .copy(operation = "DELETE"),
                nextSequence = 1L,
            ),
        )

        assertTrue(result is DebtChangeApplyResult.Applied)
        assertNull(database.debtDao().getDebtIncludingDeleted("owner", DEBT_ID))
        assertEquals(1L, database.debtDao().getSyncCheckpoint("owner")?.sequence)
    }

    private fun page(vararg changes: SyncChangeItemDto, nextSequence: Long) = PullChangesResponseDto(
        changes = changes.toList(),
        nextSequence = nextSequence,
        hasMore = false,
    )

    private fun change(sequence: Long, entityType: String, id: String, payload: String, revision: Long = 1L) = SyncChangeItemDto(
        sequence = sequence,
        entityType = entityType,
        entityId = id,
        revision = revision,
        operation = "UPSERT",
        payload = Json.parseToJsonElement(payload),
    )

    private fun debtPayload(revision: Long, counterparty: String = "Proveedor") =
        """{"id":"$DEBT_ID","user_id":"owner","obligation_type":"PAYABLE","counterparty_name":"$counterparty","total_minor":5000,"currency_code":"PEN","opened_on":"2026-10-01","opening_mode":"NEW_CASH_FLOW","status":"ACTIVE","revision":$revision}"""

    private fun eventPayload() =
        """{"id":"$EVENT_ID","user_id":"owner","debt_id":"$DEBT_ID","event_type":"DISBURSEMENT","amount_minor":5000,"principal_delta_minor":0,"occurred_at":"2026-10-01T00:00:00Z"}"""

    private companion object {
        const val DEBT_ID = "85000000-0000-4000-8000-000000000001"
        const val EVENT_ID = "85000000-0000-4000-8000-000000000002"
    }
}
