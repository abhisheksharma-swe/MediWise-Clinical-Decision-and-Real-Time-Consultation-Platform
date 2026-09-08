package com.mediwise.presentation.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorScheduleUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val appointments: List<Appointment> = emptyList(),
    val selectedStatus: String = "Upcoming",
    val isUpdating: Boolean = false
)

@HiltViewModel
class DoctorScheduleViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorScheduleUiState())
    val uiState: StateFlow<DoctorScheduleUiState> = _uiState.asStateFlow()

    init {
        loadAppointments()
    }

    fun loadAppointments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val statusMap = mapOf(
                "Upcoming" to "PENDING,CONFIRMED,IN_PROGRESS",
                "Completed" to "COMPLETED",
                "Cancelled" to "CANCELLED"
            )
            val backendStatus = statusMap[_uiState.value.selectedStatus]

            when (val result = repository.getDoctorAppointments(backendStatus)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, appointments = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onTabSelected(status: String) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadAppointments()
    }

    fun startAppointment(appointmentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = repository.startAppointment(appointmentId)
            _uiState.update { it.copy(isUpdating = false) }

            if (result is Result.Success) {
                loadAppointments() // reload after starting consultation
            } else if (result is Result.Error) {
                _uiState.update { it.copy(error = result.exception.message) }
            }
        }
    }

    fun completeAppointment(appointmentId: String, notes: String, diagnosis: String?, prescription: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = repository.completeAppointment(appointmentId, notes, diagnosis, prescription)
            _uiState.update { it.copy(isUpdating = false) }

            if (result is Result.Success) {
                loadAppointments() // reload after completing consultation
            } else if (result is Result.Error) {
                _uiState.update { it.copy(error = result.exception.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
