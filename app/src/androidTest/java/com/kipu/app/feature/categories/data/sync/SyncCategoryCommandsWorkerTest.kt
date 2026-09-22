package com.kipu.app.feature.categories.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import com.kipu.app.feature.categories.data.remote.CategoryCommandResponseDto
import com.kipu.app.feature.categories.data.remote.MerchantCatalogItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncCategoryCommandsWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val merchantDao: MerchantCatalogDao = mockk(relaxed = true)
    private val api: CategoriesApi = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)

    private val testUserId = UUID.randomUUID().toString()

    @Before
    fun setup() {
        coEvery { workerParams.inputData } returns workDataOf(
            SyncCategoryCommandsWorker.KEY_USER_ID to testUserId
        )
    }

    @Test
    fun workerProcessesPendingCommandAndHydratesCatalog() = runTest {
        val opId = UUID.randomUUID().toString()
        val pendingCmd = CategorySyncOutboxEntity(
            operationId = opId,
            userId = testUserId,
            commandType = "CREATE_CATEGORY",
            aggregateType = "CATEGORY",
            aggregateId = UUID.randomUUID().toString(),
            payloadJson = """{"operation_id":"$opId","category_id":"cat-1","name":"Alimentacion","icon":"food","color":"#FF0000","payload_hash":"hash1"}""",
            payloadHash = "hash1",
            state = "PENDING",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { categoryDao.getPendingOutboxCommands(testUserId) } returns listOf(pendingCmd)
        coEvery { api.createCategory(any()) } returns CategoryApiResponse.Success(
            CategoryCommandResponseDto(success = true)
        )
        coEvery { merchantDao.getLatestVersion() } returns 0L
        coEvery { api.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(
            listOf(
                MerchantCatalogItemDto("m1", "Tambo", "tambo", true, 1L)
            )
        )

        val worker = SyncCategoryCommandsWorker(
            appContext = context,
            workerParams = workerParams,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            api = api,
            sessionCoordinator = sessionCoordinator
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        coVerify { categoryDao.updateOutboxCommand(match { it.state == "COMPLETED" }) }
        coVerify { merchantDao.insertMerchants(match { it.size == 1 && it.first().id == "m1" }) }
    }
}
