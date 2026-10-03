package com.kipu.app.feature.movements.presentation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.PlansMovementEntitlementProvider
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.DefaultMovementHistoryAccessPolicy
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MovementHistoryAccessIntegrationTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var cacheDao: FeatureAccessCacheDao
    private lateinit var repository: OfflineFirstMovementRepository
    private lateinit var entitlementProvider: PlansMovementEntitlementProvider
    private lateinit var queryUseCase: QueryMovementHistory
    private lateinit var testLeaseEvaluator: FakeLeaseEvaluator

    private val userUuid = UUID.randomUUID()
    private val userId = userUuid.toString()
    private val accountId = "acc-main"
    private val categoryId = "cat-food"

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        cacheDao = database.featureAccessCacheDao()
        val projectionStore = BalanceProjectionStore(dao)
        val source = MovementLocalDataSource(database, dao, projectionStore)

        val syncScheduler = MovementSyncScheduler(context)
        repository = OfflineFirstMovementRepository(
            localDataSource = source,
            balanceProjectionStore = projectionStore,
            hasher = TransactionRequestHasher(),
            revisionHasher = MovementRevisionRequestHasher(),
            syncScheduler = syncScheduler,
            accountDao = database.accountDao(),
            cardDao = database.cardDao(),
            categoryDao = database.categoryDao(),
            merchantDao = database.merchantCatalogDao(),
        )

        testLeaseEvaluator = FakeLeaseEvaluator()
        entitlementProvider = PlansMovementEntitlementProvider(cacheDao, testLeaseEvaluator)
        val accessPolicy = DefaultMovementHistoryAccessPolicy()
        queryUseCase = QueryMovementHistory(
            repository = repository,
            accessPolicy = accessPolicy,
            entitlementProvider = entitlementProvider,
        )

        // Seed account and category
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta Principal",
                type = "CASH",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 100_000L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = categoryId,
                userId = userId,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.categoryDao().insertPresentation(
            CategoryPresentationEntity(
                categoryId = categoryId,
                userId = userId,
                name = "Alimentación",
                icon = "food_icon",
                color = "#FF5722",
                updatedAt = 1000L,
            )
        )

        // Seed 3 movements
        repository.registerTransaction(
            RegisterTransactionCommand(
                idempotencyKey = "cmd-1",
                userId = userId,
                type = MovementType.EXPENSE,
                amountMinor = 2_000L,
                currency = "PEN",
                sourceAccountId = accountId,
                categoryId = categoryId,
                occurredAt = 1_000_000L,
            )
        )
        repository.registerTransaction(
            RegisterTransactionCommand(
                idempotencyKey = "cmd-2",
                userId = userId,
                type = MovementType.EXPENSE,
                amountMinor = 5_000L,
                currency = "PEN",
                sourceAccountId = accountId,
                categoryId = categoryId,
                occurredAt = 2_000_000L,
            )
        )
        repository.registerTransaction(
            RegisterTransactionCommand(
                idempotencyKey = "cmd-3",
                userId = userId,
                type = MovementType.INCOME,
                amountMinor = 10_000L,
                currency = "PEN",
                sourceAccountId = accountId,
                categoryId = categoryId,
                occurredAt = 3_000_000L,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freeUserCanAlwaysQueryBasicHistory() = runBlocking {
        // No premium cache in DB (Free tier)
        val basicQuery = MovementHistoryQuery(limit = 10)
        val page = queryUseCase(userId, basicQuery)

        assertEquals(MovementHistoryAccessDecision.Allowed, page.accessDecision)
        assertFalse(page.fallbackUsed)
        assertEquals(3, page.items.size)
    }

    @Test
    fun freeUserWithAdvancedCriteriaReceivesFallbackAndPreservesBasicHistory() = runBlocking {
        // Advanced query with amount filter
        val advancedQuery = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(minAmountMinor = 3_000L, currency = "PEN"),
            limit = 10,
        )
        val page = queryUseCase(userId, advancedQuery)

        assertTrue(page.accessDecision is MovementHistoryAccessDecision.PremiumRequired)
        assertTrue(page.fallbackUsed)
        // Returns basic history without the denied filter rather than blocking the user
        assertEquals(3, page.items.size)
    }

    @Test
    fun premiumUserWithin72hOfflineConcessionHasFullAdvancedAccess() = runBlocking {
        val verificationTime = 1_000_000L
        val hours48Later = verificationTime + (48L * 3600L * 1000L)
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.Allowed(hours48Later)

        // Seed verified server premium cache (lifetime / no early expiration)
        cacheDao.putVerified(
            FeatureAccessCacheEntity(
                userId = userUuid,
                policyVersion = 1,
                effectiveTier = "PREMIUM",
                entitlementExpiresAt = null,
                verifiedAt = Instant.ofEpochMilli(verificationTime),
                source = "VERIFIED_SERVER",
            )
        )

        val advancedQuery = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(minAmountMinor = 3_000L, currency = "PEN"),
            limit = 10,
        )

        val page = queryUseCase(userId, advancedQuery)
        assertEquals(MovementHistoryAccessDecision.Allowed, page.accessDecision)
        assertFalse(page.fallbackUsed)
        assertEquals(2, page.items.size) // 5_000 and 10_000
    }

    @Test
    fun premiumUserAtOrAfter72hConcessionIsDeniedAdvancedAndFallsBackToBasic() = runBlocking {
        val verificationTime = 1_000_000L
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.RevalidationRequired(
            OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED,
        )

        cacheDao.putVerified(
            FeatureAccessCacheEntity(
                userId = userUuid,
                policyVersion = 1,
                effectiveTier = "PREMIUM",
                entitlementExpiresAt = null,
                verifiedAt = Instant.ofEpochMilli(verificationTime),
                source = "VERIFIED_SERVER",
            )
        )

        val advancedQuery = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(minAmountMinor = 3_000L, currency = "PEN"),
            limit = 10,
        )

        // Exactly at 72h: trustedNow >= notAfter -> RevalidationRequired
        val pageAtLimit = queryUseCase(userId, advancedQuery)
        assertTrue(pageAtLimit.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(pageAtLimit.fallbackUsed)
        assertEquals(3, pageAtLimit.items.size)

        // After 72h
        val pageAfterLimit = queryUseCase(userId, advancedQuery)
        assertTrue(pageAfterLimit.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(pageAfterLimit.fallbackUsed)
        assertEquals(3, pageAfterLimit.items.size)
    }

    @Test
    fun commercialExpirationEarlierThan72hBoundsConcession() = runBlocking {
        val verificationTime = 1_000_000L
        val subscriptionExpiresAt = verificationTime + (24L * 3600L * 1000L) // 24 hours only
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.Allowed(
            verificationTime + (12L * 3600L * 1000L),
        )

        cacheDao.putVerified(
            FeatureAccessCacheEntity(
                userId = userUuid,
                policyVersion = 1,
                effectiveTier = "PREMIUM",
                entitlementExpiresAt = Instant.ofEpochMilli(subscriptionExpiresAt),
                verifiedAt = Instant.ofEpochMilli(verificationTime),
                source = "VERIFIED_SERVER",
            )
        )

        val advancedQuery = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(minAmountMinor = 3_000L, currency = "PEN"),
            limit = 10,
        )

        // At 12h: allowed
        val pageAt12h = queryUseCase(userId, advancedQuery)
        assertEquals(MovementHistoryAccessDecision.Allowed, pageAt12h.accessDecision)
        assertFalse(pageAt12h.fallbackUsed)

        // At 25h: expired commercially despite being within 72h of verification
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.RevalidationRequired(
            OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED,
        )
        val pageAt25h = queryUseCase(userId, advancedQuery)
        assertTrue(pageAt25h.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(pageAt25h.fallbackUsed)
    }

    @Test
    fun reconnectionAndVerificationRestoresAdvancedAccessImmediately() = runBlocking {
        val oldVerificationTime = 1_000_000L
        val expiredTime = oldVerificationTime + (100L * 3600L * 1000L)
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.RevalidationRequired(
            OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED,
        )

        cacheDao.putVerified(
            FeatureAccessCacheEntity(
                userId = userUuid,
                policyVersion = 1,
                effectiveTier = "PREMIUM",
                entitlementExpiresAt = null,
                verifiedAt = Instant.ofEpochMilli(oldVerificationTime),
                source = "VERIFIED_SERVER",
            )
        )

        val advancedQuery = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(minAmountMinor = 3_000L, currency = "PEN"),
            limit = 10,
        )

        // Denied because old verification expired
        val expiredPage = queryUseCase(userId, advancedQuery)
        assertTrue(expiredPage.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)

        // Reconnect: server verification arrives
        cacheDao.putVerified(
            FeatureAccessCacheEntity(
                userId = userUuid,
                policyVersion = 1,
                effectiveTier = "PREMIUM",
                entitlementExpiresAt = null,
                verifiedAt = Instant.ofEpochMilli(expiredTime),
                source = "VERIFIED_SERVER",
            )
        )
        testLeaseEvaluator.decision = OfflineEntitlementLeaseDecision.Allowed(expiredTime + 3600_000L)

        // Query again at expiredTime + 1 hour: now fully allowed!
        val restoredPage = queryUseCase(userId, advancedQuery)
        assertEquals(MovementHistoryAccessDecision.Allowed, restoredPage.accessDecision)
        assertFalse(restoredPage.fallbackUsed)
        assertEquals(2, restoredPage.items.size)
    }

    private class FakeLeaseEvaluator : EffectiveEntitlementEvaluator {
        var decision: OfflineEntitlementLeaseDecision = OfflineEntitlementLeaseDecision.PremiumRequired

        override fun evaluate(
            userId: String,
            cache: FeatureAccessCacheEntity?,
        ): OfflineEntitlementLeaseDecision = if (cache?.effectiveTier == "PREMIUM") decision else OfflineEntitlementLeaseDecision.PremiumRequired
    }
}
