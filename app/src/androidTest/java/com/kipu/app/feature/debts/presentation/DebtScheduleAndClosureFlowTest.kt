package com.kipu.app.feature.debts.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.debts.data.sync.DebtReminderDestination
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtScheduleAndClosureFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scheduledInstallmentIsClearlyNotAPaymentAndReminderOpensItsDebt() {
        var scheduleCancelled = false
        val state = DebtScheduleUiState(
            debt = debt(),
            installments = listOf(
                DebtScheduleItem(
                    id = INSTALLMENT_ID,
                    installmentNumber = 1,
                    dueDate = LocalDate.parse("2026-11-10"),
                    principalMinor = 3_334L,
                    status = "PENDING",
                ),
            ),
            installmentCount = "3",
            firstDueDate = "2026-11-10",
            reminderLeadDays = 3,
        )
        compose.setContent {
            DebtScheduleScreen(
                state = state,
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = {},
                onCancelSchedule = { scheduleCancelled = true },
                onCloseDebt = { _, _ -> },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Cuotas planificadas \u00b7 no son pagos").assertIsDisplayed()
        compose.onNodeWithText("Pendiente de pago").assertIsDisplayed()
        compose.onNodeWithText("S/ 33.34").assertIsDisplayed()
        compose.onNodeWithTag("debt-schedule-cancel").performClick()
        assertEquals(true, scheduleCancelled)
        assertEquals("kipu://debts/detail/$DEBT_ID", DebtReminderDestination.uri(DEBT_ID))
    }

    @Test
    fun cancelledDebtKeepsItsBalanceVisibleAndIsNotPresentedAsPaid() {
        compose.setContent {
            DebtScheduleScreen(
                state = DebtScheduleUiState(
                    debt = debt().copy(status = DebtLifecycleStatus.CANCELLED, remainingPrincipalMinor = 8_000L),
                    installments = listOf(
                        DebtScheduleItem(INSTALLMENT_ID, 1, LocalDate.parse("2026-11-10"), 3_334L, status = "CANCELLED"),
                    ),
                ),
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = {},
                onCancelSchedule = {},
                onCloseDebt = { _, _ -> },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Cancelada \u00b7 saldo pendiente no pagado").assertIsDisplayed()
        compose.onNodeWithText("S/ 80.00").assertIsDisplayed()
        compose.onNodeWithText("Cuota cancelada").assertIsDisplayed()
    }

    private fun debt() = DebtSummary(
        debtId = DEBT_ID,
        userId = OWNER_ID,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        remainingPrincipalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = 3,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 1L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val INSTALLMENT_ID = "88000000-0000-4000-8000-000000000003"
    }
}
