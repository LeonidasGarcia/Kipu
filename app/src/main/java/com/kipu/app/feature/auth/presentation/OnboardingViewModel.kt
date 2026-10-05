package com.kipu.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.auth.data.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val preferences: OnboardingPreferences) : ViewModel() {
    val checkpoint = preferences.checkpoint
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun savePage(page: Int) {
        viewModelScope.launch {
            try { preferences.savePage(page) } catch (_: IOException) {
                _error.value = "No se pudo guardar el progreso. Intenta nuevamente."
            }
        }
    }

    fun complete(onComplete: () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                preferences.complete()
                onComplete()
            } catch (_: IOException) {
                _error.value = "No se pudo guardar el progreso. Intenta nuevamente."
            } finally { _saving.value = false }
        }
    }
}
