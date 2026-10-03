package com.kipu.app.feature.accounts.data.sync

import android.content.Context
import androidx.room.withTransaction
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
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.remote.CreateAccountRequestDto
import com.kipu.app.feature.accounts.data.remote.CommandResponseDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdatePersonalTeaRequestDto
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.SetArchivedRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateAppearanceRequestDto
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementLedgerAliasEntity
import com.kipu.app.feature.movements.data.local.MovementLedgerEffectEntity
import com.kipu.app.feature.movements.data.local.TransactionRevisionEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementRevisionSnapshotCodec
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstrumentSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncInstrumentCommandsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncInstrumentCommandsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncInstrumentCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncDao: InstrumentSyncDao,
    private val cardDao: CardDao,
    private val accountDao: AccountDao,
    private val database: KipuDatabase,
    private val api: FinancialInstrumentsApi,
    private val movementDao: MovementDao,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "instrument-sync"
        private const val COMMAND_LEASE_MICROS = 60_000_000L
        private const val MAX_COMMAND_BATCHES = 100
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncInstrumentWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (currentVerifiedOwner != null && targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncInstrumentWorker", "Target owner differs from verified active owner")
            return Result.retry()
        }

        // Re-query after each batch so a just-acknowledged predecessor can unlock its
        // successor in this run. Claim is a conditional Room update, so overlapping
        // workers cannot dispatch the same live lease.
        repeat(MAX_COMMAND_BATCHES) {
            val nowMicros = System.currentTimeMillis() * 1_000L
            val pendingCommands = syncDao.getPendingCommands(targetUserId, nowMicros)
            if (pendingCommands.isEmpty()) return Result.success()

            var claimedAny = false
            for (cmd in pendingCommands) {
                val claimTime = System.currentTimeMillis() * 1_000L
                val claimed = syncDao.claimCommand(
                    userId = targetUserId,
                    operationId = cmd.operationId,
                    nowMicros = claimTime,
                    leaseExpiresAt = claimTime + COMMAND_LEASE_MICROS,
                )
                if (claimed == 0) continue
                claimedAny = true
                if (!processCommand(cmd, claimTime)) return Result.retry()
            }
            if (!claimedAny) return Result.success()
        }
        SecureLog.w("SyncInstrumentWorker", "Command batch limit reached; scheduling another pass")
        return Result.retry()
    }

    private suspend fun processCommand(cmd: InstrumentSyncOutboxEntity, nowMicros: Long): Boolean {
        if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE" || cmd.commandType == "PAY_CREDIT_CARD") {
            return processCanonicalCreditCommand(cmd, nowMicros)
        }
        if (cmd.commandType == "UPDATE_CARD_PERSONAL_TEA") {
            return processPersonalTeaCommand(cmd, nowMicros)
        }

        val result: FinancialApiResponse<Any> = try {
            when (cmd.commandType) {
                "CREATE_ACCOUNT" -> {
                    val dto = json.decodeFromString<CreateAccountRequestDto>(cmd.payloadJson)
                    api.createAccount(dto)
                }
                "REGISTER_CARD" -> {
                    val dto = json.decodeFromString<RegisterCardRequestDto>(cmd.payloadJson)
                    val accountId = cardDao.getById(cmd.userId, dto.cardId)?.accountId
                    if (dto.type == "CREDIT" && accountId == null) {
                        FinancialApiResponse.Error(400, "Credit card liability account is missing")
                    } else {
                        api.registerCard(dto.copy(accountId = accountId ?: dto.accountId))
                    }
                }
                "UPDATE_APPEARANCE" -> {
                    val dto = json.decodeFromString<UpdateAppearanceRequestDto>(cmd.payloadJson)
                    api.updateAppearance(dto)
                }
                "SET_ARCHIVED" -> {
                    val dto = json.decodeFromString<SetArchivedRequestDto>(cmd.payloadJson)
                    api.setArchived(dto)
                }
                "DELETE_UNUSED_CARD" -> {
                    val dto = json.decodeFromString<DeleteUnusedCardRequestDto>(cmd.payloadJson)
                    api.deleteUnusedCard(dto)
                }
                "RECORD_OPENING_ADJUSTMENT" -> {
                    val dto = json.decodeFromString<RecordOpeningAdjustmentRequestDto>(cmd.payloadJson)
                    api.recordOpeningAdjustment(dto)
                }
                else -> {
                    SecureLog.w("SyncInstrumentWorker", "Unknown command type: ${cmd.commandType}")
                    FinancialApiResponse.Error(400, "Unknown command type")
                }
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }

        return when (result) {
            is FinancialApiResponse.Success -> {
                val response = result.data as? CommandResponseDto
                if (response == null || !response.status.equals("APPLIED", true) && !response.status.equals("DUPLICATE", true)) {
                    syncDao.updateState(cmd.userId, cmd.operationId, "ERROR", System.currentTimeMillis() * 1_000L + 10_000_000L, "INVALID_RESPONSE", System.currentTimeMillis() * 1_000L)
                    return false
                }
                database.withTransaction {
                    when (cmd.aggregateType) {
                        "CARD" -> response.revision?.let {
                            cardDao.updateRemoteRevision(cmd.userId, response.cardId ?: cmd.aggregateId, it, System.currentTimeMillis() * 1_000L)
                        }
                        "ACCOUNT" -> response.revision?.let {
                            accountDao.updateRemoteRevision(cmd.userId, response.accountId ?: cmd.aggregateId, it, System.currentTimeMillis() * 1_000L)
                        }
                    }
                    syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L)
                }
                true
            }
            is FinancialApiResponse.Error -> {
                if (result.statusCode in 400..499 && result.statusCode != 401 && result.statusCode != 408 && result.statusCode != 429) {
                    val errorState = if (result.statusCode == 409) "CONFLICT" else "FAILED_PERMANENT"
                    syncDao.updateState(
                        userId = cmd.userId,
                        operationId = cmd.operationId,
                        newState = errorState,
                        nextAttemptAt = null,
                        errorCode = result.message,
                        nowMicros = System.currentTimeMillis() * 1000L,
                    )
                    true
                } else {
                    // Retryable error
                    val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                    val nextAttempt = (System.currentTimeMillis() + backoffSeconds * 1000L) * 1000L
                    syncDao.updateState(
                        userId = cmd.userId,
                        operationId = cmd.operationId,
                        newState = "ERROR",
                        nextAttemptAt = nextAttempt,
                        errorCode = result.message,
                        nowMicros = System.currentTimeMillis() * 1000L,
                    )
                    false
                }
            }
            is FinancialApiResponse.NetworkFailure -> {
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                val nextAttempt = (System.currentTimeMillis() + backoffSeconds * 1000L) * 1000L
                syncDao.updateState(
                    userId = cmd.userId,
                    operationId = cmd.operationId,
                    newState = "PENDING",
                    nextAttemptAt = nextAttempt,
                    errorCode = result.exception.message,
                    nowMicros = System.currentTimeMillis() * 1000L,
                )
                false
            }
        }
    }

    private suspend fun processCanonicalCreditCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<CreditCommandRequestDto>(cmd.payloadJson)
        } catch (e: Exception) {
            SecureLog.e("SyncInstrumentWorker", "Error preparing credit command: " + e.javaClass.simpleName)
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val transactionId = request.transaction.id
        val response = try {
            when (cmd.commandType) {
                "CONFIRM_CREDIT_PURCHASE" -> api.registerCreditPurchase(request)
                "PAY_CREDIT_CARD" -> api.allocateCreditPayment(request)
                else -> error("Unsupported canonical credit command")
            }
        } catch (e: Exception) {
            SecureLog.e("SyncInstrumentWorker", "Error sending credit command: " + e.javaClass.simpleName)
            updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "NETWORK_ERROR")
            return false
        }

        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    if (response.data.transactionId != null && response.data.transactionId != transactionId) {
                        updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "TRANSACTION_ID_MISMATCH")
                        return false
                    }
                    if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE" &&
                        !reconcilePurchaseInstallments(cmd.userId, request, response.data.installments)
                    ) {
                        updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "INSTALLMENT_RECONCILIATION_FAILED")
                        return false
                    }
                    database.withTransaction {
                        syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L)
                        movementDao.updateTransactionSyncStatus(
                            userId = cmd.userId,
                            transactionId = transactionId,
                            syncStatus = MovementSyncStatus.SYNCED.name,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    true
                }
                "CONFLICT" -> {
                    updateCreditCommandState(cmd, "CONFLICT", null, response.data.error?.code ?: "CONFLICT")
                    movementDao.updateTransactionSyncStatus(
                        cmd.userId, transactionId, MovementSyncStatus.CONFLICT.name, System.currentTimeMillis(),
                    )
                    true
                }
                "REJECTED" -> {
                    rejectCreditCommand(cmd, transactionId, response.data.error?.code ?: "REJECTED")
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> {
                if (response.statusCode in 400..499 && response.statusCode != 401 && response.statusCode != 408 && response.statusCode != 429) {
                    if (response.statusCode == 409) {
                        updateCreditCommandState(cmd, "CONFLICT", null, "HTTP_409")
                        movementDao.updateTransactionSyncStatus(cmd.userId, transactionId, MovementSyncStatus.CONFLICT.name, System.currentTimeMillis())
                    } else {
                        rejectCreditCommand(cmd, transactionId, "HTTP_${response.statusCode}")
                    }
                    true
                } else {
                    val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                    updateCreditCommandState(cmd, "ERROR", nowMicros + backoffSeconds * 1_000_000L, "HTTP_" + response.statusCode)
                    false
                }
            }
            is FinancialApiResponse.NetworkFailure -> {
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                updateCreditCommandState(
                    cmd,
                    "ERROR",
                    nowMicros + backoffSeconds * 1_000_000L,
                    "NETWORK_ERROR",
                )
                false
            }
        }
    }

    private suspend fun processPersonalTeaCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<UpdatePersonalTeaRequestDto>(cmd.payloadJson)
        } catch (e: Exception) {
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val response = api.updatePersonalTea(request)
        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    response.data.revision?.let {
                        // Local card metadata remains owner-scoped; update revision after server acceptance.
                        cardDao.updateRemoteRevision(cmd.userId, cmd.aggregateId, it, System.currentTimeMillis() * 1_000L)
                    }
                    syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L)
                    true
                }
                "CONFLICT", "REJECTED" -> {
                    updateCreditCommandState(cmd, response.data.status, null, response.data.error?.code ?: response.data.status)
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> if (response.statusCode in 400..499 && response.statusCode != 408 && response.statusCode != 429) {
                updateCreditCommandState(cmd, if (response.statusCode == 409) "CONFLICT" else "FAILED_PERMANENT", null, "HTTP_${response.statusCode}")
                true
            } else {
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "HTTP_${response.statusCode}")
                false
            }
            is FinancialApiResponse.NetworkFailure -> {
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "NETWORK_ERROR")
                false
            }
        }
    }

    private suspend fun reconcilePurchaseInstallments(
        userId: String,
        request: CreditCommandRequestDto,
        remoteInstallments: List<com.kipu.app.feature.accounts.data.remote.CreditInstallmentResponseDto>,
    ): Boolean = database.withTransaction {
        val count = request.transaction.installmentCount ?: return@withTransaction false
        if (count !in 1..36 || remoteInstallments.size != count) return@withTransaction false
        if (remoteInstallments.map { it.installmentNumber }.toSet().size != count) return@withTransaction false
        if (remoteInstallments.sumOf { it.principalMinor } != request.transaction.amountMinor) return@withTransaction false
        val localInstallments = database.creditDao().getInstallmentsForTransaction(userId, request.transaction.id)
        if (localInstallments.size != count) return@withTransaction false
        val localByNumber = localInstallments.associateBy { it.installmentNumber }
        val now = System.currentTimeMillis() * 1_000L
        for (remote in remoteInstallments) {
            if (remote.id.isBlank() || remote.principalMinor < 0L || remote.interestMinor < 0L) return@withTransaction false
            if (localByNumber[remote.installmentNumber] == null) return@withTransaction false
            val updated = database.creditDao().adoptRemoteInstallmentId(
                userId = userId,
                transactionId = request.transaction.id,
                installmentNumber = remote.installmentNumber,
                remoteId = remote.id,
                dueDate = java.time.LocalDate.parse(remote.dueDate).toEpochDay(),
                principalMinor = remote.principalMinor,
                interestMinor = remote.interestMinor,
                status = "PENDING",
                revision = 1L,
                updatedAt = now,
            )
            if (updated != 1) return@withTransaction false
        }
        true
    }

    private suspend fun updateCreditCommandState(
        cmd: InstrumentSyncOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
    ) {
        syncDao.updateState(
            userId = cmd.userId,
            operationId = cmd.operationId,
            newState = state,
            nextAttemptAt = nextAttemptAt,
            errorCode = errorCode,
            nowMicros = System.currentTimeMillis() * 1_000L,
        )
    }

    private suspend fun rejectCreditCommand(
        cmd: InstrumentSyncOutboxEntity,
        transactionId: String,
        errorCode: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        database.withTransaction {
            val transaction = movementDao.getTransactionById(cmd.userId, transactionId)
            val creditDao = database.creditDao()
            val paymentAllocations = if (cmd.commandType == "PAY_CREDIT_CARD") {
                creditDao.getAllocationsForPayment(cmd.userId, transactionId)
            } else emptyList()

            if (transaction != null && transaction.status !in setOf("VOIDED", "FAILED")) {
                val beforeRevisionId = transaction.currentRevisionId
                    ?.takeIf { movementDao.getRevision(cmd.userId, it) != null }
                    ?: UUID.randomUUID().toString().also { baselineId ->
                        movementDao.insertRevision(
                            TransactionRevisionEntity(
                                userId = cmd.userId,
                                revisionId = baselineId,
                                transactionId = transaction.id,
                                commandId = null,
                                commandType = "MIGRATION_BASELINE",
                                baseRevision = (transaction.revision - 1L).coerceAtLeast(0L),
                                localRevision = transaction.revision,
                                previousPayload = null,
                                newPayload = MovementRevisionSnapshotCodec.encode(transaction),
                                changeReason = null,
                                provenance = "LOCAL_BASELINE",
                                createdAt = nowMillis,
                            ),
                        )
                        movementDao.getRevision(cmd.userId, baselineId)
                    }
                    ?: error("Rejected movement baseline was not stored")

                val nextRevision = Math.addExact(transaction.revision, 1L)
                val voidRevisionId = UUID.randomUUID().toString()
                val voided = transaction.copy(
                    status = "VOIDED",
                    syncStatus = MovementSyncStatus.FAILED_PERMANENT.name,
                    revision = nextRevision,
                    currentRevisionId = voidRevisionId,
                    source = "LOCAL_REJECTION",
                    updatedAt = nowMillis,
                )
                movementDao.insertRevision(
                    TransactionRevisionEntity(
                        userId = cmd.userId,
                        revisionId = voidRevisionId,
                        transactionId = transaction.id,
                        commandId = cmd.operationId,
                        commandType = "LOCAL_REJECTION_VOID",
                        baseRevision = transaction.revision,
                        localRevision = nextRevision,
                        previousPayload = MovementRevisionSnapshotCodec.encode(transaction),
                        newPayload = MovementRevisionSnapshotCodec.encode(voided),
                        changeReason = errorCode,
                        provenance = "LOCAL_PERMANENT_REJECTION",
                        createdAt = nowMillis,
                    ),
                )

                val priorEffects = movementDao.getLedgerEntriesForTransaction(cmd.userId, transactionId)
                    .filter { it.role != "REVERSAL" }
                val reversals = mutableListOf<com.kipu.app.feature.movements.data.local.LedgerEntryEntity>()
                val reversalMappings = mutableListOf<Pair<MovementLedgerEffectEntity, com.kipu.app.feature.movements.data.local.LedgerEntryEntity>>()
                priorEffects.forEachIndexed { ordinal, original ->
                    val knownAlias = movementDao.getLedgerAlias(cmd.userId, original.id)
                    val originalIdentity = if (knownAlias != null) {
                        movementDao.getLedgerEffect(cmd.userId, knownAlias.commandId, knownAlias.effectOrdinal)
                            ?: error("Ledger alias has no immutable effect identity")
                    } else {
                        val baselineCommandId = UUID.nameUUIDFromBytes(
                            "kipu-ledger-baseline:${cmd.userId}:${original.id}".toByteArray(StandardCharsets.UTF_8),
                        ).toString()
                        val baselineEffect = MovementLedgerEffectEntity(
                            userId = cmd.userId,
                            commandId = baselineCommandId,
                            effectOrdinal = 0,
                            transactionId = transaction.id,
                            revisionId = beforeRevisionId,
                            ledgerEntryId = original.id,
                            reversesCommandId = null,
                            reversesEffectOrdinal = null,
                        )
                        movementDao.insertLedgerEffect(baselineEffect)
                        movementDao.insertLedgerAlias(
                            MovementLedgerAliasEntity(cmd.userId, original.id, baselineCommandId, 0),
                        )
                        baselineEffect
                    }
                    val reversal = com.kipu.app.feature.movements.data.local.LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        userId = cmd.userId,
                        transactionId = transaction.id,
                        accountId = original.accountId,
                        role = "REVERSAL",
                        signedAmountMinor = Math.negateExact(original.signedAmountMinor),
                        currencyCode = original.currencyCode,
                        createdAt = nowMillis,
                    )
                    reversals += reversal
                    reversalMappings += originalIdentity to reversal
                }
                if (reversals.isNotEmpty()) movementDao.insertLedgerEntries(reversals)
                reversalMappings.forEachIndexed { ordinal, (originalIdentity, reversal) ->
                    movementDao.insertLedgerEffect(
                        MovementLedgerEffectEntity(
                            userId = cmd.userId,
                            commandId = cmd.operationId,
                            effectOrdinal = ordinal,
                            transactionId = transaction.id,
                            revisionId = voidRevisionId,
                            ledgerEntryId = reversal.id,
                            reversesCommandId = originalIdentity.commandId,
                            reversesEffectOrdinal = originalIdentity.effectOrdinal,
                        ),
                    )
                    movementDao.insertLedgerAlias(MovementLedgerAliasEntity(cmd.userId, reversal.id, cmd.operationId, ordinal))
                }
                check(movementDao.markTransactionVoidedAfterRejection(
                    cmd.userId, transactionId, nextRevision, voidRevisionId, nowMillis,
                ) == 1) { "Movement rejection must void exactly one transaction" }

                if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE") {
                    creditDao.getInstallmentsForTransaction(cmd.userId, transactionId).forEach { installment ->
                        if (installment.status != "VOIDED") {
                            creditDao.updateInstallmentStatus(
                                userId = cmd.userId,
                                installmentId = installment.id,
                                status = "VOIDED",
                                revision = installment.revision + 1L,
                                updatedAt = nowMillis,
                            )
                        }
                    }
                }
                if (cmd.commandType == "PAY_CREDIT_CARD") {
                    paymentAllocations.map { it.installmentId }.distinct().forEach { installmentId ->
                        val installment = creditDao.getInstallmentById(cmd.userId, installmentId) ?: return@forEach
                        if (installment.status == "VOIDED") return@forEach
                        val allocated = creditDao.getAllocatedMinorForInstallment(cmd.userId, installmentId)
                        val status = when {
                            allocated == 0L -> "PENDING"
                            allocated >= installment.principalMinor -> "PAID"
                            else -> "PARTIAL"
                        }
                        if (status != installment.status) {
                            creditDao.updateInstallmentStatus(
                                userId = cmd.userId,
                                installmentId = installmentId,
                                status = status,
                                revision = installment.revision + 1L,
                                updatedAt = nowMillis,
                            )
                        }
                    }
                }

                (priorEffects + reversals).groupBy { it.accountId }.forEach { (accountId, entries) ->
                    database.financialMovementDao().rebuildAccountProjection(cmd.userId, accountId, nowMillis)
                    movementDao.upsertBalanceProjection(
                        com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                            userId = cmd.userId,
                            accountId = accountId,
                            balanceMinor = movementDao.calculateLedgerSumForAccount(cmd.userId, accountId) ?: 0L,
                            currencyCode = entries.first().currencyCode,
                            lastTransactionAt = nowMillis,
                            updatedAt = nowMillis,
                        ),
                    )
                }

                movementDao.markReceiptsRejectedForTransaction(cmd.userId, transactionId, nowMillis)
                val receiptHash = MessageDigest.getInstance("SHA-256")
                    .digest("LOCAL_REJECTION_VOID\n${cmd.operationId}\n$transactionId\n$errorCode".toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                val rejectionReceiptId = UUID.nameUUIDFromBytes(
                    "kipu-local-rejection-receipt:${cmd.userId}:${cmd.operationId}".toByteArray(StandardCharsets.UTF_8),
                ).toString()
                movementDao.insertOrUpdateReceipt(
                    LocalCommandReceiptEntity(
                        userId = cmd.userId,
                        idempotencyKey = rejectionReceiptId,
                        requestHash = receiptHash,
                        transactionId = transactionId,
                        status = "VOIDED",
                        responsePayload = "{\"status\":\"APPLIED\",\"result\":\"VOIDED\",\"result_code\":\"SERVER_REJECTED\",\"revision\":$nextRevision}",
                        contractVersion = 1,
                        commandType = "LOCAL_REJECTION_VOID",
                        expectedRevision = transaction.revision,
                        resultingRevision = nextRevision,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )
            }
            syncDao.updateState(
                userId = cmd.userId,
                operationId = cmd.operationId,
                newState = "FAILED_PERMANENT",
                nextAttemptAt = null,
                errorCode = errorCode,
                nowMicros = nowMillis * 1_000L,
            )
        }
    }
}
