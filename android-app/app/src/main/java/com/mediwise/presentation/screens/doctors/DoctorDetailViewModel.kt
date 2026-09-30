package com.mediwise.presentation.screens.doctors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.model.Review
import com.mediwise.domain.model.SlotModel
import com.mediwise.domain.repository.DoctorRepository
import com.mediwise.domain.usecase.review.GetDoctorReviewsUseCase
import com.mediwise.domain.usecase.schedule.GetSlotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DoctorDetailUiState(
    val isLoading: Boolean = false,
    val doctor: Doctor? = null,
    val isFavorite: Boolean = false,
    val todaySlots: List<SlotModel> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DoctorDetailViewModel @Inject constructor(
    private val repository: DoctorRepository,
    private val getSlotsUseCase: GetSlotsUseCase,
    private val getDoctorReviewsUseCase: GetDoctorReviewsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorDetailUiState())
    val uiState: StateFlow<DoctorDetailUiState> = _uiState.asStateFlow()

    private var currentDoctorId: String? = null

    init {
        val doctorId = savedStateHandle.get<String>("doctorId")
        if (doctorId != null) {
            currentDoctorId = doctorId
            loadDoctor(doctorId)
        } else {
            _uiState.update { it.copy(error = "Doctor ID not found") }
        }
    }

    fun loadDoctor(id: String? = null) {
        val targetId = id ?: currentDoctorId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Fetch Doctor details
            when (val result = repository.getDoctorById(targetId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(doctor = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                    return@launch
                }
                is Result.Loading -> {}
            }

            // 2. Fetch today's slots
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            when (val slotResult = getSlotsUseCase(targetId, todayStr)) {
                is Result.Success -> {
                    val available = slotResult.data.filter { it.status == "AVAILABLE" }
                    _uiState.update { it.copy(todaySlots = available) }
                }
                is Result.Error -> {}
                is Result.Loading -> {}
            }

            // 3. Check if favorite
            when (val favResult = repository.getFavorites()) {
                is Result.Success -> {
                    val isFav = favResult.data.any { it.id == targetId }
                    _uiState.update { it.copy(isFavorite = isFav) }
                }
                is Result.Error -> {}
                is Result.Loading -> {}
            }

            // 4. Fetch reviews
            when (val reviewsResult = getDoctorReviewsUseCase(targetId)) {
                is Result.Success -> _uiState.update { it.copy(reviews = reviewsResult.data) }
                is Result.Error -> {}
                is Result.Loading -> {}
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleFavorite() {
        val doctorId = currentDoctorId ?: return
        viewModelScope.launch {
            val newFav = !_uiState.value.isFavorite
            _uiState.update { it.copy(isFavorite = newFav) }
            repository.toggleFavorite(doctorId)
        }
    }
}

