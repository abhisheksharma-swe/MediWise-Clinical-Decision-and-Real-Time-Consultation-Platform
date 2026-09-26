package com.mediwise.domain.repository

import com.mediwise.core.result.Result
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.model.Condition
import com.mediwise.domain.model.MedicalRecord
import com.mediwise.domain.model.Medication

interface MedicalRecordRepository {
    suspend fun getRecord(patientId: String): Result<MedicalRecord>
    suspend fun getMyRecord(): Result<MedicalRecord>
    suspend fun addCondition(patientId: String, name: String, diagnosedDate: String?, notes: String?): Result<Condition>
    suspend fun addAllergy(patientId: String, allergen: String, reaction: String?, severity: String?, notes: String?): Result<Allergy>
    suspend fun addMedication(patientId: String, name: String, dosage: String?, frequency: String?, startDate: String?, endDate: String?): Result<Medication>
    suspend fun addMyCondition(name: String, diagnosedDate: String?, notes: String?): Result<Condition>
    suspend fun addMyAllergy(allergen: String, reaction: String?, severity: String?, notes: String?): Result<Allergy>
    suspend fun addMyMedication(name: String, dosage: String?, frequency: String?, startDate: String?, endDate: String?): Result<Medication>
    suspend fun updateConditionStatus(conditionId: String, status: String): Result<Condition>
}
