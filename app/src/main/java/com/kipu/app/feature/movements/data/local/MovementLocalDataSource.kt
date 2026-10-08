package com.kipu.app.feature.movements.data.local

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.data.MovementOutboxPayloadFactory
import com.kipu.app.feature.movements.domain.MovementRevisionPlanner
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.plans.domain.PlanQuotaPolicy
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import com.kipu.app.feature.plans.data.entitlement.DenyUnverifiedEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementLocalDataSource @Inject constructor(
    private val database: KipuDatabase,
    private val movementDao: MovementDao,
    private val balanceProjectionStore: BalanceProjectionStore,
    private val quotaPolicy: PlanQuotaPolicy = PlanQuotaPolicy(),
    private val revisionPlanner: MovementRevisionPlanner = MovementRevisionPlanner(),
    private val entitlementEvaluator: EffectiveEntitlementEvaluator = DenyUnverifiedEntitlementEvaluator,
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

    suspend fun queryTransactions(query: SupportSQLiteQuery): List<TransactionEntity> {
        return movementDao.queryTransactions(query)
    }

    suspend fun queryHistoryCount(query: SupportSQLiteQuery): Long {
        return movementDao.queryHistoryCounts(query).single().totalCount
    }

    suspend fun queryHistoryNetFlows(query: SupportSQLiteQuery): List<MovementHistoryNetFlowRow> {
        return movementDao.queryHistoryNetFlows(query)
    }

    suspend fun getMostUsedSourceAccountId(userId: String): String? {
        return movementDao.getMostUsedSourceAccountId(userId)
    }

    fun observeHistoryInvalidations(): Flow<Unit> {
        return database.invalidationTracker.createFlow(
            "transactions",
            "accounts",
            "cards",
            "category_presentations",
            "merchant_catalog_cache",
            emitInitialState = true,
        ).map { Unit }
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
                if (command.categoryId != null) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "category", "Las transferencias no admiten categoría"
                    )
                }
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
                    if (parent == null || !parent.isActive || parent.categoryType != category.categoryType) {
                        return@withTransaction RegisterTransactionResult.ValidationError(
                            "category", "La categoría principal está inactiva o tiene un tipo incompatible"
                        )
                    }
                }
                val expectedCategoryType = when (command.type) {
                    MovementType.EXPENSE -> CategoryType.EXPENSE
                    MovementType.INCOME -> CategoryType.INCOME
                    MovementType.TRANSFER -> null
                }
                if (category.categoryType != "GENERAL" && category.categoryType != expectedCategoryType?.name) {
                    return@withTransaction RegisterTransactionResult.ValidationError(
                        "category", "La categoría no corresponde al tipo de movimiento"
                    )
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
            val now = System.currentTimeMillis()
            val transactionId = UUID.randomUUID().toString()
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
                revision = 1L,
                acknowledgedRevision = null,
                currentRevisionId = null,
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
                    contractVersion = 2,
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
                    contractVersion = 2,
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

    internal fun buildRevisionHead(entity: TransactionEntity, hasPendingOutbox: Boolean = false): MovementRevisionHead {
        val financialState = when (entity.status) {
            "CONFIRMED" -> MovementFinancialState.CONFIRMED
            "REVISED" -> MovementFinancialState.REVISED
            "VOIDED" -> MovementFinancialState.VOIDED
            "ACTIVE" -> MovementFinancialState.CONFIRMED
            else -> MovementFinancialState.LEGACY_FAILED
        }
        val payload = MovementRevisionPayload(
            type = MovementType.valueOf(entity.type),
            operationKind = entity.operationKind ?: entity.legacyKind?.takeIf {
                it.equals("DEBT_DISBURSEMENT", ignoreCase = true) || it.equals("DEBT_PAYMENT", ignoreCase = true)
            },
            amountMinor = entity.amountMinor,
            currency = entity.currencyCode,
            sourceAccountId = entity.sourceAccountId,
            destinationAccountId = entity.destinationAccountId,
            categoryId = entity.categoryId,
            merchantId = entity.merchantId,
            merchantProvisionalText = entity.merchantProvisionalText,
            occurredAt = entity.occurredAt,
            note = entity.note,
        )
        val officialRevision: Long?
        val localProposedRevision: Long?
        val baselineRevision: Long?
        if (entity.acknowledgedRevision != null) {
            officialRevision = entity.acknowledgedRevision
            localProposedRevision = if (entity.revision > entity.acknowledgedRevision) entity.revision else null
            baselineRevision = null
        } else if (hasPendingOutbox) {
            officialRevision = null
            localProposedRevision = entity.revision
            baselineRevision = null
        } else {
            officialRevision = null
            localProposedRevision = null
            baselineRevision = entity.revision
        }
        return MovementRevisionHead(
            transactionId = entity.id,
            userId = entity.userId,
            payload = payload,
            financialState = financialState,
            officialRevision = officialRevision,
            localProposedRevision = localProposedRevision,
            baselineRevision = baselineRevision,
        )
    }

    suspend fun getRevisionHead(userId: String, transactionId: String): MovementRevisionHead? {
        val txEntity = movementDao.getTransactionById(userId, transactionId) ?: return null
        val pendingOutbox = movementDao.getPendingOutboxForTransaction(userId, txEntity.id)
        return buildRevisionHead(txEntity, hasPendingOutbox = pendingOutbox.isNotEmpty())
    }

    suspend fun getRevisionAudit(userId: String, transactionId: String): List<MovementRevisionAudit> =
        movementDao.getRevisions(userId, transactionId).sortedByDescending { it.localRevision }.map {
            MovementRevisionAudit(it.localRevision, it.commandType, it.createdAt, it.changeReason)
        }

    private suspend fun collectReferences(
        userId: String,
        oldSourceAccountId: String?,
        oldDestinationAccountId: String?,
        oldCategoryId: String?,
        oldMerchantId: String?,
        newPayload: MovementRevisionPayload,
    ): MovementRevisionReferences {
        val accountMap = mutableMapOf<String, MovementAccountReference>()
        val categoryMap = mutableMapOf<String, MovementCategoryReference>()
        val merchantMap = mutableMapOf<String, MovementMerchantReference>()

        val accountIds = listOfNotNull(
            oldSourceAccountId,
            oldDestinationAccountId,
            newPayload.sourceAccountId,
            newPayload.destinationAccountId,
        ).distinct()

        for (accId in accountIds) {
            val acc = database.accountDao().getById(userId, accId)
            if (acc != null) {
                val isOld = (accId == oldSourceAccountId || accId == oldDestinationAccountId)
                val eligible = if (isOld) true else (!acc.isArchived && !isPlanLockedInstrument(userId, accId))
                accountMap[accId] = MovementAccountReference(
                    userId = acc.userId,
                    currency = acc.currency,
                    eligible = eligible,
                )
            }
        }

        val categoryIds = listOfNotNull(oldCategoryId, newPayload.categoryId).distinct()
        for (catId in categoryIds) {
            val cat = database.categoryDao().getCategoryById(catId)
            if (cat != null) {
                val isOld = (catId == oldCategoryId)
                val eligible = if (isOld) true else (cat.isActive && !isPlanLockedCategory(userId, cat))
                categoryMap[catId] = MovementCategoryReference(
                    userId = cat.userId ?: userId,
                    type = MovementType.valueOf(cat.categoryType),
                    eligible = eligible,
                )
            }
        }

        val merchantIds = listOfNotNull(oldMerchantId, newPayload.merchantId).distinct()
        for (mId in merchantIds) {
            val m = database.merchantCatalogDao().getMerchantById(mId)
            if (m != null) {
                merchantMap[mId] = MovementMerchantReference(
                    userId = null,
                    visible = m.isActive,
                    eligible = m.isActive,
                )
            }
        }

        return MovementRevisionReferences(
            accounts = accountMap,
            categories = categoryMap,
            merchants = merchantMap,
        )
    }

    suspend fun commitRevisionAtomic(
        userId: String,
        command: MovementRevisionCommand.Revise,
        requestHash: String,
    ): MovementMutationResult {
        return database.withTransaction {
            val existingReceipt = movementDao.getReceipt(userId, command.idempotencyKey)
            if (existingReceipt != null) {
                if (existingReceipt.requestHash == requestHash && existingReceipt.transactionId != null) {
                    val head = getRevisionHead(userId, existingReceipt.transactionId)
                    if (head != null) {
                        return@withTransaction MovementMutationResult.Success(head, isDuplicate = true)
                    }
                }
                return@withTransaction MovementMutationResult.Conflict(
                    current = getRevisionHead(userId, command.transactionId),
                    code = "IDEMPOTENCY_CONFLICT",
                )
            }

            val txEntity = movementDao.getTransactionById(userId, command.transactionId)
                ?: return@withTransaction MovementMutationResult.Rejected("NOT_AUTHORIZED")

            val pendingOutbox = movementDao.getPendingOutboxForTransaction(userId, txEntity.id)
            val currentHead = buildRevisionHead(txEntity, hasPendingOutbox = pendingOutbox.isNotEmpty())

            if (currentHead.financialState == MovementFinancialState.VOIDED) {
                return@withTransaction MovementMutationResult.Rejected("ALREADY_VOIDED_FOR_EDIT")
            }
            if (currentHead.financialState == MovementFinancialState.LEGACY_FAILED) {
                return@withTransaction MovementMutationResult.Rejected("FINANCIAL_EVIDENCE_REQUIRED")
            }
            if (command.expectedRevision != currentHead.commandBaseRevision) {
                return@withTransaction MovementMutationResult.Conflict(
                    current = currentHead,
                    code = "REVISION_CONFLICT",
                )
            }

            val hasSpecializedRelations = txEntity.cardId != null ||
                txEntity.installmentCount != null ||
                !txEntity.legacyKind.isNullOrBlank()
            val isStandardKind = txEntity.operationKind.equals("STANDARD", ignoreCase = true)
            val isLegacyStandard = txEntity.operationKind == null && !hasSpecializedRelations
            val context = MovementMaintenanceContext(
                hasSpecializedRelations = hasSpecializedRelations,
                legacyStandardVerified = isLegacyStandard,
            )

            val references = collectReferences(
                userId = userId,
                oldSourceAccountId = txEntity.sourceAccountId,
                oldDestinationAccountId = txEntity.destinationAccountId,
                oldCategoryId = txEntity.categoryId,
                oldMerchantId = txEntity.merchantId,
                newPayload = command.payload,
            )

            val existingEntries = movementDao.getLedgerEntriesForTransaction(userId, txEntity.id)
            val appliedEffects = existingEntries.map { entry ->
                MovementFinancialEffect(
                    accountId = entry.accountId,
                    role = LedgerRole.valueOf(entry.role),
                    signedAmountMinor = entry.signedAmountMinor,
                    currency = entry.currencyCode,
                )
            }

            val planResult = revisionPlanner.plan(
                owner = userId,
                head = currentHead,
                command = command,
                context = context,
                references = references,
                appliedEffects = appliedEffects,
            )
            val plan = when (planResult) {
                is MovementRevisionPlanningResult.Ready -> planResult.plan
                is MovementRevisionPlanningResult.Rejected -> return@withTransaction MovementMutationResult.Rejected(planResult.code)
                is MovementRevisionPlanningResult.AlreadyVoided -> return@withTransaction MovementMutationResult.Rejected("ALREADY_VOIDED_FOR_EDIT")
            }

            val now = System.currentTimeMillis()
            val revisionId = UUID.randomUUID().toString()
            val previousSnapshot = MovementRevisionSnapshotCodec.encode(txEntity)

            val updatedEntity = txEntity.copy(
                amountMinor = plan.payload.amountMinor,
                currencyCode = plan.payload.currency,
                sourceAccountId = plan.payload.sourceAccountId,
                destinationAccountId = plan.payload.destinationAccountId,
                categoryId = plan.payload.categoryId,
                merchantId = plan.payload.merchantId,
                merchantProvisionalText = plan.payload.merchantProvisionalText,
                occurredAt = plan.payload.occurredAt,
                note = plan.payload.note,
                status = plan.financialState.name,
                revision = plan.proposedRevision,
                currentRevisionId = revisionId,
                syncStatus = "PENDING",
                updatedAt = now,
            )

            movementDao.insertRevision(
                TransactionRevisionEntity(
                    userId = userId,
                    revisionId = revisionId,
                    transactionId = txEntity.id,
                    commandId = command.idempotencyKey,
                    commandType = "REVISE",
                    baseRevision = command.expectedRevision,
                    localRevision = plan.proposedRevision,
                    previousPayload = previousSnapshot,
                    newPayload = MovementRevisionSnapshotCodec.encode(updatedEntity),
                    changeReason = command.reason,
                    provenance = "LOCAL",
                    createdAt = now,
                )
            )

            for (effect in plan.effects) {
                val entryId = UUID.randomUUID().toString()
                movementDao.insertLedgerEntries(listOf(
                    LedgerEntryEntity(
                        id = entryId,
                        userId = userId,
                        transactionId = txEntity.id,
                        accountId = effect.accountId,
                        role = effect.role.name,
                        signedAmountMinor = effect.signedAmountMinor,
                        currencyCode = effect.currency,
                        createdAt = now,
                    )
                ))
                movementDao.insertLedgerEffect(
                    MovementLedgerEffectEntity(
                        userId = userId,
                        commandId = command.idempotencyKey,
                        effectOrdinal = effect.ordinal,
                        transactionId = txEntity.id,
                        revisionId = revisionId,
                        ledgerEntryId = entryId,
                        reversesCommandId = null,
                        reversesEffectOrdinal = null,
                    )
                )
                movementDao.insertLedgerAlias(
                    MovementLedgerAliasEntity(
                        userId = userId,
                        physicalEntryId = entryId,
                        commandId = command.idempotencyKey,
                        effectOrdinal = effect.ordinal,
                    )
                )
            }

            movementDao.updateTransaction(updatedEntity)

            val touchedAccounts = mutableSetOf<Pair<String, String>>()
            txEntity.sourceAccountId?.let { touchedAccounts.add(it to txEntity.currencyCode) }
            txEntity.destinationAccountId?.let { touchedAccounts.add(it to txEntity.currencyCode) }
            plan.payload.sourceAccountId?.let { touchedAccounts.add(it to plan.payload.currency) }
            plan.payload.destinationAccountId?.let { touchedAccounts.add(it to plan.payload.currency) }
            for ((accId, curr) in touchedAccounts) {
                balanceProjectionStore.rebuildBalanceFromLedger(userId, accId, curr)
            }

            movementDao.insertOrUpdateReceipt(
                LocalCommandReceiptEntity(
                    contractVersion = 1,
                    userId = userId,
                    idempotencyKey = command.idempotencyKey,
                    requestHash = requestHash,
                    transactionId = txEntity.id,
                    status = "APPLIED",
                    responsePayload = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            val outboxPayload = MovementOutboxPayloadFactory.buildRevise(
                idempotencyKey = command.idempotencyKey,
                transactionId = txEntity.id,
                expectedRevision = command.expectedRevision,
                dependsOnCommandId = command.dependsOnCommandId,
                reason = command.reason,
                requestHash = requestHash,
                payload = plan.payload,
            )
            movementDao.insertOutbox(
                MovementOutboxEntity(
                    contractVersion = 1,
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    idempotencyKey = command.idempotencyKey,
                    aggregateId = txEntity.id,
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

            val newHead = buildRevisionHead(updatedEntity, hasPendingOutbox = true)
            MovementMutationResult.Success(head = newHead, isDuplicate = false)
        }
    }

    suspend fun commitVoidAtomic(
        userId: String,
        command: MovementRevisionCommand.Void,
        requestHash: String,
    ): MovementMutationResult {
        return database.withTransaction {
            val existingReceipt = movementDao.getReceipt(userId, command.idempotencyKey)
            if (existingReceipt != null) {
                if (existingReceipt.requestHash == requestHash && existingReceipt.transactionId != null) {
                    val head = getRevisionHead(userId, existingReceipt.transactionId)
                    if (head != null) {
                        return@withTransaction MovementMutationResult.Success(head, isDuplicate = true)
                    }
                }
                return@withTransaction MovementMutationResult.Conflict(
                    current = getRevisionHead(userId, command.transactionId),
                    code = "IDEMPOTENCY_CONFLICT",
                )
            }

            val txEntity = movementDao.getTransactionById(userId, command.transactionId)
                ?: return@withTransaction MovementMutationResult.Rejected("NOT_AUTHORIZED")

            val pendingOutbox = movementDao.getPendingOutboxForTransaction(userId, txEntity.id)
            val currentHead = buildRevisionHead(txEntity, hasPendingOutbox = pendingOutbox.isNotEmpty())

            if (command.expectedRevision != currentHead.commandBaseRevision) {
                return@withTransaction MovementMutationResult.Conflict(
                    current = currentHead,
                    code = "REVISION_CONFLICT",
                )
            }

            val settlementEvent = if (txEntity.operationKind in setOf("DEBT_PAYMENT", "DEBT_AMORTIZATION")) {
                database.debtDao().getSettlementEventForTransaction(userId, txEntity.id)
                    ?.takeIf { it.eventType == "PAYMENT" }
            } else null
            if (settlementEvent != null) {
                return@withTransaction commitGroupedDebtSettlementVoid(
                    userId = userId,
                    command = command,
                    requestHash = requestHash,
                    selected = txEntity,
                    selectedHead = currentHead,
                    event = settlementEvent,
                )
            }

            val hasSpecializedRelations = txEntity.cardId != null ||
                txEntity.installmentCount != null ||
                !txEntity.legacyKind.isNullOrBlank()
            val isStandardKind = txEntity.operationKind.equals("STANDARD", ignoreCase = true)
            val isLegacyStandard = txEntity.operationKind == null && !hasSpecializedRelations
            val context = MovementMaintenanceContext(
                hasSpecializedRelations = hasSpecializedRelations,
                legacyStandardVerified = isLegacyStandard,
            )

            val references = collectReferences(
                userId = userId,
                oldSourceAccountId = txEntity.sourceAccountId,
                oldDestinationAccountId = txEntity.destinationAccountId,
                oldCategoryId = txEntity.categoryId,
                oldMerchantId = txEntity.merchantId,
                newPayload = currentHead.payload,
            )

            val existingEntries = movementDao.getLedgerEntriesForTransaction(userId, txEntity.id)
            val appliedEffects = existingEntries.map { entry ->
                MovementFinancialEffect(
                    accountId = entry.accountId,
                    role = LedgerRole.valueOf(entry.role),
                    signedAmountMinor = entry.signedAmountMinor,
                    currency = entry.currencyCode,
                )
            }

            val planResult = revisionPlanner.plan(
                owner = userId,
                head = currentHead,
                command = command,
                context = context,
                references = references,
                appliedEffects = appliedEffects,
            )
            val plan = when (planResult) {
                is MovementRevisionPlanningResult.Ready -> planResult.plan
                is MovementRevisionPlanningResult.AlreadyVoided -> {
                    return@withTransaction MovementMutationResult.Success(currentHead, isDuplicate = true)
                }
                is MovementRevisionPlanningResult.Rejected -> return@withTransaction MovementMutationResult.Rejected(planResult.code)
            }

            val now = System.currentTimeMillis()
            val revisionId = UUID.randomUUID().toString()
            val previousSnapshot = MovementRevisionSnapshotCodec.encode(txEntity)

            val updatedEntity = txEntity.copy(
                status = MovementFinancialState.VOIDED.name,
                revision = plan.proposedRevision,
                currentRevisionId = revisionId,
                syncStatus = "PENDING",
                updatedAt = now,
            )

            movementDao.insertRevision(
                TransactionRevisionEntity(
                    userId = userId,
                    revisionId = revisionId,
                    transactionId = txEntity.id,
                    commandId = command.idempotencyKey,
                    commandType = "VOID",
                    baseRevision = command.expectedRevision,
                    localRevision = plan.proposedRevision,
                    previousPayload = previousSnapshot,
                    newPayload = MovementRevisionSnapshotCodec.encode(updatedEntity),
                    changeReason = command.reason,
                    provenance = "LOCAL",
                    createdAt = now,
                )
            )

            for (effect in plan.effects) {
                val entryId = UUID.randomUUID().toString()
                movementDao.insertLedgerEntries(listOf(
                    LedgerEntryEntity(
                        id = entryId,
                        userId = userId,
                        transactionId = txEntity.id,
                        accountId = effect.accountId,
                        role = effect.role.name,
                        signedAmountMinor = effect.signedAmountMinor,
                        currencyCode = effect.currency,
                        createdAt = now,
                    )
                ))
                movementDao.insertLedgerEffect(
                    MovementLedgerEffectEntity(
                        userId = userId,
                        commandId = command.idempotencyKey,
                        effectOrdinal = effect.ordinal,
                        transactionId = txEntity.id,
                        revisionId = revisionId,
                        ledgerEntryId = entryId,
                        reversesCommandId = null,
                        reversesEffectOrdinal = null,
                    )
                )
                movementDao.insertLedgerAlias(
                    MovementLedgerAliasEntity(
                        userId = userId,
                        physicalEntryId = entryId,
                        commandId = command.idempotencyKey,
                        effectOrdinal = effect.ordinal,
                    )
                )
            }

            movementDao.updateTransaction(updatedEntity)

            val touchedAccounts = mutableSetOf<Pair<String, String>>()
            txEntity.sourceAccountId?.let { touchedAccounts.add(it to txEntity.currencyCode) }
            txEntity.destinationAccountId?.let { touchedAccounts.add(it to txEntity.currencyCode) }
            for ((accId, curr) in touchedAccounts) {
                balanceProjectionStore.rebuildBalanceFromLedger(userId, accId, curr)
            }

            movementDao.insertOrUpdateReceipt(
                LocalCommandReceiptEntity(
                    contractVersion = 1,
                    userId = userId,
                    idempotencyKey = command.idempotencyKey,
                    requestHash = requestHash,
                    transactionId = txEntity.id,
                    status = "APPLIED",
                    responsePayload = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            val outboxPayload = MovementOutboxPayloadFactory.buildVoid(
                idempotencyKey = command.idempotencyKey,
                transactionId = txEntity.id,
                expectedRevision = command.expectedRevision,
                dependsOnCommandId = command.dependsOnCommandId,
                reason = command.reason,
                requestHash = requestHash,
            )
            movementDao.insertOutbox(
                MovementOutboxEntity(
                    contractVersion = 1,
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    idempotencyKey = command.idempotencyKey,
                    aggregateId = txEntity.id,
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

            val newHead = buildRevisionHead(updatedEntity, hasPendingOutbox = true)
            MovementMutationResult.Success(head = newHead, isDuplicate = false)
        }
    }

    suspend fun queryExpenseConsumption(
        userId: String,
        query: ExpenseConsumptionQuery,
    ): ExpenseConsumptionResult {
        val expenses = movementDao.getExpensesInPeriod(
            userId = userId,
            currencyCode = query.currency,
            fromInclusive = query.fromInclusive,
            toExclusive = query.toExclusive,
        )
        val filtered = if (query.categoryIds.isEmpty()) expenses else expenses.filter { it.categoryId in query.categoryIds }
        val totalMinor = filtered.sumOf { it.amountMinor }
        return ExpenseConsumptionResult(totalMinor, datasetVersion = 1L)
    }

    private suspend fun commitGroupedDebtSettlementVoid(
        userId: String,
        command: MovementRevisionCommand.Void,
        requestHash: String,
        selected: TransactionEntity,
        selectedHead: MovementRevisionHead,
        event: com.kipu.app.feature.debts.data.local.DebtEventEntity,
    ): MovementMutationResult {
        val databaseDebtDao = database.debtDao()
        val transactionIds = listOfNotNull(event.transactionId, event.interestTransactionId).distinct()
        if (selected.id !in transactionIds || transactionIds.isEmpty()) {
            return MovementMutationResult.Rejected("SETTLEMENT_LINK_INVALID")
        }
        val now = System.currentTimeMillis()
        val touchedAccounts = mutableSetOf<Pair<String, String>>()
        var effectOrdinal = 0

        for (transactionId in transactionIds) {
            val transaction = movementDao.getTransactionById(userId, transactionId)
                ?: return MovementMutationResult.Rejected("SETTLEMENT_LINK_INVALID")
            if (transaction.status == "VOIDED") continue
            val head = if (transaction.id == selected.id) selectedHead else buildRevisionHead(
                transaction,
                hasPendingOutbox = movementDao.getPendingOutboxForTransaction(userId, transaction.id).isNotEmpty(),
            )
            val groupedCommandId = if (transaction.id == selected.id) command.idempotencyKey else
                UUID.nameUUIDFromBytes("${command.idempotencyKey}:debt-settlement:${transaction.id}".toByteArray()).toString()
            val transactionCommand = command.copy(
                idempotencyKey = groupedCommandId,
                transactionId = transaction.id,
                expectedRevision = head.commandBaseRevision,
                dependsOnCommandId = null,
            )
            val references = collectReferences(
                userId = userId,
                oldSourceAccountId = transaction.sourceAccountId,
                oldDestinationAccountId = transaction.destinationAccountId,
                oldCategoryId = transaction.categoryId,
                oldMerchantId = transaction.merchantId,
                newPayload = head.payload,
            )
            val effects = movementDao.getLedgerEntriesForTransaction(userId, transaction.id).map { entry ->
                MovementFinancialEffect(
                    accountId = entry.accountId,
                    role = LedgerRole.valueOf(entry.role),
                    signedAmountMinor = entry.signedAmountMinor,
                    currency = entry.currencyCode,
                )
            }
            val plan = when (val result = revisionPlanner.plan(
                owner = userId,
                head = head,
                command = transactionCommand,
                context = MovementMaintenanceContext(hasSpecializedRelations = false, legacyStandardVerified = false),
                references = references,
                appliedEffects = effects,
            )) {
                is MovementRevisionPlanningResult.Ready -> result.plan
                is MovementRevisionPlanningResult.AlreadyVoided -> continue
                is MovementRevisionPlanningResult.Rejected -> return MovementMutationResult.Rejected(result.code)
            }
            val revisionId = UUID.randomUUID().toString()
            val updated = transaction.copy(
                status = MovementFinancialState.VOIDED.name,
                revision = plan.proposedRevision,
                currentRevisionId = revisionId,
                syncStatus = "PENDING",
                updatedAt = now,
            )
            movementDao.insertRevision(
                TransactionRevisionEntity(
                    userId = userId,
                    revisionId = revisionId,
                    transactionId = transaction.id,
                    commandId = groupedCommandId,
                    commandType = "VOID",
                    baseRevision = transactionCommand.expectedRevision,
                    localRevision = plan.proposedRevision,
                    previousPayload = MovementRevisionSnapshotCodec.encode(transaction),
                    newPayload = MovementRevisionSnapshotCodec.encode(updated),
                    changeReason = command.reason,
                    provenance = "LOCAL",
                    createdAt = now,
                ),
            )
            for (effect in plan.effects) {
                val entryId = UUID.randomUUID().toString()
                movementDao.insertLedgerEntries(listOf(
                    LedgerEntryEntity(
                        id = entryId,
                        userId = userId,
                        transactionId = transaction.id,
                        accountId = effect.accountId,
                        role = effect.role.name,
                        signedAmountMinor = effect.signedAmountMinor,
                        currencyCode = effect.currency,
                        createdAt = now,
                    ),
                ))
                movementDao.insertLedgerEffect(
                    MovementLedgerEffectEntity(
                        userId = userId,
                        commandId = groupedCommandId,
                        effectOrdinal = effectOrdinal++,
                        transactionId = transaction.id,
                        revisionId = revisionId,
                        ledgerEntryId = entryId,
                        reversesCommandId = null,
                        reversesEffectOrdinal = null,
                    ),
                )
                movementDao.insertLedgerAlias(
                    MovementLedgerAliasEntity(
                        userId = userId,
                        physicalEntryId = entryId,
                        commandId = groupedCommandId,
                        effectOrdinal = effectOrdinal - 1,
                    ),
                )
                touchedAccounts += effect.accountId to effect.currency
            }
            movementDao.updateTransaction(updated)
        }

        val updatedSelected = movementDao.getTransactionById(userId, selected.id)
            ?: return MovementMutationResult.Rejected("SETTLEMENT_LINK_INVALID")
        for ((accountId, currency) in touchedAccounts) {
            balanceProjectionStore.rebuildBalanceFromLedger(userId, accountId, currency)
        }
        database.financialMovementDao().updateOperationStatus(userId, requireNotNull(event.transactionId), "VOIDED")

        val debt = databaseDebtDao.getDebt(userId, event.debtId)
            ?: return MovementMutationResult.Rejected("SETTLEMENT_DEBT_NOT_FOUND")
        val events = databaseDebtDao.getEvents(userId, event.debtId)
        var debtDelta = 0L
        for (debtEvent in events) {
            val linked = debtEvent.transactionId?.let { movementDao.getTransactionById(userId, it) }
            if (debtEvent.transactionId == null || linked?.status != "VOIDED") {
                debtDelta += debtEvent.principalDeltaMinor
                    ?: if (debtEvent.eventType == "PAYMENT") -debtEvent.amountMinor else 0L
            }
        }
        val remaining = (debt.totalMinor + debtDelta).coerceIn(0L, debt.totalMinor)
        databaseDebtDao.updateDebt(
            debt.copy(
                status = if (remaining == 0L) "SETTLED" else "ACTIVE",
                revision = debt.revision + 1,
                syncState = "PENDING",
                updatedAt = now,
            ),
        )
        for (installment in databaseDebtDao.getInstallments(userId, event.debtId)) {
            var paid = 0L
            for (debtEvent in events) {
                if (debtEvent.installmentId != installment.id || debtEvent.eventType != "PAYMENT") continue
                val linked = debtEvent.transactionId?.let { movementDao.getTransactionById(userId, it) }
                if (debtEvent.transactionId == null || linked?.status != "VOIDED") {
                    paid = (paid + debtEvent.amountMinor).coerceAtMost(installment.amountMinor)
                }
            }
            databaseDebtDao.upsertInstallment(
                installment.copy(
                    status = when {
                        paid >= installment.amountMinor -> "PAID"
                        paid > 0L -> "PARTIAL"
                        else -> "PLANNED"
                    },
                    revision = installment.revision + 1,
                    updatedAt = now,
                ),
            )
        }

        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                contractVersion = 1,
                userId = userId,
                idempotencyKey = command.idempotencyKey,
                requestHash = requestHash,
                transactionId = selected.id,
                status = "APPLIED",
                responsePayload = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        movementDao.insertOutbox(
            MovementOutboxEntity(
                contractVersion = 1,
                id = UUID.randomUUID().toString(),
                userId = userId,
                idempotencyKey = command.idempotencyKey,
                aggregateId = selected.id,
                payload = MovementOutboxPayloadFactory.buildVoid(
                    idempotencyKey = command.idempotencyKey,
                    transactionId = selected.id,
                    expectedRevision = command.expectedRevision,
                    dependsOnCommandId = command.dependsOnCommandId,
                    reason = command.reason,
                    requestHash = requestHash,
                ),
                state = "PENDING",
                attemptCount = 0,
                nextAttemptAt = null,
                leaseUntil = null,
                lastErrorCode = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return MovementMutationResult.Success(buildRevisionHead(updatedSelected, hasPendingOutbox = true), isDuplicate = false)
    }

    suspend fun getConflictProposals(userId: String, transactionId: String): List<MovementConflictProposalEntity> {
        return movementDao.getConflictProposals(userId, transactionId)
    }

    suspend fun discardProposal(userId: String, proposalId: String): Boolean {
        return database.withTransaction {
            val proposal = movementDao.getConflictProposalById(userId, proposalId) ?: return@withTransaction false
            if (proposal.resolution != "UNRESOLVED") return@withTransaction false
            val now = System.currentTimeMillis()
            movementDao.updateConflictProposalResolution(userId, proposalId, "DISCARDED", now)
            val pendingOutbox = movementDao.getPendingOutboxForTransaction(userId, proposal.transactionId)
                .firstOrNull { it.idempotencyKey == proposal.commandId }
            if (pendingOutbox != null) {
                movementDao.updateOutboxResult(userId, pendingOutbox.id, "FAILED_PERMANENT", null, "PROPOSAL_DISCARDED", now)
            }
            true
        }
    }

    suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String): MovementMutationResult {
        val proposal = database.withTransaction {
            val p = movementDao.getConflictProposalById(userId, proposalId) ?: return@withTransaction null
            if (p.resolution != "UNRESOLVED") return@withTransaction null
            p
        } ?: return MovementMutationResult.Rejected("PROPOSAL_NOT_FOUND")

        val txEntity = movementDao.getTransactionById(userId, proposal.transactionId)
            ?: return MovementMutationResult.Rejected("NOT_AUTHORIZED")
        if (txEntity.status == "VOIDED") {
            return MovementMutationResult.Rejected("ALREADY_VOIDED_FOR_EDIT")
        }

        val proposedPayload = try {
            val dto = kotlinx.serialization.json.Json.decodeFromString<com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionRequestDto>(proposal.proposedSnapshot)
            dto.revisedPayload?.let {
                MovementRevisionPayload(
                    type = MovementType.valueOf(it.type),
                    operationKind = it.operationKind,
                    amountMinor = it.amountMinor,
                    currency = it.currencyCode,
                    sourceAccountId = it.sourceAccountId,
                    destinationAccountId = it.destinationAccountId,
                    categoryId = it.categoryId,
                    merchantId = it.merchantId,
                    merchantProvisionalText = it.merchantProvisionalText,
                    occurredAt = java.time.Instant.parse(it.occurredAt).toEpochMilli(),
                    note = it.note,
                )
            }
        } catch (_: Exception) { null } ?: return MovementMutationResult.Rejected("INVALID_PROPOSAL_SNAPSHOT")

        val currentHead = getRevisionHead(userId, proposal.transactionId)
            ?: return MovementMutationResult.Rejected("NOT_AUTHORIZED")

        val redoCommand = MovementRevisionCommand.Revise(
            idempotencyKey = newIdempotencyKey,
            transactionId = proposal.transactionId,
            expectedRevision = currentHead.commandBaseRevision,
            payload = proposedPayload,
            reason = "Redo proposal $proposalId",
        )

        val result = commitRevisionAtomic(
            userId = userId,
            command = redoCommand,
            requestHash = com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher().computeHash(redoCommand),
        )

        if (result is MovementMutationResult.Success) {
            database.withTransaction {
                movementDao.updateConflictProposalResolution(userId, proposalId, "RESOLVED_REDO")
            }
        }

        return result
    }

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
        val premiumVerified = entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
        val rootsOfType = activeRoots.filter { it.categoryType == root.categoryType }
        val quota = quotaPolicy.evaluate(
            group = QuotaGroup.CUSTOM_CATEGORIES,
            activeResourceIds = rootsOfType.map { it.id },
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
        val premiumVerified = entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
        return accountId in quotaPolicy.evaluate(
            group = QuotaGroup.INSTRUMENTS,
            activeResourceIds = activeIds,
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        ).planLockedResourceIds
    }
}
