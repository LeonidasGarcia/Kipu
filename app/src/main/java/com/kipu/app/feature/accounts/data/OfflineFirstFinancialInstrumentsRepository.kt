package com.kipu.app.feature.accounts.data

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.feature.accounts.domain.CreditPaymentAllocator
import com.kipu.app.feature.accounts.domain.DuePrincipalInstallment
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.core.finance.domain.model.MovementStatus
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.InstallmentCalculator
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
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.CreditTransactionCommandDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.PersonalTeaCardCommandDto
import com.kipu.app.feature.accounts.data.remote.UpdatePersonalTeaRequestDto
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
import com.kipu.app.feature.accounts.domain.model.CreditLiabilityAccountIds
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.plans.domain.PlanQuotaPolicy
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val NON_COMPUTABLE_ACCOUNT_TYPES = setOf("CASH", "GOALS_VIRTUAL", "CREDIT_LIABILITY")

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineFirstFinancialInstrumentsRepository @Inject constructor(
    private val database: KipuDatabase,
    private val financialApi: FinancialInstrumentsApi,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val movementDao: FinancialMovementDao,
    private val syncDao: InstrumentSyncDao,
    private val sessionCoordinator: SessionCoordinator,
    private val syncScheduler: InstrumentSyncScheduler,
    private val quotaPolicy: PlanQuotaPolicy = PlanQuotaPolicy(),
    private val planQuotaSyncScheduler: com.kipu.app.feature.plans.data.sync.PlanQuotaSyncScheduler? = null,
) : FinancialInstrumentsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val creditCommandJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun stableCreditTransactionId(userId: String, operationId: String, suffix: String): String =
        UUID.nameUUIDFromBytes("kipu:$userId:$operationId:$suffix".toByteArray(Charsets.UTF_8)).toString()

    private fun currentUserId(): String? {
        return (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }

    override suspend fun createLiquidAccount(account: Account, operationId: OperationId): Result<Account> {
        if (account.type == AccountType.CREDIT_LIABILITY) {
            return Result.failure(IllegalArgumentException("Credit liability accounts are managed by their card"))
        }
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
                        stylePresetId = card.stylePresetId,
                        color = card.colorToken,
                        icon = card.iconToken,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    )
                )

                val payloadHash = sha256("${operationId.value}:${card.id.value}:${card.lastFourDigits}:${card.stylePresetId.orEmpty()}")
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
                    stylePresetId = card.stylePresetId,
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
        if (card.userId.value != userId) {
            return Result.failure(IllegalArgumentException("Credit card owner does not match the active session"))
        }
        val nowMicros = System.currentTimeMillis() * 1000L

        return runCatching {
            database.withTransaction {
                val liabilityAccountId = card.liabilityAccountId.value
                accountDao.insertIfAbsent(
                    AccountEntity(
                        id = liabilityAccountId,
                        userId = userId,
                        creationOperationId = CreditLiabilityAccountIds.creationOperationId(userId, card.id.value),
                        alias = "Pasivo tarjeta •••• ${card.lastFourDigits}",
                        type = "CREDIT_LIABILITY",
                        currency = card.currency.name,
                        presetId = null,
                        color = null,
                        icon = null,
                        initialBalanceMinorUnits = 0L,
                        openedAt = nowMicros,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    ),
                )
                val liabilityAccount = requireNotNull(accountDao.getById(userId, liabilityAccountId))
                require(liabilityAccount.type == "CREDIT_LIABILITY" && liabilityAccount.currency == card.currency.name) {
                    "Credit card liability account is incompatible"
                }
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
                        accountId = liabilityAccountId,
                        creditLimitMinorUnits = card.creditLimitMinorUnits,
                        billingDay = card.billingDay,
                        dueDay = card.dueDay,
                        presetId = card.preset?.name,
                        stylePresetId = card.stylePresetId,
                        color = card.colorToken,
                        icon = card.iconToken,
                        isArchived = false,
                        remoteRevision = 0L,
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                        personalTeaBps = card.personalTeaBps,
                    )
                )

                val payloadHash = sha256("${operationId.value}:${card.id.value}:${card.creditLimitMinorUnits}:${card.stylePresetId.orEmpty()}")
                val payload = RegisterCardRequestDto(
                    operationId = operationId.value,
                    cardId = card.id.value,
                    type = "CREDIT",
                    issuer = card.issuer,
                    network = card.network.name,
                    lastFourDigits = card.lastFourDigits,
                    currency = card.currency.name,
                    alias = card.alias,
                    accountId = liabilityAccountId,
                    creditLimitMinorUnits = card.creditLimitMinorUnits,
                    billingDay = card.billingDay,
                    dueDay = card.dueDay,
                    presetId = card.preset?.name,
                    stylePresetId = card.stylePresetId,
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

    override suspend fun updatePersonalTea(
        cardId: CardId,
        teaBps: Int?,
        operationId: OperationId,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        if (teaBps != null && teaBps !in 0..100_000) {
            return Result.failure(IllegalArgumentException("TEA must be from 0% through 1000%"))
        }
        val nowMicros = System.currentTimeMillis() * 1_000L

        return runCatching {
            database.withTransaction {
                val card = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Card not found: ${cardId.value}")
                require(card.type == "CREDIT" && !card.isArchived) { "Only an active credit card can have a personal TEA" }
                check(cardDao.updatePersonalTea(userId, cardId.value, teaBps, nowMicros) == 1) {
                    "Could not update personal TEA"
                }

                val payload = UpdatePersonalTeaRequestDto(
                    idempotencyKey = operationId.value,
                    requestHash = "pending",
                    card = PersonalTeaCardCommandDto(cardId.value, teaBps),
                )
                val canonicalPayload = json.encodeToString(payload)
                val request = payload.copy(requestHash = sha256(canonicalPayload))
                val payloadJson = creditCommandJson.encodeToString(request)
                val predecessor = syncDao.getLatestForAggregate(userId, "CARD", cardId.value)?.operationId
                syncDao.insert(
                    InstrumentSyncOutboxEntity(
                        operationId = operationId.value,
                        userId = userId,
                        commandType = "UPDATE_CARD_PERSONAL_TEA",
                        aggregateType = "CARD",
                        aggregateId = cardId.value,
                        predecessorOperationId = predecessor,
                        expectedRevision = card.remoteRevision,
                        contractVersion = 1,
                        payloadJson = payloadJson,
                        payloadHash = sha256(payloadJson),
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    ),
                )
            }
            syncScheduler.scheduleSync(userId)
        }
    }

    override suspend fun getCreditProductCatalog(): Result<List<CreditProductReference>> {
        return when (val response = financialApi.fetchCreditProducts()) {
            is FinancialApiResponse.Success -> runCatching {
                val products = response.data
                    .filter { it.institutionCode in setOf("BCP", "BBVA", "INTERBANK") }
                    .map { item ->
                        CreditProductReference(
                            id = item.id,
                            institutionCode = item.institutionCode,
                            institutionName = item.institutionName?.takeIf(String::isNotBlank)
                                ?: when (item.institutionCode.uppercase()) {
                                    "BCP" -> "Banco de Crédito del Perú"
                                    "BBVA" -> "BBVA Perú"
                                    "INTERBANK" -> "Interbank"
                                    "SCOTIABANK" -> "Scotiabank Perú"
                                    else -> item.institutionCode
                                },
                            productName = item.productName,
                            cardNetwork = item.cardNetwork,
                            penTeaMinBps = item.referenceTeaPenMinBps,
                            penTeaMaxBps = item.referenceTeaPenMaxBps,
                            usdTeaMinBps = item.referenceTeaUsdMinBps,
                            usdTeaMaxBps = item.referenceTeaUsdMaxBps,
                            publishedTeaSummary = item.publishedTeaSummary,
                            publishedTceaSummary = item.publishedTceaSummary,
                            membershipFeePenMinor = item.membershipFeePenMinor,
                            membershipFeeUsdMinor = item.membershipFeeUsdMinor,
                            membershipCondition = item.membershipCondition,
                            sourceUrl = item.sourceUrl,
                            verificationStatus = item.verificationStatus,
                            catalogAsOf = item.catalogAsOf?.let(LocalDate::parse),
                            effectiveTo = item.effectiveTo?.let(LocalDate::parse),
                        )
                    }
                    .sortedWith(compareBy<CreditProductReference>({ it.institutionCode }, { it.productName }))
                check(products.size == 44) { "Expected 44 listed BCP, BBVA and Interbank credit products; received ${products.size}" }
                products
            }
            is FinancialApiResponse.Error -> Result.failure(IllegalStateException("Credit catalog request failed (${response.statusCode}): ${response.message}"))
            is FinancialApiResponse.NetworkFailure -> Result.failure(response.exception)
        }
    }

    override suspend fun getCreditUtilizationNotifications(cardId: String?): Result<List<CreditUtilizationNotification>> {
        return when (val response = financialApi.fetchCreditUtilizationNotifications(cardId)) {
            is FinancialApiResponse.Success -> Result.success(
                response.data
                    .filter {
                        it.notificationType == "CREDIT_UTILIZATION_THRESHOLD_CROSSED" &&
                            it.referenceEntityType == "CARD" && !it.isRead && it.referenceEntityId != null
                    }
                    .map {
                        CreditUtilizationNotification(
                            id = it.id,
                            cardId = requireNotNull(it.referenceEntityId),
                            title = it.title,
                            body = it.body,
                            eventKey = it.eventKey,
                            createdAt = it.createdAt,
                        )
                    },
            )
            is FinancialApiResponse.Error -> Result.failure(IllegalStateException("Credit notifications request failed (${response.statusCode}): ${response.message}"))
            is FinancialApiResponse.NetworkFailure -> Result.failure(response.exception)
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
        val nowMillis = System.currentTimeMillis()
        val transactionId = stableCreditTransactionId(userId, operationId.value, "payment")
        val transactionCommand = CreditTransactionCommandDto(
            id = transactionId,
            type = "TRANSFER",
            amountMinor = paymentAmount.minorUnits,
            currencyCode = paymentAmount.currency.name,
            sourceAccountId = sourceAccountId.value,
            occurredAt = effectiveAt.toString(),
            cardId = cardId.value,
            operationKind = "CARD_PAYMENT",
            installmentCount = 1,
        )
        val payloadHash = sha256(
            """{"contract_version":1,"transaction":${creditCommandJson.encodeToString(transactionCommand)}}""",
        )

        val commitResult = runCatching {
            database.withTransaction {
                val priorReceipt = database.movementDao().getReceipt(userId, operationId.value)
                if (priorReceipt != null) {
                    require(priorReceipt.requestHash == payloadHash) {
                        "Idempotency conflict: operation identity was reused with a different payment"
                    }
                    require(priorReceipt.status == "APPLIED") { "This payment was rejected; submit a new operation after reviewing the error" }
                    return@withTransaction
                }
                val priorCommand = syncDao.getByOperationId(userId, operationId.value)
                if (priorCommand != null) {
                    require(priorCommand.payloadHash == payloadHash) {
                        "Idempotency conflict: operation identity was reused with a different payment"
                    }
                    require(priorCommand.state !in setOf("FAILED_PERMANENT", "CONFLICT")) {
                        "This payment was rejected; submit a new operation after reviewing the error"
                    }
                    return@withTransaction
                }

                val sourceAccount = accountDao.getById(userId, sourceAccountId.value)
                    ?: throw IllegalArgumentException("Source account not found: " + sourceAccountId.value)
                val creditCard = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Credit card not found: " + cardId.value)
                require(!sourceAccount.isArchived) { "Cannot pay from archived account" }
                require(sourceAccount.type == "BANK" || sourceAccount.type == "SAVINGS") {
                    "Only an active bank or savings account can pay a credit card"
                }
                require(creditCard.type == "CREDIT") { "Only credit cards accept debt payments" }
                val liabilityAccountId = requireNotNull(creditCard.accountId) {
                    "Credit card liability account is missing"
                }
                val liabilityAccount = accountDao.getById(userId, liabilityAccountId)
                    ?: throw IllegalArgumentException("Credit card liability account not found")
                require(liabilityAccount.type == "CREDIT_LIABILITY" && liabilityAccount.currency == creditCard.currency) {
                    "Credit card liability account is incompatible"
                }
                require(!creditCard.isArchived) { "Cannot pay archived credit card" }
                require(paymentAmount.minorUnits > 0L) { "Payment amount must be positive" }
                val lockedIds = lockedInstrumentIds(userId)
                require(cardId.value !in lockedIds) { "Card is locked by the Free plan selection" }
                require(sourceAccountId.value !in lockedIds) { "Source account is locked by the Free plan selection" }
                require(sourceAccount.currency == paymentAmount.currency.name) { "Source account currency mismatch" }
                require(creditCard.currency == paymentAmount.currency.name) { "Credit card currency mismatch" }

                val sourceBalance = movementDao.getAccountBalance(userId, sourceAccountId.value)
                require(sourceBalance >= paymentAmount.minorUnits) { "Insufficient funds in source account" }

                val creditDao = database.creditDao()
                val outstandingInstallments = creditDao.getOutstandingInstallmentsForCard(userId, cardId.value)
                val currentDebt = outstandingInstallments.fold(0L) { sum, item ->
                    Math.addExact(sum, item.outstandingMinor)
                }
                val ledgerDebt = creditDao.getLedgerOutstandingPrincipalForCard(userId, cardId.value)
                require(ledgerDebt >= 0L && ledgerDebt == currentDebt) {
                    "Credit liability ledger does not match the installment schedule"
                }
                require(currentDebt >= paymentAmount.minorUnits) { "Payment amount exceeds current card debt" }

                val command = CreditCommandRequestDto(
                    idempotencyKey = operationId.value,
                    requestHash = payloadHash,
                    transaction = transactionCommand,
                )
                val transaction = com.kipu.app.feature.movements.data.local.TransactionEntity(
                    id = transactionId,
                    userId = userId,
                    type = "TRANSFER",
                    amountMinor = paymentAmount.minorUnits,
                    currencyCode = paymentAmount.currency.name,
                    sourceAccountId = sourceAccountId.value,
                    occurredAt = effectiveAt.toEpochMilli(),
                    status = "ACTIVE",
                    syncStatus = "PENDING",
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                    cardId = cardId.value,
                    operationKind = "CARD_PAYMENT",
                    installmentCount = 1,
                )
                val movementDao = database.movementDao()
                movementDao.insertTransaction(transaction)
                movementDao.insertLedgerEntries(
                    listOf(
                        com.kipu.app.feature.movements.data.local.LedgerEntryEntity(
                            id = stableCreditTransactionId(userId, operationId.value, "cash-ledger"),
                            userId = userId,
                            transactionId = transactionId,
                            accountId = sourceAccountId.value,
                            role = "SOURCE",
                            signedAmountMinor = -paymentAmount.minorUnits,
                            currencyCode = paymentAmount.currency.name,
                            createdAt = nowMillis,
                        ),
                        com.kipu.app.feature.movements.data.local.LedgerEntryEntity(
                            id = stableCreditTransactionId(userId, operationId.value, "liability-ledger"),
                            userId = userId,
                            transactionId = transactionId,
                            accountId = liabilityAccountId,
                            role = "LIABILITY",
                            signedAmountMinor = paymentAmount.minorUnits,
                            currencyCode = paymentAmount.currency.name,
                            createdAt = nowMillis,
                        ),
                    ),
                )
                database.financialMovementDao().rebuildAccountProjection(
                    userId = userId,
                    accountId = sourceAccountId.value,
                    now = nowMillis,
                )
                database.financialMovementDao().rebuildAccountProjection(
                    userId = userId,
                    accountId = liabilityAccountId,
                    now = nowMillis,
                )

                val allocationPlan = CreditPaymentAllocator.allocate(
                    paymentAmount.minorUnits,
                    outstandingInstallments.map { item ->
                        DuePrincipalInstallment(
                            id = item.installment.id,
                            dueDateEpochDay = item.installment.dueDate,
                            purchaseOccurredAtMillis = item.purchaseOccurredAt,
                            installmentNumber = item.installment.installmentNumber,
                            outstandingPrincipalMinor = item.outstandingMinor,
                        )
                    },
                )
                val installmentsById = outstandingInstallments.associateBy { it.installment.id }
                val allocations = mutableListOf<com.kipu.app.feature.accounts.data.local.CreditPaymentAllocationEntity>()
                allocationPlan.forEach { allocation ->
                    val item = requireNotNull(installmentsById[allocation.installmentId])
                    allocations += com.kipu.app.feature.accounts.data.local.CreditPaymentAllocationEntity(
                        id = stableCreditTransactionId(
                            userId,
                            operationId.value,
                            "allocation:" + item.installment.id,
                        ),
                        userId = userId,
                        paymentTransactionId = transactionId,
                        installmentId = item.installment.id,
                        allocatedMinor = allocation.allocatedMinor,
                        createdAt = nowMillis,
                    )
                    creditDao.updateInstallmentStatus(
                        userId = userId,
                        installmentId = item.installment.id,
                        status = if (allocation.fullyPaid) "PAID" else "PARTIAL",
                        revision = item.installment.revision + 1L,
                        updatedAt = nowMillis,
                    )
                }
                creditDao.insertAllocations(allocations)

                movementDao.insertOrUpdateReceipt(
                    com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity(
                        userId = userId,
                        idempotencyKey = operationId.value,
                        requestHash = payloadHash,
                        transactionId = transactionId,
                        status = "APPLIED",
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )
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
                        payloadJson = creditCommandJson.encodeToString(command),
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    ),
                )
            }
        }
        if (commitResult.isSuccess) runCatching { syncScheduler.scheduleSync(userId) }
        return commitResult
    }

    override suspend fun confirmCreditPurchase(
        cardId: CardId,
        amount: Money,
        merchant: String,
        effectiveAt: Instant,
        installments: Int,
        operationId: OperationId,
        categoryId: String,
        merchantId: String?,
        note: String?,
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val nowMicros = System.currentTimeMillis() * 1000L
        val nowMillis = System.currentTimeMillis()
        val transactionId = stableCreditTransactionId(userId, operationId.value, "purchase")
        val transactionCommand = CreditTransactionCommandDto(
            id = transactionId,
            type = "EXPENSE",
            amountMinor = amount.minorUnits,
            currencyCode = amount.currency.name,
            categoryId = categoryId,
            merchantId = merchantId,
            merchantProvisionalText = merchant.trim().takeIf { merchantId == null },
            occurredAt = effectiveAt.toString(),
            note = note?.takeIf(String::isNotBlank),
            cardId = cardId.value,
            operationKind = "CARD_PURCHASE",
            installmentCount = installments,
        )
        // A local collision check over a typed JSON command. PostgreSQL independently
        // computes the authoritative receipt hash from its canonical JSONB value.
        val payloadHash = sha256(
            """{"contract_version":1,"transaction":${creditCommandJson.encodeToString(transactionCommand)}}""",
        )

        val commitResult = runCatching {
            database.withTransaction {
                val priorReceipt = database.movementDao().getReceipt(userId, operationId.value)
                if (priorReceipt != null) {
                    require(priorReceipt.requestHash == payloadHash) {
                        "Idempotency conflict: operation identity was reused with a different purchase"
                    }
                    require(priorReceipt.status == "APPLIED") { "This purchase was rejected; submit a new operation after reviewing the error" }
                    return@withTransaction
                }
                val priorCommand = syncDao.getByOperationId(userId, operationId.value)
                if (priorCommand != null) {
                    require(priorCommand.payloadHash == payloadHash) {
                        "Idempotency conflict: operation identity was reused with a different purchase"
                    }
                    require(priorCommand.state !in setOf("FAILED_PERMANENT", "CONFLICT")) {
                        "This purchase was rejected; submit a new operation after reviewing the error"
                    }
                    return@withTransaction
                }

                val card = cardDao.getById(userId, cardId.value)
                    ?: throw IllegalArgumentException("Card not found: " + cardId.value)
                require(card.type == "CREDIT") { "Only credit cards can record credit purchases" }
                val liabilityAccountId = requireNotNull(card.accountId) {
                    "Credit card liability account is missing"
                }
                val liabilityAccount = accountDao.getById(userId, liabilityAccountId)
                    ?: throw IllegalArgumentException("Credit card liability account not found")
                require(liabilityAccount.type == "CREDIT_LIABILITY" && liabilityAccount.currency == card.currency) {
                    "Credit card liability account is incompatible"
                }
                require(!card.isArchived) { "Cannot record purchase on archived card" }
                require(cardId.value !in lockedInstrumentIds(userId)) {
                    "Card is locked by the Free plan selection"
                }
                require(card.currency == amount.currency.name) { "Card currency mismatch" }
                require(amount.minorUnits > 0L) { "Purchase amount must be positive" }
                require(installments in 1..36) { "Installments must be between 1 and 36: $installments" }
                require(merchant.isNotBlank()) { "Merchant cannot be blank" }
                require(categoryId.isNotBlank()) { "A purchase category is required" }
                val category = database.categoryDao().getCategoryById(categoryId)
                    ?: throw IllegalArgumentException("Purchase category not found")
                require(category.isActive) { "Purchase category is inactive" }
                require(category.userId == null || category.userId == userId) {
                    "Purchase category belongs to another owner"
                }
                require(category.categoryType != "INCOME") { "Purchase category must classify an expense" }
                require(!isPlanLockedCategory(userId, category)) { "Purchase category is locked by the Free plan selection" }
                val creditDao = database.creditDao()
                val currentDebt = creditDao.getLedgerOutstandingPrincipalForCard(userId, cardId.value)
                val scheduledDebt = creditDao.getOutstandingInstallmentsForCard(userId, cardId.value)
                    .fold(0L) { sum, item -> Math.addExact(sum, item.outstandingMinor) }
                require(currentDebt == scheduledDebt) {
                    "Credit liability ledger does not match the installment schedule"
                }
                val debtAfterPurchase = Math.addExact(currentDebt, amount.minorUnits)
                val creditLimit = requireNotNull(card.creditLimitMinorUnits) {
                    "Credit card limit is not configured"
                }
                require(debtAfterPurchase <= creditLimit) {
                    "Purchase would exceed the available credit limit"
                }

                val peruDate = effectiveAt.atZone(ZoneId.of("America/Lima")).toLocalDate()
                val firstDueDate = CreditCalculations.calculateFirstInstallmentDueDate(
                    purchaseDate = peruDate,
                    preferredClosingDay = card.billingDay ?: 1,
                    preferredDueDay = card.dueDay ?: 1,
                )
                val principalSchedule = InstallmentCalculator.simulate(
                    cardId = cardId,
                    principal = amount,
                    installmentsCount = installments,
                    firstDueDate = firstDueDate,
                    dueDay = card.dueDay ?: 1,
                    teaBps = null,
                )

                val command = CreditCommandRequestDto(
                    idempotencyKey = operationId.value,
                    requestHash = payloadHash,
                    transaction = transactionCommand,
                )
                val transaction = com.kipu.app.feature.movements.data.local.TransactionEntity(
                    id = transactionId,
                    userId = userId,
                    type = "EXPENSE",
                    amountMinor = amount.minorUnits,
                    currencyCode = amount.currency.name,
                    categoryId = categoryId,
                    merchantId = merchantId,
                    merchantProvisionalText = merchant.trim().takeIf { merchantId == null },
                    occurredAt = effectiveAt.toEpochMilli(),
                    note = note?.takeIf(String::isNotBlank),
                    status = "ACTIVE",
                    syncStatus = "PENDING",
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                    cardId = cardId.value,
                    operationKind = "CARD_PURCHASE",
                    installmentCount = installments,
                )
                database.movementDao().insertTransaction(transaction)
                database.movementDao().insertLedgerEntries(
                    listOf(
                        com.kipu.app.feature.movements.data.local.LedgerEntryEntity(
                            id = stableCreditTransactionId(userId, operationId.value, "liability-ledger"),
                            userId = userId,
                            transactionId = transactionId,
                            accountId = liabilityAccountId,
                            role = "LIABILITY",
                            signedAmountMinor = -amount.minorUnits,
                            currencyCode = amount.currency.name,
                            createdAt = nowMillis,
                        ),
                    ),
                )
                database.financialMovementDao().rebuildAccountProjection(
                    userId = userId,
                    accountId = liabilityAccountId,
                    now = nowMillis,
                )
                database.creditDao().insertInstallments(
                    principalSchedule.schedule.map { installment ->
                        com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity(
                            id = stableCreditTransactionId(
                                userId,
                                operationId.value,
                                "installment:" + installment.installmentNumber,
                            ),
                            userId = userId,
                            transactionId = transactionId,
                            installmentNumber = installment.installmentNumber,
                            dueDate = installment.dueDate.toEpochDay(),
                            principalMinor = installment.principalPortion.minorUnits,
                            interestMinor = 0L,
                            status = "PENDING",
                            revision = 1L,
                            createdAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                    },
                )
                database.movementDao().insertOrUpdateReceipt(
                    com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity(
                        userId = userId,
                        idempotencyKey = operationId.value,
                        requestHash = payloadHash,
                        transactionId = transactionId,
                        status = "APPLIED",
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )

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
                        payloadJson = creditCommandJson.encodeToString(command),
                        payloadHash = payloadHash,
                        state = "PENDING",
                        createdAt = nowMicros,
                        updatedAt = nowMicros,
                    ),
                )
            }
        }
        if (commitResult.isSuccess) runCatching { syncScheduler.scheduleSync(userId) }
        return commitResult
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

    private fun AccountEntity.toDomain(isPlanLocked: Boolean = false): Account {
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
            isPlanLocked = isPlanLocked,
        )
    }

    private fun AccountEntity.isVisibleInDomain(): Boolean =
        runCatching { AccountType.valueOf(type) }.getOrNull()?.let { it != AccountType.CREDIT_LIABILITY } == true

    private fun CardEntity.toDomain(isPlanLocked: Boolean = false): Card {
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
                stylePresetId = stylePresetId,
                colorToken = color,
                iconToken = icon,
                isArchived = isArchived,
                isPlanLocked = isPlanLocked,
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
                personalTeaBps = personalTeaBps,
                liabilityAccountId = AccountId(requireNotNull(accountId) { "Credit card liability account is missing" }),
                preset = CardPreset.fromId(presetId),
                stylePresetId = stylePresetId,
                colorToken = color,
                iconToken = icon,
                isArchived = isArchived,
                isPlanLocked = isPlanLocked,
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
                    combine(flow, observePlanLockedInstrumentIds(access.userId)) { list, locked ->
                        list.filter { it.isVisibleInDomain() }.map { it.toDomain(it.id in locked) }
                    }
                }
                else -> flowOf(emptyList())
            }
        }.distinctUntilChanged()
    }

    override fun observeAccountById(accountId: AccountId): Flow<Account?> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> combine(
                    accountDao.observeById(access.userId, accountId.value),
                    observePlanLockedInstrumentIds(access.userId),
                ) { account, locked -> account?.takeIf { it.isVisibleInDomain() }?.toDomain(accountId.value in locked) }
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
                    combine(flow, observePlanLockedInstrumentIds(access.userId)) { list, locked ->
                        list.map { it.toDomain(it.id in locked) }
                    }
                }
                else -> flowOf(emptyList())
            }
        }.distinctUntilChanged()
    }

    override fun observeCardById(cardId: CardId): Flow<Card?> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> combine(
                    cardDao.observeById(access.userId, cardId.value),
                    observePlanLockedInstrumentIds(access.userId),
                ) { card, locked -> card?.toDomain(cardId.value in locked) }
                else -> flowOf(null)
            }
        }.distinctUntilChanged()
    }

    private fun observePlanLockedInstrumentIds(userId: String): Flow<Set<String>> = combine(
        accountDao.observeActive(userId),
        cardDao.observeActive(userId),
        database.planQuotaSelectionDao().observeSelectedResourceIds(userId, QuotaGroup.INSTRUMENTS.name),
        database.featureAccessCacheDao().observe(UUID.fromString(userId)),
    ) { accounts, cards, selected, cache ->
        val activeIds = accounts.filter { it.type !in NON_COMPUTABLE_ACCOUNT_TYPES }.map { it.id } + cards.map { it.id }
        val premiumVerified = cache != null && cache.effectiveTier == "PREMIUM" && cache.verifiedAt != null &&
            (cache.entitlementExpiresAt == null || cache.entitlementExpiresAt.isAfter(Instant.now()))
        quotaPolicy.evaluate(
            group = QuotaGroup.INSTRUMENTS,
            activeResourceIds = activeIds,
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        ).planLockedResourceIds
    }.distinctUntilChanged()

    private suspend fun lockedInstrumentIds(userId: String): Set<String> {
        val activeIds = accountDao.getActiveComputable(userId).map { it.id } + cardDao.getActive(userId).map { it.id }
        val selected = database.planQuotaSelectionDao()
            .getSelectedResourceIds(userId, QuotaGroup.INSTRUMENTS.name)
        val cache = database.featureAccessCacheDao().get(UUID.fromString(userId))
        val premiumVerified = cache != null && cache.effectiveTier == "PREMIUM" && cache.verifiedAt != null &&
            (cache.entitlementExpiresAt == null || cache.entitlementExpiresAt.isAfter(Instant.now()))
        return quotaPolicy.evaluate(
            group = QuotaGroup.INSTRUMENTS,
            activeResourceIds = activeIds,
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        ).planLockedResourceIds
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
        val premiumVerified = cache != null && cache.effectiveTier == "PREMIUM" && cache.verifiedAt != null &&
            (cache.entitlementExpiresAt == null || cache.entitlementExpiresAt.isAfter(Instant.now()))
        val quota = quotaPolicy.evaluate(
            group = QuotaGroup.CUSTOM_CATEGORIES,
            activeResourceIds = activeRoots.map { it.id },
            selectedResourceIds = selected,
            limits = FreePlanLimits(),
            premiumVerified = premiumVerified,
        )
        return root.id in quota.planLockedResourceIds
    }

    override fun observeSelectedFreeInstrumentIds(): Flow<Set<String>> =
        sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> database.planQuotaSelectionDao()
                    .observeSelectedResourceIds(access.userId, QuotaGroup.INSTRUMENTS.name)
                    .map { it.toSet() }
                else -> flowOf(emptySet())
            }
        }.distinctUntilChanged()

    override suspend fun saveSelectedFreeInstrumentIds(ids: Set<String>): Result<Unit> = runCatching {
        val userId = currentUserId() ?: error("No active owner session")
        require(ids.size <= FreePlanLimits().instruments) { "Puedes seleccionar hasta ${FreePlanLimits().instruments} instrumentos" }
        val accounts = accountDao.getActiveComputable(userId)
        val cards = cardDao.getActive(userId)
        val activeIds = accounts.map { it.id }.toSet() + cards.map { it.id }
        require(activeIds.containsAll(ids)) { "La selección contiene instrumentos inactivos o que no pertenecen a esta cuenta" }
        val resourceTypes = accounts.associate { it.id to "ACCOUNT" } + cards.associate { it.id to "CARD" }
        database.planQuotaSelectionDao().replaceSelection(
            userId = userId,
            featureKey = QuotaGroup.INSTRUMENTS.name,
            resourceType = "INSTRUMENT",
            resourceIds = ids,
            now = System.currentTimeMillis(),
            resourceTypesById = resourceTypes,
        )
        planQuotaSyncScheduler?.scheduleSync(userId)
    }

    override fun observeCardDebt(cardId: CardId): Flow<Money> {
        return sessionCoordinator.localAccess.flatMapLatest { access ->
            when (access) {
                is LocalAccess.Available -> {
                    combine(
                        cardDao.observeById(access.userId, cardId.value),
                            database.creditDao().observeLedgerOutstandingPrincipalForCard(access.userId, cardId.value),
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
                        val computableAccounts = accounts.count { it.type !in NON_COMPUTABLE_ACCOUNT_TYPES }
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
