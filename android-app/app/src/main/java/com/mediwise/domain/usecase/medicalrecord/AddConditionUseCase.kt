package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Condition
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class AddConditionUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(patientId: String, name: String, diagnosedDate: String? = null, notes: String? = null): Result<Condition> =
        repo.addCondition(patientId, name, diagnosedDate, notes)
}
