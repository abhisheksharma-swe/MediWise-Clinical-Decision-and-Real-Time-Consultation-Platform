package com.mediwise.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.network.AppNotificationSocket
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.model.Role
import com.mediwise.core.result.onSuccess
import com.mediwise.core.result.onError
import com.mediwise.domain.repository.AppointmentRepository
import com.mediwise.domain.repository.DoctorRepository
import com.mediwise.domain.repository.NotificationRepository
import com.mediwise.domain.usecase.appointment.GetMyAppointmentsUseCase
import com.mediwise.domain.usecase.doctor.GetDoctorsUseCase
import com.mediwise.domain.usecase.profile.GetProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val isDoctor: Boolean = false,
    val userName: String = "User",
    val unreadNotifications: Int = 0,
    val upcomingAppointments: List<Appointment> = emptyList(),
    val topDoctors: List<Doctor> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDoctorsUseCase: GetDoctorsUseCase,
    private val getAppointmentsUseCase: GetMyAppointmentsUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val doctorRepository: DoctorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val notificationRepository: NotificationRepository,
    private val sessionDataStore: SessionDataStore,
    private val appNotificationSocket: AppNotificationSocket
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        // App-wide realtime notification subscription: established once the user reaches
        // Home post-login, torn down on logout (see ProfileViewModel.logout()).
        appNotificationSocket.start()
        viewModelScope.launch {
            appNotificationSocket.events.collect {
                _uiState.update { state -> state.copy(unreadNotifications = state.unreadNotifications + 1) }
            }
        }
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val isDoctor = sessionDataStore.userRoleEnum.first() == Role.DOCTOR
            _uiState.update { it.copy(isDoctor = isDoctor) }

            if (isDoctor) {
                loadDoctorHomeData()
            } else {
                loadPatientHomeData()
            }

            // Unread notification count applies to both roles equally.
            notificationRepository.getNotifications(page = 0, size = 50)
                .onSuccess { notifications ->
                    _uiState.update { it.copy(unreadNotifications = notifications.count { n -> !n.isRead }) }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadPatientHomeData() {
        getProfileUseCase().onSuccess { prof ->
            val name = prof.fullName
            if (name.isNotBlank()) {
                _uiState.update { it.copy(userName = name) }
            }
        }

        getDoctorsUseCase(page = 0, size = 5)
            .onSuccess { docs -> _uiState.update { it.copy(topDoctors = docs) } }
            .onError { e -> _uiState.update { it.copy(error = e.message) } }

        getAppointmentsUseCase(status = "PENDING,CONFIRMED", page = 0, size = 5)
            .onSuccess { appts -> _uiState.update { it.copy(upcomingAppointments = appts) } }
    }

    // Patient- and doctor-facing profile/appointment data live on entirely separate
    // backend resources (see ProfileViewModel for the same split) — calling the patient
    // endpoint for a doctor account would silently create a stray, unused PatientProfile
    // row, and "top doctors to browse" simply doesn't apply to a doctor's own dashboard.
    private suspend fun loadDoctorHomeData() {
        doctorRepository.getMyDoctorProfile().onSuccess { doc ->
            if (doc.fullName.isNotBlank()) {
                _uiState.update { it.copy(userName = "Dr. ${doc.fullName}") }
            }
        }

        appointmentRepository.getDoctorAppointments(status = "PENDING,CONFIRMED,IN_PROGRESS", page = 0, size = 5)
            .onSuccess { appts -> _uiState.update { it.copy(upcomingAppointments = appts) } }
    }

    fun refresh() = loadHomeData()
}

