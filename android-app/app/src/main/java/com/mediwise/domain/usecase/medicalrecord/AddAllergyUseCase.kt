package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class AddAllergyUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(patientId: String, allergen: String, reaction: String? = null, severity: String? = null, notes: String? = null): Result<Allergy> =
        repo.addAllergy(patientId, allergen, reaction, severity, notes)
}
