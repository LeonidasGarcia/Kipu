package com.kipu.app.feature.categories.presentation.categories

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.ObserveSelectedFreeCategoryRoots
import com.kipu.app.feature.categories.domain.usecase.SaveSelectedFreeCategoryRoots
import com.kipu.app.feature.categories.domain.usecase.SetCategoryActive
import com.kipu.app.feature.categories.domain.usecase.UpdateCategoryPresentation
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val firstUser = UserId.generate()
    private val secondUser = UserId.generate()
    private val session = FakeSessionCoordinator()
    private val scheduler = RecordingCategorySyncScheduler()
    private val categoryFlows = mapOf(
        firstUser.value to MutableStateFlow(listOf(categoryItem(firstUser, "Primera"))),
        secondUser.value to MutableStateFlow(listOf(categoryItem(secondUser, "Segunda"))),
    )
    private val selectionFlows = mapOf(
        firstUser.value to MutableStateFlow(emptySet<CategoryId>()),
        secondUser.value to MutableStateFlow(emptySet<CategoryId>()),
    )

    private lateinit var viewModel: CategoriesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val observeCategories = mockk<ObserveCategories>()
        val observeSelectedRoots = mockk<ObserveSelectedFreeCategoryRoots>()
        val ensureInitialCatalog = mockk<EnsureInitialCategoryCatalog>()
        every { observeCategories.invoke(firstUser) } returns categoryFlows.getValue(firstUser.value)
        every { observeCategories.invoke(secondUser) } returns categoryFlows.getValue(secondUser.value)
        every { observeCategories.observePremiumVerified(firstUser) } returns MutableStateFlow(false)
        every { observeCategories.observePremiumVerified(secondUser) } returns MutableStateFlow(false)
        every { observeSelectedRoots.invoke(firstUser) } returns selectionFlows.getValue(firstUser.value)
        every { observeSelectedRoots.invoke(secondUser) } returns selectionFlows.getValue(secondUser.value)
        coEvery { ensureInitialCatalog.invoke(firstUser) } returns Result.success(Unit)
        coEvery { ensureInitialCatalog.invoke(secondUser) } returns Result.success(Unit)

        viewModel = CategoriesViewModel(
            observeCategories = observeCategories,
            createCategory = mockk<CreateCategory>(relaxed = true),
            setCategoryActive = mockk<SetCategoryActive>(relaxed = true),
            updateCategoryPresentation = mockk<UpdateCategoryPresentation>(relaxed = true),
            observeSelectedFreeCategoryRoots = observeSelectedRoots,
            saveSelectedFreeCategoryRoots = mockk<SaveSelectedFreeCategoryRoots>(relaxed = true),
            ensureInitialCategoryCatalog = ensureInitialCatalog,
            sessionCoordinator = session,
            syncScheduler = scheduler,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hydrates current owner and cancels previous owner observation on session switch`() = runTest {
        session.localAccess.value = LocalAccess.Available(firstUser.value, RemoteSession.Absent)
        advanceUntilIdle()
        assertEquals(listOf("Primera"), viewModel.uiState.value.categories.map { it.displayName })
        assertEquals(listOf(firstUser.value), scheduler.scheduled)

        session.localAccess.value = LocalAccess.Available(secondUser.value, RemoteSession.Absent)
        advanceUntilIdle()
        assertEquals(listOf(firstUser.value), scheduler.cancelled)
        assertEquals(listOf(firstUser.value, secondUser.value), scheduler.scheduled)
        assertEquals(listOf("Segunda"), viewModel.uiState.value.categories.map { it.displayName })

        categoryFlows.getValue(firstUser.value).value = listOf(categoryItem(firstUser, "No debe mostrarse"))
        advanceUntilIdle()
        assertEquals(listOf("Segunda"), viewModel.uiState.value.categories.map { it.displayName })
    }

    @Test
    fun `counts only custom roots across both category tabs`() = runTest {
        val mixedRoots = listOf(
            categoryItem(firstUser, "Gasto", com.kipu.app.feature.categories.domain.model.CategoryType.EXPENSE),
            categoryItem(firstUser, "Ingreso", com.kipu.app.feature.categories.domain.model.CategoryType.INCOME),
            categoryItem(firstUser, "Sistema", origin = CategoryOrigin.SYSTEM),
        )
        categoryFlows.getValue(firstUser.value).value = mixedRoots
        session.localAccess.value = LocalAccess.Available(firstUser.value, RemoteSession.Absent)

        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.activeCustomRootsCount)
        assertTrue(!viewModel.uiState.value.isFreeLimitReached)
    }

    @Test
    fun `toggleQuotaSelection allows up to 5 expense and 5 income roots (10 total)`() = runTest {
        val expenseRoots = (1..6).map {
            categoryItem(firstUser, "Expense $it", com.kipu.app.feature.categories.domain.model.CategoryType.EXPENSE)
        }
        val incomeRoots = (1..6).map {
            categoryItem(firstUser, "Income $it", com.kipu.app.feature.categories.domain.model.CategoryType.INCOME)
        }
        categoryFlows.getValue(firstUser.value).value = expenseRoots + incomeRoots
        session.localAccess.value = LocalAccess.Available(firstUser.value, RemoteSession.Absent)
        advanceUntilIdle()

        viewModel.openQuotaSelection()
        assertTrue(viewModel.uiState.value.isQuotaSelectionOpen)

        // Select 5 expense roots successfully
        for (i in 0 until 5) {
            viewModel.toggleQuotaSelection(expenseRoots[i].category.id)
        }
        assertEquals(5, viewModel.uiState.value.quotaSelectionDraft.size)
        assertTrue(viewModel.uiState.value.errorMessage == null)

        // 6th expense root is rejected
        viewModel.toggleQuotaSelection(expenseRoots[5].category.id)
        assertEquals(5, viewModel.uiState.value.quotaSelectionDraft.size)
        assertEquals("Solo puedes seleccionar hasta 5 categorías de gasto en el plan Free", viewModel.uiState.value.errorMessage)

        // Now select 5 income roots successfully (reaching 10 total)
        for (i in 0 until 5) {
            viewModel.toggleQuotaSelection(incomeRoots[i].category.id)
        }
        assertEquals(10, viewModel.uiState.value.quotaSelectionDraft.size)

        // 6th income root is rejected
        viewModel.toggleQuotaSelection(incomeRoots[5].category.id)
        assertEquals(10, viewModel.uiState.value.quotaSelectionDraft.size)
        assertEquals("Solo puedes seleccionar hasta 5 categorías de ingreso en el plan Free", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `toggleQuotaSelection with GENERAL category consumes both expense and income quota`() = runTest {
        val expenseRoots = (1..5).map {
            categoryItem(firstUser, "Expense $it", com.kipu.app.feature.categories.domain.model.CategoryType.EXPENSE)
        }
        val generalRoot = categoryItem(firstUser, "General", com.kipu.app.feature.categories.domain.model.CategoryType.GENERAL)
        categoryFlows.getValue(firstUser.value).value = expenseRoots + generalRoot
        session.localAccess.value = LocalAccess.Available(firstUser.value, RemoteSession.Absent)
        advanceUntilIdle()

        viewModel.openQuotaSelection()

        // Select 5 expense roots
        for (item in expenseRoots) {
            viewModel.toggleQuotaSelection(item.category.id)
        }
        assertEquals(5, viewModel.uiState.value.quotaSelectionDraft.size)

        // Selecting general root is blocked because expense quota is already full
        viewModel.toggleQuotaSelection(generalRoot.category.id)
        assertEquals(5, viewModel.uiState.value.quotaSelectionDraft.size)
        assertEquals("Solo puedes seleccionar hasta 5 categorías de gasto en el plan Free", viewModel.uiState.value.errorMessage)
    }

    private fun categoryItem(
        ownerId: UserId,
        name: String,
        type: com.kipu.app.feature.categories.domain.model.CategoryType = com.kipu.app.feature.categories.domain.model.CategoryType.EXPENSE,
        origin: CategoryOrigin = CategoryOrigin.CUSTOM,
    ): CategoryItem {
        val categoryId = CategoryId.generate()
        return CategoryItem(
            category = Category(
                id = categoryId,
                ownerId = if (origin == CategoryOrigin.SYSTEM) null else ownerId,
                parentId = null,
                origin = origin,
                isActive = true,
                categoryType = type,
            ),
            presentation = com.kipu.app.feature.categories.domain.model.CategoryPresentation(
                categoryId = categoryId,
                ownerId = ownerId,
                name = name,
                icon = "category",
                color = "#0F766E",
            ),
        )
    }

    private class RecordingCategorySyncScheduler : CategorySyncScheduler {
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun scheduleSync(userId: String) {
            scheduled += userId
        }

        override fun cancelSync(userId: String) {
            cancelled += userId
        }
    }

    private class FakeSessionCoordinator : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow<LocalAccess>(LocalAccess.NoOwner)
        override val currentOwner: LocalOwner? = null

        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }
}
