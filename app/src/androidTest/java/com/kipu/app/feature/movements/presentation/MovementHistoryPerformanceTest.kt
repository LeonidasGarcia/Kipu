package com.kipu.app.feature.movements.presentation

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementHistoryPerformanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tenThousandMovementsRenderLazilyAndRemainScrollable() {
        val transactions = List(10_000) { index ->
            TransactionItem(
                transaction = Transaction(
                    id = "performance-$index",
                    userId = "performance-user",
                    type = MovementType.EXPENSE,
                    amountMinor = 1_000L + index,
                    currency = "PEN",
                    sourceAccountId = "performance-account",
                    categoryId = "performance-category",
                    occurredAt = System.currentTimeMillis(),
                ),
                sourceAccountAlias = "Cuenta principal",
                categoryName = "Alimentación",
            )
        }
        val renderedState = MutableStateFlow(
            MovementHistoryUiState(
                isLoading = false,
                allTransactions = transactions,
                filteredTransactions = mapOf("Hoy" to transactions),
            ),
        )
        val viewModel = mockk<MovementHistoryViewModel>(relaxed = true) {
            every { uiState } returns renderedState
        }

        val startedAt = SystemClock.elapsedRealtime()
        compose.setContent {
            KipuTheme {
                MovementHistoryRoute(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        val initialRenderMillis = SystemClock.elapsedRealtime() - startedAt

        assertTrue(
            "10,000-row history should compose its initial viewport in under 5 seconds; took ${initialRenderMillis}ms",
            initialRenderMillis < 5_000L,
        )
        compose.onNodeWithTag("tx_row_performance-0").assertIsDisplayed()
        compose.onNodeWithTag("tx_row_performance-9999").assertDoesNotExist()
        compose.onNodeWithTag("list_movements")
            .performScrollToNode(hasTestTag("tx_row_performance-9999"))
        compose.onNodeWithTag("tx_row_performance-9999").assertIsDisplayed()
    }
}
