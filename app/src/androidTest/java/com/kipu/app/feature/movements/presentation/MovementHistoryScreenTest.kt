package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

class MovementHistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardPaymentHistoryUsesExplicitTitleAndAccountToCardSubtitle() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "payment-row",
                userId = "test-user",
                type = MovementType.TRANSFER,
                amountMinor = 2_500L,
                currency = "PEN",
                sourceAccountId = "bank-account",
                cardId = "credit-card",
                operationKind = "CARD_PAYMENT",
                occurredAt = 1_758_000_000_000L,
            ),
            sourceAccountAlias = "Ahorros",
            cardAlias = "Visa Oro",
        )

        compose.setContent {
            KipuTheme { TransactionRow(item) }
        }

        compose.onNodeWithText("Pago de tarjeta").assertIsDisplayed()
        compose.onNodeWithText("Ahorros → Visa Oro").assertIsDisplayed()
    }
}
