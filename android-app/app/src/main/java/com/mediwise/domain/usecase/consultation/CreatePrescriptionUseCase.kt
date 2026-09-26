package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.PrescriptionItemModel
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class CreatePrescriptionUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(consultationId: String, notes: String?, items: List<PrescriptionItemModel>): Result<PrescriptionModel> =
        repo.createPrescription(consultationId, notes, items)
}
