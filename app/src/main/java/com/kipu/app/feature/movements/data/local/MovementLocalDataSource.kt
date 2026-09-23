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
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementLocalDataSource @Inject constructor(
    private val database: KipuDatabase,
    private val movementDao: MovementDao,
    private val balanceProjectionStore: BalanceProjectionStore,
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
                } else {
                    return@withTransaction RegisterTransactionResult.Conflict(
                        "Idempotency conflict: key ${command.idempotencyKey} already used with different payload"
                    )
                }
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
        occurredAt = occurredAt,
        note = note,
        status = TransactionStatus.fromString(status),
        syncStatus = MovementSyncStatus.fromString(syncStatus),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
