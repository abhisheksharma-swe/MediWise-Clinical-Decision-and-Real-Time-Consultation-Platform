package com.mediwise.domain.usecase.appointment

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.repository.AppointmentRepository
import javax.inject.Inject

class BookAppointmentUseCase @Inject constructor(private val repo: AppointmentRepository) {
    suspend operator fun invoke(doctorId: String, slotId: String, type: String, chiefComplaint: String? = null): Result<Appointment> =
        repo.bookAppointment(doctorId, slotId, type, chiefComplaint)
}
