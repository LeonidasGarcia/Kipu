package com.kipu.app.feature.plans.presentation

import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable fun PlanSelectionRoute(onConfirmed: () -> Unit, viewModel: PlanSelectionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect {
                if (it == PlanSelectionEvent.Confirmed) currentOnConfirmed()
            }
        }
    }
    PlanSelectionScreen(state, viewModel::onOptionSelected, viewModel::confirmSelection)
}
