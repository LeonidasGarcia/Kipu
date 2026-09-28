package com.kipu.app.feature.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.core.finance.domain.model.MovementStatus
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.OfflineFirstFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningAdjustmentRepositoryTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstFinancialInstrumentsRepository

    private val userId = UUID.randomUUID().toString()
    private val accountId = UUID.randomUUID().toString()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val nowMicros = System.currentTimeMillis() * 1_000L
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta de prueba",
                type = "BANK",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 2_000L,
                openedAt = nowMicros,
                createdAt = nowMicros,
                updatedAt = nowMicros,
            ),
        )
        database.financialMovementDao().insert(
            FinancialMovementEntity(
                id = MovementId.generate().value,
                operationId = UUID.randomUUID().toString(),
                operationSequence = 0,
                userId = userId,
                kind = MovementKind.OPENING.name,
                amountMinorUnits = 2_000L,
                currency = Currency.PEN.name,
                accountId = accountId,
                openingAccountId = accountId,
                effectiveAt = nowMicros,
                status = MovementStatus.POSTED.name,
                createdAt = nowMicros,
            ),
        )
        repository = OfflineFirstFinancialInstrumentsRepository(
            database = database,
            financialApi = mockk<FinancialInstrumentsApi>(relaxed = true),
            accountDao = database.accountDao(),
            cardDao = database.cardDao(),
            movementDao = database.financialMovementDao(),
            syncDao = database.instrumentSyncDao(),
            sessionCoordinator = TestSessionCoordinator(userId),
            syncScheduler = mockk<InstrumentSyncScheduler>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedOpeningCorrectionsReverseTheCurrentlyActiveAdjustment() = runTest {
        assertEquals(2_000L, database.financialMovementDao().getAccountBalance(userId, accountId))

        assertAdjustment(2_100L)
        assertEquals(2_100L, database.financialMovementDao().getAccountBalance(userId, accountId))

        assertAdjustment(2_000L)
        assertEquals(2_000L, database.financialMovementDao().getAccountBalance(userId, accountId))

        assertAdjustment(2_500L)
        assertEquals(2_500L, database.financialMovementDao().getAccountBalance(userId, accountId))
    }

    private suspend fun assertAdjustment(amountMinor: Long) {
        val result = repository.recordOpeningAdjustment(
            accountId = AccountId(accountId),
            correctedAmount = Money(amountMinor, Currency.PEN),
            correctedDate = Instant.now(),
            operationId = OperationId.generate(),
        )
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
    }

    private class TestSessionCoordinator(userId: String) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow<LocalAccess>(LocalAccess.Available(userId, RemoteSession.Absent))
        override val currentOwner = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }
}
