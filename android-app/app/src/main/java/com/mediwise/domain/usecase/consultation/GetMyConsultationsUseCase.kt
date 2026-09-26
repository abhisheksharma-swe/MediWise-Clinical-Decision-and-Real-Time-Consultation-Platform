package com.mediwise.domain.usecase.consultation

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Consultation
import com.mediwise.domain.repository.ConsultationRepository
import javax.inject.Inject

class GetMyConsultationsUseCase @Inject constructor(private val repo: ConsultationRepository) {
    suspend operator fun invoke(page: Int = 0, size: Int = 20): Result<List<Consultation>> = repo.getMyHistory(page, size)
}
