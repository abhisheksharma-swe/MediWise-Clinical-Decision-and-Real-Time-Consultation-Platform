package com.mediwise.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediwise.core.result.Result
import com.mediwise.domain.model.SlotModel
import com.mediwise.domain.repository.DoctorRepository
import com.mediwise.domain.usecase.appointment.BookAppointmentUseCase
import com.mediwise.domain.usecase.appointment.RescheduleAppointmentUseCase
import com.mediwise.domain.usecase.schedule.GetSlotsUseCase
import com.mediwise.domain.usecase.schedule.LockSlotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val slots: List<SlotModel> = emptyList(),
    val isLocking: Boolean = false,
    val isBooking: Boolean = false,
    val lockedSlotId: String? = null,
    val doctorName: String = "",
    val consultationModes: List<String> = listOf("ONLINE"),
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getSlotsUseCase: GetSlotsUseCase,
    private val lockSlotUseCase: LockSlotUseCase,
    private val bookAppointmentUseCase: BookAppointmentUseCase,
    private val rescheduleAppointmentUseCase: RescheduleAppointmentUseCase,
    private val doctorRepository: DoctorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    fun loadDoctorName(doctorId: String) {
        viewModelScope.launch {
            when (val result = doctorRepository.getDoctorById(doctorId)) {
                is Result.Success -> _uiState.update {
                    it.copy(doctorName = result.data.fullName, consultationModes = result.data.consultationModes)
                }
                else -> {}
            }
        }
    }

    fun loadSlotsForDate(date: LocalDate, doctorId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            when (val result = getSlotsUseCase(doctorId, dateStr)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, slots = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * Locks the slot, then immediately books it (creates the PENDING appointment
     * the backend expects to be paid within its payment window). [onBooked] is
     * only invoked once a real appointment exists server-side — the caller
     * navigates to Payment with a genuine appointmentId, never a fabricated one.
     */
    fun lockAndBookSlot(slotId: String, doctorId: String, type: String, onBooked: (appointmentId: String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocking = true, error = null) }
            when (val lockResult = lockSlotUseCase(slotId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLocking = false, lockedSlotId = slotId, isBooking = true) }
                    when (val bookResult = bookAppointmentUseCase(doctorId = doctorId, slotId = slotId, type = type)) {
                        is Result.Success -> {
                            _uiState.update { it.copy(isBooking = false) }
                            onBooked(bookResult.data.id)
                        }
                        is Result.Error -> {
                            _uiState.update { it.copy(isBooking = false, error = bookResult.exception.message) }
                        }
                        is Result.Loading -> {}
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLocking = false, error = lockResult.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * Reschedule mode: locks the newly picked slot, then moves the existing appointment
     * onto it (instead of creating a new appointment via [lockAndBookSlot]).
     */
    fun lockAndRescheduleSlot(slotId: String, appointmentId: String, onRescheduled: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocking = true, error = null) }
            when (val lockResult = lockSlotUseCase(slotId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLocking = false, lockedSlotId = slotId, isBooking = true) }
                    when (val result = rescheduleAppointmentUseCase(appointmentId, slotId)) {
                        is Result.Success -> {
                            _uiState.update { it.copy(isBooking = false) }
                            onRescheduled()
                        }
                        is Result.Error -> {
                            _uiState.update { it.copy(isBooking = false, error = result.exception.message) }
                        }
                        is Result.Loading -> {}
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLocking = false, error = lockResult.exception.message) }
                }
                is Result.Loading -> {}
            }
        }
    }
}

