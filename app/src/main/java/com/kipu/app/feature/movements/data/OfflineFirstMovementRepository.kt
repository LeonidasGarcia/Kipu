package com.kipu.app.feature.movements.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstMovementRepository @Inject constructor(
    private val localDataSource: MovementLocalDataSource,
    private val balanceProjectionStore: BalanceProjectionStore,
    private val hasher: TransactionRequestHasher,
    private val syncScheduler: MovementSyncScheduler,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
) : MovementRepository {

    override fun observeTransactions(userId: String): Flow<List<TransactionItem>> =
        observeItems(userId, localDataSource.observeTransactions(userId))

    override fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionItem>> =
        observeItems(userId, localDataSource.observeRecentTransactions(userId, limit))

    private fun observeItems(userId: String, transactions: Flow<List<TransactionEntity>>): Flow<List<TransactionItem>> =
        combine(
            transactions,
            categoryDao.observePresentationsForUser(userId),
            merchantDao.observeMerchants(),
        ) { entities, presentations, merchants ->
            val categoriesById = presentations.associateBy { it.categoryId }
            val merchantsById = merchants.associateBy { it.id }
            entities.map { entity ->
                with(localDataSource) {
                    val domain = entity.toDomain()
                    val sourceAlias = domain.sourceAccountId?.let { accountDao.getById(userId, it)?.alias }
                    val destAlias = domain.destinationAccountId?.let { accountDao.getById(userId, it)?.alias }
                    val category = domain.categoryId?.let(categoriesById::get)
                    TransactionItem(
                        transaction = domain,
                        sourceAccountAlias = sourceAlias,
                        destinationAccountAlias = destAlias,
                        categoryName = category?.name,
                        categoryIcon = category?.icon,
                        merchantName = domain.merchantProvisionalText
                            ?: domain.merchantId?.let { merchantsById[it]?.name },
                    )
                }
            }
        }

    override suspend fun getTransactionById(userId: String, transactionId: String): Transaction? {
        return localDataSource.getTransactionById(userId, transactionId)?.let {
            with(localDataSource) { it.toDomain() }
        }
    }

    override suspend fun registerTransaction(command: RegisterTransactionCommand): RegisterTransactionResult {
        val requestHash = hasher.computeHash(command)
        val result = localDataSource.commitTransactionAtomic(command, requestHash)
        if (result is RegisterTransactionResult.Success && !result.isDuplicate) {
            syncScheduler.scheduleSync(command.userId)
        }
        return result
    }

    override suspend fun findSimilarTransactions(
        userId: String,
        sourceAccountId: String,
        type: MovementType,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        windowMillis: Long,
    ): List<Transaction> {
        val entities = localDataSource.findSimilarTransactions(
            userId = userId,
            sourceAccountId = sourceAccountId,
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            occurredAt = occurredAt,
            windowMillis = windowMillis,
        )
        return entities.map { with(localDataSource) { it.toDomain() } }
    }

    override fun observeBalance(userId: String, accountId: String): Flow<Long?> {
        return balanceProjectionStore.observeBalance(userId, accountId)
    }
}
