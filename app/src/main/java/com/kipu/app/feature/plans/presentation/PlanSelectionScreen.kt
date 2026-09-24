package com.kipu.app.feature.plans.presentation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.kipu.app.R
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.ui.theme.KipuPrimaryContainer
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuSelectionRing
import com.kipu.app.ui.theme.KipuSurfaceContainerLowest
import com.kipu.app.ui.theme.KipuMotionTokens

private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(12.dp)

@Composable
fun PlanSelectionScreen(
    state: PlanSelectionUiState,
    onOptionSelected: (CommercialOption) -> Unit,
    onConfirm: () -> Unit,
    onContinueFree: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.plan_selection_title), style = MaterialTheme.typography.displayMedium)
        Text(
            stringResource(R.string.plan_selection_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        FreePlanCard()
        PremiumTrialCard()

        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumOptionCard(
                option = CommercialOption.ANNUAL,
                title = R.string.plan_annual_title,
                price = R.string.plan_annual_price,
                summary = R.string.plan_annual_summary,
                selected = state.selectedOption == CommercialOption.ANNUAL,
                badge = R.string.plan_annual_badge,
                supporting = R.string.plan_annual_savings,
                onSelected = onOptionSelected,
            )
            PremiumOptionCard(
                option = CommercialOption.MONTHLY,
                title = R.string.plan_monthly_title,
                price = R.string.plan_monthly_price,
                summary = R.string.plan_monthly_summary,
                selected = state.selectedOption == CommercialOption.MONTHLY,
                onSelected = onOptionSelected,
            )
            PremiumOptionCard(
                option = CommercialOption.LIFETIME,
                title = R.string.plan_lifetime_title,
                price = R.string.plan_lifetime_price,
                summary = R.string.plan_lifetime_summary,
                selected = state.selectedOption == CommercialOption.LIFETIME,
                badge = R.string.plan_lifetime_badge,
                onSelected = onOptionSelected,
            )
        }

        CommercialInfoNote()

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onConfirm,
            enabled = !state.isConfirming,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(containerColor = KipuPrimaryContainer),
        ) {
            if (state.isConfirming) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isConfirming) stringResource(R.string.plan_confirming) else stringResource(R.string.plan_confirm))
        }

        FilledTonalButton(
            onClick = onContinueFree,
            enabled = !state.isConfirming,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = ControlShape,
        ) {
            Text(stringResource(R.string.plan_continue_free))
        }

        Text(
            stringResource(R.string.plan_scope_note),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FreePlanCard() {
    val features = listOf(
        R.string.plan_free_feature_core,
        R.string.plan_free_feature_instruments,
        R.string.plan_free_feature_categories,
        R.string.plan_free_feature_debts,
        R.string.plan_free_feature_goals,
        R.string.plan_free_feature_budgets,
    )
    Card(
        modifier = Modifier.fillMaxWidth().testTag("plan-free-card"),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_free_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                PlanBadge(R.string.plan_free_badge)
            }
            Text(stringResource(R.string.plan_free_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.plan_free_no_payment), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            features.forEach { feature -> FreeFeatureRow(feature) }
        }
    }
}

@Composable
private fun FreeFeatureRow(@StringRes feature: Int) {
    val included = stringResource(R.string.plan_included_content_description)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).semantics { contentDescription = included },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(feature), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PremiumTrialCard() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("plan-trial-card"),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_trial_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                PlanBadge(R.string.plan_trial_badge)
            }
            Text(stringResource(R.string.plan_trial_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumOptionCard(
    option: CommercialOption,
    @StringRes title: Int,
    @StringRes price: Int,
    @StringRes summary: Int,
    selected: Boolean,
    onSelected: (CommercialOption) -> Unit,
    @StringRes badge: Int? = null,
    @StringRes supporting: Int? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(KipuMotionTokens.FeedbackMillis),
        label = "plan-option-border-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(KipuMotionTokens.FeedbackMillis),
        label = "plan-option-border-width",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(option) })
            .minimumInteractiveComponentSize()
            .testTag("plan-option-${option.name}"),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    badge?.let { PlanBadge(it) }
                }
                Text(
                    stringResource(price),
                    modifier = Modifier.testTag("plan-price-${option.name}"),
                    style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                )
                Text(stringResource(summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                supporting?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CommercialInfoNote() {
    val description = stringResource(R.string.plan_trial_info_content_description)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ControlShape,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(48.dp).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.plan_trial_info),
                modifier = Modifier.weight(1f).padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PlanBadge(@StringRes label: Int) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(999.dp)) {
        Text(
            stringResource(label),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(name = "Pantalla 1B", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PlanSelectionScreenPreview() {
    KipuTheme {
        PlanSelectionScreen(
            state = PlanSelectionUiState(isLoadingEligibility = false),
            onOptionSelected = {},
            onConfirm = {},
            onContinueFree = {},
        )
    }
}
