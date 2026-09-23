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
}
