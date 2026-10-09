package com.kipu.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.presentation.DebtDetailScreen
import com.kipu.app.feature.debts.presentation.DebtDetailUiEvent
import com.kipu.app.feature.debts.presentation.DebtDetailViewModel
import com.kipu.app.feature.debts.presentation.DebtEditDetailsScreen
import com.kipu.app.feature.debts.presentation.DebtEditUiEvent
import com.kipu.app.feature.debts.presentation.DebtEditViewModel
import com.kipu.app.feature.debts.presentation.DebtListScreen
import com.kipu.app.feature.debts.presentation.DebtListViewModel
import com.kipu.app.feature.debts.presentation.DebtOpeningUiEvent
import com.kipu.app.feature.debts.presentation.DebtOpeningViewModel
import com.kipu.app.feature.debts.presentation.DebtSettlementSheet
import com.kipu.app.feature.debts.presentation.DebtSettlementUiEvent
import com.kipu.app.feature.debts.presentation.DebtSettlementViewModel
import com.kipu.app.feature.debts.presentation.DebtScheduleScreen
import com.kipu.app.feature.debts.presentation.DebtScheduleUiEvent
import com.kipu.app.feature.debts.presentation.DebtScheduleViewModel
import com.kipu.app.feature.debts.presentation.PayableDebtFormScreen
import com.kipu.app.feature.debts.presentation.ReceivableDebtFormScreen

const val DEBT_LIST_ROUTE = "debts"
const val DEBT_OPENING_ROUTE = "debts/open/{obligationType}"
const val DEBT_DETAIL_ROUTE = "debts/detail/{debtId}"
const val DEBT_EDIT_ROUTE = "debts/detail/{debtId}/edit"
const val DEBT_SETTLE_ROUTE = "debts/detail/{debtId}/settle"
const val DEBT_SCHEDULE_ROUTE = "debts/detail/{debtId}/schedule"
const val DEBT_REMINDER_DEEP_LINK = "kipu://debts/detail/{debtId}"

fun NavController.navigateToDebtOpening(type: DebtObligationType) {
    navigate("debts/open/${type.name}")
}

fun NavController.navigateToDebtDetail(debtId: String) {
    navigate("debts/detail/$debtId")
}

fun NavController.navigateToDebtSettlement(debtId: String) {
    navigate("debts/detail/$debtId/settle")
}

fun NavController.navigateToDebtSchedule(debtId: String) {
    navigate("debts/detail/$debtId/schedule")
}

fun NavGraphBuilder.debtDestinations(navController: NavController) {
    composable(DEBT_LIST_ROUTE) { backStackEntry ->
        // The account dashboard entry stays beneath this root tab. Scope the debt list state to it
        // so switching tabs does not discard the list ViewModel and rerun its initial queries.
        val rootOwner = remember(navController, backStackEntry) {
            runCatching { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.getOrNull()
        }
        val viewModel: DebtListViewModel = hiltViewModel(rootOwner ?: backStackEntry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        DebtListScreen(
            debts = state.debts,
            selectedType = state.selectedType,
            onTypeSelected = viewModel::selectType,
            onDebtSelected = navController::navigateToDebtDetail,
            scheduledInstallments = state.scheduledInstallments,
            errorMessage = state.errorMessage,
            isLoading = state.isLoading,
        )
    }
    composable(
        DEBT_OPENING_ROUTE,
        arguments = listOf(navArgument("obligationType") { type = NavType.StringType }),
    ) {
        val viewModel: DebtOpeningViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    if (event is DebtOpeningUiEvent.Saved) {
                        val returnRoute = if (
                            runCatching { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.isSuccess
                        ) {
                            ACCOUNTS_DASHBOARD_ROUTE
                        } else {
                            DEBT_LIST_ROUTE
                        }
                        navController.navigate("debts/detail/${event.debtId}") {
                            popUpTo(returnRoute) { inclusive = false }
                        }
                    }
            }
        }
        if (viewModel.obligationType == DebtObligationType.PAYABLE) {
            PayableDebtFormScreen(
                state = state,
                onCounterpartyNameChange = viewModel::onCounterpartyNameChange,
                onPrincipalAmountChange = viewModel::onPrincipalAmountChange,
                onCurrencyChange = viewModel::onCurrencyChange,
                onOpeningModeChange = viewModel::onOpeningModeChange,
                onAccountSelected = viewModel::onAccountSelected,
                onDueDateChange = viewModel::onDueDateChange,
                onNotesChange = viewModel::onNotesChange,
                onSave = viewModel::save,
                onNavigateBack = navController::popBackStack,
            )
        } else {
            ReceivableDebtFormScreen(
                state = state,
                onCounterpartyNameChange = viewModel::onCounterpartyNameChange,
                onPrincipalAmountChange = viewModel::onPrincipalAmountChange,
                onCurrencyChange = viewModel::onCurrencyChange,
                onOpeningModeChange = viewModel::onOpeningModeChange,
                onAccountSelected = viewModel::onAccountSelected,
                onDueDateChange = viewModel::onDueDateChange,
                onNotesChange = viewModel::onNotesChange,
                onSave = viewModel::save,
                onNavigateBack = navController::popBackStack,
            )
        }
    }
    composable(
        DEBT_DETAIL_ROUTE,
        arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
        deepLinks = listOf(navDeepLink { uriPattern = DEBT_REMINDER_DEEP_LINK }),
    ) {
        val viewModel: DebtDetailViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is DebtDetailUiEvent.Deleted) navController.popBackStack()
            }
        }
        when {
            state.isLoading -> CenteredDebtLoading()
            state.debt == null -> Text(
                state.errorMessage ?: "No se encontró esta deuda.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
            else -> DebtDetailScreen(
                debt = state.debt!!,
                hasFinancialHistory = state.hasFinancialHistory,
                onNavigateBack = navController::popBackStack,
                onEdit = { navController.navigate("debts/detail/${viewModel.debtId}/edit") },
                onDelete = viewModel::delete,
                onSettle = { navController.navigateToDebtSettlement(viewModel.debtId) },
                onSchedule = { navController.navigateToDebtSchedule(viewModel.debtId) },
                errorMessage = state.errorMessage,
                activities = state.activities,
                installments = state.installments,
            )
        }
    }
    composable(
        DEBT_SCHEDULE_ROUTE,
        arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
    ) {
        val viewModel: DebtScheduleViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is DebtScheduleUiEvent.Closed) navController.popBackStack()
            }
        }
        DebtScheduleScreen(
            state = state,
            onInstallmentCountChange = viewModel::onInstallmentCountChange,
            onFirstDueDateChange = viewModel::onFirstDueDateChange,
            onReminderLeadDaysChange = viewModel::onReminderLeadDaysChange,
            onSchedule = viewModel::saveSchedule,
            onCancelSchedule = viewModel::cancelSchedule,
            onCloseDebt = viewModel::closeDebt,
            onNavigateBack = navController::popBackStack,
            onAdjustmentAmountChange = viewModel::onAdjustmentAmountChange,
            onForgivenessAmountChange = viewModel::onForgivenessAmountChange,
            onClosureReasonChange = viewModel::onClosureReasonChange,
        )
    }
    composable(
        DEBT_SETTLE_ROUTE,
        arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
    ) {
        val viewModel: DebtSettlementViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is DebtSettlementUiEvent.Settled) navController.popBackStack()
            }
        }
        when {
            state.isLoading -> CenteredDebtLoading()
            state.debt == null -> Text(
                state.errorMessage ?: "No se encontró esta deuda.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
            else -> DebtSettlementSheet(
                debt = state.debt!!,
                state = state,
                onPrincipalAmountChange = viewModel::onPrincipalAmountChange,
                onInterestAmountChange = viewModel::onInterestAmountChange,
                onAccountSelected = viewModel::onAccountSelected,
                onInterestCategorySelected = viewModel::onInterestCategorySelected,
                onSave = viewModel::save,
                onDismiss = navController::popBackStack,
            )
        }
    }
    composable(
        DEBT_EDIT_ROUTE,
        arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
    ) {
        val viewModel: DebtEditViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is DebtEditUiEvent.Saved) navController.popBackStack()
            }
        }
        DebtEditDetailsScreen(
            state = state,
            onCounterpartyNameChange = viewModel::onCounterpartyNameChange,
            onDueDateChange = viewModel::onDueDateChange,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::save,
            onNavigateBack = navController::popBackStack,
        )
    }
}

@Composable
private fun CenteredDebtLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}
