package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savesAliasAndPresentationWithoutChangingOpeningBalance() {
        var savedAlias: String? = null
        var savedPreset: AccountPreset? = null
        var correctedAmount: Long? = null
        setScreen(
            account = account(),
            onSaveAppearance = { alias, preset ->
                savedAlias = alias
                savedPreset = preset
            },
            onCorrectOpeningBalance = { correctedAmount = it },
        )

        compose.onNodeWithText("Nombre de la cuenta").performScrollTo().performTextReplacement("Ahorro de viaje")
        compose.onNodeWithTag("account_preset_selector").performScrollTo().performClick()
        compose.onNodeWithTag("account_preset_bbva").performClick()
        compose.onNodeWithText("Guardar presentación").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("Ahorro de viaje", savedAlias)
            assertEquals(AccountPreset.BBVA, savedPreset)
            assertEquals(null, correctedAmount)
        }
    }

    @Test
    fun cashAccountDoesNotShowAnInstitutionSelector() {
        setScreen(account().copy(type = AccountType.CASH, preset = AccountPreset.CASH))

        compose.onAllNodesWithTag("account_preset_selector").assertCountEquals(0)
        compose.onNodeWithText("Nombre de la cuenta").assertIsDisplayed()
    }

    @Test
    fun openingBalanceChangesOnlyAfterExplicitAuditedCorrectionConfirmation() {
        var correctedAmount: Long? = null
        setScreen(account = account(), onCorrectOpeningBalance = { correctedAmount = it })

        compose.onNodeWithText("Corregir saldo inicial").performScrollTo().performClick()
        compose.onNodeWithText("Se conserva el movimiento de apertura original", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nuevo saldo inicial (PEN)").performTextReplacement("12.50")
        compose.runOnIdle { assertEquals(null, correctedAmount) }

        compose.onNodeWithText("Registrar corrección").performClick()

        compose.runOnIdle { assertEquals(1_250L, correctedAmount) }
    }

    @Test
    fun archiveRequiresConfirmationAndExplainsHistoryIsRetained() {
        var archived = false
        setScreen(account = account(), onArchive = { archived = true })

        compose.onNodeWithText("Archivar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("No se eliminarán los movimientos ni el historial", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertFalse(archived) }

        compose.onNodeWithText("Archivar").performClick()

        compose.runOnIdle { assertTrue(archived) }
    }

    @Test
    fun archivedInstrumentCanBeReactivatedAfterConfirmationAndBackWorks() {
        var reactivated = false
        var navigatedBack = false
        setScreen(
            account = account().copy(isArchived = true),
            onReactivate = { reactivated = true },
            onNavigateBack = { navigatedBack = true },
        )

        compose.onNodeWithText("Este instrumento está archivado", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reactivar cuenta").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(reactivated) }
        compose.onNodeWithText("Reactivar").performClick()
        compose.runOnIdle { assertTrue(reactivated) }

        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.runOnIdle { assertTrue(navigatedBack) }
    }

    @Test
    fun outstandingCreditCardDebtStaysVisibleAndCanUseExistingPaymentFlowWhenActive() {
        val card = creditCard()
        var paymentRequested = false
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = null,
                    card = card,
                    currentBalance = null,
                    creditCardSummary = cardSummary(card),
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = {},
                    onPayCreditCard = { paymentRequested = true },
                )
            }
        }

        compose.onNodeWithText("Deuda actual").assertIsDisplayed()
        compose.onNodeWithText("Pagar tarjeta").performScrollTo().performClick()

        compose.runOnIdle { assertTrue(paymentRequested) }
    }

    @Test
    fun archivedCreditCardDebtRemainsVisibleAndRequiresReactivationBeforePayment() {
        val card = creditCard(isArchived = true)
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = null,
                    card = card,
                    currentBalance = null,
                    creditCardSummary = cardSummary(card),
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = {},
                )
            }
        }

        compose.onNodeWithText("Deuda actual").assertIsDisplayed()
        compose.onNodeWithText("Este instrumento está archivado", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Pagar tarjeta").assertCountEquals(0)
        compose.onNodeWithText("Reactivar tarjeta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun creditCardHasEditableVisibleNameAndPresentationWithoutFinancialEdits() {
        val card = creditCard()
        var savedAlias: String? = null
        var savedPreset: CardPreset? = null
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = null,
                    card = card,
                    currentBalance = null,
                    creditCardSummary = cardSummary(card),
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onSaveCardAppearance = { alias, preset ->
                        savedAlias = alias
                        savedPreset = preset
                    },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = {},
                )
            }
        }

        compose.onNodeWithTag("card_presentation_card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nombre visible").performScrollTo().performTextReplacement("Viajes BCP")
        compose.onNodeWithTag("card_preset_selector").performScrollTo().performClick()
        compose.onNodeWithTag("card_preset_bcp_visa").performClick()
        compose.onNodeWithTag("save_card_presentation").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("Viajes BCP", savedAlias)
            assertEquals(CardPreset.BCP_VISA, savedPreset)
        }
        compose.onNodeWithText("Los consumos, el límite y las fechas de pago no cambian.").assertIsDisplayed()
    }

    private fun setScreen(
        account: Account,
        onNavigateBack: () -> Unit = {},
        onSaveAppearance: (String, AccountPreset?) -> Unit = { _, _ -> },
        onCorrectOpeningBalance: (Long) -> Unit = {},
        onArchive: () -> Unit = {},
        onReactivate: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = account,
                    card = null,
                    currentBalance = Money(2_500L, Currency.PEN),
                    creditCardSummary = null,
                    isLoading = false,
                    onNavigateBack = onNavigateBack,
                    onSaveAppearance = onSaveAppearance,
                    onCorrectOpeningBalance = onCorrectOpeningBalance,
                    onArchive = onArchive,
                    onReactivate = onReactivate,
                )
            }
        }
    }

    private fun account() = Account(
        id = AccountId.generate(),
        userId = UserId.generate(),
        alias = "Mi cuenta",
        type = AccountType.SAVINGS,
        currency = Currency.PEN,
        preset = AccountPreset.BCP,
        initialBalance = Money(10_000L, Currency.PEN),
        openedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

    private fun creditCard(isArchived: Boolean = false) = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        alias = "Visa principal",
        issuer = "Banco",
        network = CardNetwork.VISA,
        lastFourDigits = "4242",
        currency = Currency.PEN,
        creditLimitMinorUnits = 100_000L,
        billingDay = 10,
        dueDay = 20,
        isArchived = isArchived,
    )

    private fun cardSummary(card: CreditCard) = CreditCardWithSummary(
        card = card,
        debt = Money(3_000L, Currency.PEN),
        availableCredit = Money(card.creditLimitMinorUnits - 3_000L, Currency.PEN),
        utilizationPercentage = 3.0,
    )
}
