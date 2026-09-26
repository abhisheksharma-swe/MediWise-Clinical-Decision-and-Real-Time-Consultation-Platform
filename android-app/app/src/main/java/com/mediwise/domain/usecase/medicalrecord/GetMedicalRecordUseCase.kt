package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.MedicalRecord
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class GetMedicalRecordUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(patientId: String): Result<MedicalRecord> = repo.getRecord(patientId)
}
