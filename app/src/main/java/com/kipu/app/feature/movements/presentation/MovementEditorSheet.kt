package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.KipuExpense
import com.kipu.app.ui.theme.KipuIncome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun MovementEditorRoute(
    transactionId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: MovementEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MovementEditorUiEvent.CloseEditor -> onDismiss()
                is MovementEditorUiEvent.TransactionRevised -> onSaved()
                is MovementEditorUiEvent.ShowMessage -> Unit
            }
        }
    }

    MovementEditorSheet(
        uiState = uiState,
        onAmountChanged = viewModel::onAmountChanged,
        onSourceAccountSelected = viewModel::onSourceAccountSelected,
        onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onMerchantChanged = viewModel::onMerchantChanged,
        onDateChanged = viewModel::onDateChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onSave = viewModel::onSave,
        onClose = viewModel::onAttemptClose,
        onConfirmDiscard = viewModel::onConfirmDiscard,
        onDismissDiscardDialog = viewModel::onDismissDiscardDialog,
        onDiscardConflict = { uiState.conflictProposal?.let { viewModel.onDiscardConflict(it.proposalId) } },
        onRedoConflict = { uiState.conflictProposal?.let { viewModel.onRedoConflict(it.proposalId) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementEditorSheet(
    uiState: MovementEditorUiState,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onDateChanged: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDiscardConflict: () -> Unit,
    onRedoConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("editor_sheet"),
    ) {
        MovementEditorContent(
            uiState = uiState,
            onAmountChanged = onAmountChanged,
            onSourceAccountSelected = onSourceAccountSelected,
            onDestinationAccountSelected = onDestinationAccountSelected,
            onCategorySelected = onCategorySelected,
            onMerchantChanged = onMerchantChanged,
            onDateChanged = onDateChanged,
            onNoteChanged = onNoteChanged,
            onSave = onSave,
            onClose = onClose,
            onConfirmDiscard = onConfirmDiscard,
            onDismissDiscardDialog = onDismissDiscardDialog,
            onDiscardConflict = onDiscardConflict,
            onRedoConflict = onRedoConflict,
        )
    }
}

@Composable
fun MovementEditorContent(
    uiState: MovementEditorUiState,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onDateChanged: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDiscardConflict: () -> Unit,
    onRedoConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(scrollState),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_edit_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_close_editor")
                    .semantics { contentDescription = "Cerrar editor" },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("editor_loading"),
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
            return
        }

        if (uiState.generalError != null) {
            Text(
                text = uiState.generalError,
                color = KipuExpense,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag("editor_general_error"),
            )
        }

        // Specialized Warning Card
        if (uiState.isSpecialized) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("warning_specialized_movement"),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.specializedMessage
                            ?: stringResource(R.string.movement_edit_specialized_warning),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF92400E),
                    )
                }
            }
        }

        // Conflict Card (T072)
        if (uiState.hasConflict) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, KipuExpense),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("card_conflict_warning"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = KipuExpense,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.movement_edit_conflict_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Versión oficial actual: ${uiState.currency} ${formatMinorUnits(uiState.initialAmountMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (uiState.conflictProposedAmountMinor != null) {
                        Text(
                            text = "Propuesta en conflicto: ${uiState.currency} ${formatMinorUnits(uiState.conflictProposedAmountMinor)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    if (uiState.isOfficialVoided) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.movement_edit_conflict_voided),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = KipuExpense,
                            modifier = Modifier.testTag("text_conflict_voided"),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = onDiscardConflict,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_conflict_discard"),
                        ) {
                            Text(stringResource(R.string.movement_edit_conflict_discard))
                        }
                        if (!uiState.isOfficialVoided) {
                            Button(
                                onClick = onRedoConflict,
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_conflict_redo"),
                            ) {
                                Text(stringResource(R.string.movement_edit_conflict_redo))
                            }
                        }
                    }
                }
            }
        }

        // Fixed badges row: Movement Type & Currency (immutable per spec)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = when (uiState.movementType) {
                    MovementType.INCOME -> KipuIncome.copy(alpha = 0.15f)
                    MovementType.EXPENSE -> KipuExpense.copy(alpha = 0.15f)
                    MovementType.TRANSFER -> colorScheme.primary.copy(alpha = 0.15f)
                },
                modifier = Modifier.testTag("badge_movement_type"),
            ) {
                Text(
                    text = when (uiState.movementType) {
                        MovementType.EXPENSE -> stringResource(R.string.movement_type_expense)
                        MovementType.INCOME -> stringResource(R.string.movement_type_income)
                        MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = when (uiState.movementType) {
                        MovementType.INCOME -> KipuIncome
                        MovementType.EXPENSE -> KipuExpense
                        MovementType.TRANSFER -> colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Surface(
                shape = CircleShape,
                color = colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = uiState.currency,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Amount input
        OutlinedTextField(
            value = uiState.amountText,
            onValueChange = onAmountChanged,
            label = { Text(stringResource(R.string.movement_amount_label)) },
            singleLine = true,
            isError = uiState.amountError != null,
            supportingText = uiState.amountError?.let {
                { Text(text = it, color = colorScheme.error, modifier = Modifier.testTag("error_amount")) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSpecialized && !uiState.hasConflict,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_amount_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Source Account Selector
        AccountDropdownSelector(
            label = stringResource(R.string.movement_source_account),
            selectedAccountId = uiState.selectedSourceAccountId,
            accounts = uiState.availableAccounts,
            onAccountSelected = onSourceAccountSelected,
            error = uiState.sourceAccountError,
            testTag = "editor_source_account",
            errorTag = "error_source_account",
            enabled = !uiState.isSpecialized && !uiState.hasConflict,
        )

        // Destination Account Selector (Transfer only)
        if (uiState.movementType == MovementType.TRANSFER) {
            Spacer(modifier = Modifier.height(12.dp))
            AccountDropdownSelector(
                label = stringResource(R.string.movement_destination_account),
                selectedAccountId = uiState.selectedDestinationAccountId,
                accounts = uiState.availableAccounts,
                onAccountSelected = onDestinationAccountSelected,
                error = uiState.destinationAccountError,
                testTag = "editor_destination_account",
                errorTag = "error_destination_account",
                enabled = !uiState.isSpecialized && !uiState.hasConflict,
            )
        }

        // Category Selector (Expense and Income)
        if (uiState.movementType != MovementType.TRANSFER) {
            Spacer(modifier = Modifier.height(12.dp))
            CategoryDropdownSelector(
                label = stringResource(R.string.movement_category) + if (uiState.movementType == MovementType.EXPENSE) " *" else "",
                selectedCategoryId = uiState.selectedCategoryId,
                selectedCategoryName = uiState.selectedCategoryName,
                categories = uiState.availableCategories,
                onCategorySelected = onCategorySelected,
                error = uiState.categoryError,
                testTag = "editor_category_picker",
                errorTag = "error_category",
                enabled = !uiState.isSpecialized && !uiState.hasConflict,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Merchant Input
        OutlinedTextField(
            value = uiState.merchantName,
            onValueChange = onMerchantChanged,
            label = { Text(stringResource(R.string.movement_merchant)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSpecialized && !uiState.hasConflict,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_merchant_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Date Picker Field
        val formattedDate = remember(uiState.occurredAt) {
            formatEpochToDateString(uiState.occurredAt)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            color = colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = !uiState.isSpecialized && !uiState.hasConflict) {
                    showDatePicker = true
                }
                .testTag("editor_date_picker"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.movement_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Note Input
        OutlinedTextField(
            value = uiState.note,
            onValueChange = onNoteChanged,
            label = { Text(stringResource(R.string.movement_note)) },
            placeholder = { Text(stringResource(R.string.movement_note_placeholder)) },
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSpecialized && !uiState.hasConflict,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_note_input"),
        )

        // Summary of changes ("Resumen de cambios")
        if (uiState.hasUnsavedChanges) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_change_summary"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.movement_edit_summary_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.isOnlyNoteChanged) {
                        Text(
                            text = stringResource(R.string.movement_edit_summary_only_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                            modifier = Modifier.testTag("summary_only_note"),
                        )
                    } else {
                        val parsedAmount = MoneyInputParser.parseMinorUnits(uiState.amountText)
                        if (parsedAmount != null && parsedAmount != uiState.initialAmountMinor) {
                            Text(
                                text = "Monto: Anterior ${uiState.currency} ${formatMinorUnits(uiState.initialAmountMinor)} → Nuevo ${uiState.currency} ${formatMinorUnits(parsedAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("summary_amount"),
                            )
                        }
                        if (uiState.occurredAt != uiState.initialOccurredAt) {
                            Text(
                                text = stringResource(R.string.movement_edit_summary_date_period),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("summary_date"),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        Button(
            onClick = onSave,
            enabled = !uiState.isSpecialized && !uiState.hasConflict && uiState.hasUnsavedChanges && !uiState.isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("editor_btn_save"),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    color = colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("editor_saving_progress"),
                )
            } else {
                Text(
                    text = stringResource(R.string.movement_edit_save),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    // Discard Changes Dialog
    if (uiState.showConfirmDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardDialog,
            title = {
                Text(
                    text = stringResource(R.string.movement_edit_discard_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.movement_edit_discard_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDiscard,
                    modifier = Modifier.testTag("btn_confirm_discard"),
                ) {
                    Text(
                        text = stringResource(R.string.movement_edit_discard_dialog_confirm),
                        color = KipuExpense,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDiscardDialog,
                    modifier = Modifier.testTag("btn_cancel_discard"),
                ) {
                    Text(text = stringResource(R.string.movement_edit_discard_dialog_cancel))
                }
            },
            modifier = Modifier.testTag("dialog_confirm_discard"),
        )
    }

    // DatePicker Dialog
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.occurredAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { selected ->
                            onDateChanged(selected)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun AccountDropdownSelector(
    label: String,
    selectedAccountId: String?,
    accounts: List<Account>,
    onAccountSelected: (String) -> Unit,
    error: String?,
    testTag: String,
    errorTag: String,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccount = accounts.firstOrNull { it.id.value == selectedAccountId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (error != null) colorScheme.error else colorScheme.outlineVariant,
            ),
            color = colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled) { expanded = true }
                .testTag(testTag),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedAccount?.alias ?: "Seleccionar cuenta",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (selectedAccount != null) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = if (selectedAccount != null) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.alias) },
                    onClick = {
                        onAccountSelected(account.id.value)
                        expanded = false
                    },
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
                    .testTag(errorTag),
            )
        }
    }
}

@Composable
private fun CategoryDropdownSelector(
    label: String,
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    categories: List<CategoryOption>,
    onCategorySelected: (CategoryOption) -> Unit,
    error: String?,
    testTag: String,
    errorTag: String,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (error != null) colorScheme.error else colorScheme.outlineVariant,
            ),
            color = colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled) { expanded = true }
                .testTag(testTag),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedCategoryName ?: "Seleccionar categoría",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (selectedCategoryName != null) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (selectedCategoryName != null) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.displayName) },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    },
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
                    .testTag(errorTag),
            )
        }
    }
}

private fun formatEpochToDateString(epochMilli: Long): String {
    if (epochMilli <= 0L) return "Seleccionar fecha"
    val localDate = Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
