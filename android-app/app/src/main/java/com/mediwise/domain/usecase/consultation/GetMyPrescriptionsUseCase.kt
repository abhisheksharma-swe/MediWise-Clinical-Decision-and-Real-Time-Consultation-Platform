package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.PrescriptionModel
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class GetMyPrescriptionsUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(page: Int = 0, size: Int = 20): Result<List<PrescriptionModel>> = repo.getMyPrescriptions(page, size)
}
