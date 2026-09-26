package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class GetConsultationUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(appointmentId: String): Result<Consultation?> = repo.getForAppointment(appointmentId)
}
