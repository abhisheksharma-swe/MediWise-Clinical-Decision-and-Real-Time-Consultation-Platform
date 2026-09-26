package com.mediwise.data.repository

import com.mediwise.core.network.safeApiCall
import com.mediwise.core.result.Result
import com.mediwise.data.remote.api.MedicalRecordApi
import com.mediwise.data.remote.dto.CreateAllergyRequestDto
import com.mediwise.data.remote.dto.CreateConditionRequestDto
import com.mediwise.data.remote.dto.CreateMedicationRequestDto
import com.mediwise.data.remote.dto.UpdateConditionStatusRequestDto
import com.mediwise.data.remote.dto.toDomain
import com.mediwise.domain.model.Allergy
import com.mediwise.domain.model.Condition
import com.mediwise.domain.model.MedicalRecord
import com.mediwise.domain.model.Medication
import com.mediwise.domain.repository.MedicalRecordRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalRecordRepositoryImpl @Inject constructor(
    private val api: MedicalRecordApi
) : MedicalRecordRepository {

    override suspend fun getRecord(patientId: String): Result<MedicalRecord> {
        return safeApiCall {
            api.getRecord(patientId).data?.toDomain() ?: throw Exception("Empty medical record response")
        }
    }

    override suspend fun getMyRecord(): Result<MedicalRecord> {
        return safeApiCall {
            api.getMyRecord().data?.toDomain() ?: throw Exception("Empty medical record response")
        }
    }

    override suspend fun addCondition(patientId: String, name: String, diagnosedDate: String?, notes: String?): Result<Condition> {
        return safeApiCall {
            val response = api.addCondition(patientId, CreateConditionRequestDto(name, diagnosedDate, notes))
            response.data?.toDomain() ?: throw Exception("Empty condition response")
        }
    }

    override suspend fun addAllergy(patientId: String, allergen: String, reaction: String?, severity: String?, notes: String?): Result<Allergy> {
        return safeApiCall {
            val response = api.addAllergy(patientId, CreateAllergyRequestDto(allergen, reaction, severity, notes))
            response.data?.toDomain() ?: throw Exception("Empty allergy response")
        }
    }

    override suspend fun addMedication(patientId: String, name: String, dosage: String?, frequency: String?, startDate: String?, endDate: String?): Result<Medication> {
        return safeApiCall {
            val response = api.addMedication(patientId, CreateMedicationRequestDto(name, dosage, frequency, startDate, endDate))
            response.data?.toDomain() ?: throw Exception("Empty medication response")
        }
    }

    override suspend fun addMyCondition(name: String, diagnosedDate: String?, notes: String?): Result<Condition> {
        return safeApiCall {
            val response = api.addMyCondition(CreateConditionRequestDto(name, diagnosedDate, notes))
            response.data?.toDomain() ?: throw Exception("Empty condition response")
        }
    }

    override suspend fun addMyAllergy(allergen: String, reaction: String?, severity: String?, notes: String?): Result<Allergy> {
        return safeApiCall {
            val response = api.addMyAllergy(CreateAllergyRequestDto(allergen, reaction, severity, notes))
            response.data?.toDomain() ?: throw Exception("Empty allergy response")
        }
    }

    override suspend fun addMyMedication(name: String, dosage: String?, frequency: String?, startDate: String?, endDate: String?): Result<Medication> {
        return safeApiCall {
            val response = api.addMyMedication(CreateMedicationRequestDto(name, dosage, frequency, startDate, endDate))
            response.data?.toDomain() ?: throw Exception("Empty medication response")
        }
    }

    override suspend fun updateConditionStatus(conditionId: String, status: String): Result<Condition> {
        return safeApiCall {
            val response = api.updateConditionStatus(conditionId, UpdateConditionStatusRequestDto(status))
            response.data?.toDomain() ?: throw Exception("Empty condition response")
        }
    }
}
