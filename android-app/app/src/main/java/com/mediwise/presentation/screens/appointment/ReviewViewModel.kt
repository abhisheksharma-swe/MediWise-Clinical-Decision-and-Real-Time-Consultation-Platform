package com.mediwise.presentation.screens.appointment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.repository.AppointmentRepository
import com.mediwise.domain.usecase.review.SubmitReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = false,
    val doctorName: String = "",
    val doctorSpecialty: String = "",
    val date: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val submitReviewUseCase: SubmitReviewUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun load(appointmentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = appointmentRepository.getAppointmentById(appointmentId)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorName = result.data.doctorName,
                        doctorSpecialty = result.data.doctorSpecialty,
                        date = result.data.date
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun submit(appointmentId: String, rating: Int, reviewText: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val result = submitReviewUseCase(appointmentId, rating, reviewText.ifBlank { null })) {
                is Result.Success -> _uiState.update { it.copy(isSubmitting = false, submitted = true) }
                is Result.Error -> _uiState.update { it.copy(isSubmitting = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }
}
