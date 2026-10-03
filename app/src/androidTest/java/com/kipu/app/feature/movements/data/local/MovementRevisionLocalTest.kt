package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MovementRevisionLocalTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var source: MovementLocalDataSource
    private lateinit var repository: OfflineFirstMovementRepository
    private lateinit var projectionStore: BalanceProjectionStore
    private val userId = UUID.randomUUID().toString()
    private val revisionHasher = MovementRevisionRequestHasher()
    private val requestHasher = TransactionRequestHasher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        projectionStore = BalanceProjectionStore(dao)
        source = MovementLocalDataSource(database, dao, projectionStore)

        val syncScheduler = MovementSyncScheduler(context)
        repository = OfflineFirstMovementRepository(
            localDataSource = source,
            balanceProjectionStore = projectionStore,
            hasher = requestHasher,
            revisionHasher = revisionHasher,
            syncScheduler = syncScheduler,
            accountDao = database.accountDao(),
            cardDao = database.cardDao(),
            categoryDao = database.categoryDao(),
            merchantDao = database.merchantCatalogDao(),
        )

        runBlocking {
            seedAccount("acc-a", "PEN")
            seedAccount("acc-b", "PEN")
            database.categoryDao().insertCategory(
                CategoryEntity(
                    id = "food",
                    userId = null,
                    parentId = null,
                    origin = "SYSTEM",
                    categoryType = "EXPENSE",
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            )
        }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seedAccount(id: String, currency: String = "PEN", owner: String = userId) {
        database.accountDao().insert(
            AccountEntity(
                id = id,
                userId = owner,
                creationOperationId = UUID.randomUUID().toString(),
                alias = id,
                type = "SAVINGS",
                currency = currency,
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    private fun registerCommand(
        accountId: String = "acc-a",
        amountMinor: Long = 2000L,
        occurredAt: Long = 1500L,
    ) = RegisterTransactionCommand(
        idempotencyKey = UUID.randomUUID().toString(),
        userId = userId,
        type = MovementType.EXPENSE,
        amountMinor = amountMinor,
        currency = "PEN",
        sourceAccountId = accountId,
        destinationAccountId = null,
        categoryId = "food",
        occurredAt = occurredAt,
        note = "Initial expense",
    )

    @Test
    fun commitRevisionAtomicAppendsCompensationsAndRebuildsBalance() = runTest {
        // 1. Initial register: S/20 on acc-a
        val regCmd = registerCommand(amountMinor = 2000L)
        val regResult = repository.registerTransaction(regCmd)
        assertTrue(regResult is RegisterTransactionResult.Success)
        val txId = (regResult as RegisterTransactionResult.Success).transaction.id

        assertEquals(-2000L, dao.calculateLedgerSumForAccount(userId, "acc-a"))
        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)

        // 2. Revise to S/15 (recovers S/5)
        val reviseKey = UUID.randomUUID().toString()
        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = reviseKey,
            transactionId = txId,
            expectedRevision = 1L,
            payload = MovementRevisionPayload(
                type = MovementType.EXPENSE,
                operationKind = "STANDARD",
                amountMinor = 1500L,
                currency = "PEN",
                sourceAccountId = "acc-a",
                destinationAccountId = null,
                categoryId = "food",
                merchantId = null,
                merchantProvisionalText = null,
                occurredAt = 1500L,
                note = "Revised expense",
            ),
        )

        val reviseResult = repository.revise(userId, reviseCmd)
        assertTrue(reviseResult is MovementMutationResult.Success)
        val success = reviseResult as MovementMutationResult.Success
        assertFalse(success.isDuplicate)
        assertEquals(2L, success.head.commandBaseRevision)

        // 3. Verify ledger entries: original (-2000) + reversal (+2000) + new (-1500) = net -1500
        val entries = dao.getLedgerEntriesForTransaction(userId, txId)
        assertEquals(3, entries.size)
        assertEquals(-1500L, dao.calculateLedgerSumForAccount(userId, "acc-a"))
        assertEquals(-1500L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)

        // 4. Verify revision snapshot
        val revisions = dao.getRevisions(userId, txId)
        assertEquals(1, revisions.size)
        assertEquals(reviseKey, revisions[0].commandId)
        assertEquals(1L, revisions[0].baseRevision)
        assertEquals(2L, revisions[0].localRevision)

        // 5. Verify receipt & outbox
        val receipt = dao.getReceipt(userId, reviseKey)
        assertNotNull(receipt)
        assertEquals("APPLIED", receipt?.status)

        val outbox = dao.findClaimableOutbox(userId, System.currentTimeMillis() + 1000L, 10)
        assertTrue(outbox.any { it.idempotencyKey == reviseKey })
    }

    @Test
    fun reviseReplayReturnsDuplicateWithoutNewLedgerEntries() = runTest {
        val regCmd = registerCommand(amountMinor = 2000L)
        val regResult = repository.registerTransaction(regCmd) as RegisterTransactionResult.Success
        val txId = regResult.transaction.id

        val reviseKey = UUID.randomUUID().toString()
        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = reviseKey,
            transactionId = txId,
            expectedRevision = 1L,
            payload = MovementRevisionPayload(
                type = MovementType.EXPENSE,
                operationKind = "STANDARD",
                amountMinor = 1500L,
                currency = "PEN",
                sourceAccountId = "acc-a",
                destinationAccountId = null,
                categoryId = "food",
                occurredAt = 1500L,
                note = "Revised",
            ),
        )

        val first = repository.revise(userId, reviseCmd)
        val second = repository.revise(userId, reviseCmd)

        assertTrue(first is MovementMutationResult.Success)
        assertFalse((first as MovementMutationResult.Success).isDuplicate)

        assertTrue(second is MovementMutationResult.Success)
        assertTrue((second as MovementMutationResult.Success).isDuplicate)

        // Still only 3 entries (+2000, -1500, and original -2000)
        assertEquals(3, dao.getLedgerEntriesForTransaction(userId, txId).size)
    }

    @Test
    fun rollbackPreservesOriginalStateAndDiscardsFailedRevision() = runTest {
        val regCmd = registerCommand(amountMinor = 2000L)
        val regResult = repository.registerTransaction(regCmd) as RegisterTransactionResult.Success
        val txId = regResult.transaction.id

        // Attempt revision inside a transaction that fails
        try {
            database.withTransaction {
                val reviseCmd = MovementRevisionCommand.Revise(
                    idempotencyKey = UUID.randomUUID().toString(),
                    transactionId = txId,
                    expectedRevision = 1L,
                    payload = MovementRevisionPayload(
                        type = MovementType.EXPENSE,
                        operationKind = "STANDARD",
                        amountMinor = 1500L,
                        currency = "PEN",
                        sourceAccountId = "acc-a",
                        categoryId = "food",
                        occurredAt = 1500L,
                    ),
                )
                source.commitRevisionAtomic(userId, reviseCmd, "temp-hash")
                throw IllegalStateException("Simulated abort after revision")
            }
        } catch (_: IllegalStateException) {
            // Expected
        }

        // Totals and rows must remain exactly as the original S/20
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, txId).size)
        assertEquals(-2000L, dao.calculateLedgerSumForAccount(userId, "acc-a"))
        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(0, dao.getRevisions(userId, txId).size)
    }

    @Test
    fun dateChangeFromPeriodAToPeriodBUpdatesConsumptionToAZeroBTwentyWithCashBalanceIntact() = runTest {
        val zone = ZoneId.of("America/Lima")
        val periodA = ExpenseConsumptionQuery("PEN", 1000L, 2000L, zone)
        val periodB = ExpenseConsumptionQuery("PEN", 2000L, 3000L, zone)

        // 1. Initial: S/20 in Period A (occurredAt = 1500L)
        val regCmd = registerCommand(amountMinor = 2000L, occurredAt = 1500L)
        val regResult = repository.registerTransaction(regCmd) as RegisterTransactionResult.Success
        val txId = regResult.transaction.id

        // Consumption check: A=2000, B=0
        assertEquals(2000L, repository.queryConsumption(userId, periodA).amountMinor)
        assertEquals(0L, repository.queryConsumption(userId, periodB).amountMinor)
        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)

        // 2. Revise date to Period B (occurredAt = 2500L)
        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = txId,
            expectedRevision = 1L,
            payload = MovementRevisionPayload(
                type = MovementType.EXPENSE,
                operationKind = "STANDARD",
                amountMinor = 2000L,
                currency = "PEN",
                sourceAccountId = "acc-a",
                categoryId = "food",
                occurredAt = 2500L,
                note = "Moved to Period B",
            ),
        )
        val reviseResult = repository.revise(userId, reviseCmd)
        assertTrue(reviseResult is MovementMutationResult.Success)

        // Cash balance intact: date change appends 0 cash ledger entries!
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, txId).size)
        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)

        // Consumption check: A=0, B=2000
        assertEquals(0L, repository.queryConsumption(userId, periodA).amountMinor)
        assertEquals(2000L, repository.queryConsumption(userId, periodB).amountMinor)

        // 3. Void transaction: both period A and period B become 0, cash balance restored to 0
        val voidCmd = MovementRevisionCommand.Void(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = txId,
            expectedRevision = 2L,
        )
        val voidResult = repository.void(userId, voidCmd)
        assertTrue(voidResult is MovementMutationResult.Success)

        assertEquals(0L, repository.queryConsumption(userId, periodA).amountMinor)
        assertEquals(0L, repository.queryConsumption(userId, periodB).amountMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
    }

    @Test
    fun reviseAccountReversesOldAccountAndDebitsNewAccount() = runTest {
        // Register S/20 on acc-a
        val regCmd = registerCommand(accountId = "acc-a", amountMinor = 2000L)
        val regResult = repository.registerTransaction(regCmd) as RegisterTransactionResult.Success
        val txId = regResult.transaction.id

        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-b")?.balanceMinor ?: 0L)

        // Revise source account to acc-b
        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = txId,
            expectedRevision = 1L,
            payload = MovementRevisionPayload(
                type = MovementType.EXPENSE,
                operationKind = "STANDARD",
                amountMinor = 2000L,
                currency = "PEN",
                sourceAccountId = "acc-b",
                categoryId = "food",
                occurredAt = 1500L,
            ),
        )
        val reviseResult = repository.revise(userId, reviseCmd)
        assertTrue(reviseResult is MovementMutationResult.Success)

        // acc-a should now be 0 (reversed), acc-b should be -2000
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(-2000L, dao.getBalanceProjection(userId, "acc-b")?.balanceMinor)
    }

    @Test
    fun voidTransferReversesBothAccountsAndReplayIsIdempotent() = runTest {
        // Register Transfer S/40 from acc-a to acc-b
        val transferCmd = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = userId,
            type = MovementType.TRANSFER,
            amountMinor = 4000L,
            currency = "PEN",
            sourceAccountId = "acc-a",
            destinationAccountId = "acc-b",
            categoryId = null,
            merchantId = null,
            occurredAt = 1000L,
        )
        val regResult = repository.registerTransaction(transferCmd) as RegisterTransactionResult.Success
        val txId = regResult.transaction.id

        // acc-a: -4000, acc-b: +4000
        assertEquals(-4000L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(4000L, dao.getBalanceProjection(userId, "acc-b")?.balanceMinor)
        assertEquals(2, dao.getLedgerEntriesForTransaction(userId, txId).size)

        // Void the transfer
        val voidCmd = MovementRevisionCommand.Void(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = txId,
            expectedRevision = 1L,
            reason = "Transferencia anulada por usuario",
        )
        val voidResult1 = repository.void(userId, voidCmd)
        assertTrue(voidResult1 is MovementMutationResult.Success)
        assertFalse((voidResult1 as MovementMutationResult.Success).isDuplicate)

        // Balances restored: acc-a: 0, acc-b: 0
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-b")?.balanceMinor)
        assertEquals(4, dao.getLedgerEntriesForTransaction(userId, txId).size)

        // Replay same void command
        val voidResult2 = repository.void(userId, voidCmd)
        assertTrue(voidResult2 is MovementMutationResult.Success)
        assertTrue((voidResult2 as MovementMutationResult.Success).isDuplicate)

        // Still only 4 ledger entries (zero duplicates generated)
        assertEquals(4, dao.getLedgerEntriesForTransaction(userId, txId).size)
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-a")?.balanceMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "acc-b")?.balanceMinor)
    }
}

