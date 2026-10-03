package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
fun MovementAccessCard(
    state: MovementHistoryUiState,
    onViewPlans: () -> Unit,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotionEnabled(),
) {
    val show = state.fallbackUsed || state.recovery != HistoryAccessRecovery.IDLE
    val content = when (state.recovery) {
        HistoryAccessRecovery.VERIFYING -> AccessCardContent.VERIFYING
        HistoryAccessRecovery.NO_PURCHASE -> AccessCardContent.NO_PURCHASE
        HistoryAccessRecovery.PENDING -> AccessCardContent.PENDING
        HistoryAccessRecovery.RETRYABLE -> AccessCardContent.RETRY
        HistoryAccessRecovery.NO_ACCESS -> AccessCardContent.PREMIUM
        HistoryAccessRecovery.VERIFIED -> if (state.accessStatus == MovementHistoryAccessDecision.Allowed) AccessCardContent.SUCCESS else AccessCardContent.RECONNECT
        HistoryAccessRecovery.IDLE -> if (state.accessStatus is MovementHistoryAccessDecision.RevalidationRequired) AccessCardContent.RECONNECT else AccessCardContent.PREMIUM
    }
    AnimatedVisibility(
        visible = show,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis)),
        modifier = modifier,
    ) {
        val colors = rememberKipuColors()
        val warning = content in setOf(AccessCardContent.RECONNECT, AccessCardContent.RETRY, AccessCardContent.NO_PURCHASE)
        Surface(
            color = if (warning) colors.warningContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("banner_fallback_used"),
        ) {
            AnimatedContent(
                targetState = content,
                transitionSpec = {
                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(tween(KipuMotionTokens.QuickMillis)) togetherWith fadeOut(tween(KipuMotionTokens.MicroMillis))
                },
                label = "historyAccessFeedback",
            ) { target ->
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (target == AccessCardContent.SUCCESS) Icons.Default.CheckCircle else if (target == AccessCardContent.PREMIUM) Icons.Default.Lock else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(stringResource(target.title), style = MaterialTheme.typography.titleSmall,
                                color = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(target.body), style = MaterialTheme.typography.bodyMedium,
                                color = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (target) {
                        AccessCardContent.VERIFYING -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        AccessCardContent.SUCCESS -> TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.history_dismiss_notice))
                        }
                        else -> {
                            OutlinedButton(
                                onClick = if (target == AccessCardContent.PREMIUM) onViewPlans else onVerify,
                                enabled = target == content && state.recovery != HistoryAccessRecovery.VERIFYING,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_revalidate_premium"),
                            ) {
                                Text(stringResource(if (target == AccessCardContent.PREMIUM) R.string.history_view_premium
                                    else if (target == AccessCardContent.RECONNECT) R.string.history_access_verify else R.string.history_retry))
                            }
                            if (target == AccessCardContent.NO_PURCHASE) TextButton(onClick = onViewPlans, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.history_view_premium))
                            }
                            if (target == AccessCardContent.PREMIUM) TextButton(onClick = onVerify,
                                enabled = target == content && state.recovery != HistoryAccessRecovery.VERIFYING,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_restore_history")) {
                                Text(stringResource(R.string.history_restore_purchases))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AccessCardContent(val title: Int, val body: Int) {
    PREMIUM(R.string.history_access_premium_title, R.string.history_access_premium_body),
    RECONNECT(R.string.history_access_revalidate_title, R.string.history_access_revalidate_body),
    VERIFYING(R.string.history_access_verifying, R.string.history_access_verifying_body),
    SUCCESS(R.string.history_access_success, R.string.history_access_success_body),
    RETRY(R.string.history_access_retry_title, R.string.history_access_retry_body),
    NO_PURCHASE(R.string.history_access_no_purchase, R.string.history_access_no_purchase_body),
    PENDING(R.string.history_access_pending, R.string.history_access_pending_body),
}
