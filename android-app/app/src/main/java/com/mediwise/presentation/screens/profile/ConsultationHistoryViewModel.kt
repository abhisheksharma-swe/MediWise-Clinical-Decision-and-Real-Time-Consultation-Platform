package com.mediwise.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.ConsultationRecord
import com.mediwise.domain.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultationHistoryUiState(
    val isLoading: Boolean = false,
    val records: List<ConsultationRecord> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ConsultationHistoryViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationHistoryUiState())
    val uiState: StateFlow<ConsultationHistoryUiState> = _uiState.asStateFlow()

    fun load(patientId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = if (patientId.isNullOrBlank()) {
                appointmentRepository.getMyConsultationHistory(size = 50)
            } else {
                appointmentRepository.getPatientConsultationHistory(patientId, size = 50)
            }
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, records = result.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message ?: "Unable to load consultation history") }
                is Result.Loading -> Unit
            }
        }
    }
}
