package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveFinancialDashboardTest {

    private val testUserId = UserId.generate()

    @Test
    fun `dashboard totals include archived account balances while liquidAccounts list filters them out`() = runTest {
        val activeAccId = AccountId.generate()
        val archivedAccId = AccountId.generate()

        val activeAccount = Account(
            id = activeAccId,
            userId = testUserId,
            alias = "Ahorros Activa",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(100_00L, Currency.PEN),
            openedAt = java.time.Instant.now(),
            isArchived = false,
        )

        val archivedAccount = Account(
            id = archivedAccId,
            userId = testUserId,
            alias = "Ahorros Archivada",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(50_00L, Currency.PEN),
            openedAt = java.time.Instant.now(),
            isArchived = true,
        )

        val fakeRepo = object : FakeFinancialInstrumentsRepository() {
            override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> {
                val all = listOf(activeAccount, archivedAccount)
                return flowOf(if (activeOnly) all.filter { !it.isArchived } else all)
            }

            override fun observeAccountBalance(accountId: AccountId): Flow<Money> {
                return when (accountId) {
                    activeAccId -> flowOf(Money(100_00L, Currency.PEN))
                    archivedAccId -> flowOf(Money(50_00L, Currency.PEN))
                    else -> flowOf(Money(0L, Currency.PEN))
                }
            }
        }

        val useCase = ObserveFinancialDashboard(fakeRepo)
        val dashboard = useCase().first()

        // totalPen must include both active and archived balances: 100.00 + 50.00 = 150.00 PEN
        assertEquals(150_00L, dashboard.totalPen.minorUnits)
        // liquidAccounts must only show the active account
        assertEquals(1, dashboard.liquidAccounts.size)
        assertEquals(activeAccId, dashboard.liquidAccounts[0].account.id)
    }

    @Test
    fun `dashboard displays archived credit card if it has remaining debt but hides it if debt is zero`() = runTest {
        val cardWithDebtId = CardId.generate()
        val cardZeroDebtId = CardId.generate()

        val cardWithDebt = CreditCard(
            id = cardWithDebtId,
            userId = testUserId,
            alias = "Visa con deuda",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "1111",
            currency = Currency.PEN,
            creditLimitMinorUnits = 1000_00L,
            billingDay = 15,
            dueDay = 5,
            isArchived = true,
        )

        val cardZeroDebt = CreditCard(
            id = cardZeroDebtId,
            userId = testUserId,
            alias = "Visa sin deuda",
            issuer = "BBVA",
            network = CardNetwork.VISA,
            lastFourDigits = "2222",
            currency = Currency.PEN,
            creditLimitMinorUnits = 1000_00L,
            billingDay = 15,
            dueDay = 5,
            isArchived = true,
        )

        val fakeRepo = object : FakeFinancialInstrumentsRepository() {
            override fun observeCards(activeOnly: Boolean): Flow<List<Card>> {
                val all = listOf(cardWithDebt, cardZeroDebt)
                return flowOf(if (activeOnly) all.filter { !it.isArchived } else all)
            }

            override fun observeCardDebt(cardId: CardId): Flow<Money> {
                return when (cardId) {
                    cardWithDebtId -> flowOf(Money(200_00L, Currency.PEN))
                    cardZeroDebtId -> flowOf(Money(0L, Currency.PEN))
                    else -> flowOf(Money(0L, Currency.PEN))
                }
            }
        }

        val useCase = ObserveFinancialDashboard(fakeRepo)
        val dashboard = useCase().first()

        // Only the archived card with nonzero debt remains visible so the user can settle it
        assertEquals(1, dashboard.creditCards.size)
        assertEquals(cardWithDebtId, dashboard.creditCards[0].card.id)
        assertEquals(200_00L, dashboard.creditCards[0].debt.minorUnits)
    }
}
