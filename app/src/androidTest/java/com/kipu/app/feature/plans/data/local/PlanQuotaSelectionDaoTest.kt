package com.kipu.app.feature.plans.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanQuotaSelectionDaoTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: PlanQuotaSelectionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KipuDatabase::class.java,
        ).build()
        dao = database.planQuotaSelectionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun selectionAndItemsCommitTogetherAndRevisionOnlyChangesWhenSelectionChanges() {
        kotlinx.coroutines.runBlocking {
            val first = dao.replaceSelection(
                userId = "user-1",
                featureKey = "CUSTOM_CATEGORIES",
                resourceType = "CATEGORY_ROOT",
                resourceIds = listOf("category-2", "category-1", "category-2"),
                now = 100L,
            )
            val duplicate = dao.replaceSelection(
                "user-1", "CUSTOM_CATEGORIES", "CATEGORY_ROOT",
                listOf("category-1", "category-2"), 200L,
            )

            assertEquals(1L, first.revision)
            assertEquals(first, duplicate)
            assertEquals(listOf("category-1", "category-2"), dao.getSelectedResourceIds("user-1", "CUSTOM_CATEGORIES"))

            val second = dao.replaceSelection(
                "user-1", "CUSTOM_CATEGORIES", "CATEGORY_ROOT", listOf("category-3"), 300L,
            )
            assertEquals(2L, second.revision)
            assertEquals(listOf("category-3"), dao.getSelectedResourceIds("user-1", "CUSTOM_CATEGORIES"))
            assertEquals(emptyList<String>(), dao.getSelectedResourceIds("user-2", "CUSTOM_CATEGORIES"))
        }
    }

    @Test
    fun rebaseUsesTheGreaterLocalOrAcceptedRevisionAndPreservesLatestItems() {
        kotlinx.coroutines.runBlocking {
            listOf(0L to 3L, 5L to 6L).forEach { (acceptedRevision, expectedRevision) ->
                database.clearAllTables()
                dao.replaceSelection(
                    userId = "user-1",
                    featureKey = "CUSTOM_CATEGORIES",
                    resourceType = "CATEGORY_ROOT",
                    resourceIds = listOf("local-2", "local-1"),
                    now = 100L,
                    resourceTypesById = mapOf("local-1" to "CATEGORY_ROOT", "local-2" to "CATEGORY_CHILD"),
                )
                val locallyUpdated = dao.replaceSelection(
                    userId = "user-1",
                    featureKey = "CUSTOM_CATEGORIES",
                    resourceType = "CATEGORY_ROOT",
                    resourceIds = listOf("latest-local"),
                    now = 200L,
                    resourceTypesById = mapOf("latest-local" to "CATEGORY_CHILD"),
                )

                val rebased = dao.rebaseSelection(
                    userId = "user-1",
                    featureKey = "CUSTOM_CATEGORIES",
                    acceptedRevision = acceptedRevision,
                    now = 300L,
                )

                assertEquals(2L, locallyUpdated.revision)
                assertEquals(expectedRevision, rebased?.revision)
                assertEquals(300L, rebased?.updatedAt)
                assertEquals(listOf("latest-local"), dao.getSelectedResourceIds("user-1", "CUSTOM_CATEGORIES"))
                assertEquals("CATEGORY_CHILD", dao.getSelectedItems("user-1", "CUSTOM_CATEGORIES").single().resourceType)
            }
        }
    }
}
