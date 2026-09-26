package com.mediwise.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.domain.usecase.consultation.GetMyConsultationsUseCase
import com.mediwise.domain.usecase.consultation.GetMyPrescriptionsUseCase
import com.mediwise.domain.usecase.consultation.GetPatientConsultationsUseCase
import com.mediwise.domain.usecase.consultation.GetPatientPrescriptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultationHistoryUiState(
    val isLoading: Boolean = false,
    val consultations: List<Consultation> = emptyList(),
    val prescriptionsByConsultation: Map<String, List<PrescriptionModel>> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class ConsultationHistoryViewModel @Inject constructor(
    private val getMyConsultationsUseCase: GetMyConsultationsUseCase,
    private val getPatientConsultationsUseCase: GetPatientConsultationsUseCase,
    private val getMyPrescriptionsUseCase: GetMyPrescriptionsUseCase,
    private val getPatientPrescriptionsUseCase: GetPatientPrescriptionsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationHistoryUiState())
    val uiState: StateFlow<ConsultationHistoryUiState> = _uiState.asStateFlow()

    fun load(patientId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val consultationsResult = if (patientId.isNullOrBlank()) {
                getMyConsultationsUseCase(size = 50)
            } else {
                getPatientConsultationsUseCase(patientId, size = 50)
            }
            val prescriptionsResult = if (patientId.isNullOrBlank()) {
                getMyPrescriptionsUseCase(size = 100)
            } else {
                getPatientPrescriptionsUseCase(patientId, size = 100)
            }

            when (consultationsResult) {
                is Result.Success -> {
                    val prescriptions = (prescriptionsResult as? Result.Success)?.data.orEmpty()
                    val grouped = prescriptions.groupBy { it.consultationId }
                    _uiState.update {
                        it.copy(isLoading = false, consultations = consultationsResult.data, prescriptionsByConsultation = grouped)
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = consultationsResult.exception.message ?: "Unable to load consultation history")
                }
                is Result.Loading -> Unit
            }
        }
    }
}
