package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPaymentSuggestion
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class PayCardDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun suggestsNextInstallmentAndKeepsTotalDebtAvailable() {
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = summary(),
                    nextInstallmentDue = CardPaymentSuggestion(
                        dueDate = LocalDate.parse("2026-11-10"),
                        amount = Money(2_500L, Currency.PEN),
                    ),
                    eligibleAccounts = emptyList(),
                    onPayCreditCard = { _, _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Deuda total: PEN 90.00").assertIsDisplayed()
        compose.onNodeWithTag("card_next_installment_suggestion")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Vence", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("credit_card_payment_amount").assertTextContains("25.00")

        compose.onNodeWithText("Total").performScrollTo().performClick()
        compose.onNodeWithTag("credit_card_payment_amount").assertTextContains("90.00")
    }

    @Test
    fun usesTotalDebtWhenNoInstallmentScheduleExists() {
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = summary(),
                    eligibleAccounts = emptyList(),
                    onPayCreditCard = { _, _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("credit_card_payment_amount").assertTextContains("90.00")
    }

    private fun summary(): CreditCardWithSummary {
        val card = CreditCard(
            id = CardId("8a000000-0000-4000-8000-000000000001"),
            userId = UserId("8a000000-0000-4000-8000-000000000002"),
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "1234",
            currency = Currency.PEN,
            creditLimitMinorUnits = 100_000L,
            billingDay = 20,
            dueDay = 10,
        )
        return CreditCardWithSummary(
            card = card,
            debt = Money(9_000L, Currency.PEN),
            availableCredit = Money(91_000L, Currency.PEN),
            utilizationPercentage = 9.0,
        )
    }
}
