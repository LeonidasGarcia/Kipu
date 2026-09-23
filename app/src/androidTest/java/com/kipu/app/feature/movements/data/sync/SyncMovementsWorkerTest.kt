package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.local.MovementDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMovementsWorkerTest {
    private val userId = UUID.randomUUID().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var movementDao: MovementDao
    private lateinit var accountDao: AccountDao
    private val movementApi: MovementApi = mockk(relaxed = true)
    private val financialApi: FinancialInstrumentsApi = mockk()
    private val session: SessionCoordinator = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        movementDao = database.movementDao()
        accountDao = database.accountDao()
        every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to userId)
        every { session.currentOwner } returns LocalOwner(userId)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pullsAccountThenTransactionAndBuildsOneLedgerProjection() = runTest {
        val accountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val entryId = UUID.randomUUID().toString()
        val accountChange = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":1000,"opened_at":"2026-09-23T10:00:00Z"}"""
            ),
        )
        val transactionChange = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = transactionId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","amount_minor":50,"currency_code":"PEN","source_account_id":"$accountId","category_id":null,"merchant_id":null,"merchant_provisional_text":"Bodega","occurred_at":"2026-09-23T11:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"$entryId","account_id":"$accountId","role":"SOURCE","signed_amount_minor":-50,"currency_code":"PEN","created_at":"2026-09-23T11:00:00Z"}]}"""
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(accountChange, transactionChange), 2, true)),
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 2, false)),
        )
        val worker = worker()

        assertTrue(worker.doWork().javaClass.simpleName.contains("Success"))
        assertEquals("Efectivo", accountDao.getById(userId, accountId)?.alias)
        assertEquals("Bodega", movementDao.getTransactionById(userId, transactionId)?.merchantProvisionalText)
        assertEquals(950L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertEquals(950L, movementDao.getBalanceProjection(userId, accountId)?.balanceMinor)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    private fun worker() = SyncMovementsWorker(
        appContext = context,
        workerParams = params,
        movementDao = movementDao,
        api = movementApi,
        financialApi = financialApi,
        accountDao = accountDao,
        database = database,
        sessionCoordinator = session,
    )
}
