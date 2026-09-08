package com.mediwise.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.data.remote.dto.SymptomLogRequestDto
import com.mediwise.domain.model.AiTriageReport
import com.mediwise.domain.repository.AiRepository
import com.mediwise.domain.usecase.profile.GetProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiTriageUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val patientId: String = "",
    val report: AiTriageReport? = null,
    val error: String? = null
)

@HiltViewModel
class AiTriageViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val getProfileUseCase: GetProfileUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiTriageUiState())
    val uiState: StateFlow<AiTriageUiState> = _uiState.asStateFlow()

    fun load(patientId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val resolvedPatientId = patientId?.takeIf { it.isNotBlank() } ?: when (val result = getProfileUseCase()) {
                is Result.Success -> result.data.id
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message ?: "Unable to load your profile") }
                    return@launch
                }
                is Result.Loading -> ""
            }

            if (resolvedPatientId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "Your patient profile is not ready yet") }
                return@launch
            }

            _uiState.update { it.copy(patientId = resolvedPatientId) }
            when (val result = aiRepository.getLatestReport(resolvedPatientId)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, report = result.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, report = null) }
                is Result.Loading -> Unit
            }
        }
    }

    fun submit(symptoms: String, severity: String, notes: String) {
        val patientId = _uiState.value.patientId
        val symptomList = symptoms.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (patientId.isBlank() || symptomList.isEmpty()) {
            _uiState.update { it.copy(error = "Enter at least one symptom") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val result = aiRepository.logSymptoms(
                SymptomLogRequestDto(
                    patientId = patientId,
                    symptoms = symptomList,
                    severity = severity.takeIf { it.isNotBlank() },
                    notes = notes.takeIf { it.isNotBlank() }
                )
            )) {
                is Result.Success -> _uiState.update { it.copy(isSubmitting = false, report = result.data) }
                is Result.Error -> _uiState.update { it.copy(isSubmitting = false, error = result.exception.message ?: "AI analysis failed") }
                is Result.Loading -> Unit
            }
        }
    }
}
