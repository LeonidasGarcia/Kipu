package com.kipu.app.feature.debts.data.local

data class DebtSummaryRow(
    val debtId: String,
    val userId: String,
    val obligationType: String,
    val counterpartyName: String,
    val principalMinor: Long,
    val remainingPrincipalMinor: Long,
    val currencyCode: String,
    val openedOn: String,
    val openingMode: String,
    val dueDate: String?,
    val reminderLeadDays: Int?,
    val notes: String?,
    val status: String,
    val syncState: String,
    val revision: Long,
)
