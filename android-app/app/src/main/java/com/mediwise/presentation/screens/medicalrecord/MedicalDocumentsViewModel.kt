package com.mediwise.presentation.screens.medicalrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.MedicalDocument
import com.mediwise.domain.usecase.medicalrecord.GetMedicalDocumentsUseCase
import com.mediwise.domain.usecase.medicalrecord.GetMyMedicalRecordUseCase
import com.mediwise.domain.usecase.medicalrecord.UploadMedicalDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicalDocumentsUiState(
    val isLoading: Boolean = false,
    val documents: List<MedicalDocument> = emptyList(),
    val isUploading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MedicalDocumentsViewModel @Inject constructor(
    private val getMedicalDocumentsUseCase: GetMedicalDocumentsUseCase,
    private val getMyMedicalRecordUseCase: GetMyMedicalRecordUseCase,
    private val uploadMedicalDocumentUseCase: UploadMedicalDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalDocumentsUiState())
    val uiState: StateFlow<MedicalDocumentsUiState> = _uiState.asStateFlow()

    private var patientId: String = ""

    /** Null patientId means "my own documents" — resolved via the patient's own record first. */
    fun load(patientId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val resolvedId = if (!patientId.isNullOrBlank()) {
                patientId
            } else {
                when (val ownRecord = getMyMedicalRecordUseCase()) {
                    is Result.Success -> ownRecord.data.patientId
                    is Result.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = ownRecord.exception.message) }
                        return@launch
                    }
                    is Result.Loading -> return@launch
                }
            }
            this@MedicalDocumentsViewModel.patientId = resolvedId
            when (val result = getMedicalDocumentsUseCase(resolvedId)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, documents = result.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun upload(documentType: String, fileBytes: ByteArray, fileName: String, mimeType: String) {
        if (patientId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            when (val result = uploadMedicalDocumentUseCase(patientId, documentType, fileBytes, fileName, mimeType)) {
                is Result.Success -> _uiState.update { it.copy(isUploading = false, documents = listOf(result.data) + it.documents) }
                is Result.Error -> _uiState.update { it.copy(isUploading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }
}
