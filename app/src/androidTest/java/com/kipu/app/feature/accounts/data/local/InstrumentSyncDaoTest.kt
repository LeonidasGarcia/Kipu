package com.kipu.app.feature.accounts.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentSyncDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = UUID.randomUUID().toString()
    private lateinit var database: KipuDatabase
    private lateinit var dao: InstrumentSyncDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        dao = database.instrumentSyncDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun expiredLeaseCanBeClaimedOnceAfterWorkerRestart() = runTest {
        val command = command(state = "IN_FLIGHT", leaseExpiresAt = 999L)
        dao.insert(command)

        assertEquals(listOf(command.operationId), dao.getPendingCommands(userId, 1_000L).map { it.operationId })
        assertEquals(1, dao.claimCommand(userId, command.operationId, 1_000L, 61_000L))
        assertEquals(emptyList<InstrumentSyncOutboxEntity>(), dao.getPendingCommands(userId, 1_001L))
        assertEquals(0, dao.claimCommand(userId, command.operationId, 1_001L, 61_001L))
    }

    @Test
    fun successorWaitsForAndRetainsItsAcknowledgedPredecessor() = runTest {
        val predecessor = command(state = "ERROR", nextAttemptAt = 5_000L)
        val successor = command(
            predecessorOperationId = predecessor.operationId,
            createdAt = predecessor.createdAt + 1,
        )
        dao.insert(predecessor)
        dao.insert(successor)

        assertEquals(emptyList<InstrumentSyncOutboxEntity>(), dao.getPendingCommands(userId, 1_000L))
        dao.update(predecessor.copy(state = "SYNCED", nextAttemptAt = null, updatedAt = 2_000L))
        assertEquals(listOf(successor.operationId), dao.getPendingCommands(userId, 2_001L).map { it.operationId })
        assertEquals("SYNCED", dao.getByOperationId(userId, predecessor.operationId)?.state)
        assertNotNull(dao.getByOperationId(userId, predecessor.operationId))
    }

    @Test
    fun terminallyFailedPredecessorDoesNotReleaseItsSuccessor() = runTest {
        val predecessor = command(state = "FAILED_PERMANENT")
        val successor = command(predecessorOperationId = predecessor.operationId)
        dao.insert(predecessor)
        dao.insert(successor)

        assertEquals(emptyList<InstrumentSyncOutboxEntity>(), dao.getPendingCommands(userId, 10_000L))
    }

    private fun command(
        state: String = "PENDING",
        predecessorOperationId: String? = null,
        nextAttemptAt: Long? = null,
        leaseExpiresAt: Long? = null,
        createdAt: Long = 1L,
    ) = InstrumentSyncOutboxEntity(
        operationId = UUID.randomUUID().toString(),
        userId = userId,
        commandType = "CONFIRM_CREDIT_PURCHASE",
        aggregateType = "CARD",
        aggregateId = UUID.randomUUID().toString(),
        predecessorOperationId = predecessorOperationId,
        payloadJson = "{}",
        payloadHash = "hash",
        state = state,
        nextAttemptAt = nextAttemptAt,
        leaseExpiresAt = leaseExpiresAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
