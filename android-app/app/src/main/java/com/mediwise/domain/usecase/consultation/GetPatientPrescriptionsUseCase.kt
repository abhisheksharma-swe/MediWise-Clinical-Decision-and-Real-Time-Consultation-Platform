package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class GetPatientPrescriptionsUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(patientId: String, page: Int = 0, size: Int = 20): Result<List<PrescriptionModel>> =
        repo.getPrescriptionsForPatient(patientId, page, size)
}
