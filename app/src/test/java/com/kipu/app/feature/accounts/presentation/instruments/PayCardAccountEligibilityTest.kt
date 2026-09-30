package com.kipu.app.feature.accounts.presentation.instruments

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PayCardAccountEligibilityTest {
    @Test
    fun `credit card payment only offers active bank and savings accounts with funds`() {
        val candidates = listOf(
            accountWithBalance("cash", AccountType.CASH, 100_00L),
            accountWithBalance("bank", AccountType.BANK, 100_00L),
            accountWithBalance("savings", AccountType.SAVINGS, 50_00L),
            accountWithBalance("empty", AccountType.BANK, 0L),
            accountWithBalance("archived", AccountType.SAVINGS, 100_00L, archived = true),
            accountWithBalance("usd", AccountType.BANK, 100_00L, currency = Currency.USD),
        )

        assertEquals(
            listOf("bank", "savings"),
            eligibleCardPaymentAccounts(candidates, Currency.PEN).map { it.account.alias },
        )
    }

    private fun accountWithBalance(
        id: String,
        type: AccountType,
        balance: Long,
        archived: Boolean = false,
        currency: Currency = Currency.PEN,
    ) = AccountWithBalance(
        account = Account(
            id = AccountId(java.util.UUID.randomUUID().toString()),
            userId = UserId(java.util.UUID.randomUUID().toString()),
            alias = id,
            type = type,
            currency = currency,
            initialBalance = Money(balance, currency),
            openedAt = Instant.EPOCH,
            isArchived = archived,
        ),
        balance = Money(balance, currency),
    )
}
