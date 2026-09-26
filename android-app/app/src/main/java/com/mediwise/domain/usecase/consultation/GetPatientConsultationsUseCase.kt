package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class GetPatientConsultationsUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(patientId: String, page: Int = 0, size: Int = 20): Result<List<Consultation>> =
        repo.getPatientHistory(patientId, page, size)
}
