package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money

data class AccountWithBalance(
    val account: Account,
    val balance: Money,
    val linkedDebitCards: List<DebitCard> = emptyList(),
)

data class CreditCardWithSummary(
    val card: CreditCard,
    val debt: Money,
    val availableCredit: Money,
    val utilizationPercentage: Double,
)

data class FinancialDashboardData(
    val totalPen: Money,
    val totalUsd: Money,
    val liquidAccounts: List<AccountWithBalance>,
    val creditCards: List<CreditCardWithSummary>,
    val activeComputableCount: Int,
    val maxFreeQuota: Int = 4,
)
