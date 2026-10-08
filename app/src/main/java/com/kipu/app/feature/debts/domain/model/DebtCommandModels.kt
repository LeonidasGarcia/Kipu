package com.kipu.app.feature.debts.domain.model

import java.time.LocalDate

enum class DebtObligationType {
    PAYABLE,
    RECEIVABLE,
}

enum class DebtEventType {
    DISBURSEMENT,
    PAYMENT,
    ADJUSTMENT,
    FORGIVENESS,
}

enum class DebtLifecycleStatus {
    ACTIVE,
    SETTLED,
    CANCELLED,
}

enum class DebtOpeningMode {
    NEW_CASH_FLOW,
    HISTORICAL,
}

data class DebtPrincipalEvent(
    val eventType: DebtEventType,
    val amountMinor: Long,
    val principalDeltaMinor: Long? = null,
    val linkedTransactionVoided: Boolean = false,
)

data class DebtScheduleItem(
    val id: String,
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val principalMinor: Long,
    val status: String = "PENDING",
    val revision: Long = 1L,
)

data class DebtSchedulePlan(
    val debtId: String,
    val expectedRevision: Long,
    val installments: List<DebtScheduleItem>,
    val reminderLeadDays: Int?,
    val cancelSchedule: Boolean = false,
    val cashDeltaMinor: Long = 0L,
    val principalDeltaMinor: Long = 0L,
)

data class SetDebtScheduleCommand(
    val identity: DebtCommandIdentity,
    val debtId: String,
    val expectedRevision: Long,
    val installments: List<DebtScheduleItem>,
    val reminderLeadDays: Int?,
    val cancelSchedule: Boolean = false,
)

enum class CloseDebtAction { SETTLE, CANCEL, ADJUST, FORGIVE }

data class DebtClosureCommand(
    val identity: DebtCommandIdentity,
    val debtId: String,
    val expectedRevision: Long,
    val action: CloseDebtAction,
    val amountMinor: Long? = null,
    val principalDeltaMinor: Long? = null,
    val reason: String? = null,
)

data class DebtClosurePlan(
    val debtId: String,
    val status: DebtLifecycleStatus,
    val remainingPrincipalMinor: Long,
    val eventType: DebtEventType?,
    val eventAmountMinor: Long,
    val principalDeltaMinor: Long,
    val reason: String?,
    val cashDeltaMinor: Long = 0L,
)

data class DebtCommandIdentity(
    val operationId: String,
    val requestHash: String,
    val contractVersion: Int = CURRENT_CONTRACT_VERSION,
) {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(requestHash.matches(Regex("[0-9a-f]{64}"))) { "requestHash must be a lowercase SHA-256 digest" }
        require(contractVersion == CURRENT_CONTRACT_VERSION) { "Unsupported debt command contract version" }
    }

    companion object {
        const val CURRENT_CONTRACT_VERSION = 1
    }
}

sealed interface DebtCommandResult {
    data class Applied(val debtId: String, val revision: Long, val remainingMinor: Long) : DebtCommandResult
    data class Duplicate(val debtId: String, val revision: Long, val remainingMinor: Long) : DebtCommandResult
    data class Conflict(val currentRevision: Long?) : DebtCommandResult
    data class Rejected(val code: String) : DebtCommandResult
    data class Retryable(val code: String) : DebtCommandResult
}

data class OpenDebtCommand(
    val identity: DebtCommandIdentity,
    val debtId: String,
    val obligationType: DebtObligationType,
    val counterpartyName: String,
    val principalMinor: Long,
    val currencyCode: String,
    val openedOn: LocalDate,
    val openingMode: DebtOpeningMode,
    val accountId: String? = null,
    val dueDate: LocalDate? = null,
    val reminderLeadDays: Int? = null,
    val notes: String? = null,
)

data class SettleDebtCommand(
    val identity: DebtCommandIdentity,
    val debtId: String,
    val expectedRevision: Long,
    val accountId: String,
    val principalMinor: Long,
    val interestMinor: Long = 0L,
    val interestCategoryId: String? = null,
    val installmentId: String? = null,
    val occurredAt: Long,
    val notes: String? = null,
)

sealed interface DebtPlanResult<out T> {
    data class Valid<T>(val value: T) : DebtPlanResult<T>
    data class Invalid(val code: String) : DebtPlanResult<Nothing>
}

data class PayableOpeningPlan(
    val debtId: String,
    val principalMinor: Long,
    val currencyCode: String,
    val accountId: String?,
    val cashDeltaMinor: Long,
    val movementType: String,
    val operationKind: String,
    val counterpartyName: String,
)

data class ReceivableOpeningPlan(
    val debtId: String,
    val principalMinor: Long,
    val currencyCode: String,
    val accountId: String?,
    val cashDeltaMinor: Long,
    val movementType: String,
    val operationKind: String,
    val counterpartyName: String,
)

data class DebtSettlementPlan(
    val debtId: String,
    val eventId: String,
    val expectedRevision: Long,
    val principalMinor: Long,
    val interestMinor: Long,
    val currencyCode: String,
    val accountId: String,
    val installmentId: String?,
    val occurredAt: Long,
    val cashDeltaMinor: Long,
    val principalDeltaMinor: Long,
    val remainingPrincipalMinor: Long,
    val operatingIncomeMinor: Long,
    val operatingExpenseMinor: Long,
    val principalMovementType: String,
    val principalOperationKind: String = "DEBT_PAYMENT",
    val interestMovementType: String?,
    val interestOperationKind: String? = "DEBT_AMORTIZATION",
    val principalTransactionId: String,
    val interestTransactionId: String?,
) {
    val groupedTransactionIds: List<String>
        get() = listOfNotNull(principalTransactionId, interestTransactionId)
}

data class DebtSummary(
    val debtId: String,
    val userId: String,
    val obligationType: DebtObligationType,
    val counterpartyName: String,
    val principalMinor: Long,
    val remainingPrincipalMinor: Long,
    val currencyCode: String,
    val openedOn: LocalDate,
    val dueDate: LocalDate?,
    val reminderLeadDays: Int?,
    val notes: String?,
    val status: DebtLifecycleStatus,
    val syncState: String,
    val revision: Long,
    val openingMode: DebtOpeningMode = DebtOpeningMode.HISTORICAL,
)

data class DebtDescriptionState(
    val debtId: String,
    val counterpartyName: String,
    val principalMinor: Long,
    val currencyCode: String,
    val openedOn: LocalDate,
    val dueDate: LocalDate?,
    val notes: String?,
    val status: DebtLifecycleStatus,
    val revision: Long,
)

data class DebtDescriptionPatch(
    val counterpartyName: String,
    val dueDate: LocalDate?,
    val notes: String?,
    val expectedRevision: Long,
)

sealed interface DebtDetailsEditResult {
    data class Applied(val state: DebtDescriptionState) : DebtDetailsEditResult
    data class Conflict(val currentRevision: Long) : DebtDetailsEditResult
    data class Rejected(val code: String) : DebtDetailsEditResult
}

sealed interface DebtDeleteResult {
    data class Deleted(val debtId: String) : DebtDeleteResult
    data class Rejected(val code: String) : DebtDeleteResult
    data class Conflict(val currentRevision: Long?) : DebtDeleteResult
}
