package com.mediwise.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.model.Role
import com.mediwise.domain.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Appointment statuses a conversation can meaningfully exist for — matches the same
 * `isRoomParticipant` gate the backend applies (it's identity-based, not status-based),
 * but there's no point surfacing a chat entry for an appointment that was never confirmed. */
private const val CHAT_ELIGIBLE_STATUSES = "CONFIRMED,IN_PROGRESS,COMPLETED"

data class ConversationListUiState(
    val isLoading: Boolean = false,
    val isDoctor: Boolean = false,
    val conversations: List<Appointment> = emptyList(),
    val error: String? = null
)

/**
 * Chat rooms are 1:1 with appointments in this app (`"appointment_$id"`) — there's no
 * separate backend "list my chat rooms" resource, so this reuses the existing appointment
 * list endpoints (same ones AppointmentListScreen/DoctorScheduleScreen already call)
 * rather than inventing new backend surface.
 */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val isDoctor = sessionDataStore.userRoleEnum.first() == Role.DOCTOR

            val result = if (isDoctor) {
                appointmentRepository.getDoctorAppointments(status = CHAT_ELIGIBLE_STATUSES, page = 0, size = 50)
            } else {
                appointmentRepository.getMyAppointments(status = CHAT_ELIGIBLE_STATUSES, page = 0, size = 50)
            }

            when (result) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDoctor = isDoctor,
                    conversations = result.data
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDoctor = isDoctor,
                    error = result.exception.message
                )
                is Result.Loading -> Unit
            }
        }
    }
}
