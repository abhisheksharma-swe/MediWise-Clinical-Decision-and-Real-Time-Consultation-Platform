package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class UpsertConsultationUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(
        appointmentId: String,
        chiefComplaint: String? = null,
        symptoms: List<String>? = null,
        observations: String? = null,
        assessment: String? = null,
        treatmentPlan: String? = null,
        notes: String? = null
    ): Result<Consultation> =
        repo.upsert(appointmentId, chiefComplaint, symptoms, observations, assessment, treatmentPlan, notes)
}
