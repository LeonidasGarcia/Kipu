package com.kipu.app.feature.accounts.data

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.core.finance.domain.model.MovementStatus
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.CreateAccountRequestDto
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.SetArchivedRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateAppearanceRequestDto
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.DebitCard
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineFirstFinancialInstrumentsRepository @Inject constructor(
    private val database: KipuDatabase,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val movementDao: FinancialMovementDao,
    private val syncDao: InstrumentSyncDao,
    private val sessionCoordinator: SessionCoordinator,
    private val syncScheduler: InstrumentSyncScheduler,
) : FinancialInstrumentsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun currentUserId(): String? {
        return (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }

    override suspend fun createLiquidAccount(account: Account, operationId: OperationId): Result<Account> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L
        val movementId = MovementId.generate().value

        val payloadHash = sha256("${operationId.value}:${account.id.value}:${account.initialBalance.minorUnits}")
        val payload = CreateAccountRequestDto(
            operationId = operationId.value,
            accountId = account.id.value,
            openingMovementId = movementId,
            alias = account.alias,
            type = account.type.name,
            currency = account.currency.name,
            presetId = account.preset?.name,
            color = account.colorToken,
            icon = account.iconToken,
            initialBalanceMinorUnits = account.initialBalance.minorUnits,
            openedAt = account.openedAt.toString(),
            payloadHash = payloadHash,
        )
        val payloadJson = json.encodeToString(payload)

        return runCatching {
            database.withTransaction {
                // 1. Insert AccountEntity
                accountDao.insert(
                    AccountEntity(
                        id = account.id.value,
                        userId = userId,
                        creationOperationId = operationId.value,
                        alias = account.alias,
                        type = account.type.name,
                        currency = account.currency.name,
                        presetId = account.preset?.name,
                        color = account.colorToken,
                        icon = account.iconToken,
                        initialBalanceMinorUnits = account.initialBalance.minorUnits,
                        openedAt = account.openedAt.toEpochMilli() * 1000L,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )

                // 2. Insert exactly one OPENING movement
                movementDao.insert(
                    FinancialMovementEntity(
                        id = movementId,
                        operationId = operationId.value,
                        operationSequence = 0,
                        userId = userId,
                        kind = MovementKind.OPENING.name,
                        amountMinorUnits = account.initialBalance.minorUnits,
                        currency = account.currency.name,
                        accountId = account.id.value,
                        openingAccountId = account.id.value,
                        effectiveAt = account.openedAt.toEpochMilli() * 1000L,
                        status = MovementStatus.POSTED.name,
                        createdAt = nowMicros,
                    )
                )

                // 3. Insert into sync outbox
                val predecessorOpId = syncDao.getLatestForAggregate(userId, "ACCOUNT", account.id.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "CREATE_ACCOUNT",
                        aggregateType = "ACCOUNT",
                        aggregateId = account.id.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = 0L,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }

            syncScheduler.scheduleSync(userId)
            account
        }
    }

    override suspend fun recordOpeningAdjustment(
        accountId: AccountId,
        correctedAmount: Money,
        correctedDate: Instant,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val account = accountDao.getById(userId, accountId.value)
                    ?: throw IllegalArgumentException("Account not found: ${accountId.value}")

                // Find original opening movement
                val movements = database.openHelper.readableDatabase.query(
                    "SELECT id, amount_minor_units, effective_at FROM financial_movements WHERE user_id = ? AND opening_account_id = ? LIMIT 1",
                    arrayOf(userId, accountId.value)
                )
                var originalOpeningId: String? = null
                var originalAmount = 0L
                if (movements.moveToFirst()) {
                    originalOpeningId = movements.getString(0)
                    originalAmount = movements.getLong(1)
                }
                movements.close()

                requireNotNull(originalOpeningId) { "Original opening movement not found for account ${accountId.value}" }

                val reversalId = MovementId.generate().value
                val adjustmentId = MovementId.generate().value

                // 1. REVERSAL movement
                movementDao.insert(
                    FinancialMovementEntity(
                        id = reversalId,
                        operationId = operationId.value,
                        operationSequence = 0,
                        userId = userId,
                        kind = MovementKind.REVERSAL.name,
                        amountMinorUnits = -originalAmount,
                        currency = account.currency,
                        accountId = accountId.value,
                        effectiveAt = nowMicros,
                        status = MovementStatus.POSTED.name,
                        reversesMovementId = originalOpeningId,
                        createdAt = nowMicros,
                    )
                )

                // 2. ADJUSTMENT movement
                movementDao.insert(
                    FinancialMovementEntity(
                        id = adjustmentId,
                        operationId = operationId.value,
                        operationSequence = 1,
                        userId = userId,
                        kind = MovementKind.ADJUSTMENT.name,
                        amountMinorUnits = correctedAmount.minorUnits,
                        currency = account.currency,
                        accountId = accountId.value,
                        effectiveAt = correctedDate.toEpochMilli() * 1000L,
                        status = MovementStatus.POSTED.name,
                        adjustsMovementId = originalOpeningId,
                        createdAt = nowMicros,
                    )
                )

                // 3. Outbox command
                val payload = RecordOpeningAdjustmentRequestDto(
                    operationId = operationId.value,
                    accountId = accountId.value,
                    reversalMovementId = reversalId,
                    adjustmentMovementId = adjustmentId,
                    newAmountMinorUnits = correctedAmount.minorUnits,
                    newEffectiveAt = correctedDate.toString(),
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val predecessorOpId = syncDao.getLatestForAggregate(userId, "ACCOUNT", accountId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "RECORD_OPENING_ADJUSTMENT",
                        aggregateType = "ACCOUNT",
                        aggregateId = accountId.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = account.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }

            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun updateAccountAppearance(
        accountId: AccountId,
        alias: String,
        preset: AccountPreset?,
        colorToken: String?,
        iconToken: String?,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val account = accountDao.getById(userId, accountId.value)
                    ?: throw IllegalArgumentException("Account not found: ${accountId.value}")

                accountDao.updateAppearance(
                    userId = userId,
                    id = accountId.value,
                    alias = alias,
                    presetId = preset?.name,
                    color = colorToken,
                    icon = iconToken,
                    nowMicros = nowMicros,
                )

                val payload = UpdateAppearanceRequestDto(
                    operationId = operationId.value,
                    instrumentId = accountId.value,
                    instrumentType = "ACCOUNT",
                    alias = alias,
                    presetId = preset?.name,
                    color = colorToken,
                    icon = iconToken,
                    expectedRevision = account.remoteRevision,
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val updatePredecessorOpId = syncDao.getLatestForAggregate(userId, "ACCOUNT", accountId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "UPDATE_APPEARANCE",
                        aggregateType = "ACCOUNT",
                        aggregateId = accountId.value,
                        predecessorOperationId = updatePredecessorOpId,
                        expectedRevision = account.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun registerDebitCard(card: DebitCard, operationId: OperationId): Result<DebitCard> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                // Verify linked account exists, is active, is SAVINGS or BANK, and currency matches
                val linkedAccount = accountDao.getById(userId, card.linkedAccountId.value)
                    ?: throw IllegalArgumentException("Linked account not found: ${card.linkedAccountId.value}")

                require(!linkedAccount.isArchived) { "Cannot link card to archived account" }
                require(linkedAccount.type in listOf("SAVINGS", "BANK")) { "Linked account must be SAVINGS or BANK" }
                require(linkedAccount.currency == card.currency.name) { "Card currency must match linked account currency" }

                cardDao.insert(
                    CardEntity(
                        id = card.id.value,
                        userId = userId,
                        creationOperationId = operationId.value,
                        type = "DEBIT",
                        alias = card.alias,
                        issuer = card.issuer,
                        network = card.network.name,
                        lastFourDigits = card.lastFourDigits,
                        currency = card.currency.name,
                        accountId = card.linkedAccountId.value,
                        creditLimitMinorUnits = null,
                        billingDay = null,
                        dueDay = null,
                        presetId = card.preset?.name,
                        color = card.colorToken,
                        icon = card.iconToken,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )

                val payloadHash = sha256("${operationId.value}:${card.id.value}:${card.lastFourDigits}")
                val payload = RegisterCardRequestDto(
                    operationId = operationId.value,
                    cardId = card.id.value,
                    type = "DEBIT",
                    issuer = card.issuer,
                    network = card.network.name,
                    lastFourDigits = card.lastFourDigits,
                    currency = card.currency.name,
                    alias = card.alias,
                    accountId = card.linkedAccountId.value,
                    creditLimitMinorUnits = null,
                    billingDay = null,
                    dueDay = null,
                    presetId = card.preset?.name,
                    color = card.colorToken,
                    icon = card.iconToken,
                    payloadHash = payloadHash,
                )
                val payloadJson = json.encodeToString(payload)

                val debitPredecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", card.id.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "REGISTER_CARD",
                        aggregateType = "CARD",
                        aggregateId = card.id.value,
                        predecessorOperationId = debitPredecessorOpId,
                        expectedRevision = 0L,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
            card
        }
    }

    override suspend fun registerCreditCard(card: CreditCard, operationId: OperationId): Result<CreditCard> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                cardDao.insert(
                    CardEntity(
                        id = card.id.value,
                        userId = userId,
                        creationOperationId = operationId.value,
                        type = "CREDIT",
                        alias = card.alias,
                        issuer = card.issuer,
                        network = card.network.name,
                        lastFourDigits = card.lastFourDigits,
                        currency = card.currency.name,
                        accountId = null,
                        creditLimitMinorUnits = card.creditLimitMinorUnits,
                        billingDay = card.billingDay,
                        dueDay = card.dueDay,
                        presetId = card.preset?.name,
                        color = card.colorToken,
                        icon = card.iconToken,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )

                val payloadHash = sha256("${operationId.value}:${card.id.value}:${card.creditLimitMinorUnits}")
                val payload = RegisterCardRequestDto(
                    operationId = operationId.value,
                    cardId = card.id.value,
                    type = "CREDIT",
                    issuer = card.issuer,
                    network = card.network.name,
                    lastFourDigits = card.lastFourDigits,
                    currency = card.currency.name,
                    alias = card.alias,
                    accountId = null,
                    creditLimitMinorUnits = card.creditLimitMinorUnits,
                    billingDay = card.billingDay,
                    dueDay = card.dueDay,
                    presetId = card.preset?.name,
                    color = card.colorToken,
                    icon = card.iconToken,
                    payloadHash = payloadHash,
                )
                val payloadJson = json.encodeToString(payload)

                val creditPredecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", card.id.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "REGISTER_CARD",
                        aggregateType = "CARD",
                        aggregateId = card.id.value,
                        predecessorOperationId = creditPredecessorOpId,
                        expectedRevision = 0L,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
            card
        }
    }

    override suspend fun updateCardAppearance(
        cardId: CardId,
        alias: String?,
        preset: CardPreset?,
        colorToken: String?,
        iconToken: String?,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val card = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Card not found: ${cardId.value}")

                cardDao.updateAppearance(
                    userId = userId,
                    id = cardId.value,
                    alias = alias,
                    presetId = preset?.name,
                    color = colorToken,
                    icon = iconToken,
                    nowMicros = nowMicros,
                )

                val payload = UpdateAppearanceRequestDto(
                    operationId = operationId.value,
                    instrumentId = cardId.value,
                    instrumentType = "CARD",
                    alias = alias ?: "",
                    presetId = preset?.name,
                    color = colorToken,
                    icon = iconToken,
                    expectedRevision = card.remoteRevision,
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val predecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", cardId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "UPDATE_APPEARANCE",
                        aggregateType = "CARD",
                        aggregateId = cardId.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = card.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun deleteUnusedCard(cardId: CardId, operationId: OperationId): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val movementsCount = movementDao.countByCard(userId, cardId.value)
                require(movementsCount == 0) { "Cannot delete card with existing movements" }

                cardDao.delete(userId, cardId.value)

                val payload = DeleteUnusedCardRequestDto(
                    cardId = cardId.value,
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val predecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", cardId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "DELETE_UNUSED_CARD",
                        aggregateType = "CARD",
                        aggregateId = cardId.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = 0L,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun payCreditCard(
        cardId: CardId,
        sourceAccountId: AccountId,
        paymentAmount: Money,
        effectiveAt: Instant,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val sourceAccount = accountDao.getById(userId, sourceAccountId.value)
                    ?: throw IllegalArgumentException("Source account not found: ${sourceAccountId.value}")
                val creditCard = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Credit card not found: ${cardId.value}")

                require(!sourceAccount.isArchived) { "Cannot pay from archived account" }
                require(!creditCard.isArchived) { "Cannot pay archived credit card" }
                require(sourceAccount.currency == paymentAmount.currency.name) { "Source account currency mismatch" }
                require(creditCard.currency == paymentAmount.currency.name) { "Credit card currency mismatch" }

                val sourceBalance = movementDao.getAccountBalance(userId, sourceAccountId.value)
                require(sourceBalance >= paymentAmount.minorUnits) { "Insufficient funds in source account" }

                val currentDebt = movementDao.getCardDebt(userId, cardId.value)
                require(currentDebt >= paymentAmount.minorUnits) { "Payment amount exceeds current card debt" }

                val movementCashId = MovementId.generate().value
                val movementLiabilityId = MovementId.generate().value

                // 1. Movement on cash account: CARD_PAYMENT_CASH (negative signed effect)
                movementDao.insert(
                    FinancialMovementEntity(
                        id = movementCashId,
                        operationId = operationId.value,
                        operationSequence = 0,
                        userId = userId,
                        kind = MovementKind.CARD_PAYMENT_CASH.name,
                        amountMinorUnits = -paymentAmount.minorUnits,
                        currency = paymentAmount.currency.name,
                        accountId = sourceAccountId.value,
                        effectiveAt = effectiveAt.toEpochMilli() * 1000L,
                        status = MovementStatus.POSTED.name,
                        createdAt = nowMicros,
                    )
                )

                // 2. Movement on credit card: CARD_PAYMENT_LIABILITY (negative signed effect reducing debt)
                movementDao.insert(
                    FinancialMovementEntity(
                        id = movementLiabilityId,
                        operationId = operationId.value,
                        operationSequence = 1,
                        userId = userId,
                        kind = MovementKind.CARD_PAYMENT_LIABILITY.name,
                        amountMinorUnits = -paymentAmount.minorUnits,
                        currency = paymentAmount.currency.name,
                        cardId = cardId.value,
                        effectiveAt = effectiveAt.toEpochMilli() * 1000L,
                        status = MovementStatus.POSTED.name,
                        createdAt = nowMicros,
                    )
                )

                val payloadJson = """{"operation_id":"${operationId.value}","card_id":"${cardId.value}","source_account_id":"${sourceAccountId.value}","amount_minor_units":${paymentAmount.minorUnits},"currency":"${paymentAmount.currency.name}","effective_at":"$effectiveAt"}"""
                val payloadHash = sha256(payloadJson)

                val predecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", cardId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "PAY_CREDIT_CARD",
                        aggregateType = "CARD",
                        aggregateId = cardId.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = creditCard.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun confirmCreditPurchase(
        cardId: CardId,
        amount: Money,
        merchant: String,
        effectiveAt: Instant,
        installments: Int,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val card = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Card not found: ${cardId.value}")
                require(card.type == "CREDIT") { "Only credit cards can record credit purchases" }
                require(!card.isArchived) { "Cannot record purchase on archived card" }
                require(card.currency == amount.currency.name) { "Card currency mismatch" }
                require(amount.minorUnits > 0L) { "Purchase amount must be positive" }
                require(installments in 1..36) { "Installments must be between 1 and 36: $installments" }

                val movementId = MovementId.generate().value

                // Insert single CREDIT_PURCHASE movement on card (positive signed amount representing debt)
                movementDao.insert(
                    FinancialMovementEntity(
                        id = movementId,
                        operationId = operationId.value,
                        operationSequence = 0,
                        userId = userId,
                        kind = MovementKind.CREDIT_PURCHASE.name,
                        amountMinorUnits = amount.minorUnits,
                        currency = amount.currency.name,
                        cardId = cardId.value,
                        effectiveAt = effectiveAt.toEpochMilli() * 1000L,
                        status = MovementStatus.POSTED.name,
                        createdAt = nowMicros,
                    )
                )

                val escapedMerchant = merchant.replace("\"", "\\\"")
                val payloadJson = """{"operation_id":"${operationId.value}","card_id":"${cardId.value}","amount_minor_units":${amount.minorUnits},"currency":"${amount.currency.name}","merchant":"$escapedMerchant","installments":$installments,"effective_at":"$effectiveAt"}"""
                val payloadHash = sha256(payloadJson)

                val predecessorOpId = syncDao.getLatestForAggregate(userId, "CARD", cardId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "CONFIRM_CREDIT_PURCHASE",
                        aggregateType = "CARD",
                        aggregateId = cardId.value,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = card.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun archiveInstrument(
        instrumentId: String,
        isCard: Boolean,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val remoteRevision = if (isCard) {
                    val card = cardDao.getById(userId, instrumentId)
                        ?: throw IllegalArgumentException("Card not found: $instrumentId")
                    cardDao.setArchived(userId, instrumentId, true, nowMicros)
                    card.remoteRevision
                } else {
                    val account = accountDao.getById(userId, instrumentId)
                        ?: throw IllegalArgumentException("Account not found: $instrumentId")
                    accountDao.setArchived(userId, instrumentId, true, nowMicros)
                    account.remoteRevision
                }

                val payload = SetArchivedRequestDto(
                    operationId = operationId.value,
                    instrumentId = instrumentId,
                    instrumentType = if (isCard) "CARD" else "ACCOUNT",
                    isArchived = true,
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val aggregateType = if (isCard) "CARD" else "ACCOUNT"
                val predecessorOpId = syncDao.getLatestForAggregate(userId, aggregateType, instrumentId)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "SET_ARCHIVED",
                        aggregateType = aggregateType,
                        aggregateId = instrumentId,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun reactivateInstrument(
        instrumentId: String,
        isCard: Boolean,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val remoteRevision = if (isCard) {
                    val card = cardDao.getById(userId, instrumentId)
                        ?: throw IllegalArgumentException("Card not found: $instrumentId")
                    cardDao.setArchived(userId, instrumentId, false, nowMicros)
                    card.remoteRevision
                } else {
                    val account = accountDao.getById(userId, instrumentId)
                        ?: throw IllegalArgumentException("Account not found: $instrumentId")
                    accountDao.setArchived(userId, instrumentId, false, nowMicros)
                    account.remoteRevision
                }

                val payload = SetArchivedRequestDto(
                    operationId = operationId.value,
                    instrumentId = instrumentId,
                    instrumentType = if (isCard) "CARD" else "ACCOUNT",
                    isArchived = false,
                )
                val payloadJson = json.encodeToString(payload)
                val payloadHash = sha256(payloadJson)

                val aggregateType = if (isCard) "CARD" else "ACCOUNT"
                val predecessorOpId = syncDao.getLatestForAggregate(userId, aggregateType, instrumentId)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "SET_ARCHIVED",
                        aggregateType = aggregateType,
                        aggregateId = instrumentId,
                        predecessorOperationId = predecessorOpId,
                        expectedRevision = remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    private fun AccountEntity.toDomain(): Account {
        val curr = Currency.fromCode(currency)
        return Account(
            id = AccountId(id),
            userId = UserId(userId),
            alias = alias,
            type = AccountType.valueOf(type),
            currency = curr,
            preset = AccountPreset.fromId(presetId),
            colorToken = color,
            iconToken = icon,
            initialBalance = Money(initialBalanceMinorUnits, curr),
            openedAt = Instant.ofEpochMilli(openedAt / 1000L),
            isArchived = isArchived,
            remoteRevision = remoteRevision,
            createdAt = Instant.ofEpochMilli(createdAt / 1000L),
            updatedAt = Instant.ofEpochMilli(updatedAt / 1000L),
        )
    }

    private fun CardEntity.toDomain(): Card {
        val curr = Currency.fromCode(currency)
        return if (type == "DEBIT") {
            DebitCard(
                id = CardId(id),
                userId = UserId(userId),
                alias = alias,
                issuer = issuer,
                network = CardNetwork.valueOf(network),
                lastFourDigits = lastFourDigits,
                currency = curr,
                linkedAccountId = AccountId(requireNotNull(accountId)),
                preset = CardPreset.fromId(presetId),
                colorToken = color,
                iconToken = icon,
                isArchived = isArchived,
                remoteRevision = remoteRevision,
                createdAt = Instant.ofEpochMilli(createdAt / 1000L),
                updatedAt = Instant.ofEpochMilli(updatedAt / 1000L),
            )
        } else {
            CreditCard(
                id = CardId(id),
                userId = UserId(userId),
                alias = alias,
                issuer = issuer,
                network = CardNetwork.valueOf(network),
                lastFourDigits = lastFourDigits,
                currency = curr,
                creditLimitMinorUnits = creditLimitMinorUnits ?: 0L,
                billingDay = billingDay ?: 1,
                dueDay = dueDay ?: 1,
                personalTeaBps = null,
                preset = CardPreset.fromId(presetId),
                colorToken = color,
                iconToken = icon,
                isArchived = isArchived,
                remoteRevision = remoteRevision,
                createdAt = Instant.ofEpochMilli(createdAt / 1000L),
                updatedAt = Instant.ofEpochMilli(updatedAt / 1000L),
            )
        }
    }

    override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    val flow = if (activeOnly) accountDao.observeActive(access.userId) else accountDao.observeAll(access.userId)
                    flow.map { list -> list.map { it.toDomain() } }
                }
                else -> flowOf(emptyList())
            }
        }.distinctUntilChanged()
    }

    override fun observeAccountById(accountId: AccountId): Flow<Account?> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> accountDao.observeById(access.userId, accountId.value).map { it?.toDomain() }
                else -> flowOf(null)
            }
        }.distinctUntilChanged()
    }

    override fun observeAccountBalance(accountId: AccountId): Flow<Money> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    combine(
                        accountDao.observeById(access.userId, accountId.value),
                        movementDao.observeAccountBalance(access.userId, accountId.value),
                    ) { account, balanceMinorUnits ->
                        if (account == null) {
                            Money(0L, Currency.PEN)
                        } else {
                            Money(balanceMinorUnits, Currency.fromCode(account.currency))
                        }
                    }
                }
                else -> flowOf(Money(0L, Currency.PEN))
            }
        }.distinctUntilChanged()
    }

    override fun observeCards(activeOnly: Boolean): Flow<List<Card>> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    val flow = if (activeOnly) cardDao.observeActive(access.userId) else cardDao.observeAll(access.userId)
                    flow.map { list -> list.map { it.toDomain() } }
                }
                else -> flowOf(emptyList())
            }
        }.distinctUntilChanged()
    }

    override fun observeCardById(cardId: CardId): Flow<Card?> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> cardDao.observeById(access.userId, cardId.value).map { it?.toDomain() }
                else -> flowOf(null)
            }
        }.distinctUntilChanged()
    }

    override fun observeCardDebt(cardId: CardId): Flow<Money> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    combine(
                        cardDao.observeById(access.userId, cardId.value),
                        movementDao.observeCardDebt(access.userId, cardId.value),
                    ) { card, debtMinorUnits ->
                        if (card == null) {
                            Money(0L, Currency.PEN)
                        } else {
                            Money(debtMinorUnits, Currency.fromCode(card.currency))
                        }
                    }
                }
                else -> flowOf(Money(0L, Currency.PEN))
            }
        }.distinctUntilChanged()
    }

    override fun observeMovementsByAccount(accountId: AccountId): Flow<List<FinancialMovement>> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    movementDao.observeByAccount(access.userId, accountId.value).map { list ->
                        list.map { entity ->
                            FinancialMovement(
                                id = MovementId(entity.id),
                                operationId = OperationId(entity.operationId),
                                sequence = entity.operationSequence,
                                userId = UserId(entity.userId),
                                kind = MovementKind.valueOf(entity.kind),
                                amountMinorUnits = entity.amountMinorUnits,
                                currency = Currency.fromCode(entity.currency),
                                accountId = entity.accountId?.let { AccountId(it) },
                                cardId = entity.cardId?.let { CardId(it) },
                                effectiveAt = Instant.ofEpochMilli(entity.effectiveAt / 1000L),
                                status = MovementStatus.valueOf(entity.status),
                                reversesMovementId = entity.reversesMovementId?.let { MovementId(it) },
                                adjustsMovementId = entity.adjustsMovementId?.let { MovementId(it) },
                                createdAt = Instant.ofEpochMilli(entity.createdAt / 1000L),
                            )
                        }
                    }
                }
                else -> flowOf(emptyList())
            }
        }.distinctUntilChanged()
    }

    override fun observeActiveComputableCount(): Flow<Int> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    combine(
                        accountDao.observeActive(access.userId),
                        cardDao.observeActive(access.userId),
                    ) { accounts, cards ->
                        val computableAccounts = accounts.count { it.type != "CASH" }
                        computableAccounts + cards.size
                    }
                }
                else -> flowOf(0)
            }
        }.distinctUntilChanged()
    }

    override suspend fun getActiveComputableCount(): Int {
        val userId = currentUserId() ?: return 0
        val computableAccounts = accountDao.countActiveComputableAccounts(userId)
        val activeCards = cardDao.countActiveCards(userId)
        return computableAccounts + activeCards
    }

    override suspend fun hasCardWithIdentity(
        issuer: String,
        network: CardNetwork,
        lastFourDigits: String,
    ): Boolean {
        val userId = currentUserId() ?: return false
        val duplicates = cardDao.findDuplicates(userId, issuer, network.name, lastFourDigits)
        return duplicates.isNotEmpty()
    }
}
