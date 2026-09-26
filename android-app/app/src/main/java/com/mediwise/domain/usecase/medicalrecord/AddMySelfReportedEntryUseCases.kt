package com.mediwise.domain.usecase.medicalrecord

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.model.Condition
import com.mediwise.domain.model.Medication
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject

class AddMyConditionUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(name: String, diagnosedDate: String? = null, notes: String? = null): Result<Condition> =
        repo.addMyCondition(name, diagnosedDate, notes)
}

class AddMyAllergyUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(allergen: String, reaction: String? = null, severity: String? = null, notes: String? = null): Result<Allergy> =
        repo.addMyAllergy(allergen, reaction, severity, notes)
}

class AddMyMedicationUseCase @Inject constructor(private val repo: MedicalRecordRepository) {
    suspend operator fun invoke(name: String, dosage: String? = null, frequency: String? = null, startDate: String? = null, endDate: String? = null): Result<Medication> =
        repo.addMyMedication(name, dosage, frequency, startDate, endDate)
}
