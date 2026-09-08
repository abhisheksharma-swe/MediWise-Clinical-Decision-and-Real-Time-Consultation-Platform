package com.mediwise.presentation.screens.appointment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppointmentDetailUiState(
    val isLoading: Boolean = false,
    val appointment: Appointment? = null,
    val isCancelling: Boolean = false,
    val cancelled: Boolean = false,
    /** Blank if the backend response didn't carry it (older cached data) - callers must treat blank as "calling unavailable". */
    val otherPartyUserId: String = "",
    val error: String? = null
)

@HiltViewModel
class AppointmentDetailViewModel @Inject constructor(
    private val repository: AppointmentRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppointmentDetailUiState())
    val uiState: StateFlow<AppointmentDetailUiState> = _uiState.asStateFlow()

    fun load(appointmentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getAppointmentById(appointmentId)) {
                is Result.Success -> {
                    val myId = sessionDataStore.userId.first().orEmpty()
                    val appt = result.data
                    // Identity-based rather than role-based - correct regardless of whether
                    // the current session's role happens to match the appointment's own fields.
                    val otherParty = if (myId.isNotBlank() && myId == appt.doctorUserId) appt.patientUserId else appt.doctorUserId
                    _uiState.update { it.copy(isLoading = false, appointment = appt, otherPartyUserId = otherParty) }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun cancel(appointmentId: String, reason: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true, error = null) }
            when (val result = repository.cancelAppointment(appointmentId, reason)) {
                is Result.Success -> _uiState.update {
                    it.copy(isCancelling = false, appointment = result.data, cancelled = true)
                }
                is Result.Error -> _uiState.update { it.copy(isCancelling = false, error = result.exception.message) }
                is Result.Loading -> {}
            }
        }
    }
}
