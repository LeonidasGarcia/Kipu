package com.kipu.app.feature.plans.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun PlanPurchaseRoute(
    onNavigateBack: () -> Unit,
    viewModel: PlanPurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduceMotion = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    PlanPurchaseScreen(
        state = state,
        onOfferSelected = viewModel::selectOffer,
        onPurchase = { context.findActivity()?.let(viewModel::startPurchase) },
        onRetryVerification = viewModel::retryVerification,
        onReloadCatalog = viewModel::refreshCatalog,
        onManageSubscriptions = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=com.kipu.app")))
            }
        },
        onNavigateBack = onNavigateBack,
        reduceMotion = reduceMotion,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
