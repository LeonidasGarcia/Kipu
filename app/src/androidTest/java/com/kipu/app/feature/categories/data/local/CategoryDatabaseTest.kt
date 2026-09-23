package com.kipu.app.feature.categories.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDatabaseTest {

    private lateinit var database: KipuDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var merchantDao: MerchantCatalogDao
    private lateinit var movementDao: FinancialMovementDao
    private lateinit var accountDao: AccountDao

    private val user1 = UUID.randomUUID().toString()
    private val user2 = UUID.randomUUID().toString()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
        merchantDao = database.merchantCatalogDao()
        movementDao = database.financialMovementDao()
        accountDao = database.accountDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertCategoriesAndObserveOwnerIsolation() = runTest {
        val cat1 = CategoryEntity(
            id = "cat-1",
            userId = user1,
            parentId = null,
            origin = "CUSTOM",
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val cat2 = CategoryEntity(
            id = "cat-2",
            userId = user2,
            parentId = null,
            origin = "CUSTOM",
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val systemCat = CategoryEntity(
            id = "cat-system",
            userId = null,
            parentId = null,
            origin = "SYSTEM",
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        categoryDao.insertCategories(listOf(cat1, cat2, systemCat))

        val user1Categories = categoryDao.observeCategoriesForUser(user1).first()
        assertEquals(2, user1Categories.size) // cat1 + systemCat
        assertTrue(user1Categories.any { it.id == "cat-1" })
        assertTrue(user1Categories.any { it.id == "cat-system" })
        assertTrue(user1Categories.none { it.id == "cat-2" })
    }

    @Test
    fun updateMovementClassificationIndependently() = runTest {
        val accountId = UUID.randomUUID().toString()
        val movementId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = user1,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Test Account",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 10000L,
                openedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )

        movementDao.insert(
            FinancialMovementEntity(
                id = movementId,
                operationId = UUID.randomUUID().toString(),
                operationSequence = 0,
                userId = user1,
                kind = "OPENING",
                amountMinorUnits = 10000L,
                currency = "PEN",
                accountId = accountId,
                effectiveAt = now,
                createdAt = now,
            )
        )

        // Verify initially classification columns are null
        val initialClassification = categoryDao.observeMovementClassification(movementId, user1).first()
        assertNotNull(initialClassification)
        assertNull(initialClassification?.category_id)
        assertNull(initialClassification?.merchant_id)
        assertNull(initialClassification?.merchant_provisional_text)

        // Update category only
        categoryDao.updateMovementClassification(movementId, user1, "cat-1", null, null)
        val updatedCat = categoryDao.observeMovementClassification(movementId, user1).first()
        assertEquals("cat-1", updatedCat?.category_id)
        assertNull(updatedCat?.merchant_id)

        // Update merchant only (clearing merchant or setting merchant does not affect category)
        categoryDao.updateMovementClassification(movementId, user1, "cat-1", "merch-1", null)
        val updatedBoth = categoryDao.observeMovementClassification(movementId, user1).first()
        assertEquals("cat-1", updatedBoth?.category_id)
        assertEquals("merch-1", updatedBoth?.merchant_id)

        // Clear merchant
        categoryDao.clearMerchantClassification(movementId, user1)
        val clearedMerch = categoryDao.observeMovementClassification(movementId, user1).first()
        assertEquals("cat-1", clearedMerch?.category_id)
        assertNull(clearedMerch?.merchant_id)
    }

    @Test
    fun merchantCatalogSearchByNormalizedSubstring() = runTest {
        merchantDao.insertMerchants(
            listOf(
                MerchantCatalogEntity("m1", "Tambo", "tambo", true, 1L, 1000L),
                MerchantCatalogEntity("m2", "Starbucks Coffee", "starbucks coffee", true, 1L, 1000L),
                MerchantCatalogEntity("m3", "Plaza Vea", "plaza vea", true, 1L, 1000L),
            )
        )

        val tamboResults = merchantDao.searchMerchants("tam").first()
        assertEquals(1, tamboResults.size)
        assertEquals("Tambo", tamboResults.first().name)

        val starbucksResults = merchantDao.searchMerchants("coffee").first()
        assertEquals(1, starbucksResults.size)
        assertEquals("Starbucks Coffee", starbucksResults.first().name)

        val emptyResults = merchantDao.searchMerchants("desconocido").first()
        assertTrue(emptyResults.isEmpty())
    }
}
