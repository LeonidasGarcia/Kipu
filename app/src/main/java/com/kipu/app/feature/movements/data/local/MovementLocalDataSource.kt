package com.kipu.app.feature.movements.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.movements.data.MovementOutboxPayloadFactory
import com.kipu.app.feature.movements.domain.model.LedgerRole
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.feature.plans.domain.PlanQuotaPolicy
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementLocalDataSource @Inject constructor(
    private val database: KipuDatabase,
    private val movementDao: MovementDao,
    private val balanceProjectionStore: BalanceProjectionStore,
    private val quotaPolicy: PlanQuotaPolicy = PlanQuotaPolicy(),
) {
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>> {
        return movementDao.observeTransactions(userId)
    }

    fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionEntity>> {
        return movementDao.observeRecentTransactions(userId, limit)
    }

    suspend fun getTransactionById(userId: String, transactionId: String): TransactionEntity? {
        return movementDao.getTransactionById(userId, transactionId)
    }

    suspend fun findSimilarTransactions(
        userId: String,
        sourceAccountId: String,
        type: MovementType,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        windowMillis: Long,
    ): List<TransactionEntity> {
        return movementDao.findSimilarTransactions(
            userId = userId,
            sourceAccountId = sourceAccountId,
            type = type.name,
            amountMinor = amountMinor,
            currencyCode = currency,
            occurredAt = occurredAt,
            windowMillis = windowMillis,
        )
    }

    suspend fun commitTransactionAtomic(
        command: RegisterTransactionCommand,
        requestHash: String,
    ): RegisterTransactionResult {
        return database.withTransaction {
            // 1. Check existing receipt for idempotency
            val existingReceipt = movementDao.getReceipt(command.userId, command.idempotencyKey)
            if (existingReceipt != null) {
                if (existingReceipt.requestHash == requestHash && existingReceipt.transactionId != null) {
                    val existingEntity = movementDao.getTransactionById(command.userId, existingReceipt.transactionId)
                    if (existingEntity != null) {
                        return@withTransaction RegisterTransactionResult.Success(
                            transaction = existingEntity.toDomain(),
                            isDuplicate = true,
                        )
                    }
                    return@withTransaction RegisterTransactionResult.Failure(
                        "El recibo local existe, pero falta su transacción; se requiere reconciliación"
                    )
                } else {
                    return@withTransaction RegisterTransactionResult.Conflict(
                        "Idempotency conflict: key ${command.idempotencyKey} already used with different payload"
                    )
                }
            }

            val sourceId = command.sourceAccountId
                ?: return@withTransaction RegisterTransactionResult.ValidationError(
                    "source_account", "La cuenta es obligatoria"
                )
            val sourceAccount = database.accountDao().getById(command.userId, sourceId)
                ?: return@withTransaction RegisterTransactionResult.ValidationError(
                    "source_account", "La cuenta no existe para este usuario"
                )
            if (sourceAccount.isArchived || sourceAccount.currency != command.currency) {
                return@withTransaction RegisterTransactionResult.ValidationError(
                    "source_account", "La cuenta está archivada o su moneda no coincide"
                )
            }
            if (isPlanLockedInstrument(command.userId, sourceId)) {
                return@withTransaction RegisterTransactionResult.ValidationError(
                    "source_account", "La cuenta está bloqueada por la selección del plan Free"
                )
            }
            if (command.type == MovementType.TRANSFER) {
                val destinationId = command.destinationAccountId
                    ?: return@withTransaction RegisterTransactionResult.ValidationError(
                        "destination_account", "La cuenta de destino es obligatoria"
                    )
                val destinationAccount = database.accountDao().getById(command.userId, destinationId)
                    ?: return@withTransaction RegisterTransactionResult.ValidationError(
                        "destination_account", "La cuenta de destino no existe para este usuario"
                    )
                if (destinationId == sourceId || destinationAccount.isArchived || destinationAccount.currency != command.currency) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "destination_account", "La cuenta de destino es inválida para esta transferencia"
                    )
                }
                if (isPlanLockedInstrument(command.userId, destinationId)) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "destination_account", "La cuenta está bloqueada por la selección del plan Free"
                    )
                }
            }
            if (command.categoryId != null) {
                val category = database.categoryDao().getCategoryById(command.categoryId)
                if (category == null || !category.isActive ||
                    (category.userId != null && category.userId != command.userId)) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "category", "La categoría no está activa para este usuario"
                    )
                }
                if (category.parentId != null) {
                    val parent = database.categoryDao().getCategoryById(category.parentId)
                    if (parent == null || !parent.isActive) {
                        return@withTransaction RegisterTransactionResult.ValidationError(
                            "category", "La categoría principal está inactiva"
                        )
                    }
                }
                if (isPlanLockedCategory(command.userId, category)) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "category", "La categoría está bloqueada por la selección del plan Free"
                    )
                }
            }
            if (command.merchantId != null) {
                val merchant = database.merchantCatalogDao().getMerchantById(command.merchantId)
                if (merchant == null || !merchant.isActive) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "merchant", "El comercio no está disponible en el catálogo"
                    )
                }
            }
            if (command.merchantId != null && command.merchantProvisionalText != null) {
                return@withTransaction RegisterTransactionResult.ValidationError(
                    "merchant", "Selecciona un comercio o escribe un nombre provisional"
                )
            }

            // 2. Insert transaction
            val transactionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val transactionEntity = TransactionEntity(
                id = transactionId,
                userId = command.userId,
                type = command.type.name,
                amountMinor = command.amountMinor,
                currencyCode = command.currency,
                sourceAccountId = command.sourceAccountId,
                destinationAccountId = command.destinationAccountId,
                categoryId = command.categoryId,
                merchantId = command.merchantId,
                merchantProvisionalText = command.merchantProvisionalText,
                occurredAt = command.occurredAt,
                note = command.note,
                status = TransactionStatus.ACTIVE.name,
                syncStatus = MovementSyncStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            )
            movementDao.insertTransaction(transactionEntity)

            // 3. Generate ledger entries
            val ledgerEntries = mutableListOf<LedgerEntryEntity>()
            when (command.type) {
                MovementType.EXPENSE -> {
                    val sourceAcc = requireNotNull(command.sourceAccountId)
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            userId = command.userId,
                            transactionId = transactionId,
                            accountId = sourceAcc,
                            role = LedgerRole.SOURCE.name,
                            signedAmountMinor = -command.amountMinor,
                            currencyCode = command.currency,
                            createdAt = now,
                        )
                    )
                }
                MovementType.INCOME -> {
                    val destAcc = requireNotNull(command.sourceAccountId) // For income, sourceAccountId is the receiving account
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            userId = command.userId,
                            transactionId = transactionId,
                            accountId = destAcc,
                            role = LedgerRole.DESTINATION.name,
                            signedAmountMinor = command.amountMinor,
                            currencyCode = command.currency,
                            createdAt = now,
                        )
                    )
                }
                MovementType.TRANSFER -> {
                    val sourceAcc = requireNotNull(command.sourceAccountId)
                    val destAcc = requireNotNull(command.destinationAccountId)
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            userId = command.userId,
                            transactionId = transactionId,
                            accountId = sourceAcc,
                            role = LedgerRole.SOURCE.name,
                            signedAmountMinor = -command.amountMinor,
                            currencyCode = command.currency,
                            createdAt = now,
                        )
                    )
                    ledgerEntries.add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            userId = command.userId,
                            transactionId = transactionId,
                            accountId = destAcc,
                            role = LedgerRole.DESTINATION.name,
                            signedAmountMinor = command.amountMinor,
                            currencyCode = command.currency,
                            createdAt = now,
                        )
                    )
                }
            }
            movementDao.insertLedgerEntries(ledgerEntries)

            // 4. Rebuild each affected projection from the authoritative ledger.
            // The new entries are already visible inside this Room transaction.
            for (entry in ledgerEntries.distinctBy { it.accountId }) {
                balanceProjectionStore.rebuildBalanceFromLedger(
                    userId = command.userId,
                    accountId = entry.accountId,
                    currencyCode = entry.currencyCode,
                )
            }

            // 5. Insert local command receipt
            movementDao.insertOrUpdateReceipt(
                LocalCommandReceiptEntity(
                    userId = command.userId,
                    idempotencyKey = command.idempotencyKey,
                    requestHash = requestHash,
                    transactionId = transactionId,
                    status = "APPLIED",
                    responsePayload = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            // 6. Insert movement outbox
            val outboxPayload = MovementOutboxPayloadFactory.build(
                transaction = transactionEntity,
                idempotencyKey = command.idempotencyKey,
                requestHash = requestHash,
            )
            movementDao.insertOutbox(
                MovementOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    userId = command.userId,
                    idempotencyKey = command.idempotencyKey,
                    aggregateId = transactionId,
                    payload = outboxPayload,
                    state = "PENDING",
                    attemptCount = 0,
                    nextAttemptAt = null,
                    leaseUntil = null,
                    lastErrorCode = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            RegisterTransactionResult.Success(
                transaction = transactionEntity.toDomain(),
                isDuplicate = false,
            )
        }
    }

    fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        userId = userId,
        type = MovementType.fromString(type),
        amountMinor = amountMinor,
        currency = currencyCode,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        merchantId = merchantId,
        merchantProvisionalText = merchantProvisionalText,
        legacyKind = legacyKind,
        occurredAt = occurredAt,
        note = note,
        status = TransactionStatus.fromString(status),
        syncStatus = MovementSyncStatus.fromString(syncStatus),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private suspend fun isPlanLockedCategory(
        userId: String,
        category: com.kipu.app.feature.categories.data.local.CategoryEntity,
    ): Boolean {
        val root = category.parentId?.let { database.categoryDao().getCategoryById(it) } ?: category
        if (root.parentId != null || root.origin != "CUSTOM" || root.userId != userId) return false
        val activeRoots = database.categoryDao().getCategoriesForUser(userId)
            .filter { it.userId == userId && it.parentId == null && it.origin == "CUSTOM" && it.isActive }
        val selected = database.planQuotaSelectionDao()
            .getSelectedResourceIds(userId, QuotaGroup.CUSTOM_CATEGORIES.name)
        val cache = database.featureAccessCacheDao().get(UUID.fromString(userId))
        val premiumVerified = cache != null && cache.effectiveTier == "PREMIUM" && cache.verifiedAt != null &&
            (cache.entitlementExpiresAt == null || cache.entitlementExpiresAt.isAfter(java.time.Instant.now()))
        val quota = quotaPolicy.evaluate(
            group = QuotaGroup.CUSTOM_CATEGORIES,
            activeResourceIds = activeRoots.map { it.id },
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        )
        return root.id in quota.planLockedResourceIds
    }

    private suspend fun isPlanLockedInstrument(userId: String, accountId: String): Boolean {
        val account = database.accountDao().getById(userId, accountId) ?: return false
        if (account.type == "CASH") return false
        val activeIds = database.accountDao().getActiveComputable(userId).map { it.id } +
            database.cardDao().getActive(userId).map { it.id }
        val selected = database.planQuotaSelectionDao()
            .getSelectedResourceIds(userId, QuotaGroup.INSTRUMENTS.name)
        val cache = database.featureAccessCacheDao().get(UUID.fromString(userId))
        val premiumVerified = cache != null && cache.effectiveTier == "PREMIUM" && cache.verifiedAt != null &&
            (cache.entitlementExpiresAt == null || cache.entitlementExpiresAt.isAfter(java.time.Instant.now()))
        return accountId in quotaPolicy.evaluate(
            group = QuotaGroup.INSTRUMENTS,
            activeResourceIds = activeIds,
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        ).planLockedResourceIds
    }
}
