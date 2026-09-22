package com.kipu.app.feature.accounts

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.categories.data.FakeCategoryDao
import com.kipu.app.feature.categories.data.FakeCategorySyncScheduler
import com.kipu.app.feature.categories.data.FakeMerchantCatalogDao
import com.kipu.app.feature.categories.data.FakeSessionCoordinator
import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.categories.domain.usecase.UpdateMovementClassification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovementClassificationIntegrityTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository
    private lateinit var updateMovementClassification: UpdateMovementClassification

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        categoryDao = FakeCategoryDao()
        merchantDao = FakeMerchantCatalogDao()
        sessionCoordinator = FakeSessionCoordinator(
            LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        )
        syncScheduler = FakeCategorySyncScheduler()

        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }

        repository = OfflineFirstCategoriesRepository(
            transactionRunner = transactionRunner,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            sessionCoordinator = sessionCoordinator,
            syncScheduler = syncScheduler,
        )

        updateMovementClassification = UpdateMovementClassification(repository)
    }

    @Test
    fun `updating classification leaves amount, status, currency and dates untouched`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()
        val merchantId = MerchantId("merchant-123")

        val originalMovement = FinancialMovementEntity(
            id = movementId.value,
            operationId = "op-1",
            operationSequence = 1,
            userId = testUserId.value,
            kind = "EXPENSE",
            amountMinorUnits = 15075L,
            currency = "PEN",
            accountId = "acc-1",
            cardId = null,
            effectiveAt = 1700000000000L,
            status = "POSTED",
            categoryId = null,
            merchantId = null,
            merchantProvisionalText = null,
            createdAt = 1700000000000L,
        )

        // Assign classification
        val result = updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = merchantId,
            provisionalText = null,
        )
        assertTrue(result.isSuccess)

        // Verify that financial attributes are untouched
        assertEquals(15075L, originalMovement.amountMinorUnits)
        assertEquals("PEN", originalMovement.currency)
        assertEquals("EXPENSE", originalMovement.kind)
        assertEquals("POSTED", originalMovement.status)
        assertEquals("acc-1", originalMovement.accountId)
        assertNull(originalMovement.cardId)
        assertEquals(1700000000000L, originalMovement.effectiveAt)

        // Verify that classification was stored
        val updatedClassification = categoryDao.movements[movementId.value]
        assertEquals(categoryId.value, updatedClassification?.category_id)
        assertEquals(merchantId.value, updatedClassification?.merchant_id)
        assertNull(updatedClassification?.merchant_provisional_text)
    }

    @Test
    fun `clearing merchant classification leaves category and ledger intact`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()

        updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = MerchantId("merchant-456"),
            provisionalText = null,
        )

        val clearRes = updateMovementClassification.clearMerchant(movementId)
        assertTrue(clearRes.isSuccess)

        val classification = categoryDao.movements[movementId.value]
        assertEquals(categoryId.value, classification?.category_id)
        assertNull(classification?.merchant_id)
        assertNull(classification?.merchant_provisional_text)
    }
}
