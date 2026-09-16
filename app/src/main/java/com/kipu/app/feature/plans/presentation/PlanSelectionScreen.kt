package com.kipu.app.feature.plans.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.*
import com.kipu.app.R
import com.kipu.app.ui.theme.KipuTheme

@Composable
fun PlanSelectionScreen(state: PlanSelectionUiState, onOptionSelected: (CommercialOption) -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.plans_subtitle))
        Card(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan-option-FREE")
                .selectable(state.selectedOption == CommercialOption.FREE, role = Role.RadioButton) { onOptionSelected(CommercialOption.FREE) },
            border = if (state.selectedOption == CommercialOption.FREE) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            shape = RoundedCornerShape(16.dp),
        ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kipu Free", style = MaterialTheme.typography.titleLarge); Text("S/ 0 de por vida", fontWeight = FontWeight.Bold)
            listOf("4 instrumentos", "5 categorías personalizadas", "2 deudas", "2 metas", "2 presupuestos").forEach { Text("• $it") }
        } }
        Text("Opciones Premium", style = MaterialTheme.typography.titleLarge)
        CommercialOption.entries.filterNot { it == CommercialOption.FREE }.forEach { option -> OptionCard(option, option == state.selectedOption, state.eligibility, onOptionSelected) }
        Text(stringResource(R.string.plans_play_disabled))
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onConfirm, enabled = !state.isConfirming, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.isConfirming) "Confirmando..." else stringResource(R.string.plans_confirm)) }
    }
}

@Composable private fun OptionCard(option: CommercialOption, selected: Boolean, eligibility: TrialEligibilitySnapshot, onSelect: (CommercialOption) -> Unit) {
    val price = when (option) { CommercialOption.FREE -> "S/ 0"; CommercialOption.MONTHLY -> "S/ 4.99"; CommercialOption.ANNUAL -> "S/ 29.99"; CommercialOption.LIFETIME -> "S/ 49.99" }
    Card(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan-option-${option.name}").selectable(selected, role = Role.RadioButton) { onSelect(option) }, border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(option.label, fontWeight = FontWeight.Bold)
            Text(price, modifier = Modifier.testTag("plan-price-${option.name}"), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            when (option) {
                CommercialOption.MONTHLY -> if (eligibility.isEligible()) Text("7 días; luego S/ 4.99 con renovación mensual. Cancela cuando quieras.") else Text("Oferta sujeta a verificación; renovación mensual y cancelación disponibles.")
                CommercialOption.ANNUAL -> if (eligibility.isEligible()) Text("7 días; luego S/ 29.99 con renovación anual. Cancela cuando quieras.") else Text("Oferta sujeta a verificación; renovación anual y cancelación disponibles.")
                CommercialOption.LIFETIME -> Text("Pago único, sin renovación")
                CommercialOption.FREE -> Text("Sin tarjeta, sin publicidad")
            }
        }
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
