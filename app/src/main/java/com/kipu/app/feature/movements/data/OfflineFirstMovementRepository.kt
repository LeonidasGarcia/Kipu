package com.kipu.app.feature.movements.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.toDomain
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.data.local.MovementQuerySqlBuilder
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.MovementHistoryQueryRepository
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstMovementRepository @Inject constructor(
    private val localDataSource: MovementLocalDataSource,
    private val balanceProjectionStore: BalanceProjectionStore,
    private val hasher: TransactionRequestHasher,
    private val revisionHasher: MovementRevisionRequestHasher,
    private val syncScheduler: MovementSyncScheduler,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
) : MovementRepository, MovementMaintenanceRepository, ExpenseConsumptionRepository, MovementHistoryQueryRepository {

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
                run {
                    val domain = entity.toDomain()
                    val sourceAlias = domain.sourceAccountId?.let { accountDao.getById(userId, it)?.alias }
                    val destAlias = domain.destinationAccountId?.let { accountDao.getById(userId, it)?.alias }
                    val card = domain.cardId?.let { cardDao.getById(userId, it) }
                    val category = domain.categoryId?.let(categoriesById::get)
                    TransactionItem(
                        transaction = domain,
                        sourceAccountAlias = sourceAlias,
                        destinationAccountAlias = destAlias,
                        cardAlias = card?.alias,
                        cardLastFourDigits = card?.lastFourDigits,
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
            it.toDomain()
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
        return entities.map { it.toDomain() }
    }

    override fun observeBalance(userId: String, accountId: String): Flow<Long?> {
        return balanceProjectionStore.observeBalance(userId, accountId)
    }

    override suspend fun getRevisionHead(userId: String, transactionId: String): MovementRevisionHead? {
        return localDataSource.getRevisionHead(userId, transactionId)
    }

    override suspend fun getRevisionAudit(userId: String, transactionId: String): List<MovementRevisionAudit> =
        localDataSource.getRevisionAudit(userId, transactionId)

    override suspend fun revise(userId: String, command: MovementRevisionCommand.Revise): MovementMutationResult {
        val requestHash = revisionHasher.computeHash(command)
        val result = localDataSource.commitRevisionAtomic(userId, command, requestHash)
        if (result is MovementMutationResult.Success && !result.isDuplicate) {
            syncScheduler.scheduleSync(userId)
        }
        return result
    }

    override suspend fun void(userId: String, command: MovementRevisionCommand.Void): MovementMutationResult {
        val requestHash = revisionHasher.computeHash(command)
        val result = localDataSource.commitVoidAtomic(userId, command, requestHash)
        if (result is MovementMutationResult.Success && !result.isDuplicate) {
            syncScheduler.scheduleSync(userId)
        }
        return result
    }

    override suspend fun queryConsumption(userId: String, query: ExpenseConsumptionQuery): ExpenseConsumptionResult {
        return localDataSource.queryExpenseConsumption(userId, query)
    }

    override suspend fun getConflictProposals(userId: String, transactionId: String): List<com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity> {
        return localDataSource.getConflictProposals(userId, transactionId)
    }

    override suspend fun discardProposal(userId: String, proposalId: String): Boolean {
        return localDataSource.discardProposal(userId, proposalId)
    }

    override suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String): MovementMutationResult {
        val result = localDataSource.redoProposal(userId, proposalId, newIdempotencyKey)
        if (result is MovementMutationResult.Success) {
            syncScheduler.scheduleSync(userId)
        }
        return result
    }

    override suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage {
        val sqliteQuery = MovementQuerySqlBuilder.build(userId, query)
        val entities = localDataSource.queryTransactions(sqliteQuery)
        val hasMore = entities.size > query.limit
        val pageEntities = if (hasMore) entities.take(query.limit) else entities
        val items = mapEntitiesToItems(userId, pageEntities)

        val nextCursor = if (hasMore && pageEntities.isNotEmpty()) {
            val last = pageEntities.last()
            MovementHistoryCursor(occurredAt = last.occurredAt, transactionId = last.id)
        } else {
            null
        }

        return MovementHistoryPage(
            items = items,
            nextCursor = nextCursor,
            hasMore = hasMore,
            accessDecision = MovementHistoryAccessDecision.Allowed,
            fallbackUsed = false,
        )
    }

    private suspend fun mapEntitiesToItems(userId: String, entities: List<TransactionEntity>): List<TransactionItem> {
        if (entities.isEmpty()) return emptyList()
        val presentations = categoryDao.observePresentationsForUser(userId).first().associateBy { it.categoryId }
        val merchants = merchantDao.observeMerchants().first().associateBy { it.id }
        return entities.map { entity ->
            val domain = entity.toDomain()
            val sourceAlias = domain.sourceAccountId?.let { accountDao.getById(userId, it)?.alias }
            val destAlias = domain.destinationAccountId?.let { accountDao.getById(userId, it)?.alias }
            val card = domain.cardId?.let { cardDao.getById(userId, it) }
            val category = domain.categoryId?.let(presentations::get)
            TransactionItem(
                transaction = domain,
                sourceAccountAlias = sourceAlias,
                destinationAccountAlias = destAlias,
                cardAlias = card?.alias,
                cardLastFourDigits = card?.lastFourDigits,
                categoryName = category?.name,
                categoryIcon = category?.icon,
                merchantName = domain.merchantProvisionalText
                    ?: domain.merchantId?.let { merchants[it]?.name },
            )
        }
    }
}
