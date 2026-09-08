package com.mediwise.presentation.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.mediwise.core.datastore.SessionDataStore
import com.mediwise.core.fcm.FcmTokenRegistrationWorker
import com.mediwise.core.network.AppNotificationSocket
import com.mediwise.core.result.Result
import com.mediwise.core.result.onSuccess
import com.mediwise.domain.model.Doctor
import com.mediwise.domain.model.PatientProfile
import com.mediwise.domain.model.Role
import com.mediwise.domain.repository.AppointmentRepository
import com.mediwise.domain.repository.AuthRepository
import com.mediwise.domain.repository.DoctorRepository
import com.mediwise.domain.repository.NotificationRepository
import com.mediwise.domain.usecase.appointment.GetMyAppointmentsUseCase
import com.mediwise.domain.usecase.doctor.GetMyDoctorProfileUseCase
import com.mediwise.domain.usecase.doctor.UpdateMyDoctorProfileUseCase
import com.mediwise.domain.usecase.profile.GetProfileUseCase
import com.mediwise.domain.usecase.profile.UpdateProfileUseCase
import com.mediwise.domain.usecase.profile.UploadProfileImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val profile: PatientProfile? = null,
    val doctorProfile: Doctor? = null,
    val totalAppointments: Int = 0,
    val totalDoctors: Int = 0,
    val calculatedAge: String = "--",
    val updateSuccess: Boolean = false,
    val error: String? = null,
    val role: Role? = null
) {
    val isDoctor: Boolean get() = role == Role.DOCTOR
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getProfileUseCase: GetProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val getMyDoctorProfileUseCase: GetMyDoctorProfileUseCase,
    private val updateMyDoctorProfileUseCase: UpdateMyDoctorProfileUseCase,
    private val uploadProfileImageUseCase: UploadProfileImageUseCase,
    private val doctorRepository: DoctorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val getMyAppointmentsUseCase: GetMyAppointmentsUseCase,
    private val notificationRepository: NotificationRepository,
    private val sessionDataStore: SessionDataStore,
    private val appNotificationSocket: AppNotificationSocket,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, updateSuccess = false) }

            val role = sessionDataStore.userRoleEnum.first()
            _uiState.update { it.copy(role = role) }

            // Patient- and doctor-facing profile data live on entirely separate backend
            // resources (/api/v1/profile vs /api/v1/doctors/me) with no fields in common
            // beyond a name — calling the patient endpoint for a doctor account (or vice
            // versa) would silently create a stray, unused PatientProfile row for that
            // doctor. Route strictly by role instead.
            if (role == Role.DOCTOR) {
                loadDoctorProfile()
            } else {
                loadPatientProfile()
            }
        }
    }

    private suspend fun loadPatientProfile() {
        // 1. Fetch Profile Data via GetProfileUseCase
        when (val result = getProfileUseCase()) {
            is Result.Success -> {
                val profile = result.data
                val age = calculateAge(profile.dateOfBirth)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        calculatedAge = age
                    )
                }
            }
            is Result.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
            }
            is Result.Loading -> {}
        }

        // 2. Fetch Total Appointments & Consulted Doctors Count
        getMyAppointmentsUseCase(page = 0, size = 50).onSuccess { appts ->
            val distinctDocIds = appts.map { it.doctorId }.filter { it.isNotBlank() }.toSet()
            _uiState.update {
                it.copy(
                    totalAppointments = appts.size,
                    totalDoctors = maxOf(distinctDocIds.size, it.totalDoctors)
                )
            }
        }

        // 3. Fetch Favorites Count
        doctorRepository.getFavorites().onSuccess { favs ->
            _uiState.update {
                it.copy(totalDoctors = maxOf(it.totalDoctors, favs.size))
            }
        }
    }

    private suspend fun loadDoctorProfile() {
        when (val result = getMyDoctorProfileUseCase()) {
            is Result.Success -> {
                _uiState.update { it.copy(isLoading = false, doctorProfile = result.data) }
            }
            is Result.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
            }
            is Result.Loading -> {}
        }

        // Appointments this doctor has conducted (their own schedule, not a patient's bookings).
        appointmentRepository.getDoctorAppointments(status = null, page = 0, size = 50).onSuccess { appts ->
            _uiState.update { it.copy(totalAppointments = appts.size) }
        }
    }

    fun updateProfile(
        fullName: String,
        dob: String,
        bloodType: String,
        gender: String,
        address: String,
        emergencyContact: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = updateProfileUseCase(
                fullName = fullName,
                dob = dob,
                bloodType = bloodType,
                gender = gender,
                address = address,
                emergencyContact = emergencyContact
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, updateSuccess = true) }
                    loadProfile()
                    onComplete(true)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
                    onComplete(false)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun updateDoctorProfile(
        fullName: String,
        specialty: String,
        bio: String,
        experienceYears: String,
        consultationFee: String,
        available: Boolean,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = updateMyDoctorProfileUseCase(
                fullName = fullName,
                specialty = specialty,
                bio = bio,
                experienceYears = experienceYears,
                consultationFee = consultationFee,
                available = available
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, updateSuccess = true, doctorProfile = result.data) }
                    onComplete(true)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.exception.message) }
                    onComplete(false)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String = "image/jpeg") {
        // The avatar-upload endpoint (/api/v1/profile/image) is a patient-profile resource;
        // doctor photos are managed separately (e.g. via the admin panel) and have no
        // equivalent self-service endpoint yet, so this is a no-op for doctor accounts.
        if (_uiState.value.isDoctor) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingImage = true, error = null) }
            when (val res = uploadProfileImageUseCase(imageBytes, mimeType)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isUploadingImage = false,
                            profile = state.profile?.copy(profileImageUrl = res.data)
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isUploadingImage = false, error = res.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun calculateAge(dobStr: String): String {
        if (dobStr.isBlank()) return "--"
        return try {
            val dob = LocalDate.parse(dobStr.trim(), DateTimeFormatter.ISO_DATE)
            val years = Period.between(dob, LocalDate.now()).years
            if (years in 0..130) "$years" else "--"
        } catch (_: Exception) {
            "--"
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Unregister this device's FCM token and stop any pending registration retry
            // before the session (and the device id state it needs) is cleared.
            val registeredToken = sessionDataStore.registeredFcmToken.first()
                ?: sessionDataStore.pendingFcmToken.first()
            if (!registeredToken.isNullOrBlank()) {
                val deviceId = sessionDataStore.getOrCreateDeviceId()
                notificationRepository.unregisterFcmToken(registeredToken, deviceId)
            }
            WorkManager.getInstance(appContext).cancelUniqueWork(FcmTokenRegistrationWorker.WORK_NAME)
            appNotificationSocket.stop()

            authRepository.logout()
        }
    }
}


