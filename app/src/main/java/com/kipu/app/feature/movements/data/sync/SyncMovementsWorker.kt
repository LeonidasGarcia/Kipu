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
                    if (page.changes.isEmpty()) return true
                    for (change in page.changes.sortedBy { it.sequence }) {
                        val payload = change.payload
                        val applied = try {
                            when {
                                change.operation == "DELETE" -> true
                                change.entityType == "ACCOUNT" && payload != null -> {
                                    val account = json.decodeFromJsonElement<SyncAccountPayload>(payload)
                                    applyPulledAccount(userId, account, change.revision)
                                }
                                change.entityType == "TRANSACTION" && payload != null -> {
                                    val transaction = json.decodeFromJsonElement<SyncTransactionPayload>(payload)
                                    applyPulledTransaction(userId, transaction, change.revision)
                                }
                                else -> true
                            }
                        } catch (e: Exception) {
                            SecureLog.e("SyncMovementsWorker", "Could not apply sync change: ${e.javaClass.simpleName}")
                            false
                        }
                        if (!applied) return false
                        checkpoint = change.sequence
                        movementDao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, checkpoint))
                    }
                    if (!page.hasMore) return true
                }
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    private suspend fun applyPulledAccount(userId: String, remote: SyncAccountPayload, revision: Long): Boolean =
        database.withTransaction {
            val existing = accountDao.getById(userId, remote.id)
            if (existing == null) {
                val openedAt = Instant.parse(remote.openedAt).toEpochMilli()
                accountDao.insertIfAbsent(AccountEntity(
                    id = remote.id, userId = userId, creationOperationId = "remote:${remote.id}",
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
            val account = accountDao.getById(userId, remote.id) ?: return@withTransaction false
            val balance = movementDao.calculateLedgerSumForAccount(userId, remote.id)
                ?: remote.initialBalanceMinorUnits
            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                userId = userId, accountId = remote.id, balanceMinor = balance,
                currencyCode = account.currency, lastTransactionAt = account.openedAt,
            ))
            true
        }

    private suspend fun applyPulledTransaction(
        userId: String,
        remote: SyncTransactionPayload,
        revision: Long,
    ): Boolean = database.withTransaction {
        val existing = movementDao.getTransactionById(userId, remote.id)
        if (existing != null) {
            movementDao.updateTransactionSyncStatus(userId, remote.id, MovementSyncStatus.SYNCED.name)
            return@withTransaction true
        }
        val accountIds = remote.ledgerEntries.map { it.accountId }.distinct()
        if (accountIds.any { accountDao.getById(userId, it) == null }) return@withTransaction false
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
        ))
        movementDao.insertPulledLedgerEntries(remote.ledgerEntries.map { entry ->
            LedgerEntryEntity(
                id = entry.id, userId = userId, transactionId = remote.id,
                accountId = entry.accountId, role = entry.role,
                signedAmountMinor = entry.signedAmountMinor,
                currencyCode = entry.currencyCode,
                createdAt = Instant.parse(entry.createdAt).toEpochMilli(),
            )
        })
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
