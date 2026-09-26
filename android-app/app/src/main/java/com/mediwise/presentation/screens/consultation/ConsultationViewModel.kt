package com.mediwise.presentation.screens.consultation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.domain.usecase.consultation.CreatePrescriptionUseCase
import com.mediwise.domain.usecase.consultation.GetConsultationUseCase
import com.mediwise.domain.usecase.consultation.UpsertConsultationUseCase
import com.mediwise.domain.usecase.followup.CreateFollowUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultationUiState(
    val isLoading: Boolean = false,
    val consultationId: String? = null,
    val chiefComplaint: String = "",
    val symptoms: List<String> = emptyList(),
    val observations: String = "",
    val assessment: String = "",
    val treatmentPlan: String = "",
    val notes: String = "",
    val prescriptionItems: List<PrescriptionItemModel> = emptyList(),
    val isSavingNotes: Boolean = false,
    val isSavingPrescription: Boolean = false,
    val notesSaved: Boolean = false,
    val prescriptionSaved: Boolean = false,
    val followUpReason: String = "",
    val followUpDate: String = "",
    val isSavingFollowUp: Boolean = false,
    val followUpSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConsultationViewModel @Inject constructor(
    private val getConsultationUseCase: GetConsultationUseCase,
    private val upsertConsultationUseCase: UpsertConsultationUseCase,
    private val createPrescriptionUseCase: CreatePrescriptionUseCase,
    private val createFollowUpUseCase: CreateFollowUpUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsultationUiState())
    val uiState: StateFlow<ConsultationUiState> = _uiState.asStateFlow()

    fun load(appointmentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getConsultationUseCase(appointmentId)) {
                is Result.Success -> {
                    val c: Consultation? = result.data
                    _uiState.update {
                        if (c == null) it.copy(isLoading = false)
                        else it.copy(
                            isLoading = false,
                            consultationId = c.id,
                            chiefComplaint = c.chiefComplaint,
                            symptoms = c.symptoms,
                            observations = c.observations,
                            assessment = c.assessment,
                            treatmentPlan = c.treatmentPlan,
                            notes = c.doctorNotes
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun updateChiefComplaint(value: String) = _uiState.update { it.copy(chiefComplaint = value) }
    fun updateObservations(value: String) = _uiState.update { it.copy(observations = value) }
    fun updateAssessment(value: String) = _uiState.update { it.copy(assessment = value) }
    fun updateTreatmentPlan(value: String) = _uiState.update { it.copy(treatmentPlan = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun addSymptom(symptom: String) {
        if (symptom.isBlank()) return
        _uiState.update { it.copy(symptoms = it.symptoms + symptom.trim()) }
    }

    fun removeSymptom(index: Int) {
        _uiState.update { it.copy(symptoms = it.symptoms.filterIndexed { i, _ -> i != index }) }
    }

    fun saveConsultation(appointmentId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isSavingNotes = true, error = null, notesSaved = false) }
            when (val result = upsertConsultationUseCase(
                appointmentId = appointmentId,
                chiefComplaint = state.chiefComplaint,
                symptoms = state.symptoms,
                observations = state.observations,
                assessment = state.assessment,
                treatmentPlan = state.treatmentPlan,
                notes = state.notes
            )) {
                is Result.Success -> _uiState.update {
                    it.copy(isSavingNotes = false, notesSaved = true, consultationId = result.data.id)
                }
                is Result.Error -> _uiState.update { it.copy(isSavingNotes = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun addPrescriptionItem() {
        _uiState.update { it.copy(prescriptionItems = it.prescriptionItems + PrescriptionItemModel(medicineName = "")) }
    }

    fun updatePrescriptionItem(index: Int, item: PrescriptionItemModel) {
        _uiState.update {
            it.copy(prescriptionItems = it.prescriptionItems.toMutableList().also { list -> list[index] = item })
        }
    }

    fun removePrescriptionItem(index: Int) {
        _uiState.update { it.copy(prescriptionItems = it.prescriptionItems.filterIndexed { i, _ -> i != index }) }
    }

    fun updateFollowUpReason(value: String) = _uiState.update { it.copy(followUpReason = value) }
    fun updateFollowUpDate(value: String) = _uiState.update { it.copy(followUpDate = value) }

    /** The consultation must be saved first (it needs an id) before a follow-up can attach to it. */
    fun submitFollowUp() {
        val consultationId = _uiState.value.consultationId ?: run {
            _uiState.update { it.copy(error = "Save the consultation notes before recommending a follow-up.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingFollowUp = true, error = null, followUpSaved = false) }
            val state = _uiState.value
            when (val result = createFollowUpUseCase(
                consultationId,
                state.followUpDate.ifBlank { null },
                state.followUpReason.ifBlank { null }
            )) {
                is Result.Success -> _uiState.update { it.copy(isSavingFollowUp = false, followUpSaved = true) }
                is Result.Error -> _uiState.update { it.copy(isSavingFollowUp = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    /** The consultation must be saved first (it needs an id) before a prescription can attach to it. */
    fun submitPrescription() {
        val consultationId = _uiState.value.consultationId ?: run {
            _uiState.update { it.copy(error = "Save the consultation notes before adding a prescription.") }
            return
        }
        val items = _uiState.value.prescriptionItems.filter { it.medicineName.isNotBlank() }
        if (items.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one medicine.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPrescription = true, error = null, prescriptionSaved = false) }
            when (val result = createPrescriptionUseCase(consultationId, notes = null, items = items)) {
                is Result.Success -> _uiState.update { it.copy(isSavingPrescription = false, prescriptionSaved = true) }
                is Result.Error -> _uiState.update { it.copy(isSavingPrescription = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }
}
