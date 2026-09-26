package com.mediwise.domain.usecase.appointment

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Appointment
import com.mediwise.domain.repository.AppointmentRepository
import javax.inject.Inject

class RescheduleAppointmentUseCase @Inject constructor(private val repo: AppointmentRepository) {
    suspend operator fun invoke(id: String, newSlotId: String): Result<Appointment> =
        repo.rescheduleAppointment(id, newSlotId)
}
