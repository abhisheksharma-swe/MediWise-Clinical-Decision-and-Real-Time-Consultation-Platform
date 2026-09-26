package com.mediwise.presentation.screens.medicalrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.model.Condition
import com.mediwise.domain.model.Medication
import com.mediwise.domain.usecase.medicalrecord.AddAllergyUseCase
import com.mediwise.domain.usecase.medicalrecord.AddConditionUseCase
import com.mediwise.domain.usecase.medicalrecord.AddMedicationUseCase
import com.mediwise.domain.usecase.medicalrecord.AddMyAllergyUseCase
import com.mediwise.domain.usecase.medicalrecord.AddMyConditionUseCase
import com.mediwise.domain.usecase.medicalrecord.AddMyMedicationUseCase
import com.mediwise.domain.usecase.medicalrecord.GetMedicalRecordUseCase
import com.mediwise.domain.usecase.medicalrecord.GetMyMedicalRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicalRecordUiState(
    val isLoading: Boolean = false,
    val conditions: List<Condition> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MedicalRecordViewModel @Inject constructor(
    private val getMedicalRecordUseCase: GetMedicalRecordUseCase,
    private val getMyMedicalRecordUseCase: GetMyMedicalRecordUseCase,
    private val addConditionUseCase: AddConditionUseCase,
    private val addAllergyUseCase: AddAllergyUseCase,
    private val addMedicationUseCase: AddMedicationUseCase,
    private val addMyConditionUseCase: AddMyConditionUseCase,
    private val addMyAllergyUseCase: AddMyAllergyUseCase,
    private val addMyMedicationUseCase: AddMyMedicationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalRecordUiState())
    val uiState: StateFlow<MedicalRecordUiState> = _uiState.asStateFlow()

    private var patientId: String? = null

    fun load(patientId: String?) {
        this.patientId = patientId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = if (patientId.isNullOrBlank()) getMyMedicalRecordUseCase() else getMedicalRecordUseCase(patientId)
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        conditions = result.data.conditions,
                        allergies = result.data.allergies,
                        medications = result.data.medications
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun addCondition(name: String, diagnosedDate: String?, notes: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val pid = patientId
            val result = if (pid.isNullOrBlank()) addMyConditionUseCase(name, diagnosedDate, notes)
                else addConditionUseCase(pid, name, diagnosedDate, notes)
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isSaving = false, conditions = listOf(result.data) + it.conditions) }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun addAllergy(allergen: String, reaction: String?, severity: String?, notes: String?) {
        if (allergen.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val pid = patientId
            val result = if (pid.isNullOrBlank()) addMyAllergyUseCase(allergen, reaction, severity, notes)
                else addAllergyUseCase(pid, allergen, reaction, severity, notes)
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isSaving = false, allergies = listOf(result.data) + it.allergies) }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun addMedication(name: String, dosage: String?, frequency: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val pid = patientId
            val result = if (pid.isNullOrBlank()) addMyMedicationUseCase(name, dosage, frequency)
                else addMedicationUseCase(pid, name, dosage, frequency)
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isSaving = false, medications = listOf(result.data) + it.medications) }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }
}
