package com.kipu.app.feature.accounts.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.feature.accounts.presentation.instruments.InstallmentSimulatorScreen
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreditAccessibilitySemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val cardId = CardId.generate()
    private val card = CreditCard(
        id = cardId,
        userId = UserId.generate(),
        alias = "Principal",
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 500_000,
        billingDay = 15,
        dueDay = 5,
    )

    @Test
    fun simulatedPhysicalCardExposesOneDescriptiveAccessibilityNode() {
        compose.setContent {
            KipuTheme {
                InstrumentCardPreview(
                    title = "Principal",
                    instrumentType = "Tarjeta de crédito",
                    subtitle = "BCP · Visa",
                    lastFourDigits = "1234",
                )
            }
        }

        compose.onAllNodesWithContentDescription(
            "Vista previa de instrumento",
            substring = true,
        ).assertCountEquals(1)
        compose.onNodeWithContentDescription("Vista previa de instrumento", substring = true).assertIsDisplayed()
    }

    @Test
    fun installmentPreviewExposesSelectorAndConfirmationActions() {
        var confirmedInstallments: Int? = null
        var rejected = false
        val candidate = PurchaseCandidate(
            id = "accessibility-purchase",
            cardId = cardId,
            amount = Money(10_000, Currency.PEN),
            merchant = "Comercio de prueba",
            occurredAt = Instant.parse("2026-09-16T12:00:00Z"),
            suggestedInstallments = 3,
        )
        compose.setContent {
            KipuTheme {
                InstallmentSimulatorScreen(
                    candidate = candidate,
                    card = card,
                    onConfirmPurchase = { confirmedInstallments = it },
                    onRejectPurchase = { rejected = true },
                )
            }
        }

        compose.onNodeWithText("Simulación de compra").assertIsDisplayed()
        compose.onAllNodesWithText("S/ 100.00").assertCountEquals(2)
        compose.onNodeWithText("05/11/2026").assertIsDisplayed()
        compose.onNodeWithText("05/12/2026").assertIsDisplayed()
        compose.onNodeWithText("Distribución referencial sin intereses", substring = true).assertIsDisplayed()
        val readingOrder = listOf(
            compose.onNodeWithText("Simulación de compra").fetchSemanticsNode().boundsInRoot.center.y,
            compose.onNodeWithText("Monto principal").fetchSemanticsNode().boundsInRoot.center.y,
            compose.onNodeWithContentDescription("Selector de 1 a 36 cuotas, actual 3")
                .fetchSemanticsNode().boundsInRoot.center.y,
            compose.onNodeWithText("Cronograma proyectado").fetchSemanticsNode().boundsInRoot.center.y,
            compose.onNodeWithText("Confirmar compra").fetchSemanticsNode().boundsInRoot.center.y,
        )
        assertTrue("Screen reader traversal follows the visual top-to-bottom order", readingOrder.zipWithNext().all { it.first < it.second })
        compose.onNodeWithContentDescription("Selector de 1 a 36 cuotas, actual 3")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                check(setProgress(4f))
            }
        compose.onNodeWithText("4 cuotas").assertIsDisplayed()
        compose.onNodeWithText("Confirmar compra").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(4, confirmedInstallments) }
        compose.onNodeWithText("Editar compra").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(rejected) }
    }

    @Test
    fun paymentAmortizationDialogExposesSourceAndAmountControls() {
        val account = Account(
            id = AccountId.generate(),
            userId = UserId.generate(),
            alias = "Ahorros",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(100_000, Currency.PEN),
            openedAt = Instant.parse("2026-09-01T12:00:00Z"),
        )
        var dismissed = false
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = CreditCardWithSummary(
                        card = card,
                        debt = Money(25_000, Currency.PEN),
                        availableCredit = Money(475_000, Currency.PEN),
                        utilizationPercentage = 5.0,
                    ),
                    eligibleAccounts = listOf(AccountWithBalance(account, Money(100_000, Currency.PEN))),
                    onPayCreditCard = { _, _, _, _, _ -> },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Pagar desde").assertIsDisplayed()
        compose.onNodeWithText("Monto (PEN)").assertIsDisplayed()
        compose.onNodeWithText("Deuda total: PEN 250.00").assertIsDisplayed()
        compose.onNodeWithText("Pagar desde").assertIsDisplayed()
        compose.onNodeWithText("Total").assertIsDisplayed()
        compose.onNodeWithText("Confirmar Pago").assertIsEnabled()
        compose.onNodeWithText("no se registra como otro gasto.", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Confirmar Pago").performClick()
        compose.onNodeWithText("Ingresa un monto mayor a cero").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun paymentFailureRestoresConfirmActionAndAllowsRetry() {
        val account = Account(
            id = AccountId.generate(),
            userId = UserId.generate(),
            alias = "Ahorros",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(100_000, Currency.PEN),
            openedAt = Instant.parse("2026-09-01T12:00:00Z"),
        )
        var attempts = 0
        var dismissed = false

        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = CreditCardWithSummary(
                        card = card,
                        debt = Money(25_000, Currency.PEN),
                        availableCredit = Money(475_000, Currency.PEN),
                        utilizationPercentage = 5.0,
                    ),
                    eligibleAccounts = listOf(AccountWithBalance(account, Money(100_000, Currency.PEN))),
                    onPayCreditCard = { _, _, _, onSuccess, onFailure ->
                        attempts += 1
                        if (attempts == 1) onFailure("No se pudo procesar el pago") else onSuccess()
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Confirmar Pago").assertIsEnabled().performClick()
        compose.onNodeWithText("No se pudo procesar el pago").assertIsDisplayed()
        compose.onNodeWithText("Confirmar Pago").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertTrue(dismissed)
        }
    }
}
