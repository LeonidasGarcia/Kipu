package com.kipu.app.feature.plans.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.*
import com.kipu.app.R
import com.kipu.app.ui.theme.KipuTheme

@Composable
fun PlanSelectionScreen(state: PlanSelectionUiState, onOptionSelected: (CommercialOption) -> Unit, onConfirm: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.displayMedium)
            Text(
                stringResource(R.string.plans_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        FreePlanCard(
            selected = state.selectedOption == CommercialOption.FREE,
            onSelect = { onOptionSelected(CommercialOption.FREE) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Opciones Premium", style = MaterialTheme.typography.headlineMedium)
            CommercialOption.entries.filterNot { it == CommercialOption.FREE }.forEach { option ->
                OptionCard(option, option == state.selectedOption, state.eligibility, onOptionSelected)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.plans_play_disabled),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ConfirmPlanButton(state.isConfirming, onConfirm)
        }
    }
}

@Composable
private fun FreePlanCard(selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("plan-option-FREE")
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = optionBorder(selected),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kipu Free", style = MaterialTheme.typography.titleMedium)
                PlanRadioButton(selected)
            }
            Text(
                "S/ 0 de por vida",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            )
            listOf("4 instrumentos", "5 categorías personalizadas", "2 deudas", "2 metas", "2 presupuestos").forEach {
                Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OptionCard(option: CommercialOption, selected: Boolean, eligibility: TrialEligibilitySnapshot, onSelect: (CommercialOption) -> Unit) {
    val price = when (option) { CommercialOption.FREE -> "S/ 0"; CommercialOption.MONTHLY -> "S/ 4.99"; CommercialOption.ANNUAL -> "S/ 29.99"; CommercialOption.LIFETIME -> "S/ 49.99" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("plan-option-${option.name}")
            .selectable(selected, role = Role.RadioButton) { onSelect(option) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = optionBorder(selected),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(option.label, style = MaterialTheme.typography.titleMedium)
                PlanRadioButton(selected)
            }
            Text(price, modifier = Modifier.testTag("plan-price-${option.name}"), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            when (option) {
                CommercialOption.MONTHLY -> if (eligibility.isEligible()) Text("7 días; luego S/ 4.99 con renovación mensual. Cancela cuando quieras.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Text("Oferta sujeta a verificación; renovación mensual y cancelación disponibles.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CommercialOption.ANNUAL -> if (eligibility.isEligible()) Text("7 días; luego S/ 29.99 con renovación anual. Cancela cuando quieras.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Text("Oferta sujeta a verificación; renovación anual y cancelación disponibles.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CommercialOption.LIFETIME -> Text("Pago único, sin renovación", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CommercialOption.FREE -> Text("Sin tarjeta, sin publicidad", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun optionBorder(selected: Boolean) = BorderStroke(
    width = if (selected) 2.dp else 1.dp,
    color = if (selected) MaterialTheme.colorScheme.surfaceTint else MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun PlanRadioButton(selected: Boolean) {
    RadioButton(
        selected = selected,
        onClick = null,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
private fun ConfirmPlanButton(isConfirming: Boolean, onConfirm: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onConfirm,
        enabled = !isConfirming,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            if (isConfirming) "Confirmando..." else stringResource(R.string.plans_confirm),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlanSelectionScreenPreview() {
    KipuTheme {
        PlanSelectionScreen(
            state = PlanSelectionUiState(isLoadingEligibility = false),
            onOptionSelected = {},
            onConfirm = {},
        )
    }
}
