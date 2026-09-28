package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.local.CreditPaymentAllocationEntity
import com.kipu.app.feature.accounts.domain.model.CreditLiabilityAccountIds
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementOutboxEntity
import com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.remote.MovementApiResponse
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import java.time.LocalDate
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncMovementsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncMovementsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncMovementsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val movementDao: MovementDao,
    private val api: MovementApi,
    private val financialApi: FinancialInstrumentsApi,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val database: KipuDatabase,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "movement-sync"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncMovementsWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (currentVerifiedOwner != null && targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncMovementsWorker", "Target owner differs from verified active owner")
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        val pendingCommands = movementDao.claimPendingOutbox(targetUserId, now, limit = 10)

        var anyFailed = false

        for (cmd in pendingCommands) {
            val success = processCommand(cmd, targetUserId, now)
            if (!success) {
                anyFailed = true
            }
        }

        if (!pullChanges(targetUserId)) anyFailed = true

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun pullChanges(userId: String): Boolean {
        var checkpoint = movementDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        repeat(20) {
            when (val response = financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint))) {
                is FinancialApiResponse.Success -> {
                    val page = response.data
                    if (page.changes.isEmpty()) return page.nextSequence == checkpoint
                    val appliedPage = database.withTransaction {
                        var expectedSequence = checkpoint
                        for (change in page.changes.sortedBy { it.sequence }) {
                            if (change.sequence != expectedSequence + 1L) return@withTransaction false
                            val payload = change.payload
                            val applied = try {
                                when (change.entityType.uppercase()) {
                                    "ACCOUNT" -> when (change.operation.uppercase()) {
                                        "UPSERT" -> payload?.let {
                                            applyPulledAccount(userId, json.decodeFromJsonElement<SyncAccountPayload>(it), change.revision)
                                        } ?: false
                                        "ARCHIVE" -> accountDao.getById(userId, change.entityId)?.let {
                                            accountDao.setArchived(userId, change.entityId, true, System.currentTimeMillis() * 1_000L)
                                            accountDao.updateRemoteRevision(userId, change.entityId, change.revision, System.currentTimeMillis() * 1_000L)
                                            true
                                        } ?: false
                                        else -> false
                                    }
                                    "CARD" -> when (change.operation.uppercase()) {
                                        "UPSERT" -> payload?.let {
                                            applyPulledCard(userId, json.decodeFromJsonElement<SyncCardPayload>(it), change.revision)
                                        } ?: false
                                        "ARCHIVE" -> cardDao.getById(userId, change.entityId)?.let {
                                            cardDao.setArchived(userId, change.entityId, true, System.currentTimeMillis() * 1_000L)
                                            cardDao.updateRemoteRevision(userId, change.entityId, change.revision, System.currentTimeMillis() * 1_000L)
                                            true
                                        } ?: false
                                        "DELETE" -> applyPulledCardDeletion(userId, change.entityId)
                                        else -> false
                                    }
                                    "TRANSACTION", "MOVEMENT" -> if (change.operation.equals("UPSERT", true) && payload != null) {
                                        applyPulledTransaction(userId, json.decodeFromJsonElement<SyncTransactionPayload>(payload), change.revision)
                                    } else false
                                    else -> false
                                }
                            } catch (e: Exception) {
                                SecureLog.e("SyncMovementsWorker", "Could not apply sync change: ${e.javaClass.simpleName}")
                                false
                            }
                            if (!applied) return@withTransaction false
                            expectedSequence = change.sequence
                        }
                        if (expectedSequence != page.nextSequence) return@withTransaction false
                        movementDao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, expectedSequence))
                        checkpoint = expectedSequence
                        true
                    }
                    if (!appliedPage) return false
                    if (!page.hasMore) return true
                }
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    private suspend fun applyPulledAccount(userId: String, remote: SyncAccountPayload, revision: Long): Boolean =
        database.withTransaction {
            if (remote.id.isBlank() || remote.currency !in setOf("PEN", "USD") || remote.initialBalanceMinorUnits < 0L) {
                return@withTransaction false
            }
            if (remote.type == "CREDIT_LIABILITY" && remote.initialBalanceMinorUnits != 0L) {
                return@withTransaction false
            }
            val existing = accountDao.getById(userId, remote.id)
            if (existing != null && (existing.type != remote.type || existing.currency != remote.currency)) {
                return@withTransaction false
            }
            if (existing == null) {
                val openedAt = Instant.parse(remote.openedAt).toEpochMilli()
                accountDao.insertIfAbsent(AccountEntity(
                    id = remote.id, userId = userId,
                    creationOperationId = remote.creationOperationId ?: "remote:${remote.id}",
                    alias = remote.alias, type = remote.type, currency = remote.currency,
                    presetId = null, color = null, icon = null,
                    initialBalanceMinorUnits = remote.initialBalanceMinorUnits,
                    openedAt = openedAt, remoteRevision = revision,
                    createdAt = openedAt, updatedAt = System.currentTimeMillis(),
                ))
                if (remote.initialBalanceMinorUnits != 0L) {
                    movementDao.insertPulledLedgerEntries(listOf(LedgerEntryEntity(
                        id = "remote:opening:${remote.id}", userId = userId,
                        transactionId = "remote:opening:${remote.id}", accountId = remote.id,
                        role = "DESTINATION", signedAmountMinor = remote.initialBalanceMinorUnits,
                        currencyCode = remote.currency, createdAt = openedAt,
                    )))
                }
            }
            if (remote.isArchived) accountDao.setArchived(userId, remote.id, true, System.currentTimeMillis() * 1_000L)
            accountDao.updateRemoteRevision(userId, remote.id, revision, System.currentTimeMillis() * 1_000L)
            val account = accountDao.getById(userId, remote.id) ?: return@withTransaction false
            val balance = movementDao.calculateLedgerSumForAccount(userId, remote.id)
                ?: remote.initialBalanceMinorUnits
            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                userId = userId, accountId = remote.id, balanceMinor = balance,
                currencyCode = account.currency, lastTransactionAt = account.openedAt,
            ))
            true
        }

    private suspend fun applyPulledCard(userId: String, remote: SyncCardPayload, revision: Long): Boolean =
        database.withTransaction {
            if (remote.id.isBlank() || remote.id != remote.id.trim()) return@withTransaction false
            if (remote.type !in setOf("CREDIT", "DEBIT") || remote.currency !in setOf("PEN", "USD")) return@withTransaction false
            if (remote.lastFourDigits.length != 4 || remote.lastFourDigits.any { !it.isDigit() }) return@withTransaction false
            if (remote.issuer.trim().length !in 1..80 || com.kipu.app.feature.accounts.domain.model.Card.isForbiddenSensitiveCardInput(remote.issuer)) return@withTransaction false
            if (remote.alias?.let { it.trim().length !in 1..80 } == true) return@withTransaction false
            if (remote.network !in setOf("VISA", "MASTERCARD", "AMEX", "DINERS", "OTHER")) return@withTransaction false
            val accountId = remote.accountId
            if (remote.type == "DEBIT") {
                val linkedAccount = accountId?.let { accountDao.getById(userId, it) } ?: return@withTransaction false
                if (linkedAccount.type == "CREDIT_LIABILITY" || linkedAccount.currency != remote.currency) return@withTransaction false
            }
            if (remote.type == "CREDIT") {
                if (accountId == null || remote.creditLimitMinorUnits == null || remote.creditLimitMinorUnits < 0L ||
                    remote.billingDay !in 1..31 || remote.dueDay !in 1..31
                ) return@withTransaction false
                var liabilityAccount = accountDao.getById(userId, accountId)
                if (liabilityAccount == null) {
                    val openedAt = remote.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        ?: System.currentTimeMillis()
                    accountDao.insertIfAbsent(
                        AccountEntity(
                            id = accountId,
                            userId = userId,
                            creationOperationId = CreditLiabilityAccountIds.creationOperationId(userId, remote.id),
                            alias = "Pasivo tarjeta •••• ${remote.lastFourDigits}",
                            type = "CREDIT_LIABILITY",
                            currency = remote.currency,
                            presetId = null,
                            color = null,
                            icon = null,
                            initialBalanceMinorUnits = 0L,
                            openedAt = openedAt,
                            isArchived = false,
                            remoteRevision = 0L,
                            createdAt = openedAt,
                            updatedAt = System.currentTimeMillis() * 1_000L,
                        ),
                    )
                    liabilityAccount = accountDao.getById(userId, accountId)
                }
                if (liabilityAccount?.type != "CREDIT_LIABILITY" || liabilityAccount.currency != remote.currency ||
                    liabilityAccount.initialBalanceMinorUnits != 0L
                ) return@withTransaction false
            }
            if (remote.type == "DEBIT" && (remote.creditLimitMinorUnits != null || remote.billingDay != null || remote.dueDay != null)) return@withTransaction false

            val existing = cardDao.getById(userId, remote.id)
            val nowMicros = System.currentTimeMillis() * 1_000L
            val createdAt = remote.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: existing?.createdAt
                ?: System.currentTimeMillis()
            val projection = CardEntity(
                id = remote.id,
                userId = userId,
                creationOperationId = existing?.creationOperationId ?: remote.creationOperationId ?: "remote:${remote.id}",
                accountId = accountId,
                alias = remote.alias,
                type = remote.type,
                currency = remote.currency,
                network = remote.network,
                issuer = remote.issuer,
                lastFourDigits = remote.lastFourDigits,
                creditLimitMinorUnits = remote.creditLimitMinorUnits,
                billingDay = remote.billingDay,
                dueDay = remote.dueDay,
                presetId = remote.presetId,
                stylePresetId = remote.stylePresetId,
                color = remote.color,
                icon = remote.icon,
                isArchived = remote.isArchived,
                remoteRevision = revision,
                createdAt = createdAt,
                updatedAt = nowMicros,
                personalTeaBps = remote.personalTeaBps,
            )
            if (existing == null) cardDao.insert(projection) else cardDao.update(projection)
            true
        }

    private suspend fun applyPulledCardDeletion(userId: String, cardId: String): Boolean =
        database.withTransaction {
            val existing = cardDao.getById(userId, cardId) ?: return@withTransaction true
            if (cardDao.countTransactionsForCard(userId, cardId) != 0) return@withTransaction false
            cardDao.delete(userId, existing.id)
            true
        }

    private suspend fun applyPulledTransaction(
        userId: String,
        remote: SyncTransactionPayload,
        revision: Long,
    ): Boolean = database.withTransaction {
        val existing = movementDao.getTransactionById(userId, remote.id)
        val accountIds = remote.ledgerEntries.map { it.accountId }.distinct()
        if (accountIds.any { accountDao.getById(userId, it) == null }) return@withTransaction false
        val isCardPurchase = remote.operationKind.equals("CARD_PURCHASE", true)
        val isCardPayment = remote.operationKind.equals("CARD_PAYMENT", true)
        if (isCardPurchase || isCardPayment) {
            val cardId = remote.cardId ?: return@withTransaction false
            val card = cardDao.getById(userId, cardId) ?: return@withTransaction false
            if (card.type != "CREDIT" || card.currency != remote.currencyCode) return@withTransaction false
            if (isCardPurchase && (remote.type != "EXPENSE" || remote.sourceAccountId != null || remote.installmentCount?.let { it in 1..36 } != true || remote.categoryId.isNullOrBlank())) return@withTransaction false
            if (isCardPayment && (remote.type != "TRANSFER" || remote.sourceAccountId == null || remote.destinationAccountId != null)) return@withTransaction false
        } else if (remote.cardId != null || remote.installments.isNotEmpty() || remote.allocations.isNotEmpty()) {
            return@withTransaction false
        }
        if (isCardPurchase) {
            val card = cardDao.getById(userId, requireNotNull(remote.cardId)) ?: return@withTransaction false
            val liabilityId = card.accountId ?: return@withTransaction false
            val liability = remote.ledgerEntries.singleOrNull() ?: return@withTransaction false
            if (liability.accountId != liabilityId || liability.role != "LIABILITY" ||
                liability.signedAmountMinor != -remote.amountMinor || liability.currencyCode != remote.currencyCode
            ) return@withTransaction false
        }
        if (isCardPayment) {
            val card = cardDao.getById(userId, requireNotNull(remote.cardId)) ?: return@withTransaction false
            val liabilityId = card.accountId ?: return@withTransaction false
            val sourceId = remote.sourceAccountId ?: return@withTransaction false
            val entries = remote.ledgerEntries
            if (remote.allocations.isEmpty() || entries.size != 2 ||
                entries.count { it.accountId == sourceId && it.role == "SOURCE" && it.signedAmountMinor == -remote.amountMinor && it.currencyCode == remote.currencyCode } != 1 ||
                entries.count { it.accountId == liabilityId && it.role == "LIABILITY" && it.signedAmountMinor == remote.amountMinor && it.currencyCode == remote.currencyCode } != 1 ||
                entries.sumOf { it.signedAmountMinor } != 0L
            ) return@withTransaction false
        }

        if (existing != null) {
            if (existing.cardId != remote.cardId || existing.operationKind != remote.operationKind ||
                existing.installmentCount != remote.installmentCount
            ) return@withTransaction false
            val occurredAt = existing.occurredAt
            if (isCardPurchase && !applyPulledInstallmentSnapshot(userId, remote, occurredAt)) return@withTransaction false
            if (isCardPayment && !applyPulledPaymentAllocations(userId, remote, occurredAt)) return@withTransaction false
            if (!reconcilePulledLedgerEntries(userId, remote, occurredAt)) return@withTransaction false
            accountIds.forEach { accountId ->
                val account = accountDao.getById(userId, accountId) ?: return@withTransaction false
                val balance = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
                movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                    userId = userId, accountId = accountId, balanceMinor = balance,
                    currencyCode = account.currency, lastTransactionAt = occurredAt,
                ))
            }
            movementDao.updateTransactionSyncStatus(userId, remote.id, MovementSyncStatus.SYNCED.name)
            return@withTransaction true
        }
        val occurredAt = Instant.parse(remote.occurredAt).toEpochMilli()
        movementDao.insertPulledTransaction(TransactionEntity(
            id = remote.id, userId = userId, type = remote.type,
            amountMinor = remote.amountMinor, currencyCode = remote.currencyCode,
            sourceAccountId = remote.sourceAccountId, destinationAccountId = remote.destinationAccountId,
            categoryId = remote.categoryId, merchantId = remote.merchantId,
            merchantProvisionalText = remote.merchantProvisionalText,
            occurredAt = occurredAt, note = remote.note,
            status = remote.status, syncStatus = MovementSyncStatus.SYNCED.name,
            createdAt = occurredAt, updatedAt = occurredAt,
            cardId = remote.cardId,
            operationKind = remote.operationKind,
            installmentCount = remote.installmentCount,
        ))
        if (isCardPurchase && !applyPulledInstallmentSnapshot(userId, remote, occurredAt)) return@withTransaction false
        if (isCardPayment && !applyPulledPaymentAllocations(userId, remote, occurredAt)) return@withTransaction false
        if (!reconcilePulledLedgerEntries(userId, remote, occurredAt)) return@withTransaction false
        accountIds.forEach { accountId ->
            val account = accountDao.getById(userId, accountId) ?: return@withTransaction false
            val balance = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                userId = userId, accountId = accountId, balanceMinor = balance,
                currencyCode = account.currency, lastTransactionAt = occurredAt,
            ))
        }
        true
    }

    private suspend fun reconcilePulledLedgerEntries(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
    ): Boolean {
        val expected = remote.ledgerEntries.map { entry ->
            listOf(entry.accountId, entry.role, entry.signedAmountMinor.toString(), entry.currencyCode)
        }.sortedBy { it.joinToString("|") }
        val existing = movementDao.getLedgerEntriesForTransaction(userId, remote.id)
        if (existing.isNotEmpty()) {
            val actual = existing.map { entry ->
                listOf(entry.accountId, entry.role, entry.signedAmountMinor.toString(), entry.currencyCode)
            }.sortedBy { it.joinToString("|") }
            return actual == expected
        }
        if (remote.ledgerEntries.isEmpty()) return true
        movementDao.insertPulledLedgerEntries(remote.ledgerEntries.mapIndexed { index, entry ->
            LedgerEntryEntity(
                id = entry.id ?: "remote:${remote.id}:ledger:$index",
                userId = userId,
                transactionId = remote.id,
                accountId = entry.accountId,
                role = entry.role,
                signedAmountMinor = entry.signedAmountMinor,
                currencyCode = entry.currencyCode,
                createdAt = entry.createdAt?.let { Instant.parse(it).toEpochMilli() } ?: occurredAt,
            )
        })
        return true
    }

    private suspend fun applyPulledInstallmentSnapshot(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
    ): Boolean {
        val count = remote.installmentCount ?: return false
        if (count !in 1..36 || remote.installments.size != count ||
            remote.installments.map { it.installmentNumber }.toSet().size != count ||
            remote.installments.sumOf { it.principalMinor } != remote.amountMinor
        ) return false
        val creditDao = database.creditDao()
        val current = creditDao.getInstallmentsForTransaction(userId, remote.id)
        val currentByNumber = current.associateBy { it.installmentNumber }
        val now = System.currentTimeMillis() * 1_000L
        if (current.isEmpty()) {
            val rows = remote.installments.map { installment ->
                if (installment.id.isBlank() || installment.principalMinor < 0L || installment.interestMinor < 0L) return false
                CreditInstallmentEntity(
                    id = installment.id,
                    userId = userId,
                    transactionId = remote.id,
                    installmentNumber = installment.installmentNumber,
                    dueDate = LocalDate.parse(installment.dueDate).toEpochDay(),
                    principalMinor = installment.principalMinor,
                    interestMinor = installment.interestMinor,
                    status = "PENDING",
                    revision = 1L,
                    createdAt = occurredAt,
                    updatedAt = now,
                )
            }
            creditDao.insertPulledInstallments(rows)
            return true
        }
        if (current.size != count) return false
        for (installment in remote.installments) {
            val local = currentByNumber[installment.installmentNumber] ?: return false
            if (installment.id.isBlank() || installment.principalMinor < 0L || installment.interestMinor < 0L) return false
            val allocated = creditDao.getAllocatedMinorForInstallment(userId, local.id)
            val status = when {
                allocated == 0L -> "PENDING"
                allocated >= installment.principalMinor -> "PAID"
                else -> "PARTIAL"
            }
            val updated = creditDao.adoptRemoteInstallmentId(
                userId = userId,
                transactionId = remote.id,
                installmentNumber = installment.installmentNumber,
                remoteId = installment.id,
                dueDate = LocalDate.parse(installment.dueDate).toEpochDay(),
                principalMinor = installment.principalMinor,
                interestMinor = installment.interestMinor,
                status = status,
                revision = local.revision,
                updatedAt = now,
            )
            if (updated != 1) return false
        }
        return true
    }

    private suspend fun applyPulledPaymentAllocations(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
    ): Boolean {
        if (remote.allocations.any { it.amountMinor <= 0L } || remote.allocations.sumOf { it.amountMinor } != remote.amountMinor) return false
        val creditDao = database.creditDao()
        val existing = creditDao.getAllocationsForPayment(userId, remote.id)
        val remotePairs = remote.allocations.map { it.installmentId to it.amountMinor }.sortedBy { it.first }
        if (existing.isNotEmpty()) {
            return existing.map { it.installmentId to it.allocatedMinor }.sortedBy { it.first } == remotePairs
        }
        for (allocation in remote.allocations) {
            val installment = creditDao.getInstallmentById(userId, allocation.installmentId) ?: return false
            creditDao.insertPulledAllocations(listOf(
                CreditPaymentAllocationEntity(
                    id = "remote:${remote.id}:${allocation.installmentId}",
                    userId = userId,
                    paymentTransactionId = remote.id,
                    installmentId = allocation.installmentId,
                    allocatedMinor = allocation.amountMinor,
                    createdAt = occurredAt,
                ),
            ))
            val totalAllocated = creditDao.getAllocatedMinorForInstallment(userId, installment.id)
            creditDao.updateInstallmentStatus(
                userId = userId,
                installmentId = installment.id,
                status = if (totalAllocated >= installment.principalMinor) "PAID" else "PARTIAL",
                revision = installment.revision + 1L,
                updatedAt = occurredAt,
            )
        }
        return true
    }

    private suspend fun processCommand(cmd: MovementOutboxEntity, userId: String, now: Long): Boolean {
        val leaseUntil = now + 60_000L // 1 minute lease
        movementDao.setOutboxLease(userId, cmd.id, "IN_FLIGHT", leaseUntil)

        return try {
            val requestDto = json.decodeFromString<RegisterTransactionRequestDto>(cmd.payload)
            when (val response = api.registerTransaction(requestDto)) {
                is MovementApiResponse.Success -> {
                    when (response.data.status) {
                        "APPLIED", "DUPLICATE" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "SYNCED", null, null)
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.SYNCED.name)
                            true
                        }
                        "CONFLICT" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "CONFLICT", null, "CONFLICT")
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.CONFLICT.name)
                            true
                        }
                        "REJECTED" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "FAILED_PERMANENT", null, response.data.error?.code ?: "REJECTED")
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.FAILED_PERMANENT.name)
                            true
                        }
                        else -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "RETRY", now + 10_000L, "UNKNOWN_STATUS")
                            false
                        }
                    }
                }
                is MovementApiResponse.Error -> {
                    val isPermanent = response.statusCode in 400..499 && response.statusCode != 408 && response.statusCode != 429
                    if (isPermanent) {
                        movementDao.updateOutboxResult(userId, cmd.id, "FAILED_PERMANENT", null, "HTTP_${response.statusCode}")
                        movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.FAILED_PERMANENT.name)
                        true
                    } else {
                        val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
                        movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "HTTP_${response.statusCode}")
                        false
                    }
                }
                is MovementApiResponse.NetworkFailure -> {
                    val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
                    movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "NETWORK_ERROR")
                    false
                }
            }
        } catch (e: Exception) {
            SecureLog.e("SyncMovementsWorker", "Error processing outbox command: ${e.javaClass.simpleName}")
            val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
            movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "PARSE_ERROR")
            false
        }
    }
}
