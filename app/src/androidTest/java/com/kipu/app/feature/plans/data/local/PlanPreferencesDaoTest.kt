package com.kipu.app.feature.plans.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.plans.domain.model.PlanSelection
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import kotlin.test.assertFailsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanPreferencesDaoTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: PlanPreferencesDao

    private val userA = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val userB = UUID.fromString("20000000-0000-0000-0000-000000000002")
    private val firstOperation = UUID.fromString("30000000-0000-0000-0000-000000000003")
    private val selectedAt = Instant.parse("2026-09-14T15:03:12.123456Z")

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.planPreferencesDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun confirmSelection_atomicallyCommitsPreferenceRevisionAndOutbox() = runTest {
        val operation = dao.confirmSelection(userA, PlanSelection.TRIAL_INTENT, selectedAt, firstOperation)

        assertEquals(firstOperation, operation.operationId)
        assertEquals(1L, operation.selectionRevision)
        assertEquals(PlanSelection.TRIAL_INTENT, operation.selection)
        assertEquals(OutboxStatus.PENDING, operation.status)
        assertEquals(
            listOf(userA.toString(), "TRIAL_INTENT", micros(selectedAt), micros(selectedAt)),
            row("SELECT user_id, selection, selected_at, updated_at FROM plan_preferences"),
        )
        assertEquals(
            listOf(userA.toString(), 1L, 0L),
            row("SELECT user_id, last_issued_revision, accepted_revision FROM plan_selection_sync_state"),
        )
        assertEquals(
            listOf(firstOperation.toString(), userA.toString(), 1L, "TRIAL_INTENT", "PENDING"),
            row("SELECT operation_id, user_id, selection_revision, selection, status FROM sync_outbox"),
        )
        assertEquals(0L, scalarLong("SELECT count(*) FROM feature_access_cache"))
    }

    @Test
    fun confirmSelection_rollsBackAllThreeWritesWhenOutboxInsertFails() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_outbox BEFORE INSERT ON sync_outbox
               BEGIN SELECT RAISE(ABORT, 'injected outbox failure'); END""",
        )

        assertFailsWith<Exception> {
            dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)
        }

        assertEquals(0L, scalarLong("SELECT count(*) FROM plan_preferences"))
        assertEquals(0L, scalarLong("SELECT count(*) FROM plan_selection_sync_state"))
        assertEquals(0L, scalarLong("SELECT count(*) FROM sync_outbox"))
    }

    @Test
    fun duplicateOperation_isLookedUpBeforeMutationAndReturnsOriginalOperation() = runTest {
        val original = dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)
        val duplicate = dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)

        assertEquals(original, duplicate)
        assertEquals(1L, scalarLong("SELECT count(*) FROM sync_outbox"))
        assertEquals(1L, scalarLong("SELECT last_issued_revision FROM plan_selection_sync_state"))
    }

    @Test
    fun duplicateOperationWithDifferentImmutablePayload_isLocalConflictWithoutMutation() = runTest {
        dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)

        assertFailsWith<LocalPlanSelectionConflict> {
            dao.confirmSelection(
                userA,
                PlanSelection.PREMIUM_INTENT,
                selectedAt.plusSeconds(1),
                firstOperation,
            )
        }

        assertEquals("FREE", scalarString("SELECT selection FROM plan_preferences"))
        assertEquals(1L, scalarLong("SELECT count(*) FROM sync_outbox"))
        assertEquals(1L, scalarLong("SELECT last_issued_revision FROM plan_selection_sync_state"))
    }

    @Test
    fun revisionsAreMonotonicFromMaximumIssuedOrAcceptedRevision() = runTest {
        dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE plan_selection_sync_state SET accepted_revision = 7 WHERE user_id = ?",
            arrayOf(userA.toString()),
        )

        val second = dao.confirmSelection(
            userA,
            PlanSelection.PREMIUM_INTENT,
            selectedAt.plusSeconds(2),
            UUID.fromString("30000000-0000-0000-0000-000000000004"),
        )

        assertEquals(8L, second.selectionRevision)
        assertEquals(8L, scalarLong("SELECT last_issued_revision FROM plan_selection_sync_state"))
    }

    @Test
    fun expiredLeaseIsRecoveredWithoutChangingImmutablePayload() = runTest {
        val operation = dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)
        val leaseUntil = selectedAt.plusSeconds(30)
        assertNotNull(dao.acquireNextPending(userA, selectedAt, leaseUntil))

        assertEquals(0, dao.recoverExpiredLeases(leaseUntil.minusNanos(1)))
        assertEquals(1, dao.recoverExpiredLeases(leaseUntil))
        val recovered = dao.findOutbox(firstOperation)
        assertEquals(OutboxStatus.PENDING, recovered?.status)
        assertEquals(operation.operationId, recovered?.operationId)
        assertEquals(operation.selectionRevision, recovered?.selectionRevision)
        assertEquals(operation.selection, recovered?.selection)
        assertEquals(operation.selectedAt, recovered?.selectedAt)
        assertNull(recovered?.leaseUntil)
    }

    @Test
    fun appliedDuplicateStaleAndConflictReconcileToDocumentedTerminalStates() = runTest {
        val results = listOf(
            RemoteSelectionResult.APPLIED to OutboxStatus.COMPLETED,
            RemoteSelectionResult.DUPLICATE to OutboxStatus.COMPLETED,
            RemoteSelectionResult.STALE to OutboxStatus.COMPLETED,
            RemoteSelectionResult.CONFLICT to OutboxStatus.CONFLICT,
        )

        results.forEachIndexed { index, (result, expectedStatus) ->
            val operationId = UUID(0x3000000000000000L, index.toLong() + 10)
            dao.confirmSelection(userA, PlanSelection.FREE, selectedAt.plusSeconds(index.toLong()), operationId)
            dao.reconcile(
                operationId = operationId,
                result = result,
                acceptedRevision = 20L + index,
                currentSelection = PlanSelection.PREMIUM_INTENT,
                currentSelectedAt = selectedAt.minusSeconds(5),
                serverUpdatedAt = selectedAt.plusSeconds(20),
            )

            assertEquals(expectedStatus, dao.findOutbox(operationId)?.status)
            assertEquals(PlanSelection.PREMIUM_INTENT, dao.findPreference(userA)?.selection)
            assertEquals(20L + index, dao.findSyncState(userA)?.acceptedRevision)
        }
    }

    @Test
    fun everyLookupClaimAndRecoveryIsIsolatedByAccount() = runTest {
        dao.confirmSelection(userA, PlanSelection.FREE, selectedAt, firstOperation)
        val userBOperation = UUID.fromString("40000000-0000-0000-0000-000000000004")
        dao.confirmSelection(userB, PlanSelection.PREMIUM_INTENT, selectedAt, userBOperation)

        assertEquals(firstOperation, dao.acquireNextPending(userA, selectedAt, selectedAt.plusSeconds(30))?.operationId)
        assertEquals(userBOperation, dao.acquireNextPending(userB, selectedAt, selectedAt.plusSeconds(30))?.operationId)
        assertEquals(1, dao.pendingCount(userA))
        assertEquals(1, dao.pendingCount(userB))
        assertEquals(PlanSelection.FREE, dao.findPreference(userA)?.selection)
        assertEquals(PlanSelection.PREMIUM_INTENT, dao.findPreference(userB)?.selection)
    }

    private fun micros(value: Instant): Long = value.epochSecond * 1_000_000L + value.nano / 1_000

    private fun scalarLong(sql: String): Long = database.openHelper.readableDatabase.query(sql).use {
        check(it.moveToFirst())
        it.getLong(0)
    }

    private fun scalarString(sql: String): String = database.openHelper.readableDatabase.query(sql).use {
        check(it.moveToFirst())
        it.getString(0)
    }

    private fun row(sql: String): List<Any> = database.openHelper.readableDatabase.query(sql).use {
        check(it.moveToFirst())
        (0 until it.columnCount).map { column ->
            when (it.getType(column)) {
                android.database.Cursor.FIELD_TYPE_INTEGER -> it.getLong(column)
                else -> it.getString(column)
            }
        }
    }
}
