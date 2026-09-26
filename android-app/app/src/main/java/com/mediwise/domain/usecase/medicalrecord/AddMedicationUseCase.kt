package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Medication
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class AddMedicationUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(
        patientId: String, name: String, dosage: String? = null, frequency: String? = null,
        startDate: String? = null, endDate: String? = null
    ): Result<Medication> = repo.addMedication(patientId, name, dosage, frequency, startDate, endDate)
}
