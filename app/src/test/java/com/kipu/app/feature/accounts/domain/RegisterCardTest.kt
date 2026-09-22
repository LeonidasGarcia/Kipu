package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.domain.usecase.CheckCardDuplicate
import com.kipu.app.feature.accounts.domain.usecase.DeleteUnusedCard
import com.kipu.app.feature.accounts.domain.usecase.QuotaExceededException
import com.kipu.app.feature.accounts.domain.usecase.RegisterCreditCard
import com.kipu.app.feature.accounts.domain.usecase.RegisterDebitCard
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterCardTest {

    private lateinit var fakeRepository: FakeFinancialInstrumentsRepository
    private lateinit var registerDebitCard: RegisterDebitCard
    private lateinit var registerCreditCard: RegisterCreditCard
    private lateinit var checkCardDuplicate: CheckCardDuplicate
    private lateinit var deleteUnusedCard: DeleteUnusedCard

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        fakeRepository = FakeFinancialInstrumentsRepository()
        registerDebitCard = RegisterDebitCard(fakeRepository)
        registerCreditCard = RegisterCreditCard(fakeRepository)
        checkCardDuplicate = CheckCardDuplicate(fakeRepository)
        deleteUnusedCard = DeleteUnusedCard(fakeRepository)
    }

    @Test
    fun `registering debit card when under quota succeeds`() = runTest {
        fakeRepository.activeComputableCount = 2

        val debitCard = DebitCard(
            id = CardId.generate(),
            userId = testUserId,
            alias = "Débito Sueldo",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "1234",
            currency = Currency.PEN,
            linkedAccountId = AccountId.generate(),
            preset = CardPreset.BCP_VISA,
        )

        val result = registerDebitCard(debitCard)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `registering debit card when quota is reached fails with QuotaExceededException`() = runTest {
        fakeRepository.activeComputableCount = 4

        val debitCard = DebitCard(
            id = CardId.generate(),
            userId = testUserId,
            alias = "Débito Interbank",
            issuer = "Interbank",
            network = CardNetwork.VISA,
            lastFourDigits = "5678",
            currency = Currency.PEN,
            linkedAccountId = AccountId.generate(),
        )

        val result = registerDebitCard(debitCard)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is QuotaExceededException)
    }

    @Test
    fun `registering credit card with valid parameters succeeds`() = runTest {
        fakeRepository.activeComputableCount = 1

        val creditCard = CreditCard(
            id = CardId.generate(),
            userId = testUserId,
            alias = "Visa Clásica",
            issuer = "BBVA",
            network = CardNetwork.VISA,
            lastFourDigits = "9988",
            currency = Currency.PEN,
            creditLimitMinorUnits = 5000_00L,
            billingDay = 15,
            dueDay = 5,
        )

        val result = registerCreditCard(creditCard)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `registering card with invalid lastFourDigits length throws IllegalArgumentException`() {
        val exception = runCatching {
            DebitCard(
                id = CardId.generate(),
                userId = testUserId,
                alias = "Invalid PAN",
                issuer = "BCP",
                network = CardNetwork.VISA,
                lastFourDigits = "1234567812345678", // Full PAN prohibited!
                currency = Currency.PEN,
                linkedAccountId = AccountId.generate(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `registering credit card with invalid billing day throws IllegalArgumentException`() {
        val exception = runCatching {
            CreditCard(
                id = CardId.generate(),
                userId = testUserId,
                issuer = "BCP",
                network = CardNetwork.VISA,
                lastFourDigits = "1122",
                currency = Currency.PEN,
                creditLimitMinorUnits = 1000_00L,
                billingDay = 32, // Invalid day
                dueDay = 10,
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `registering card with PAN in alias throws IllegalArgumentException`() {
        val exception = runCatching {
            DebitCard(
                id = CardId.generate(),
                userId = testUserId,
                alias = "Mi tarjeta 4557880011223344", // 16-digit PAN
                issuer = "BCP",
                network = CardNetwork.VISA,
                lastFourDigits = "3344",
                currency = Currency.PEN,
                linkedAccountId = AccountId.generate(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception?.message?.contains("PAN or CVV") == true)
    }

    @Test
    fun `registering card with PAN in issuer throws IllegalArgumentException`() {
        val exception = runCatching {
            DebitCard(
                id = CardId.generate(),
                userId = testUserId,
                alias = "Débito",
                issuer = "BCP 4557880011223344", // 16-digit PAN
                network = CardNetwork.VISA,
                lastFourDigits = "3344",
                currency = Currency.PEN,
                linkedAccountId = AccountId.generate(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception?.message?.contains("PAN or CVV") == true)
    }

    @Test
    fun `registering card with CVV in alias throws IllegalArgumentException`() {
        val exception = runCatching {
            DebitCard(
                id = CardId.generate(),
                userId = testUserId,
                alias = "123", // CVV
                issuer = "BCP",
                network = CardNetwork.VISA,
                lastFourDigits = "3344",
                currency = Currency.PEN,
                linkedAccountId = AccountId.generate(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception?.message?.contains("PAN or CVV") == true)
    }
}

