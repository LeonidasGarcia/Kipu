package com.kipu.app.feature.movements.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementPresentationObservationTest {
    @Test
    fun repeatedReferencesUseOwnerSnapshotAndRefreshAfterAliasChange() = runTest {
        val owner = "owner-a"
        val source = mockk<MovementLocalDataSource>()
        val accountDao = mockk<AccountDao>()
        val cardDao = mockk<CardDao>()
        val categoryDao = mockk<CategoryDao>()
        val merchantDao = mockk<MerchantCatalogDao>()
        val account = AccountEntity(
            id = "shared-account", userId = owner, creationOperationId = "opening",
            alias = "Original", type = "SAVINGS", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0, openedAt = 0,
            createdAt = 0, updatedAt = 0, isArchived = true,
        )
        val accounts = MutableStateFlow(listOf(account))
        val entities = List(1_000) { index ->
            TransactionEntity(
                id = "tx-$index", userId = owner, type = "EXPENSE", amountMinor = 100,
                currencyCode = "PEN", sourceAccountId = account.id, categoryId = "category",
                occurredAt = index.toLong(),
            )
        }
        every { source.observeTransactions(owner) } returns flowOf(entities)
        every { accountDao.observeAll(owner) } returns accounts
        every { cardDao.observeAll(owner) } returns flowOf(emptyList())
        every { categoryDao.observePresentationsForUser(owner) } returns flowOf(emptyList())
        every { merchantDao.observeMerchants() } returns flowOf(emptyList())
        val repository = OfflineFirstMovementRepository(
            source, mockk(), mockk(), mockk(), mockk(), accountDao, cardDao, categoryDao, merchantDao,
        )
        val emissions = Channel<List<com.kipu.app.feature.movements.domain.model.TransactionItem>>(Channel.UNLIMITED)
        val collection = launch {
            repository.observeTransactions(owner).collect { emissions.send(it) }
        }
        val items = emissions.receive()
        assertEquals(1_000, items.size)
        assertEquals(setOf("Original"), items.map { it.sourceAccountAlias }.toSet())
        accounts.value = listOf(account.copy(alias = "Renamed"))
        val renamed = emissions.receive()
        assertEquals(setOf("Renamed"), renamed.map { it.sourceAccountAlias }.toSet())
        assertEquals(entities.map { it.id }, renamed.map { it.transaction.id })
        coVerify(exactly = 0) { accountDao.getById(any(), any()) }
        coVerify(exactly = 0) { cardDao.getById(any(), any()) }
        collection.cancel()
    }
}
