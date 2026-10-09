package com.kipu.app.feature.debts.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtSettlementFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun payableSheetKeepsPrincipalAndInterestSeparateWhenSubmitting() {
        val state = mutableStateOf(
            DebtSettlementUiState(
                principalAmount = "30.00",
                interestAmount = "1.25",
                selectedAccountId = ACCOUNT_ID,
                selectedInterestCategoryId = CATEGORY_ID,
                availableAccounts = listOf(DebtAccountOption(ACCOUNT_ID, "Ahorros", "PEN")),
                availableInterestCategories = listOf(DebtCategoryOption(CATEGORY_ID, "Intereses")),
            ),
        )
        var submitted = false
        compose.setContent {
            DebtSettlementSheet(
                debt = debt(DebtObligationType.PAYABLE),
                state = state.value,
                onPrincipalAmountChange = { state.value = state.value.copy(principalAmount = it) },
                onInterestAmountChange = { state.value = state.value.copy(interestAmount = it) },
                onAccountSelected = { state.value = state.value.copy(selectedAccountId = it) },
                onInterestCategorySelected = { state.value = state.value.copy(selectedInterestCategoryId = it) },
                onSave = { submitted = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("El principal reduce el saldo; el interés se registra por separado.").assertIsDisplayed()
        compose.onNodeWithTag("settlement-principal").performTextReplacement("35.00")
        compose.onNodeWithTag("settlement-interest").performTextReplacement("2.50")
        compose.onNodeWithTag("settlement-save").assertIsEnabled()
        compose.onNodeWithTag("settlement-save").performSemanticsAction(SemanticsActions.OnClick) { it() }

        assertEquals("35.00", state.value.principalAmount)
        assertEquals("2.50", state.value.interestAmount)
        assertEquals(true, submitted)
    }

    @Test
    fun principalAboveRemainingShowsErrorAndBlocksSubmission() {
        compose.setContent {
            DebtSettlementSheet(
                debt = debt(DebtObligationType.PAYABLE),
                state = DebtSettlementUiState(
                    principalAmount = "81.00",
                    interestAmount = "0",
                    selectedAccountId = ACCOUNT_ID,
                    availableAccounts = listOf(DebtAccountOption(ACCOUNT_ID, "Ahorros", "PEN")),
                ),
                onPrincipalAmountChange = {},
                onInterestAmountChange = {},
                onAccountSelected = {},
                onInterestCategorySelected = {},
                onSave = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("El principal supera el saldo pendiente.").assertIsDisplayed()
        compose.onNodeWithTag("settlement-save").assertIsNotEnabled()
    }

    @Test
    fun staleCommandConflictIsShownWithoutAllowingResubmission() {
        compose.setContent {
            DebtSettlementSheet(
                debt = debt(DebtObligationType.RECEIVABLE),
                state = DebtSettlementUiState(
                    principalAmount = "10.00",
                    interestAmount = "0",
                    selectedAccountId = ACCOUNT_ID,
                    availableAccounts = listOf(DebtAccountOption(ACCOUNT_ID, "Ahorros", "PEN")),
                    conflictMessage = "La deuda cambió en otro dispositivo. Actualizamos el saldo pendiente.",
                ),
                onPrincipalAmountChange = {},
                onInterestAmountChange = {},
                onAccountSelected = {},
                onInterestCategorySelected = {},
                onSave = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("La deuda cambió en otro dispositivo. Actualizamos el saldo pendiente.").assertIsDisplayed()
        compose.onNodeWithTag("settlement-save").assertIsNotEnabled()
    }

    @Test
    fun debtDetailShowsPrincipalAndInterestWithDifferentFinancialEffects() {
        compose.setContent {
            DebtDetailScreen(
                debt = debt(DebtObligationType.PAYABLE),
                hasFinancialHistory = true,
                onNavigateBack = {},
                onEdit = {},
                onDelete = {},
                activities = listOf(
                    DebtSettlementActivity(
                        eventId = "event-1",
                        principalMinor = 2_000L,
                        interestMinor = 100L,
                        occurredAt = 1_791_000_000_000L,
                        isVoided = false,
                    ),
                ),
            )
        }

        compose.onNodeWithTag("debt-settlement-activities").performScrollTo()
        compose.onNodeWithText("Principal · reduce el saldo").assertIsDisplayed()
        compose.onNodeWithText("Interés · gasto operativo").assertIsDisplayed()
        compose.onNodeWithText("20.00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun debtDetailShowsInstallmentPlanSeparateFromPayments() {
        compose.setContent {
            DebtDetailScreen(
                debt = debt(DebtObligationType.PAYABLE),
                hasFinancialHistory = true,
                onNavigateBack = {},
                onEdit = {},
                onDelete = {},
                installments = listOf(
                    DebtScheduleItem(
                        id = "installment-1",
                        installmentNumber = 1,
                        dueDate = LocalDate.parse("2026-11-10"),
                        principalMinor = 2_000L,
                        status = "PARTIAL",
                    ),
                ),
            )
        }

        compose.onNodeWithTag("debt-installment-plan").performScrollTo()
        compose.onNodeWithText("Plan de cuotas (no son pagos)").assertIsDisplayed()
        compose.onNodeWithText("Cuota 1", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pago parcial").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun debtDetailShowsAdjustmentsAndForgivenessAsSeparateHistoryActions() {
        compose.setContent {
            DebtDetailScreen(
                debt = debt(DebtObligationType.PAYABLE),
                hasFinancialHistory = true,
                onNavigateBack = {},
                onEdit = {},
                onDelete = {},
                activities = listOf(
                    DebtSettlementActivity(
                        eventId = "adjustment-1",
                        principalMinor = 500L,
                        interestMinor = 0L,
                        occurredAt = 1_791_000_000_000L,
                        isVoided = false,
                        eventType = "ADJUSTMENT",
                        principalDeltaMinor = 500L,
                    ),
                    DebtSettlementActivity(
                        eventId = "forgiveness-1",
                        principalMinor = 1_000L,
                        interestMinor = 0L,
                        occurredAt = 1_791_000_000_001L,
                        isVoided = false,
                        eventType = "FORGIVENESS",
                        principalDeltaMinor = -1_000L,
                    ),
                ),
            )
        }

        compose.onNodeWithTag("debt-settlement-activities").performScrollTo()
        compose.onNodeWithText("Ajuste de principal").assertIsDisplayed()
        compose.onNodeWithText("Condonación de principal").assertIsDisplayed()
        compose.onNodeWithText("Aumenta el saldo").assertIsDisplayed()
        compose.onNodeWithText("Reduce el saldo").assertIsDisplayed()
    }

    @Test
    fun debtListDistinguishesPlannedInstallmentFromCompletedPayment() {
        val pendingDebt = debt(DebtObligationType.PAYABLE)
        val partialDebt = pendingDebt.copy(debtId = "88000000-0000-4000-8000-000000000006", counterpartyName = "Prestamo parcial")
        val settledDebt = pendingDebt.copy(debtId = "88000000-0000-4000-8000-000000000005", counterpartyName = "Préstamo pagado")
        compose.setContent {
            DebtListScreen(
                debts = listOf(pendingDebt, settledDebt, partialDebt),
                selectedType = null,
                onTypeSelected = {},
                onDebtSelected = {},
                scheduledInstallments = mapOf(
                    pendingDebt.debtId to DebtScheduleItem(
                        id = "planned-1",
                        installmentNumber = 1,
                        dueDate = LocalDate.parse("2026-11-10"),
                        principalMinor = 2_000L,
                        status = "PENDING",
                    ),
                    settledDebt.debtId to DebtScheduleItem(
                        id = "paid-1",
                        installmentNumber = 1,
                        dueDate = LocalDate.parse("2026-10-01"),
                        principalMinor = 2_000L,
                        status = "PAID",
                    ),
                    partialDebt.debtId to DebtScheduleItem(
                        id = "partial-1",
                        installmentNumber = 1,
                        dueDate = LocalDate.parse("2026-10-15"),
                        principalMinor = 2_000L,
                        status = "PARTIAL",
                    ),
                ),
            )
        }

        compose.onNodeWithText("Cuota planificada · 2026-11-10").assertIsDisplayed()
        compose.onNodeWithText("Pendiente · no es pago").assertIsDisplayed()
        compose.onNodeWithText("Pago completado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pago parcial registrado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settledDebtShowsItsStateWithoutPaymentOrScheduleActions() {
        val settled = debt(DebtObligationType.PAYABLE).copy(
            remainingPrincipalMinor = 0L,
            status = DebtLifecycleStatus.SETTLED,
        )
        compose.setContent {
            DebtDetailScreen(
                debt = settled,
                hasFinancialHistory = true,
                onNavigateBack = {},
                onEdit = {},
                onDelete = {},
            )
        }

        compose.onNodeWithTag("debt-settled-state").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Deuda liquidada. El saldo quedó en cero y el historial se conserva.").assertIsDisplayed()
        compose.onAllNodesWithText("Registrar pago").assertCountEquals(0)
        compose.onAllNodesWithText("Gestionar cuotas").assertCountEquals(0)
    }

    @Test
    fun emptyDebtListKeepsOneClearEmptyStateWithoutDuplicateActions() {
        compose.setContent {
            DebtListScreen(
                debts = emptyList(),
                selectedType = null,
                onTypeSelected = {},
                onDebtSelected = {},
            )
        }

        compose.onNodeWithText("Deudas y préstamos").assertIsDisplayed()
        compose.onNodeWithTag("debt_empty_state").assertIsDisplayed()
        compose.onNodeWithText("Aún no tienes deudas ni préstamos").assertIsDisplayed()
        compose.onAllNodesWithText("Me prestaron").assertCountEquals(0)
        compose.onAllNodesWithText("Presté dinero").assertCountEquals(0)
    }

    private fun debt(type: DebtObligationType) = DebtSummary(
        debtId = DEBT_ID,
        userId = OWNER_ID,
        obligationType = type,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        remainingPrincipalMinor = 8_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = null,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 2L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val ACCOUNT_ID = "88000000-0000-4000-8000-000000000003"
        const val CATEGORY_ID = "88000000-0000-4000-8000-000000000004"
    }
}
