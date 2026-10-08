package com.kipu.app.feature.debts.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDescriptionState
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtSettlementPlan
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import com.kipu.app.feature.debts.domain.model.SetDebtScheduleCommand
import com.kipu.app.feature.debts.domain.DebtDescriptionEditor
import com.kipu.app.feature.debts.domain.usecase.CloseDebt
import com.kipu.app.feature.debts.domain.usecase.SettleDebt
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.TransactionEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.first

@Singleton
class DebtLocalDataSource @Inject constructor(
    private val database: KipuDatabase,
    private val debtDao: DebtDao,
    private val debtOutboxDao: DebtOutboxDao,
    private val movementDao: MovementDao,
    private val accountDao: AccountDao,
    private val financialMovementDao: FinancialMovementDao,
    private val categoryDao: CategoryDao,
    private val settleDebt: SettleDebt = SettleDebt(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun openDebt(
        userId: String,
        command: OpenDebtCommand,
        hasPremiumAccess: Boolean,
    ): DebtCommandResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(command.debtId) || !isUuid(command.identity.operationId)) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_COMMAND_ID")
        }
        if (command.principalMinor <= 0L || command.currencyCode !in setOf("PEN", "USD")) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_AMOUNT_OR_CURRENCY")
        }
        if (command.reminderLeadDays != null && command.reminderLeadDays !in 0..365) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_REMINDER_LEAD_DAYS")
        }
        val normalizedName = command.counterpartyName.trim().replace(Regex("\\s+"), " ")
        if (normalizedName.isBlank()) return@withTransaction DebtCommandResult.Rejected("COUNTERPARTY_REQUIRED")

        val priorReceipt = movementDao.getReceipt(userId, command.identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != OPEN_DEBT_COMMAND || priorReceipt.requestHash != command.identity.requestHash) {
                return@withTransaction DebtCommandResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            val debt = debtDao.getDebt(userId, command.debtId)
                ?: return@withTransaction DebtCommandResult.Rejected("LOCAL_RECEIPT_WITHOUT_DEBT")
            return@withTransaction DebtCommandResult.Duplicate(debt.id, debt.revision, debt.remainingPrincipal(debtDao))
        }

        if (debtDao.getDebtIncludingDeleted(userId, command.debtId) != null) {
            return@withTransaction DebtCommandResult.Conflict(null)
        }
        if (!hasPremiumAccess && debtDao.countActiveDebts(userId) >= FREE_ACTIVE_DEBT_LIMIT) {
            return@withTransaction DebtCommandResult.Rejected("FREE_DEBT_QUOTA_EXCEEDED")
        }

        val needsCashMovement = command.openingMode == DebtOpeningMode.NEW_CASH_FLOW
        val account = if (needsCashMovement) {
            val id = command.accountId
                ?: return@withTransaction DebtCommandResult.Rejected("ACCOUNT_REQUIRED")
            val value = accountDao.getById(userId, id)
                ?: return@withTransaction DebtCommandResult.Rejected("ACCOUNT_NOT_FOUND")
            if (value.isArchived || value.currency != command.currencyCode) {
                return@withTransaction DebtCommandResult.Rejected("ACCOUNT_NOT_ELIGIBLE")
            }
            value
        } else null

        val now = clock.millis()
        val occurredAt = command.openedOn.atStartOfDay(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
        val transactionId = command.identity.operationId.takeIf { needsCashMovement }
        val debt = DebtEntity(
            id = command.debtId,
            userId = userId,
            obligationType = command.obligationType.name,
            counterpartyName = normalizedName,
            totalMinor = command.principalMinor,
            currencyCode = command.currencyCode,
            openedOn = command.openedOn.toString(),
            openingMode = command.openingMode.name,
            dueDate = command.dueDate?.toString(),
            reminderLeadDays = command.reminderLeadDays,
            notes = command.notes?.trim()?.takeIf(String::isNotEmpty),
            status = DebtLifecycleStatus.ACTIVE.name,
            revision = 1L,
            createdAt = now,
            updatedAt = now,
        )
        val event = DebtEventEntity(
            id = stableEventId(command.identity.operationId),
            userId = userId,
            debtId = debt.id,
            transactionId = transactionId,
            eventType = DebtEventType.DISBURSEMENT.name,
            amountMinor = command.principalMinor,
            principalDeltaMinor = 0L,
            occurredAt = occurredAt,
            createdAt = now,
        )

        debtDao.insertDebt(debt)

        if (account != null && transactionId != null) {
            val isPayable = command.obligationType == DebtObligationType.PAYABLE
            val signedAmount = if (isPayable) command.principalMinor else -command.principalMinor
            val transaction = TransactionEntity(
                id = transactionId,
                userId = userId,
                type = if (isPayable) "INCOME" else "EXPENSE",
                amountMinor = command.principalMinor,
                currencyCode = command.currencyCode,
                sourceAccountId = account.id,
                legacyKind = DEBT_DISBURSEMENT_KIND,
                occurredAt = occurredAt,
                note = if (isPayable) "Préstamo recibido: $normalizedName" else "Préstamo entregado: $normalizedName",
                status = "ACTIVE",
                syncStatus = "PENDING",
                createdAt = now,
                updatedAt = now,
                operationKind = DEBT_DISBURSEMENT_KIND,
            )
            movementDao.insertTransaction(transaction)
            movementDao.insertLedgerEntries(
                listOf(
                    LedgerEntryEntity(
                        id = transactionId,
                        userId = userId,
                        transactionId = transactionId,
                        accountId = account.id,
                        role = if (isPayable) "DESTINATION" else "SOURCE",
                        signedAmountMinor = signedAmount,
                        currencyCode = command.currencyCode,
                        createdAt = now,
                    ),
                ),
            )
            financialMovementDao.insertRaw(
                FinancialMovementEntity(
                    id = transactionId,
                    operationId = command.identity.operationId,
                    operationSequence = 0,
                    userId = userId,
                    kind = DEBT_DISBURSEMENT_KIND,
                    amountMinorUnits = signedAmount,
                    currency = command.currencyCode,
                    accountId = account.id,
                    effectiveAt = Math.multiplyExact(occurredAt, 1_000L),
                    status = "POSTED",
                    createdAt = Math.multiplyExact(now, 1_000L),
                ),
            )
            financialMovementDao.rebuildAccountProjection(userId, account.id, now)
        }
        debtDao.insertEvent(event)

        val payload = openPayload(command, normalizedName).toString()
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = command.identity.operationId,
                debtId = command.debtId,
                commandType = OPEN_DEBT_COMMAND,
                requestHash = command.identity.requestHash,
                payload = payload,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val result = DebtCommandResult.Applied(command.debtId, debt.revision, command.principalMinor)
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.identity.operationId,
                requestHash = command.identity.requestHash,
                transactionId = transactionId,
                status = "PENDING",
                responsePayload = result.toReceiptJson(),
                contractVersion = command.identity.contractVersion,
                commandType = OPEN_DEBT_COMMAND,
                resultingRevision = debt.revision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        result
    }

    suspend fun settleDebt(userId: String, command: SettleDebtCommand): DebtCommandResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(command.debtId) || !isUuid(command.identity.operationId)) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_SETTLEMENT_COMMAND")
        }
        val priorReceipt = movementDao.getReceipt(userId, command.identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != SETTLE_DEBT_COMMAND || priorReceipt.requestHash != command.identity.requestHash) {
                return@withTransaction DebtCommandResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            val current = debtDao.observeSummary(userId, command.debtId).first()
                ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
            return@withTransaction DebtCommandResult.Duplicate(current.debtId, current.revision, current.remainingPrincipalMinor)
        }

        val current = debtDao.observeSummary(userId, command.debtId).first()
            ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
        val account = accountDao.getById(userId, command.accountId)
            ?: return@withTransaction DebtCommandResult.Rejected("ACCOUNT_NOT_FOUND")
        if (account.isArchived) return@withTransaction DebtCommandResult.Rejected("ACCOUNT_NOT_ELIGIBLE")
        val category = command.interestCategoryId?.let { categoryDao.getCategoryById(it) }
        val categoryEligible = category != null &&
            (category.userId == null || category.userId == userId) && category.isActive &&
            category.categoryType in if (current.obligationType == DebtObligationType.PAYABLE.name) {
                setOf("GENERAL", "EXPENSE")
            } else {
                setOf("GENERAL", "INCOME")
            }
        val installmentRemaining = command.installmentId?.let { installmentId ->
            val installment = debtDao.getInstallment(userId, command.debtId, installmentId)
                ?: return@withTransaction DebtCommandResult.Rejected("INSTALLMENT_NOT_FOUND")
            var paid = 0L
            for (event in debtDao.getEvents(userId, command.debtId)) {
                if (event.installmentId != installmentId || event.eventType != DebtEventType.PAYMENT.name) continue
                val isActive = event.transactionId?.let { movementDao.getTransactionById(userId, it)?.status != "VOIDED" } ?: true
                if (isActive) paid = Math.addExact(paid, event.principalDeltaMinor?.let { -it } ?: event.amountMinor)
            }
            (installment.amountMinor - paid).coerceAtLeast(0L)
        }
        val plan = when (val planned = settleDebt.plan(
            debt = current.toDomain(),
            command = command,
            accountCurrencyCode = account.currency,
            isInterestCategoryEligible = categoryEligible,
            installmentPrincipalRemainingMinor = installmentRemaining,
        )) {
            is DebtPlanResult.Valid -> planned.value
            is DebtPlanResult.Invalid -> return@withTransaction when (planned.code) {
                "DEBT_REVISION_CONFLICT" -> DebtCommandResult.Conflict(current.revision)
                else -> DebtCommandResult.Rejected(planned.code)
            }
        }

        val now = clock.millis()
        val payable = current.obligationType == DebtObligationType.PAYABLE.name
        val principalNote = command.notes?.trim()?.takeIf(String::isNotEmpty)
            ?: if (payable) "Pago de deuda: ${current.counterpartyName}" else "Cobro de deuda: ${current.counterpartyName}"
        val principal = TransactionEntity(
            id = plan.principalTransactionId,
            userId = userId,
            type = plan.principalMovementType,
            amountMinor = plan.principalMinor,
            currencyCode = plan.currencyCode,
            sourceAccountId = plan.accountId,
            categoryId = null,
            legacyKind = plan.principalOperationKind,
            occurredAt = plan.occurredAt,
            note = principalNote,
            status = "ACTIVE",
            syncStatus = "PENDING",
            operationKind = plan.principalOperationKind,
            revision = 1L,
            createdAt = now,
            updatedAt = now,
        )
        movementDao.insertTransaction(principal)
        val ledgerEntries = mutableListOf(
            LedgerEntryEntity(
                id = principal.id,
                userId = userId,
                transactionId = principal.id,
                accountId = plan.accountId,
                role = if (payable) "SOURCE" else "DESTINATION",
                signedAmountMinor = if (payable) -plan.principalMinor else plan.principalMinor,
                currencyCode = plan.currencyCode,
                createdAt = now,
            ),
        )
        val financialMovements = mutableListOf(
            FinancialMovementEntity(
                id = principal.id,
                operationId = command.identity.operationId,
                operationSequence = 0,
                userId = userId,
                kind = plan.principalOperationKind,
                amountMinorUnits = if (payable) -plan.principalMinor else plan.principalMinor,
                currency = plan.currencyCode,
                accountId = plan.accountId,
                effectiveAt = Math.multiplyExact(plan.occurredAt, 1_000L),
                status = "POSTED",
                createdAt = Math.multiplyExact(now, 1_000L),
            ),
        )
        plan.interestTransactionId?.let { interestId ->
            val signedInterest = if (payable) -plan.interestMinor else plan.interestMinor
            val interest = TransactionEntity(
                id = interestId,
                userId = userId,
                type = requireNotNull(plan.interestMovementType),
                amountMinor = plan.interestMinor,
                currencyCode = plan.currencyCode,
                sourceAccountId = plan.accountId,
                categoryId = command.interestCategoryId,
                legacyKind = requireNotNull(plan.interestOperationKind),
                occurredAt = plan.occurredAt,
                note = command.notes?.trim()?.takeIf(String::isNotEmpty) ?: "Interés de deuda: ${current.counterpartyName}",
                status = "ACTIVE",
                syncStatus = "PENDING",
                operationKind = requireNotNull(plan.interestOperationKind),
                revision = 1L,
                createdAt = now,
                updatedAt = now,
            )
            movementDao.insertTransaction(interest)
            ledgerEntries += LedgerEntryEntity(
                id = interest.id,
                userId = userId,
                transactionId = interest.id,
                accountId = plan.accountId,
                role = if (payable) "SOURCE" else "DESTINATION",
                signedAmountMinor = signedInterest,
                currencyCode = plan.currencyCode,
                createdAt = now,
            )
            financialMovements += FinancialMovementEntity(
                id = interest.id,
                operationId = command.identity.operationId,
                operationSequence = 1,
                userId = userId,
                kind = requireNotNull(plan.interestOperationKind),
                amountMinorUnits = signedInterest,
                currency = plan.currencyCode,
                accountId = plan.accountId,
                effectiveAt = Math.multiplyExact(plan.occurredAt, 1_000L),
                status = "POSTED",
                categoryId = command.interestCategoryId,
                createdAt = Math.multiplyExact(now, 1_000L),
            )
        }
        movementDao.insertLedgerEntries(ledgerEntries)
        for (movement in financialMovements) financialMovementDao.insertRaw(movement)
        financialMovementDao.rebuildAccountProjection(userId, plan.accountId, now)

        val event = DebtEventEntity(
            id = plan.eventId,
            userId = userId,
            debtId = plan.debtId,
            transactionId = plan.principalTransactionId,
            interestTransactionId = plan.interestTransactionId,
            installmentId = plan.installmentId,
            eventType = DebtEventType.PAYMENT.name,
            amountMinor = plan.principalMinor,
            principalDeltaMinor = plan.principalDeltaMinor,
            occurredAt = plan.occurredAt,
            createdAt = now,
        )
        debtDao.insertEvent(event)
        val nextStatus = if (plan.remainingPrincipalMinor == 0L) DebtLifecycleStatus.SETTLED.name else DebtLifecycleStatus.ACTIVE.name
        val nextRevision = Math.addExact(current.revision, 1L)
        if (debtDao.updateDebt(
                debtDao.getDebt(userId, plan.debtId)!!.copy(
                    status = nextStatus,
                    revision = nextRevision,
                    syncState = "PENDING",
                    updatedAt = now,
                ),
            ) != 1
        ) return@withTransaction DebtCommandResult.Conflict(current.revision)
        plan.installmentId?.let { installmentId ->
            val installment = debtDao.getInstallment(userId, plan.debtId, installmentId)
                ?: return@withTransaction DebtCommandResult.Rejected("INSTALLMENT_NOT_FOUND")
            val paidAfter = installment.amountMinor - requireNotNull(installmentRemaining) + plan.principalMinor
            debtDao.upsertInstallment(
                installment.copy(
                    status = when {
                        paidAfter >= installment.amountMinor -> "PAID"
                        paidAfter > 0L -> "PARTIAL"
                        else -> "PLANNED"
                    },
                    revision = installment.revision + 1,
                    updatedAt = now,
                ),
            )
        }

        val payload = settlementPayload(command, plan)
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = command.identity.operationId,
                debtId = command.debtId,
                commandType = SETTLE_DEBT_COMMAND,
                requestHash = command.identity.requestHash,
                payload = payload.toString(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        val result = DebtCommandResult.Applied(plan.debtId, nextRevision, plan.remainingPrincipalMinor)
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.identity.operationId,
                requestHash = command.identity.requestHash,
                transactionId = plan.principalTransactionId,
                status = "PENDING",
                responsePayload = "{\"status\":\"APPLIED\",\"debt_id\":\"${plan.debtId}\",\"revision\":$nextRevision,\"remaining_minor\":${plan.remainingPrincipalMinor}}",
                contractVersion = command.identity.contractVersion,
                commandType = SETTLE_DEBT_COMMAND,
                expectedRevision = command.expectedRevision,
                resultingRevision = nextRevision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        result
    }

    suspend fun setDebtSchedule(userId: String, command: SetDebtScheduleCommand): DebtCommandResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(command.debtId) || !isUuid(command.identity.operationId)) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_DEBT_SCHEDULE_COMMAND")
        }
        val priorReceipt = movementDao.getReceipt(userId, command.identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != SET_DEBT_SCHEDULE_COMMAND || priorReceipt.requestHash != command.identity.requestHash) {
                return@withTransaction DebtCommandResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            val priorDebt = debtDao.observeSummary(userId, command.debtId).first()
                ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
            return@withTransaction DebtCommandResult.Duplicate(command.debtId, priorDebt.revision, priorDebt.remainingPrincipalMinor)
        }
        val current = debtDao.observeSummary(userId, command.debtId).first()
            ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
        if (current.revision != command.expectedRevision) {
            return@withTransaction DebtCommandResult.Conflict(current.revision)
        }
        if (current.status != DebtLifecycleStatus.ACTIVE.name) return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_ACTIVE")
        if (command.reminderLeadDays != null && command.reminderLeadDays !in 0..365) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_REMINDER_LEAD_DAYS")
        }
        if (command.cancelSchedule) {
            if (command.installments.isNotEmpty() || command.reminderLeadDays != null) {
                return@withTransaction DebtCommandResult.Rejected("INVALID_SCHEDULE_CANCELLATION")
            }
        } else {
            val installments = command.installments
            if (installments.isEmpty() || installments.size > 120 || current.remainingPrincipalMinor <= 0L) {
                return@withTransaction DebtCommandResult.Rejected("INVALID_INSTALLMENT_COUNT")
            }
            val sorted = installments.sortedBy(DebtScheduleItem::installmentNumber)
            val maxExistingNumber = debtDao.getInstallments(userId, command.debtId).maxOfOrNull { it.installmentNumber } ?: 0
            if (sorted.any { !isUuid(it.id) || it.principalMinor <= 0L || it.installmentNumber <= maxExistingNumber } ||
                sorted.map { it.id }.distinct().size != sorted.size ||
                sorted.map { it.installmentNumber }.distinct().size != sorted.size ||
                sorted.zipWithNext().any { (first, second) -> second.installmentNumber != first.installmentNumber + 1 }
            ) return@withTransaction DebtCommandResult.Rejected("INVALID_INSTALLMENT_SEQUENCE")
            val firstNumber = sorted.first().installmentNumber
            if (firstNumber <= 0 || firstNumber > Short.MAX_VALUE - sorted.size + 1) {
                return@withTransaction DebtCommandResult.Rejected("INVALID_INSTALLMENT_SEQUENCE")
            }
            val firstDate = sorted.first().dueDate
            if (sorted.withIndex().any { (index, installment) -> installment.dueDate != firstDate.plusMonths(index.toLong()) }) {
                return@withTransaction DebtCommandResult.Rejected("INVALID_INSTALLMENT_DATES")
            }
            val total = runCatching { sorted.fold(0L) { sum, item -> Math.addExact(sum, item.principalMinor) } }.getOrNull()
                ?: return@withTransaction DebtCommandResult.Rejected("INSTALLMENT_AMOUNT_OVERFLOW")
            if (total != current.remainingPrincipalMinor) {
                return@withTransaction DebtCommandResult.Rejected("SCHEDULE_TOTAL_MISMATCH")
            }
            if (sorted.any { debtDao.getInstallmentById(userId, it.id) != null }) {
                return@withTransaction DebtCommandResult.Rejected("INSTALLMENT_ID_REUSED")
            }
        }

        val now = clock.millis()
        val oldInstallments = debtDao.getInstallments(userId, command.debtId)
            .filter { it.status == "PENDING" || it.status == "PARTIAL" }
        if (oldInstallments.isNotEmpty()) {
            debtDao.updateInstallments(oldInstallments.map { it.copy(status = "CANCELLED", revision = it.revision + 1, updatedAt = now) })
        }
        if (!command.cancelSchedule) {
            debtDao.insertInstallments(command.installments.map { item ->
                DebtInstallmentEntity(
                    id = item.id,
                    userId = userId,
                    debtId = command.debtId,
                    installmentNumber = item.installmentNumber,
                    dueDate = item.dueDate.toString(),
                    amountMinor = item.principalMinor,
                    status = "PENDING",
                    revision = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            })
        }
        val debt = debtDao.getDebt(userId, command.debtId)
            ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
        val updated = debt.copy(
            reminderLeadDays = command.reminderLeadDays,
            revision = current.revision + 1,
            syncState = "PENDING",
            updatedAt = now,
        )
        debtDao.updateDebt(updated)
        val payload = schedulePayload(command)
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = command.identity.operationId,
                debtId = command.debtId,
                commandType = SET_DEBT_SCHEDULE_COMMAND,
                requestHash = command.identity.requestHash,
                payload = payload.toString(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.identity.operationId,
                requestHash = command.identity.requestHash,
                status = "PENDING",
                responsePayload = "{\"status\":\"APPLIED\",\"debt_id\":\"${command.debtId}\",\"revision\":${updated.revision},\"remaining_minor\":${current.remainingPrincipalMinor}}",
                contractVersion = command.identity.contractVersion,
                commandType = SET_DEBT_SCHEDULE_COMMAND,
                expectedRevision = command.expectedRevision,
                resultingRevision = updated.revision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        DebtCommandResult.Applied(command.debtId, updated.revision, current.remainingPrincipalMinor)
    }

    suspend fun closeDebt(userId: String, command: DebtClosureCommand): DebtCommandResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(command.debtId) || !isUuid(command.identity.operationId)) {
            return@withTransaction DebtCommandResult.Rejected("INVALID_DEBT_CLOSURE_COMMAND")
        }
        val priorReceipt = movementDao.getReceipt(userId, command.identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != CLOSE_DEBT_COMMAND || priorReceipt.requestHash != command.identity.requestHash) {
                return@withTransaction DebtCommandResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            val priorDebt = debtDao.observeSummary(userId, command.debtId).first()
                ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
            return@withTransaction DebtCommandResult.Duplicate(command.debtId, priorDebt.revision, priorDebt.remainingPrincipalMinor)
        }
        val currentRow = debtDao.observeSummary(userId, command.debtId).first()
            ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
        if (currentRow.revision != command.expectedRevision) return@withTransaction DebtCommandResult.Conflict(currentRow.revision)
        val current = currentRow.toDomain()
        val outcome = CloseDebt().plan(current, command)
        if (outcome !is DebtPlanResult.Valid) return@withTransaction DebtCommandResult.Rejected((outcome as DebtPlanResult.Invalid).code)

        val now = clock.millis()
        val plan = outcome.value
        if (plan.eventType != null) {
            val delta = plan.principalDeltaMinor
            debtDao.insertEvent(
                DebtEventEntity(
                    id = stableClosureEventId(command.identity.operationId),
                    userId = userId,
                    debtId = command.debtId,
                    eventType = plan.eventType.name,
                    amountMinor = plan.eventAmountMinor,
                    principalDeltaMinor = delta,
                    occurredAt = now,
                    createdAt = now,
                ),
            )
        }
        if (command.action != CloseDebtAction.SETTLE || plan.status != DebtLifecycleStatus.ACTIVE) {
            val outstanding = debtDao.getInstallments(userId, command.debtId)
                .filter { it.status == "PENDING" || it.status == "PARTIAL" }
            if (outstanding.isNotEmpty()) {
                debtDao.updateInstallments(outstanding.map { it.copy(status = "CANCELLED", revision = it.revision + 1, updatedAt = now) })
            }
        }
        val debt = debtDao.getDebt(userId, command.debtId)
            ?: return@withTransaction DebtCommandResult.Rejected("DEBT_NOT_FOUND")
        val nextRevision = current.revision + 1
        if (debtDao.updateDebt(
                debt.copy(
                    status = plan.status.name,
                    reminderLeadDays = null,
                    revision = nextRevision,
                    syncState = "PENDING",
                    updatedAt = now,
                ),
            ) != 1
        ) return@withTransaction DebtCommandResult.Conflict(current.revision)

        val payload = closurePayload(command)
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = command.identity.operationId,
                debtId = command.debtId,
                commandType = CLOSE_DEBT_COMMAND,
                requestHash = command.identity.requestHash,
                payload = payload.toString(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.identity.operationId,
                requestHash = command.identity.requestHash,
                status = "PENDING",
                responsePayload = "{\"status\":\"APPLIED\",\"debt_id\":\"${command.debtId}\",\"revision\":$nextRevision,\"remaining_minor\":${plan.remainingPrincipalMinor}}",
                contractVersion = command.identity.contractVersion,
                commandType = CLOSE_DEBT_COMMAND,
                expectedRevision = command.expectedRevision,
                resultingRevision = nextRevision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        DebtCommandResult.Applied(command.debtId, nextRevision, plan.remainingPrincipalMinor)
    }

    suspend fun editDebtDetails(
        userId: String,
        debtId: String,
        patch: DebtDescriptionPatch,
        identity: DebtCommandIdentity,
    ): DebtDetailsEditResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(debtId) || !isUuid(identity.operationId)) {
            return@withTransaction DebtDetailsEditResult.Rejected("INVALID_DEBT_EDIT_COMMAND")
        }
        val priorReceipt = movementDao.getReceipt(userId, identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != EDIT_DEBT_COMMAND || priorReceipt.requestHash != identity.requestHash) {
                return@withTransaction DebtDetailsEditResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            val current = debtDao.getDebt(userId, debtId)
                ?: return@withTransaction DebtDetailsEditResult.Rejected("DEBT_NOT_FOUND")
            return@withTransaction DebtDetailsEditResult.Applied(current.toDescriptionState())
        }
        val current = debtDao.getDebt(userId, debtId)
            ?: return@withTransaction DebtDetailsEditResult.Rejected("DEBT_NOT_FOUND")
        val outcome = DebtDescriptionEditor.apply(current.toDescriptionState(), patch)
        if (outcome !is DebtDetailsEditResult.Applied) return@withTransaction outcome

        val now = clock.millis()
        val updated = current.copy(
            counterpartyName = outcome.state.counterpartyName,
            dueDate = outcome.state.dueDate?.toString(),
            notes = outcome.state.notes,
            revision = outcome.state.revision,
            syncState = "PENDING",
            updatedAt = now,
        )
        if (debtDao.updateDebt(updated) != 1) return@withTransaction DebtDetailsEditResult.Conflict(current.revision)
        val payload = buildJsonObject {
            put("operation_id", identity.operationId)
            put("request_hash", identity.requestHash)
            put("debt_id", debtId)
            put("expected_revision", patch.expectedRevision)
            put("counterparty_name", outcome.state.counterpartyName)
            put("due_date", outcome.state.dueDate?.toString() ?: "")
            put("reminder_lead_days", current.reminderLeadDays)
            put("notes", outcome.state.notes ?: "")
        }.toString()
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = identity.operationId,
                debtId = debtId,
                commandType = EDIT_DEBT_COMMAND,
                requestHash = identity.requestHash,
                payload = payload,
                createdAt = now,
                updatedAt = now,
            ),
        )
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = identity.operationId,
                requestHash = identity.requestHash,
                status = "PENDING",
                responsePayload = "{\"status\":\"APPLIED\",\"debt_id\":\"$debtId\",\"revision\":${updated.revision}}",
                contractVersion = identity.contractVersion,
                commandType = EDIT_DEBT_COMMAND,
                expectedRevision = patch.expectedRevision,
                resultingRevision = updated.revision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        outcome
    }

    suspend fun deleteDebtIfUnreferenced(
        userId: String,
        debtId: String,
        identity: DebtCommandIdentity,
    ): DebtDeleteResult = database.withTransaction {
        if (userId.isBlank() || !isUuid(debtId) || !isUuid(identity.operationId)) {
            return@withTransaction DebtDeleteResult.Rejected("INVALID_DEBT_DELETE_COMMAND")
        }
        val priorReceipt = movementDao.getReceipt(userId, identity.operationId)
        if (priorReceipt != null) {
            if (priorReceipt.commandType != DELETE_DEBT_COMMAND || priorReceipt.requestHash != identity.requestHash) {
                return@withTransaction DebtDeleteResult.Rejected("IDEMPOTENCY_KEY_REUSED")
            }
            return@withTransaction DebtDeleteResult.Deleted(debtId)
        }
        val debt = debtDao.getDebt(userId, debtId)
            ?: return@withTransaction DebtDeleteResult.Rejected("DEBT_NOT_FOUND")
        val events = debtDao.getEvents(userId, debtId)
        if (events.isNotEmpty()) {
            return@withTransaction DebtDeleteResult.Rejected("HISTORY_PRESERVED")
        }

        val now = clock.millis()
        debtDao.deleteUnreferencedInstallments(userId, debtId)
        if (debtDao.deleteDebtIfUnreferenced(userId, debtId) != 1) {
            return@withTransaction DebtDeleteResult.Conflict(debt.revision)
        }
        val payload = buildJsonObject {
            put("operation_id", identity.operationId)
            put("request_hash", identity.requestHash)
            put("debt_id", debtId)
        }.toString()
        debtOutboxDao.insert(
            DebtOutboxEntity(
                userId = userId,
                operationId = identity.operationId,
                debtId = debtId,
                commandType = DELETE_DEBT_COMMAND,
                requestHash = identity.requestHash,
                payload = payload,
                createdAt = now,
                updatedAt = now,
            ),
        )
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = identity.operationId,
                requestHash = identity.requestHash,
                status = "PENDING",
                responsePayload = "{\"status\":\"APPLIED\",\"debt_id\":\"$debtId\",\"result\":\"DELETED\"}",
                contractVersion = identity.contractVersion,
                commandType = DELETE_DEBT_COMMAND,
                expectedRevision = debt.revision,
                resultingRevision = debt.revision + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        DebtDeleteResult.Deleted(debtId)
    }

    private fun schedulePayload(command: SetDebtScheduleCommand) = buildJsonObject {
        put("contract_version", command.identity.contractVersion)
        put("operation_id", command.identity.operationId)
        put("request_hash", command.identity.requestHash)
        put("debt_id", command.debtId)
        put("expected_revision", command.expectedRevision)
        if (command.reminderLeadDays == null) put("reminder_lead_days", JsonNull)
        else put("reminder_lead_days", command.reminderLeadDays)
        put("cancel_schedule", command.cancelSchedule)
        put("installments", buildJsonArray {
            command.installments.forEach { item ->
                add(buildJsonObject {
                    put("id", item.id)
                    put("installment_number", item.installmentNumber)
                    put("due_date", item.dueDate.toString())
                    put("principal_minor", item.principalMinor)
                })
            }
        })
    }

    private fun closurePayload(command: DebtClosureCommand) = buildJsonObject {
        put("contract_version", command.identity.contractVersion)
        put("operation_id", command.identity.operationId)
        put("request_hash", command.identity.requestHash)
        put("debt_id", command.debtId)
        put("expected_revision", command.expectedRevision)
        put("action", command.action.name)
        command.amountMinor?.let { put("amount_minor", it) }
        command.principalDeltaMinor?.let { put("principal_delta_minor", it) }
        command.reason?.trim()?.takeIf(String::isNotEmpty)?.let { put("reason", it) }
    }

    private fun stableClosureEventId(operationId: String): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest("kipu:debt-closure-event:$operationId".toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return UUID.fromString(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}",
        ).toString()
    }

    private suspend fun DebtEntity.remainingPrincipal(dao: DebtDao): Long =
        com.kipu.app.feature.debts.domain.DebtPrincipalCalculator.remainingPrincipal(
            openingPrincipalMinor = totalMinor,
            events = dao.getEvents(userId, id).map { event ->
                com.kipu.app.feature.debts.domain.model.DebtPrincipalEvent(
                    eventType = com.kipu.app.feature.debts.domain.model.DebtEventType.valueOf(event.eventType),
                    amountMinor = event.amountMinor,
                    principalDeltaMinor = event.principalDeltaMinor,
                    linkedTransactionVoided = event.transactionId?.let { transactionId ->
                        movementDao.getTransactionById(userId, transactionId)?.status == "VOIDED"
                    } ?: false,
                )
            },
        )

    private fun DebtEntity.toDescriptionState() = DebtDescriptionState(
        debtId = id,
        counterpartyName = counterpartyName,
        principalMinor = totalMinor,
        currencyCode = currencyCode,
        openedOn = java.time.LocalDate.parse(openedOn),
        dueDate = dueDate?.let(java.time.LocalDate::parse),
        notes = notes,
        status = DebtLifecycleStatus.valueOf(status),
        revision = revision,
    )

    private fun openPayload(command: OpenDebtCommand, normalizedName: String) = buildJsonObject {
        put("contract_version", command.identity.contractVersion)
        put("operation_id", command.identity.operationId)
        put("event_id", stableEventId(command.identity.operationId))
        put("request_hash", command.identity.requestHash)
        put("debt_id", command.debtId)
        put("obligation_type", command.obligationType.name)
        put("counterparty_name", normalizedName)
        put("total_minor", command.principalMinor)
        put("currency_code", command.currencyCode)
        put("opened_on", command.openedOn.toString())
        put("opening_mode", command.openingMode.name)
        command.accountId?.let { put("account_id", it) }
        command.dueDate?.let { put("due_date", it.toString()) }
        command.reminderLeadDays?.let { put("reminder_lead_days", it) }
        command.notes?.trim()?.takeIf(String::isNotEmpty)?.let { put("notes", it) }
    }

    private fun settlementPayload(command: SettleDebtCommand, plan: DebtSettlementPlan) = buildJsonObject {
        put("contract_version", command.identity.contractVersion)
        put("operation_id", command.identity.operationId)
        put("request_hash", command.identity.requestHash)
        put("debt_id", command.debtId)
        put("expected_revision", command.expectedRevision)
        put("account_id", plan.accountId)
        put("principal_minor", plan.principalMinor)
        put("interest_minor", plan.interestMinor)
        command.interestCategoryId?.let { put("interest_category_id", it) }
        plan.installmentId?.let { put("installment_id", it) }
        put("occurred_at", Instant.ofEpochMilli(plan.occurredAt).toString())
        command.notes?.trim()?.takeIf(String::isNotEmpty)?.let { put("notes", it) }
    }

    private fun com.kipu.app.feature.debts.data.local.DebtSummaryRow.toDomain() = DebtSummary(
        debtId = debtId,
        userId = userId,
        obligationType = DebtObligationType.valueOf(obligationType),
        counterpartyName = counterpartyName,
        principalMinor = principalMinor,
        remainingPrincipalMinor = remainingPrincipalMinor,
        currencyCode = currencyCode,
        openedOn = java.time.LocalDate.parse(openedOn),
        dueDate = dueDate?.let(java.time.LocalDate::parse),
        reminderLeadDays = reminderLeadDays,
        notes = notes,
        status = DebtLifecycleStatus.valueOf(status),
        syncState = syncState,
        revision = revision,
        openingMode = DebtOpeningMode.valueOf(openingMode),
    )

    private fun DebtCommandResult.Applied.toReceiptJson(): String =
        Json.encodeToString(
            buildJsonObject {
                put("status", "APPLIED")
                put("debt_id", debtId)
                put("revision", revision)
                put("remaining_minor", remainingMinor)
            },
        )

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun stableEventId(operationId: String): String =
        UUID.nameUUIDFromBytes("$operationId:opening-event".toByteArray(Charsets.UTF_8)).toString()

    companion object {
        private const val FREE_ACTIVE_DEBT_LIMIT = 2
        private const val OPEN_DEBT_COMMAND = "OPEN_DEBT"
        private const val EDIT_DEBT_COMMAND = "EDIT_DEBT_DETAILS"
        private const val DELETE_DEBT_COMMAND = "DELETE_DEBT_IF_UNREFERENCED"
        private const val SETTLE_DEBT_COMMAND = "SETTLE_DEBT"
        private const val SET_DEBT_SCHEDULE_COMMAND = "SET_DEBT_SCHEDULE"
        private const val CLOSE_DEBT_COMMAND = "CLOSE_DEBT"
        private const val DEBT_DISBURSEMENT_KIND = "DEBT_DISBURSEMENT"
    }
}
