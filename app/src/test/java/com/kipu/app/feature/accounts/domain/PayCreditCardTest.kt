package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.PayCreditCard
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayCreditCardTest {

    private lateinit var fakeRepository: FakeFinancialInstrumentsRepositoryWithPayment
    private lateinit var payCreditCard: PayCreditCard

    private val testUserId = UserId.generate()

    class FakeFinancialInstrumentsRepositoryWithPayment : FakeFinancialInstrumentsRepository() {
        var sourceBalance: Long = 1000_00L
        var cardDebt: Long = 400_00L
        val recordedPayments = mutableListOf<Triple<CardId, AccountId, Money>>()

        override suspend fun payCreditCard(
            cardId: CardId,
            sourceAccountId: AccountId,
            paymentAmount: Money,
            effectiveAt: Instant,
            operationId: OperationId
        ): Result<Unit> {
            if (paymentAmount.minorUnits > sourceBalance) {
                return Result.failure(IllegalArgumentException("Saldo insuficiente en cuenta origen"))
            }
            if (paymentAmount.minorUnits > cardDebt) {
                return Result.failure(IllegalArgumentException("El pago excede la deuda actual"))
            }
            sourceBalance -= paymentAmount.minorUnits
            cardDebt -= paymentAmount.minorUnits
            recordedPayments.add(Triple(cardId, sourceAccountId, paymentAmount))
            return Result.success(Unit)
        }
    }

    @Before
    fun setup() {
        fakeRepository = FakeFinancialInstrumentsRepositoryWithPayment()
        payCreditCard = PayCreditCard(fakeRepository)
    }

    @Test
    fun `paying credit card with valid amount reduces both balance and debt`() = runTest {
        val cardId = CardId.generate()
        val accountId = AccountId.generate()
        val amount = Money(250_00L, Currency.PEN)

        val result = payCreditCard(cardId, accountId, amount)
        assertTrue(result.isSuccess)

        assertEquals(750_00L, fakeRepository.sourceBalance)
        assertEquals(150_00L, fakeRepository.cardDebt)
        assertEquals(1, fakeRepository.recordedPayments.size)
    }

    @Test
    fun `paying more than card debt fails with IllegalArgumentException`() = runTest {
        val cardId = CardId.generate()
        val accountId = AccountId.generate()
        val amount = Money(500_00L, Currency.PEN) // Debt is only 400

        val result = payCreditCard(cardId, accountId, amount)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(400_00L, fakeRepository.cardDebt)
    }

    @Test
    fun `paying more than source account balance fails with IllegalArgumentException`() = runTest {
        fakeRepository.cardDebt = 2000_00L
        fakeRepository.sourceBalance = 500_00L

        val cardId = CardId.generate()
        val accountId = AccountId.generate()
        val amount = Money(800_00L, Currency.PEN) // Balance is only 500

        val result = payCreditCard(cardId, accountId, amount)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(500_00L, fakeRepository.sourceBalance)
    }

    @Test
    fun `paying with zero or negative amount fails`() = runTest {
        val cardId = CardId.generate()
        val accountId = AccountId.generate()

        val zeroResult = payCreditCard(cardId, accountId, Money(0L, Currency.PEN))
        assertTrue(zeroResult.isFailure)

        val negResult = payCreditCard(cardId, accountId, Money(-50_00L, Currency.PEN))
        assertTrue(negResult.isFailure)
    }
}
