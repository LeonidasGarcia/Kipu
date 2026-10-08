package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun DebtListScreen(
    debts: List<DebtSummary>,
    selectedType: DebtObligationType?,
    onTypeSelected: (DebtObligationType?) -> Unit,
    onDebtSelected: (String) -> Unit,
    onAddPayable: () -> Unit,
    onAddReceivable: () -> Unit,
    errorMessage: String? = null,
) {
    val filtered = debts.filter { selectedType == null || it.obligationType == selectedType }
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Deudas y préstamos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddPayable, modifier = Modifier.heightIn(min = 48.dp)) { Text("+ Agregar") }
            }
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onAddPayable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Me prestaron") }
                TextButton(onClick = onAddReceivable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Presté dinero") }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedType == null, { onTypeSelected(null) }, label = { Text("Todas") })
                FilterChip(selectedType == DebtObligationType.PAYABLE, { onTypeSelected(DebtObligationType.PAYABLE) }, label = { Text("Debo") })
                FilterChip(selectedType == DebtObligationType.RECEIVABLE, { onTypeSelected(DebtObligationType.RECEIVABLE) }, label = { Text("Me deben") })
            }
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Aquí aparecerán tus deudas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Registra lo que debes o lo que prestaste para seguir cada saldo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = DebtSummary::debtId) { debt ->
                        DebtListItem(debt, onClick = { onDebtSelected(debt.debtId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtListItem(debt: DebtSummary, onClick: () -> Unit) {
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()?.let { formatter.currency = it }
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(debt.counterpartyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (debt.obligationType == DebtObligationType.PAYABLE) "Debes" else "Te deben", style = MaterialTheme.typography.bodySmall)
                Text(
                    debtLifecycleLabel(debt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (debt.status == DebtLifecycleStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                debt.dueDate?.let { Text("Vence $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(formatter.format(debt.remainingPrincipalMinor / 100.0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun debtLifecycleLabel(debt: DebtSummary): String = when (debt.status) {
    DebtLifecycleStatus.ACTIVE -> "Activa"
    DebtLifecycleStatus.SETTLED -> "Liquidada"
    DebtLifecycleStatus.CANCELLED -> if (debt.remainingPrincipalMinor > 0L) {
        "Cancelada \u00b7 saldo pendiente no pagado"
    } else "Cancelada"
}
