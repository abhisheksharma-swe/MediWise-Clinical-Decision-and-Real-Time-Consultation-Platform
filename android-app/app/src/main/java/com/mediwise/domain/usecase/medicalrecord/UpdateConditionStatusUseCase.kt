package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Condition
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class UpdateConditionStatusUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(conditionId: String, status: String): Result<Condition> =
        repo.updateConditionStatus(conditionId, status)
}
